package com.aionyxe.filebridge.data.server

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.data.logs.LogRepository
import com.aionyxe.filebridge.data.logs.LogType
import com.aionyxe.filebridge.data.server.cert.CertificateManagerImpl
import com.aionyxe.filebridge.di.ApplicationScope
import com.aionyxe.filebridge.di.IoDispatcher
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.model.ServerStats
import com.aionyxe.filebridge.domain.network.ConnectivityStatus
import com.aionyxe.filebridge.domain.network.NetworkInfoProvider
import com.aionyxe.filebridge.domain.server.CertificateManager
import com.aionyxe.filebridge.domain.server.FtpServerController
import com.aionyxe.filebridge.domain.server.ServerEvent
import com.aionyxe.filebridge.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ssl.SslConfigurationFactory
import com.aionyxe.filebridge.util.formatBytes
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of [FtpServerController] backed by Apache FTPServer.
 *
 * Thread-safety contract:
 * - [start] and [stop] are suspend functions called from the same coroutine context (IO).
 *   They mutate [_state] and [ftpServer] only while holding [serverLock].
 * - FTPServer callbacks (Ftplet) call [_connectedClientCount] and [eventBus] from their own
 *   thread pool; both are thread-safe.
 */
@Singleton
class FtpServerControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val certificateManager: CertificateManager,
    private val logRepository: LogRepository,
    private val networkInfoProvider: NetworkInfoProvider,
    private val eventBus: ServerEventBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : FtpServerController {

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    override val state: StateFlow<ServerState> = _state.asStateFlow()

    override val events: SharedFlow<ServerEvent> = eventBus.events

    private val _connectedClientCount = MutableStateFlow(0)
    override val connectedClientCount: StateFlow<Int> = _connectedClientCount.asStateFlow()

    private val _stats = MutableStateFlow(ServerStats())
    override val stats: StateFlow<ServerStats> = _stats.asStateFlow()

    private val serverLock = Any()

    @Volatile
    private var ftpServer: org.apache.ftpserver.FtpServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var eventCollectorJob: Job? = null
    private var clientCountObserverJob: Job? = null
    private var connectivityMonitorJob: Job? = null

    // ---- FtpServerController ----

    override suspend fun start(config: ServerConfig): Unit = withContext(ioDispatcher) {
        // Atomically check-and-set to Starting inside the lock so that two concurrent
        // start() calls cannot both pass the guard before either writes Starting.
        synchronized(serverLock) {
            val current = _state.value
            if (current is ServerState.Running || current is ServerState.Starting) return@withContext
            _state.value = ServerState.Starting
        }

        val wifiIp = networkInfoProvider.currentWifiIpAddress()
            ?: run {
                _state.value = ServerState.Error(context.getString(R.string.error_no_wifi))
                return@withContext
            }

        try {
            // Pre-load password so AppUserManager can do sync comparison.
            val resolvedPassword = config.password

            // Resolve SSL keystore before building server (may generate cert on first run).
            val sslConfig = if (config.protocol == Protocol.FTPS) {
                CertificateManagerImpl.ensureBouncyCastleRegistered()
                val certManagerImpl = certificateManager as CertificateManagerImpl
                certManagerImpl.getOrCreate() // ensure persisted to disk
                val keystoreFile = certManagerImpl.keystoreFile()
                val keystorePassword = certManagerImpl.keystorePassword()
                SslConfigurationFactory().apply {
                    this.keystoreFile = keystoreFile
                    setKeystorePassword(keystorePassword)
                    setKeystoreType("BKS")
                    setClientAuthentication("NONE")
                    setSslProtocol("TLS")
                }.createSslConfiguration()
            } else {
                null
            }

            val userManager = AppUserManager(
                config = config,
                resolvedPassword = resolvedPassword,
                eventBus = eventBus,
            )

            _connectedClientCount.value = 0
            _stats.value = ServerStats()
            val ftplet = EventBridgeFtplet(eventBus, _connectedClientCount)

            val dataConnConfig = DataConnectionConfigurationFactory().apply {
                setPassivePorts("${config.pasvMinPort}-${config.pasvMaxPort}")
                passiveExternalAddress = wifiIp
                // Bound the lifetime of an idle data connection so a stalled transfer in a
                // large batch cannot hold its passive port open indefinitely and starve later
                // PASV commands. The library closes the data socket per file; this guards the
                // case where a peer goes away mid-transfer.
                idleTime = DATA_CONNECTION_IDLE_SECONDS
            }.createDataConnectionConfiguration()

            val listenerFactory = ListenerFactory().apply {
                port = config.port
                serverAddress = wifiIp
                dataConnectionConfiguration = dataConnConfig
                // Drop control connections that go silent so dead clients are reaped instead
                // of accumulating across a long-running session.
                idleTimeout = CONTROL_IDLE_SECONDS
                if (sslConfig != null) {
                    setSslConfiguration(sslConfig)
                    isImplicitSsl = false // Explicit TLS (AUTH TLS)
                }
            }

            val ftplets: LinkedHashMap<String, org.apache.ftpserver.ftplet.Ftplet> =
                LinkedHashMap<String, org.apache.ftpserver.ftplet.Ftplet>().also {
                    it["eventBridge"] = ftplet
                }

            // Apache FtpServer disables anonymous login at the protocol level by default, so a
            // client sending "USER anonymous" is rejected before AppUserManager is ever consulted.
            // Enable it explicitly when the app is in anonymous mode; keep it disabled otherwise so
            // anonymous probes (e.g. Windows Explorer) are correctly refused.
            val anonymousEnabled = config.authMode == AuthMode.ANONYMOUS
            val connectionConfig = ConnectionConfigFactory().apply {
                isAnonymousLoginEnabled = anonymousEnabled
                // Disable the concurrent-login caps. Apache FtpServer leaks its in-memory
                // login counters on abnormal client disconnects (very common with Windows
                // Explorer, which opens/abandons many connections), and once a cap is hit it
                // refuses ALL further logins with "421 max login limit" until the server is
                // restarted. maxLogins = 0 means unlimited (that check is guarded by != 0); the
                // anonymous check has no such sentinel, so it gets an effectively-unlimited value.
                maxLogins = 0
                maxAnonymousLogins = Int.MAX_VALUE
                // Brute-force hardening (adopted from prim-ftpd).
                maxLoginFailures = MAX_LOGIN_FAILURES
                loginFailureDelay = LOGIN_FAILURE_DELAY_MS
            }.createConnectionConfig()

            val serverFactory = FtpServerFactory().apply {
                this.connectionConfig = connectionConfig
                addListener("default", listenerFactory.createListener())
                setUserManager(userManager)
                setFtplets(ftplets)
            }

            val server = serverFactory.createServer()
            server.start()

            synchronized(serverLock) { ftpServer = server }

            // Acquire a partial wake lock to keep the CPU alive during transfers, and a
            // high-performance Wi-Fi lock so the radio does not drop into power-save (or
            // disconnect) when the screen turns off mid-batch — the most common cause of
            // silent failures on large transfers. Both carry a safety timeout so a missed
            // release can never leak the lock indefinitely; they are released symmetrically
            // in stop()/onDestroy() and the catch block below.
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(LOCK_TIMEOUT_MS)
            }

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }

            // Collect bus events → LogRepository.
            eventCollectorJob = appScope.launch(ioDispatcher) {
                eventBus.events.collect { event ->
                    updateStats(event)
                    logRepository.append(event.toLogEntry(context))
                }
            }

            // Mirror connectedClientCount into Running state.
            val initialState = ServerState.Running(
                address = wifiIp,
                port = config.port,
                protocol = config.protocol,
                connectedClients = 0,
            )
            _state.value = initialState

            clientCountObserverJob = appScope.launch {
                _connectedClientCount.collect { count ->
                    val cur = _state.value
                    if (cur is ServerState.Running) {
                        _state.value = cur.copy(connectedClients = count)
                    }
                }
            }

            // The server is bound to the current Wi-Fi address; if Wi-Fi drops the listener can
            // no longer be reached, so stop the engine instead of leaving a zombie server that
            // appears "running" but accepts nothing. The first emission reflects the current
            // (connected) state, so the guard below ignores it.
            connectivityMonitorJob = appScope.launch(ioDispatcher) {
                networkInfoProvider.connectivity.collect { status ->
                    if (status != ConnectivityStatus.WIFI_CONNECTED &&
                        _state.value is ServerState.Running
                    ) {
                        appScope.launch(ioDispatcher) {
                            shutdownToError(context.getString(R.string.error_wifi_lost))
                        }
                    }
                }
            }

            logRepository.append(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = LogType.SERVER_STARTED,
                    message = context.getString(
                        R.string.log_server_started,
                        wifiIp,
                        config.port.toString(),
                    ),
                ),
            )
        } catch (e: Exception) {
            releaseLocks()
            eventCollectorJob?.cancel()
            clientCountObserverJob?.cancel()
            connectivityMonitorJob?.cancel()
            synchronized(serverLock) { ftpServer = null }
            _state.value = ServerState.Error(
                e.message ?: context.getString(R.string.error_server_start_failed),
            )
        }
    }

    override suspend fun stop(): Unit = withContext(ioDispatcher) {
        val current = _state.value
        if (current is ServerState.Stopped || current is ServerState.Stopping) return@withContext

        _state.value = ServerState.Stopping
        shutdownEngine()

        logRepository.append(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                type = LogType.SERVER_STOPPED,
                message = context.getString(R.string.log_server_stopped),
            ),
        )

        _state.value = ServerState.Stopped
    }

    /**
     * Stops the engine because an external precondition failed (currently: Wi-Fi was lost) and
     * leaves the controller in an [ServerState.Error] state so the user sees why it stopped, rather
     * than a silent transition to [ServerState.Stopped].
     */
    private suspend fun shutdownToError(message: String): Unit = withContext(ioDispatcher) {
        val current = _state.value
        if (current !is ServerState.Running && current !is ServerState.Starting) return@withContext

        _state.value = ServerState.Stopping
        shutdownEngine()

        logRepository.append(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                type = LogType.SERVER_STOPPED,
                message = message,
            ),
        )

        _state.value = ServerState.Error(message)
    }

    // ---- Helpers ----

    /** Folds a transfer event into the running [stats] counters. */
    private fun updateStats(event: ServerEvent) {
        _stats.update { s ->
            when (event) {
                is ServerEvent.FileUploaded ->
                    s.copy(filesTransferred = s.filesTransferred + 1, bytesTransferred = s.bytesTransferred + event.size)

                is ServerEvent.FileDownloaded ->
                    s.copy(filesTransferred = s.filesTransferred + 1, bytesTransferred = s.bytesTransferred + event.size)

                is ServerEvent.TransferFailed ->
                    s.copy(failedTransfers = s.failedTransfers + 1)

                else -> s
            }
        }
    }

    /**
     * Tears down the Apache engine, cancels all observer jobs, and releases the locks. Idempotent
     * and safe to call from any shutdown path ([stop] or [shutdownToError]); a second call is a
     * no-op because [ftpServer] is already null.
     */
    private suspend fun shutdownEngine() {
        val server = synchronized(serverLock) { ftpServer }
        if (server != null) {
            try {
                server.suspend()
                // Allow in-flight transfers up to 5 s to drain.
                delay(GRACEFUL_SHUTDOWN_MS)
                server.stop()
            } catch (_: Exception) {
                // Best-effort; proceed to cleanup regardless.
            } finally {
                synchronized(serverLock) { ftpServer = null }
            }
        }

        eventCollectorJob?.cancel()
        clientCountObserverJob?.cancel()
        connectivityMonitorJob?.cancel()
        eventCollectorJob = null
        clientCountObserverJob = null
        connectivityMonitorJob = null
        _connectedClientCount.value = 0
        releaseLocks()
    }

    /**
     * Releases the wake lock and Wi-Fi lock symmetrically. Each release is guarded so a failure
     * to release one never prevents releasing the other, and a double-release can never throw.
     */
    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
    }

    private companion object {
        const val WAKE_LOCK_TAG = "FileBridge::FtpServer"
        const val WIFI_LOCK_TAG = "FileBridge::FtpServerWifi"
        const val GRACEFUL_SHUTDOWN_MS = 5_000L

        /** Safety timeout on the wake lock so a missed release cannot drain the battery. */
        const val LOCK_TIMEOUT_MS = 60 * 60 * 1000L // 1 hour

        /** Idle data-connection timeout, in seconds. */
        const val DATA_CONNECTION_IDLE_SECONDS = 300

        /** Idle control-connection timeout, in seconds. */
        const val CONTROL_IDLE_SECONDS = 300

        /** Failed-auth attempts before the connection is dropped. */
        const val MAX_LOGIN_FAILURES = 5

        /** Delay (ms) applied after a failed auth, to slow brute-force attempts. */
        const val LOGIN_FAILURE_DELAY_MS = 1000
    }
}

// ---- Extension: ServerEvent → LogEntry ----

private fun ServerEvent.toLogEntry(context: Context): LogEntry {
    val now = System.currentTimeMillis()
    return when (this) {
        is ServerEvent.ClientConnected ->
            LogEntry(now, LogType.CLIENT_CONNECTED, context.getString(R.string.log_client_connected, ip), ip)

        is ServerEvent.ClientDisconnected -> {
            val message = if (filesTransferred == 0 && failedCount == 0) {
                context.getString(R.string.log_client_disconnected, ip)
            } else {
                val parts = mutableListOf(
                    context.getString(R.string.log_summary_files, filesTransferred),
                    formatBytes(totalBytes),
                )
                if (failedCount > 0) parts.add(context.getString(R.string.log_summary_failed, failedCount))
                context.getString(R.string.log_client_disconnected_summary, ip, parts.joinToString(" · "))
            }
            LogEntry(now, LogType.CLIENT_DISCONNECTED, message, ip)
        }

        is ServerEvent.FileUploaded ->
            LogEntry(now, LogType.FILE_UPLOADED, context.getString(R.string.log_file_uploaded, ip, path, formatBytes(size)), ip)

        is ServerEvent.FileDownloaded ->
            LogEntry(now, LogType.FILE_DOWNLOADED, context.getString(R.string.log_file_downloaded, ip, path, formatBytes(size)), ip)

        is ServerEvent.TransferFailed -> {
            val res = if (upload) R.string.log_upload_failed else R.string.log_download_failed
            LogEntry(now, LogType.TRANSFER_FAILED, context.getString(res, ip, path), ip)
        }

        is ServerEvent.AuthFailure ->
            LogEntry(now, LogType.AUTH_FAILURE, context.getString(R.string.log_auth_failure, ip, username), ip)
    }
}

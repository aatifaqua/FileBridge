package com.aionyxe.filebridge.data.server

import android.content.Context
import android.os.PowerManager
import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.data.logs.LogRepository
import com.aionyxe.filebridge.data.logs.LogType
import com.aionyxe.filebridge.data.server.cert.CertificateManagerImpl
import com.aionyxe.filebridge.di.ApplicationScope
import com.aionyxe.filebridge.di.IoDispatcher
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.model.ServerState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.ssl.SslConfigurationFactory
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

    private val serverLock = Any()

    @Volatile
    private var ftpServer: org.apache.ftpserver.FtpServer? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var eventCollectorJob: Job? = null
    private var clientCountObserverJob: Job? = null

    // ---- FtpServerController ----

    override suspend fun start(config: ServerConfig): Unit = withContext(ioDispatcher) {
        synchronized(serverLock) {
            val current = _state.value
            if (current is ServerState.Running || current is ServerState.Starting) return@withContext
        }

        val wifiIp = networkInfoProvider.currentWifiIpAddress()
            ?: run {
                _state.value = ServerState.Error(context.getString(R.string.error_no_wifi))
                return@withContext
            }

        _state.value = ServerState.Starting

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
            val ftplet = EventBridgeFtplet(eventBus, _connectedClientCount)

            val dataConnConfig = DataConnectionConfigurationFactory().apply {
                setPassivePorts("${config.pasvMinPort}-${config.pasvMaxPort}")
                passiveExternalAddress = wifiIp
            }.createDataConnectionConfiguration()

            val listenerFactory = ListenerFactory().apply {
                port = config.port
                serverAddress = wifiIp
                dataConnectionConfiguration = dataConnConfig
                if (sslConfig != null) {
                    setSslConfiguration(sslConfig)
                    isImplicitSsl = false // Explicit TLS (AUTH TLS)
                }
            }

            val ftplets: LinkedHashMap<String, org.apache.ftpserver.ftplet.Ftplet> =
                LinkedHashMap<String, org.apache.ftpserver.ftplet.Ftplet>().also {
                    it["eventBridge"] = ftplet
                }

            val serverFactory = FtpServerFactory().apply {
                addListener("default", listenerFactory.createListener())
                setUserManager(userManager)
                setFtplets(ftplets)
            }

            val server = serverFactory.createServer()
            server.start()

            synchronized(serverLock) { ftpServer = server }

            // Acquire partial wake lock to keep CPU alive during transfers.
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                acquire(/* no timeout — released in stop() */ 0L)
            }

            // Collect bus events → LogRepository.
            eventCollectorJob = appScope.launch(ioDispatcher) {
                eventBus.events.collect { event ->
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

            logRepository.append(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = LogType.SERVER_STARTED,
                    message = context.getString(
                        R.string.log_server_started,
                        wifiIp,
                        config.port.toString(),
                        config.protocol.name,
                    ),
                ),
            )
        } catch (e: Exception) {
            releaseWakeLock()
            eventCollectorJob?.cancel()
            clientCountObserverJob?.cancel()
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
        eventCollectorJob = null
        clientCountObserverJob = null
        _connectedClientCount.value = 0
        releaseWakeLock()

        logRepository.append(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                type = LogType.SERVER_STOPPED,
                message = context.getString(R.string.log_server_stopped),
            ),
        )

        _state.value = ServerState.Stopped
    }

    // ---- Helpers ----

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val WAKE_LOCK_TAG = "FileBridge::FtpServer"
        const val GRACEFUL_SHUTDOWN_MS = 5_000L
    }
}

// ---- Extension: ServerEvent → LogEntry ----

private fun ServerEvent.toLogEntry(context: Context): LogEntry {
    val now = System.currentTimeMillis()
    return when (this) {
        is ServerEvent.ClientConnected ->
            LogEntry(now, LogType.CLIENT_CONNECTED, context.getString(R.string.log_client_connected), ip)

        is ServerEvent.ClientDisconnected ->
            LogEntry(now, LogType.CLIENT_DISCONNECTED, context.getString(R.string.log_client_disconnected), ip)

        is ServerEvent.FileUploaded ->
            LogEntry(now, LogType.FILE_UPLOADED, context.getString(R.string.log_file_uploaded, path), ip)

        is ServerEvent.FileDownloaded ->
            LogEntry(now, LogType.FILE_DOWNLOADED, context.getString(R.string.log_file_downloaded, path), ip)

        is ServerEvent.AuthFailure ->
            LogEntry(now, LogType.AUTH_FAILURE, context.getString(R.string.log_auth_failure, username), ip)
    }
}

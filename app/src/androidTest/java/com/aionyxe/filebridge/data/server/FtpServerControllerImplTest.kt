package com.aionyxe.filebridge.data.server

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.aionyxe.filebridge.data.logs.LogRepositoryImpl
import com.aionyxe.filebridge.data.server.cert.CertificateManagerImpl
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.server.ServerEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Integration tests for [FtpServerControllerImpl] using Apache Commons Net FTP client over
 * loopback. Requires a real Android runtime (instrumented).
 *
 * PASV external address is forced to 127.0.0.1 via [TestNetworkInfoProvider] so that data
 * connections loop back correctly even without Wi-Fi.
 */
@RunWith(AndroidJUnit4::class)
class FtpServerControllerImplTest {

    private lateinit var context: Context
    private lateinit var controller: FtpServerControllerImpl
    private lateinit var eventBus: ServerEventBus
    private lateinit var certManager: CertificateManagerImpl
    private val testScope = TestScope()
    private val testDispatcher = StandardTestDispatcher(testScope.testScheduler)

    companion object {
        const val LOOPBACK = "127.0.0.1"
        const val TEST_PORT = 12121
        const val PASV_MIN = 52000
        const val PASV_MAX = 52100
        const val TEST_USER = "testuser"
        const val TEST_PASS = "testpass"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clean up cert state.
        context.filesDir.resolve(CertificateManagerImpl.KEYSTORE_FILE_NAME).delete()

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
            context,
            "test_server_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        securePrefs.edit().clear().commit()

        eventBus = ServerEventBus()
        certManager = CertificateManagerImpl(context, securePrefs, Dispatchers.IO)

        controller = FtpServerControllerImpl(
            context = context,
            certificateManager = certManager,
            logRepository = LogRepositoryImpl(),
            networkInfoProvider = TestNetworkInfoProvider(LOOPBACK),
            eventBus = eventBus,
            ioDispatcher = Dispatchers.IO,
            appScope = testScope,
        )
    }

    @After
    fun tearDown() = runTest {
        controller.stop()
    }

    // ---- Plain FTP ----

    @Test
    fun plainFtp_anonymousMode_connectListUploadDownload() = runTest(testDispatcher) {
        val config = buildConfig(
            protocol = Protocol.FTP,
            authMode = AuthMode.ANONYMOUS,
            accessMode = AccessMode.READ_WRITE,
        )

        controller.start(config)
        assertTrue(controller.state.value is ServerState.Running)

        val client = FTPClient()
        client.connect(LOOPBACK, TEST_PORT)
        assertTrue(FTPReply.isPositiveCompletion(client.replyCode))
        assertTrue(client.login("anonymous", ""))

        client.enterLocalPassiveMode()

        // List root (should succeed).
        val files = client.listFiles()
        assertFalse("listFiles returned null", files == null)

        // Upload a small file.
        val data = "Hello FileBridge!".toByteArray()
        val uploaded = client.storeFile("hello.txt", ByteArrayInputStream(data))
        assertTrue("STOR failed", uploaded)

        // Download it back.
        val out = ByteArrayOutputStream()
        client.retrieveFile("hello.txt", out)
        assertEquals(String(data), out.toString())

        client.logout()
        client.disconnect()
    }

    @Test
    fun plainFtp_singleUserMode_authFailureEmitted() = runTest(testDispatcher) {
        val config = buildConfig(
            protocol = Protocol.FTP,
            authMode = AuthMode.SINGLE_USER,
            accessMode = AccessMode.READ_WRITE,
        )

        controller.start(config)

        val events = mutableListOf<ServerEvent>()
        val job = launch { eventBus.events.collect { events.add(it) } }

        val client = FTPClient()
        client.connect(LOOPBACK, TEST_PORT)
        client.login(TEST_USER, "WRONG_PASSWORD")
        client.disconnect()

        // Wait for disconnect event to propagate.
        kotlinx.coroutines.delay(200)
        job.cancel()

        assertTrue(
            "Expected AuthFailure event",
            events.any { it is ServerEvent.AuthFailure && it.username == TEST_USER },
        )
    }

    @Test
    fun plainFtp_readOnlyMode_storRejected() = runTest(testDispatcher) {
        val config = buildConfig(
            protocol = Protocol.FTP,
            authMode = AuthMode.ANONYMOUS,
            accessMode = AccessMode.READ_ONLY,
        )

        controller.start(config)

        val client = FTPClient()
        client.connect(LOOPBACK, TEST_PORT)
        client.login("anonymous", "")
        client.enterLocalPassiveMode()

        val stored = client.storeFile("forbidden.txt", ByteArrayInputStream("x".toByteArray()))
        assertFalse("STOR should be rejected in READ_ONLY mode", stored)

        client.logout()
        client.disconnect()
    }

    @Test
    fun connectedClientCount_incrementsOnConnect_decrementsOnDisconnect() = runTest(testDispatcher) {
        val config = buildConfig(Protocol.FTP, AuthMode.ANONYMOUS, AccessMode.READ_ONLY)

        controller.start(config)
        assertEquals(0, controller.connectedClientCount.value)

        val client = FTPClient()
        client.connect(LOOPBACK, TEST_PORT)
        client.login("anonymous", "")

        kotlinx.coroutines.delay(300)
        assertEquals(1, controller.connectedClientCount.value)

        client.logout()
        client.disconnect()

        kotlinx.coroutines.delay(300)
        assertEquals(0, controller.connectedClientCount.value)
    }

    @Test
    fun serverState_transitionsCorrectly() = runTest(testDispatcher) {
        val config = buildConfig(Protocol.FTP, AuthMode.ANONYMOUS, AccessMode.READ_ONLY)

        assertEquals(ServerState.Stopped, controller.state.value)
        controller.start(config)
        assertTrue(controller.state.value is ServerState.Running)

        controller.stop()
        assertEquals(ServerState.Stopped, controller.state.value)
    }

    // ---- FTPS (Explicit TLS) ----

    @Test
    fun ftps_explicitTls_connectsWithSelfSignedCert() = runTest(testDispatcher) {
        val config = buildConfig(Protocol.FTPS, AuthMode.ANONYMOUS, AccessMode.READ_ONLY)
        controller.start(config)
        assertTrue(controller.state.value is ServerState.Running)

        // Build a trust manager that accepts our self-signed cert.
        val trustingTrustManager = buildTrustingTrustManager()

        val ftpsClient = FTPSClient("TLS", false /* explicit */)
        ftpsClient.trustManager = trustingTrustManager

        ftpsClient.connect(LOOPBACK, TEST_PORT)
        val replyCode = ftpsClient.replyCode
        assertTrue(
            "FTPS connect should succeed; replyCode=$replyCode",
            FTPReply.isPositiveCompletion(replyCode),
        )

        ftpsClient.execAUTH("TLS")
        ftpsClient.execPBSZ(0)
        ftpsClient.execPROT("P")

        assertTrue(ftpsClient.login("anonymous", ""))
        ftpsClient.enterLocalPassiveMode()

        val files = ftpsClient.listFiles()
        assertFalse("listFiles returned null in FTPS mode", files == null)

        ftpsClient.logout()
        ftpsClient.disconnect()
    }

    // ---- Helpers ----

    private fun buildConfig(
        protocol: Protocol,
        authMode: AuthMode,
        accessMode: AccessMode,
    ) = ServerConfig(
        protocol = protocol,
        port = TEST_PORT,
        pasvMinPort = PASV_MIN,
        pasvMaxPort = PASV_MAX,
        authMode = authMode,
        username = TEST_USER,
        password = TEST_PASS,
        rootDir = context.filesDir,
        accessMode = accessMode,
    )

    private fun buildTrustingTrustManager(): X509TrustManager =
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
}

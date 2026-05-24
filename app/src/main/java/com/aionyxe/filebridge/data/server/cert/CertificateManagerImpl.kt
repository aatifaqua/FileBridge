package com.aionyxe.filebridge.data.server.cert

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aionyxe.filebridge.di.IoDispatcher
import com.aionyxe.filebridge.domain.model.CertificateInfo
import com.aionyxe.filebridge.domain.server.CertificateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificateManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePrefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CertificateManager {

    // ---- Public API ----

    override suspend fun getOrCreate(): KeyStore = withContext(ioDispatcher) {
        ensureBouncyCastleRegistered()
        val file = keystoreFile()
        val password = getOrCreatePassword()
        if (file.exists()) {
            runCatching { loadKeyStore(file, password) }.getOrNull()
                ?: generateAndSave(file, password)
        } else {
            generateAndSave(file, password)
        }
    }

    override suspend fun regenerate() = withContext(ioDispatcher) {
        ensureBouncyCastleRegistered()
        keystoreFile().delete()
        val password = getOrCreatePassword()
        generateAndSave(keystoreFile(), password)
        Unit
    }

    override suspend fun info(): CertificateInfo? = withContext(ioDispatcher) {
        ensureBouncyCastleRegistered()
        val file = keystoreFile()
        if (!file.exists()) return@withContext null
        runCatching {
            val ks = loadKeyStore(file, getOrCreatePassword())
            val alias = ks.aliases().nextElement() ?: return@runCatching null
            val cert = ks.getCertificate(alias) as? X509Certificate
                ?: return@runCatching null
            CertificateInfo(
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                expiresAt = cert.notAfter.time,
                sha256Fingerprint = cert.sha256Fingerprint(),
            )
        }.getOrNull()
    }

    override suspend fun keystorePassword(): String = withContext(ioDispatcher) {
        getOrCreatePassword()
    }

    // ---- Internal helpers ----

    /** Absolute path to the BKS keystore on disk; accessible from [FtpServerControllerImpl]. */
    fun keystoreFile(): File = context.filesDir.resolve(KEYSTORE_FILE_NAME)

    private fun getOrCreatePassword(): String {
        val existing = securePrefs.getString(KEY_KEYSTORE_PASSWORD, null)
        if (existing != null) return existing
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hex = bytes.joinToString("") { "%02x".format(it) }
        securePrefs.edit { putString(KEY_KEYSTORE_PASSWORD, hex) }
        return hex
    }

    private fun loadKeyStore(file: File, password: String): KeyStore {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE, PROVIDER_NAME)
        file.inputStream().use { ks.load(it, password.toCharArray()) }
        return ks
    }

    private fun generateAndSave(file: File, password: String): KeyStore {
        // Generate RSA 2048 key pair.
        val keyGen = KeyPairGenerator.getInstance("RSA", PROVIDER_NAME)
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        // Build self-signed X.509 certificate valid for 10 years.
        val now = Date()
        val tenYearsMs = 10L * 365 * 24 * 60 * 60 * 1000
        val expiry = Date(now.time + tenYearsMs)
        val subject = X500Name("CN=FileBridge")
        val serial = BigInteger.valueOf(System.currentTimeMillis())

        val certBuilder = JcaX509v3CertificateBuilder(
            subject, serial, now, expiry, subject, keyPair.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(PROVIDER_NAME)
            .build(keyPair.private)
        val cert = JcaX509CertificateConverter()
            .setProvider(PROVIDER_NAME)
            .getCertificate(certBuilder.build(signer))

        // Store in BKS keystore.
        val ks = KeyStore.getInstance(KEYSTORE_TYPE, PROVIDER_NAME)
        ks.load(null, null)
        ks.setKeyEntry(CERT_ALIAS, keyPair.private, password.toCharArray(), arrayOf(cert))
        file.outputStream().use { ks.store(it, password.toCharArray()) }
        return ks
    }

    private fun X509Certificate.sha256Fingerprint(): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    companion object {
        const val KEYSTORE_FILE_NAME = "ftps_keystore.bks"
        const val CERT_ALIAS = "ftpserver"
        private const val KEYSTORE_TYPE = "BKS"
        private const val PROVIDER_NAME = BouncyCastleProvider.PROVIDER_NAME
        private const val KEY_KEYSTORE_PASSWORD = "cert_keystore_password"

        @Volatile
        private var bcRegistered = false

        fun ensureBouncyCastleRegistered() {
            if (bcRegistered) return
            synchronized(CertificateManagerImpl::class.java) {
                if (bcRegistered) return
                if (Security.getProvider(PROVIDER_NAME) == null) {
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                }
                bcRegistered = true
            }
        }
    }
}

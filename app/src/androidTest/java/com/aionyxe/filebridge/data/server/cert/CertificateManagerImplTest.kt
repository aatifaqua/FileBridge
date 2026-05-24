package com.aionyxe.filebridge.data.server.cert

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CertificateManagerImplTest {

    private lateinit var context: Context
    private lateinit var securePrefs: SharedPreferences
    private lateinit var manager: CertificateManagerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Wipe keystore file and prefs between tests.
        context.filesDir.resolve(CertificateManagerImpl.KEYSTORE_FILE_NAME).delete()

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        securePrefs = EncryptedSharedPreferences.create(
            context,
            "test_cert_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        securePrefs.edit().clear().commit()

        manager = CertificateManagerImpl(
            context = context,
            securePrefs = securePrefs,
            ioDispatcher = StandardTestDispatcher(),
        )
    }

    @Test
    fun getOrCreate_isIdempotent() = runTest {
        val ks1 = manager.getOrCreate()
        val ks2 = manager.getOrCreate()

        // Same alias present both times.
        assertTrue(ks1.containsAlias(CertificateManagerImpl.CERT_ALIAS))
        assertTrue(ks2.containsAlias(CertificateManagerImpl.CERT_ALIAS))

        // Fingerprints match.
        val info1 = manager.info()
        val info2 = manager.info()
        assertNotNull(info1)
        assertEquals(info1!!.sha256Fingerprint, info2!!.sha256Fingerprint)
    }

    @Test
    fun regenerate_changesCertFingerprint() = runTest {
        manager.getOrCreate()
        val before = manager.info()!!.sha256Fingerprint

        manager.regenerate()
        val after = manager.info()!!.sha256Fingerprint

        assertNotEquals("Fingerprint must change after regenerate", before, after)
    }

    @Test
    fun certValidity_isAtLeast10Years() = runTest {
        manager.getOrCreate()
        val info = manager.info()!!
        val tenYearsMs = TimeUnit.DAYS.toMillis(365 * 10)
        val expectedMinExpiry = System.currentTimeMillis() + tenYearsMs - TimeUnit.DAYS.toMillis(1)
        assertTrue(
            "Cert should expire in ~10 years; expiresAt=${info.expiresAt} min=$expectedMinExpiry",
            info.expiresAt >= expectedMinExpiry,
        )
    }

    @Test
    fun keystorePassword_persistsAcrossInstances() = runTest {
        val pw1 = manager.keystorePassword()
        // New instance sharing same prefs.
        val manager2 = CertificateManagerImpl(
            context = context,
            securePrefs = securePrefs,
            ioDispatcher = StandardTestDispatcher(),
        )
        val pw2 = manager2.keystorePassword()
        assertEquals(pw1, pw2)
    }

    @Test
    fun keystoreFile_existsAfterGetOrCreate() = runTest {
        manager.getOrCreate()
        val file: File = manager.keystoreFile()
        assertTrue("Keystore file should exist on disk", file.exists())
        assertTrue("Keystore file should not be empty", file.length() > 0)
    }
}

package com.aionyxe.filebridge.data.credentials

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class CredentialsRepositoryImplTest {

    private lateinit var context: Context
    private lateinit var repo: CredentialsRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteSharedPreferences(PREFS_NAME)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        repo = CredentialsRepositoryImpl(prefs, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(PREFS_NAME)
    }

    @Test
    fun setGetClear() = runTest {
        assertNull(repo.getPassword())
        repo.setPassword("s3cr3t-pass")
        assertEquals("s3cr3t-pass", repo.getPassword())
        repo.clear()
        assertNull(repo.getPassword())
    }

    @Test
    fun passwordIsNotStoredInPlaintext() = runTest {
        val marker = "PLAINTEXT_MARKER_9f3a"
        repo.setPassword(marker)

        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val leaks = prefsDir.listFiles()?.any { file ->
            file.readText().contains(marker)
        } ?: false
        assertFalse("Password leaked in plaintext under shared_prefs", leaks)
    }

    private companion object {
        const val PREFS_NAME = "credentials_test"
    }
}

package com.aionyxe.filebridge.data.credentials

import android.content.SharedPreferences
import androidx.core.content.edit
import com.aionyxe.filebridge.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the single-user password in [EncryptedSharedPreferences]. The plaintext never touches
 * DataStore, logs, or any other unencrypted surface.
 */
@Singleton
class CredentialsRepositoryImpl @Inject constructor(
    private val securePrefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CredentialsRepository {

    override suspend fun getPassword(): String? = withContext(ioDispatcher) {
        securePrefs.getString(KEY_PASSWORD, null)
    }

    override suspend fun setPassword(value: String) = withContext(ioDispatcher) {
        securePrefs.edit { putString(KEY_PASSWORD, value) }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        securePrefs.edit { remove(KEY_PASSWORD) }
    }

    private companion object {
        const val KEY_PASSWORD = "user_password"
    }
}

package com.aionyxe.filebridge.data.credentials

import android.content.SharedPreferences
import androidx.core.content.edit
import com.aionyxe.filebridge.di.ApplicationScope
import com.aionyxe.filebridge.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the single-user password in [EncryptedSharedPreferences]. The plaintext never touches
 * DataStore, logs, or any other unencrypted surface.
 *
 * [passwordFlow] is a hot [MutableStateFlow] seeded with the persisted value at construction
 * time and kept in sync with every [setPassword]/[clear] call, so observers such as
 * [ObserveConnectionInfoUseCase] always see the current password without polling.
 */
@Singleton
class CredentialsRepositoryImpl @Inject constructor(
    private val securePrefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : CredentialsRepository {

    private val _passwordFlow = MutableStateFlow<String?>(null)
    override val passwordFlow: Flow<String?> = _passwordFlow.asStateFlow()

    init {
        // Seed the flow with the persisted value on the IO dispatcher so the very first
        // subscriber gets the correct current password, not null.
        appScope.launch(ioDispatcher) {
            _passwordFlow.value = securePrefs.getString(KEY_PASSWORD, null)
        }
    }

    override suspend fun getPassword(): String? = withContext(ioDispatcher) {
        securePrefs.getString(KEY_PASSWORD, null)
    }

    override suspend fun setPassword(value: String) = withContext(ioDispatcher) {
        securePrefs.edit { putString(KEY_PASSWORD, value) }
        _passwordFlow.value = value
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        securePrefs.edit { remove(KEY_PASSWORD) }
        _passwordFlow.value = null
    }

    private companion object {
        const val KEY_PASSWORD = "user_password"
    }
}

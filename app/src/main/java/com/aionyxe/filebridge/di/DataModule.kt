package com.aionyxe.filebridge.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.credentials.CredentialsRepositoryImpl
import com.aionyxe.filebridge.data.logs.LogRepository
import com.aionyxe.filebridge.data.logs.LogRepositoryImpl
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepositoryImpl
import com.aionyxe.filebridge.data.storage.StorageRepository
import com.aionyxe.filebridge.data.storage.StorageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindCredentialsRepository(impl: CredentialsRepositoryImpl): CredentialsRepository

    @Binds
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile("settings")
            }

        @Provides
        @Singleton
        fun provideSecurePreferences(
            @ApplicationContext context: Context,
        ): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

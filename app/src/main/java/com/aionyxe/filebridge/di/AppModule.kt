package com.aionyxe.filebridge.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Logs any uncaught exception that escapes a coroutine launched in [ApplicationScope] so a
     * single failed job (e.g. an event-collector or notification update) is recorded instead of
     * silently disappearing. Combined with [SupervisorJob], a child failure never tears down the
     * scope or its siblings.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScope {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e("AppScope", "Uncaught coroutine exception", throwable)
        }
        return CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    }
}

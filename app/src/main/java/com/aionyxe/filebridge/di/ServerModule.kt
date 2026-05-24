package com.aionyxe.filebridge.di

import com.aionyxe.filebridge.data.network.WifiNetworkInfoProvider
import com.aionyxe.filebridge.data.server.FtpServerControllerImpl
import com.aionyxe.filebridge.data.server.cert.CertificateManagerImpl
import com.aionyxe.filebridge.domain.network.NetworkInfoProvider
import com.aionyxe.filebridge.domain.server.CertificateManager
import com.aionyxe.filebridge.domain.server.FtpServerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the FTP server engine.
 *
 * [ServerEventBus] is self-providing via its @Inject constructor + @Singleton; no @Provides needed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServerModule {

    @Binds
    @Singleton
    abstract fun bindFtpServerController(impl: FtpServerControllerImpl): FtpServerController

    @Binds
    @Singleton
    abstract fun bindCertificateManager(impl: CertificateManagerImpl): CertificateManager

    @Binds
    @Singleton
    abstract fun bindNetworkInfoProvider(impl: WifiNetworkInfoProvider): NetworkInfoProvider
}

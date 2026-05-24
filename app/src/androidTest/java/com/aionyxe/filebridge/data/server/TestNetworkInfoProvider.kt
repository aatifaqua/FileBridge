package com.aionyxe.filebridge.data.server

import com.aionyxe.filebridge.domain.network.ConnectivityStatus
import com.aionyxe.filebridge.domain.network.NetworkInfoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Stub [NetworkInfoProvider] that always returns a fixed IP.  Used in integration tests to route
 * PASV data connections over loopback without requiring a real Wi-Fi connection.
 */
internal class TestNetworkInfoProvider(private val fixedIp: String) : NetworkInfoProvider {
    override fun currentWifiIpAddress(): String = fixedIp
    override val connectivity: Flow<ConnectivityStatus> =
        flowOf(ConnectivityStatus.WIFI_CONNECTED)
}

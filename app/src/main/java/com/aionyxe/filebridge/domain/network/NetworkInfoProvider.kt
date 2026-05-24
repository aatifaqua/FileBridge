package com.aionyxe.filebridge.domain.network

import kotlinx.coroutines.flow.Flow

enum class ConnectivityStatus {
    WIFI_CONNECTED,
    NOT_WIFI,
    DISCONNECTED,
}

interface NetworkInfoProvider {
    /** Current Wi-Fi IPv4 address, or null when not connected to Wi-Fi. */
    fun currentWifiIpAddress(): String?

    val connectivity: Flow<ConnectivityStatus>
}

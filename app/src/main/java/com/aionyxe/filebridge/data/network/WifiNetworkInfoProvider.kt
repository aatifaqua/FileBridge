package com.aionyxe.filebridge.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.aionyxe.filebridge.domain.network.ConnectivityStatus
import com.aionyxe.filebridge.domain.network.NetworkInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiNetworkInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkInfoProvider {

    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun currentWifiIpAddress(): String? {
        val cm = connectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val linkProps = cm.getLinkProperties(network) ?: return null
        return linkProps.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    override val connectivity: Flow<ConnectivityStatus> = callbackFlow {
        val cm = connectivityManager

        fun resolveStatus(network: Network?): ConnectivityStatus {
            if (network == null) return ConnectivityStatus.DISCONNECTED
            val caps = cm.getNetworkCapabilities(network) ?: return ConnectivityStatus.DISCONNECTED
            return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                ConnectivityStatus.WIFI_CONNECTED
            } else {
                ConnectivityStatus.NOT_WIFI
            }
        }

        // Emit initial state.
        trySend(resolveStatus(cm.activeNetwork))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(resolveStatus(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val status = if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    ConnectivityStatus.WIFI_CONNECTED
                } else {
                    ConnectivityStatus.NOT_WIFI
                }
                trySend(status)
            }

            override fun onLost(network: Network) {
                // Re-evaluate from active network; may be another network took over.
                trySend(resolveStatus(cm.activeNetwork))
            }
        }

        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate()
}

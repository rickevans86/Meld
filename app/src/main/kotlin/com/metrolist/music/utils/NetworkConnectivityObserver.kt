/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple NetworkConnectivityObserver based on OuterTune's implementation
 * Provides network connectivity monitoring for auto-play functionality
 */
class NetworkConnectivityObserver(private val context: Context) {
    @Volatile private var forcedOffline = context.dataStore[com.metrolist.music.constants.OfflineModeKey] ?: false
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(isCurrentlyConnected())
    val networkStatus = _networkStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = isCurrentlyConnected()
        }

        override fun onLost(network: Network) {
            _networkStatus.value = isCurrentlyConnected()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _networkStatus.value = isCurrentlyConnected()
        }
    }

    init {
        scope.launch {
            context.dataStore.data.collect {
                forcedOffline = it[com.metrolist.music.constants.OfflineModeKey] ?: false
                _networkStatus.value = isCurrentlyConnected()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            // Keep the current effective state if callback registration fails
            _networkStatus.value = isCurrentlyConnected()
        }
    }

    fun unregister() {
        scope.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    /**
     * Check current connectivity state synchronously.
     *
     * Why we don't require NET_CAPABILITY_VALIDATED: Android sets VALIDATED only after a
     * successful probe to connectivitycheck.gstatic.com (or equivalent). In regions or on
     * carriers where that probe is blocked/hijacked (transparent proxies, DPI, Google
     * blocks), VALIDATED is never reported even when the network works. Users then saw
     * "offline" without VPN. Trusting NET_CAPABILITY_INTERNET matches what OuterTune does.
     */
    fun isCurrentlyConnected(): Boolean {
        if (forcedOffline) return false
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            // Check if we have internet capability
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }
}

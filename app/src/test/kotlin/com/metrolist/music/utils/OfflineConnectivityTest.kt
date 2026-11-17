package com.metrolist.music.utils

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.OfflineModeKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineConnectivityTest {
    @Suppress("DEPRECATION")
    @Test fun `manual offline overrides a connected network and restores connectivity when disabled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.dataStore.edit { it[OfflineModeKey] = false }
        val manager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true))
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        val observer = NetworkConnectivityObserver(context)
        var reopened: NetworkConnectivityObserver? = null
        try {
            assertTrue(observer.isCurrentlyConnected())
            context.dataStore.edit { it[OfflineModeKey] = true }
            withTimeout(5_000) { observer.networkStatus.first { !it } }
            assertFalse(observer.isCurrentlyConnected())
            reopened = NetworkConnectivityObserver(context)
            assertFalse(reopened.isCurrentlyConnected())
            context.dataStore.edit { it[OfflineModeKey] = false }
            withTimeout(5_000) { observer.networkStatus.first { it } }
            assertTrue(observer.isCurrentlyConnected())
        } finally {
            observer.unregister()
            reopened?.unregister()
            context.dataStore.edit { it[OfflineModeKey] = false }
        }
    }
}

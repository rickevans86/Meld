package com.metrolist.music.playback

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Handler
import android.os.Looper

/** Device callbacks cover plugs/unplugs; route callbacks also cover switching connected outputs. */
internal class AudioOutputChangeMonitor(
    private val audioManager: AudioManager,
    private val mediaRouter: MediaRouter,
    private val onOutputChanged: () -> Unit,
) {
    private var started = false
    private var outputs = emptySet<Int>()
    private var route: Pair<MediaRouter.RouteInfo, String>? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = checkOutputs()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = checkOutputs()
    }

    private val routeCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = checkRoute()
        override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = checkRoute()
    }

    fun start() {
        if (started) return
        outputs = currentOutputs()
        route = currentRoute()
        started = true
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        mediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, routeCallback)
    }

    fun stop() {
        started = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        mediaRouter.removeCallback(routeCallback)
    }

    private fun currentOutputs() = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()

    private fun currentRoute() = mediaRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).let {
        it to it.name.toString()
    }

    private fun checkOutputs() {
        if (!started) return
        val current = currentOutputs()
        if (current != outputs) {
            outputs = current
            onOutputChanged()
        }
    }

    private fun checkRoute() {
        if (!started) return
        val current = currentRoute()
        if (current != route) {
            route = current
            onOutputChanged()
        }
    }
}

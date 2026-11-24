package com.metrolist.music.playback

import android.os.Bundle
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import com.metrolist.music.constants.isSubsonicId

/** Leave native transport positions empty at queue boundaries instead of filling them with custom actions. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object NavidromeTransportSlots {
    fun update(session: MediaSession, mediaId: String?) {
        val reserve = isSubsonicId(mediaId)
        val extras = session.sessionExtras
        val keys = listOf(
            MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV,
            MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT,
        )
        if (keys.all { if (reserve) extras.getBoolean(it) else !extras.containsKey(it) }) return
        session.setSessionExtras(Bundle(extras).apply {
            keys.forEach { key -> if (reserve) putBoolean(key, true) else remove(key) }
        })
    }
}

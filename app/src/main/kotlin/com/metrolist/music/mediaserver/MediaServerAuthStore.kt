/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.mediaserver

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec


/** Jellyfin / Plex sign-in, with the access token encrypted by a key kept in the Android keystore. */
internal class MediaServerAuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MediaServerSession? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val token = decrypt(prefs.getString(KEY_ACCESS, null)) ?: return null
        return MediaServerSession(
            kind = runCatching { MediaServerKind.valueOf(prefs.getString(KEY_KIND, null).orEmpty()) }.getOrNull() ?: return null,
            serverUrl = server,
            serverName = prefs.getString(KEY_SERVER_NAME, null).orEmpty(),
            username = prefs.getString(KEY_USERNAME, null).orEmpty(),
            userId = prefs.getString(KEY_USER_ID, null).orEmpty(),
            token = token,
        )
    }

    fun save(session: MediaServerSession) {
        prefs.edit {
            putString(KEY_KIND, session.kind.name)
            putString(KEY_SERVER, session.serverUrl)
            putString(KEY_SERVER_NAME, session.serverName)
            putString(KEY_USERNAME, session.username)
            putString(KEY_USER_ID, session.userId)
            putString(KEY_ACCESS, encrypt(session.token))
        }
    }

    /** A random id for this install, which Jellyfin and Plex use to tell devices apart. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also { id ->
            prefs.edit { putString(KEY_DEVICE_ID, id) }
        }

    fun clear() {
        val device = deviceId
        prefs.edit {
            clear()
            putString(KEY_DEVICE_ID, device)
        }
    }

    private fun encrypt(value: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        Base64.encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.onFailure { Timber.tag(TAG).e(it, "Could not encrypt token") }.getOrNull()

    private fun decrypt(value: String?): String? {
        if (value == null) return null
        return runCatching {
            val combined = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(GCM_TAG_BITS, combined.copyOfRange(0, GCM_IV_SIZE)),
            )
            String(cipher.doFinal(combined.copyOfRange(GCM_IV_SIZE, combined.size)), Charsets.UTF_8)
        }.onFailure { Timber.tag(TAG).e(it, "Could not decrypt token") }.getOrNull()
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance("AES", ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val TAG = "MediaServerAuth"
        const val PREFS_NAME = "media_server_auth"
        const val KEY_SERVER = "server"
        const val KEY_USERNAME = "username"
        const val KEY_ACCESS = "token"
        const val KEY_KIND = "kind"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_USER_ID = "user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wavelength_media_server_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}

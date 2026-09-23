package com.kabus.crew.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class Session(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kabus_crew_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString("access", null)
        set(v) { prefs.edit().putString("access", v).apply() }

    var refreshToken: String?
        get() = prefs.getString("refresh", null)
        set(v) { prefs.edit().putString("refresh", v).apply() }

    var cachedName: String?
        get() = prefs.getString("user_name", null)
        set(v) { prefs.edit().putString("user_name", v).apply() }

    var gpsSessionKey: String?
        get() = prefs.getString("gps_session_key", null)
        set(v) { prefs.edit().putString("gps_session_key", v).apply() }

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (existing != null) return existing
            val generated = "crew-" + UUID.randomUUID().toString()
            prefs.edit().putString("device_id", generated).apply()
            return generated
        }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
package com.cybnate.agent.provider

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API-key vault backed by Android Keystore (EncryptedSharedPreferences).
 * Keys are masked in UI, never logged, and sent only to the user's configured endpoint.
 */
class KeyVault(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "provider_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putKey(providerId: String, apiKey: String) {
        prefs.edit().putString("key_$providerId", apiKey).apply()
    }

    fun getKey(providerId: String): String? = prefs.getString("key_$providerId", null)

    fun removeKey(providerId: String) {
        prefs.edit().remove("key_$providerId").apply()
    }

    fun hasKey(providerId: String): Boolean = !getKey(providerId).isNullOrBlank()

    /** UI-safe mask, e.g. sk-…ab12. Never returns the full key. */
    fun masked(providerId: String): String {
        val k = getKey(providerId) ?: return "not set"
        return if (k.length <= 8) "••••" else k.take(3) + "…" + k.takeLast(4)
    }
}

package com.example.linkwrapper

import android.content.Context

/**
 * Lokální úložiště HTTP přihlašovacích údajů (Basic / Digest / NTLM přes WebView).
 *
 * Pro celou rodinu `*.psst.tudc.cz` (včetně `psst.tudc.cz` a `test.psst.tudc.cz`)
 * se používá jedno společné jméno a heslo — uživatel se přihlásí jednou.
 */
data class SavedCredentials(val username: String, val password: String)

object HttpCredentials {

    private const val PREFS_NAME = "http_auth_prefs"
    private const val PREFIX_USER = "user:"
    private const val PREFIX_PASS = "pass:"

    /** Společný klíč pro všechny hostitele PSST. */
    const val PSST_AUTH_KEY = "*.psst.tudc.cz"

    /**
     * Klíč úložiště pro daný hostitel.
     * `test.psst.tudc.cz` i `psst.tudc.cz` → `*.psst.tudc.cz`.
     */
    fun authKey(host: String): String {
        val h = host.lowercase()
        return if (h == "psst.tudc.cz" || h.endsWith(".psst.tudc.cz")) {
            PSST_AUTH_KEY
        } else {
            h
        }
    }

    fun isPsstHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase()
        return h == "psst.tudc.cz" || h.endsWith(".psst.tudc.cz")
    }

    fun get(context: Context, host: String): SavedCredentials? {
        val key = authKey(host)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val user = prefs.getString(PREFIX_USER + key, null) ?: return null
        val pass = prefs.getString(PREFIX_PASS + key, null) ?: return null
        if (user.isEmpty()) return null
        return SavedCredentials(user, pass)
    }

    fun save(context: Context, host: String, username: String, password: String) {
        val key = authKey(host)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX_USER + key, username)
            .putString(PREFIX_PASS + key, password)
            .apply()
    }

    fun clear(context: Context, host: String) {
        val key = authKey(host)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX_USER + key)
            .remove(PREFIX_PASS + key)
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun hasAny(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all.isNotEmpty()
    }

    fun hasFor(context: Context, host: String): Boolean = get(context, host) != null
}

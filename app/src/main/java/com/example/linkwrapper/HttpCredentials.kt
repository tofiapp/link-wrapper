package com.example.linkwrapper

import android.content.Context

/**
 * Lokální úložiště HTTP přihlašovacích údajů (Basic / Digest / NTLM přes WebView).
 * Ukládá se jen na zařízení, nikam se neodesílá. Heslo je v privátních
 * SharedPreferences aplikace — stejně jako historie odkazů.
 */
data class SavedCredentials(val username: String, val password: String)

object HttpCredentials {

    private const val PREFS_NAME = "http_auth_prefs"
    private const val PREFIX_USER = "user:"
    private const val PREFIX_PASS = "pass:"

    fun get(context: Context, host: String): SavedCredentials? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val user = prefs.getString(PREFIX_USER + host, null) ?: return null
        val pass = prefs.getString(PREFIX_PASS + host, null) ?: return null
        if (user.isEmpty()) return null
        return SavedCredentials(user, pass)
    }

    fun save(context: Context, host: String, username: String, password: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX_USER + host, username)
            .putString(PREFIX_PASS + host, password)
            .apply()
    }

    fun clear(context: Context, host: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFIX_USER + host)
            .remove(PREFIX_PASS + host)
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
}

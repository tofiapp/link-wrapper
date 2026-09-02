package com.example.linkwrapper

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import java.io.File

data class Credentials(val username: String, val password: String)

/**
 * Jedna relace přihlášení pro celou aplikaci.
 *
 * Údaje se uloží až po úspěšném ověření na serveru a zůstanou, dokud
 * uživatel nestiskne Odhlásit. Odhlášení smaže prefs i stopu ve WebView.
 */
object Session {

    private const val PREFS = "session_prefs"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val KEY_SIGNED_OUT = "show_signed_out"

    private const val LEGACY_AUTH_PREFS = "http_auth_prefs"
    private const val LEGACY_GATE_PREFS = "session_gate"

    fun isActive(context: Context): Boolean = credentials(context) != null

    fun credentials(context: Context): Credentials? {
        migrateLegacy(context)
        val prefs = prefs(context)
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        if (user.isEmpty() || pass.isEmpty()) return null
        return Credentials(user, pass)
    }

    /** Uloží ověřené údaje. Platí, dokud [end] nesmaže relaci. */
    fun start(context: Context, username: String, password: String) {
        prefs(context).edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .putBoolean(KEY_SIGNED_OUT, false)
            .commit()
    }

    /** Úplné smazání relace. Nic z hesla nesmí zůstat v prefs. */
    fun end(context: Context) {
        prefs(context).edit()
            .clear()
            .putBoolean(KEY_SIGNED_OUT, true)
            .commit()
        context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(LEGACY_GATE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    fun consumeSignedOutBanner(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_SIGNED_OUT, false)) return false
        prefs.edit().putBoolean(KEY_SIGNED_OUT, false).commit()
        return true
    }

    /**
     * Vyčistí cookies, HTTP auth cache a data otevřených WebView.
     * NTLM relace v procesu Chromium tím ještě nemusí zmizet — po odhlášení
     * je potřeba smazat profil a restartovat proces.
     */
    fun wipeBrowser(context: Context, webViews: Collection<WebView>) {
        webViews.forEach { wv ->
            try {
                wv.stopLoading()
                wv.clearCache(true)
                wv.clearHistory()
                wv.clearFormData()
                wv.clearSslPreferences()
            } catch (_: Exception) {
            }
        }
        try {
            @Suppress("DEPRECATION")
            val db = WebViewDatabase.getInstance(context)
            db.clearHttpAuthUsernamePassword()
            @Suppress("DEPRECATION")
            db.clearFormData()
            @Suppress("DEPRECATION")
            db.clearUsernamePassword()
        } catch (_: Exception) {
        }
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
        }
        try {
            val cookies = CookieManager.getInstance()
            cookies.removeSessionCookies(null)
            cookies.removeAllCookies(null)
            cookies.flush()
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.getSystemService(android.view.autofill.AutofillManager::class.java)
                    ?.cancel()
            } catch (_: Exception) {
            }
        }
    }

    fun deleteChromiumProfile(context: Context) {
        val dataDir = context.applicationInfo.dataDir
        listOf(
            File(dataDir, "app_webview"),
            File(dataDir, "app_webview_cronet"),
            File(dataDir, "app_webview_${AuthProbe.DATA_DIR_SUFFIX}"),
            File(context.cacheDir, "WebView"),
            File(context.cacheDir, "org.chromium.android_webview")
        ).forEach { dir ->
            try {
                if (dir.exists()) dir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
        listOf(
            "webview.db",
            "webviewCache.db",
            "webviewCookiesChromium.db",
            "webviewCookiesChromiumPrivate.db"
        ).forEach { name ->
            try {
                context.deleteDatabase(name)
            } catch (_: Exception) {
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Převezme údaje ze starého HttpCredentials úložiště, jednorázově. */
    private fun migrateLegacy(context: Context) {
        val current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (current.contains(KEY_USER)) return
        val legacy = context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
        val user = legacy.getString("user:*.psst.tudc.cz", null)
            ?: legacy.all.entries.firstOrNull { it.key.startsWith("user:") }?.value as? String
        val pass = legacy.getString("pass:*.psst.tudc.cz", null)
            ?: legacy.all.entries.firstOrNull { it.key.startsWith("pass:") }?.value as? String
        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            current.edit()
                .putString(KEY_USER, user)
                .putString(KEY_PASS, pass)
                .commit()
        }
        legacy.edit().clear().commit()
    }
}

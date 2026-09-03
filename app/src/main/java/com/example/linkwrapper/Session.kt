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
 * Přihlášení k PSST (NTLM). DSD si drží vlastní relaci v cookies.
 *
 * Údaje se uloží až po ověření na PSST, šifrované klíčem z Android
 * Keystore. Platí, dokud uživatel v nabídce nesmaže uložené údaje.
 */
object Session {

    private const val PREFS = "session_prefs"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val KEY_USER_ENC = "username_enc"
    private const val KEY_PASS_ENC = "password_enc"

    private const val LEGACY_AUTH_PREFS = "http_auth_prefs"
    private const val LEGACY_GATE_PREFS = "session_gate"

    @Volatile
    private var memoryOnly: Credentials? = null

    fun isActive(context: Context): Boolean = credentials(context) != null

    fun credentials(context: Context): Credentials? {
        memoryOnly?.let { return it }
        migrateLegacy(context)
        migratePlaintext(context)
        val prefs = prefs(context)
        val encUser = prefs.getString(KEY_USER_ENC, null) ?: return null
        val encPass = prefs.getString(KEY_PASS_ENC, null) ?: return null
        val user = SecretStore.decryptFromString(encUser) ?: return null
        val pass = SecretStore.decryptFromString(encPass) ?: return null
        if (user.isEmpty() || pass.isEmpty()) return null
        return Credentials(user, pass)
    }

    /** Uloží ověřené údaje. Na disk jen šifrovaně; jinak jen v RAM. */
    fun start(context: Context, username: String, password: String) {
        memoryOnly = Credentials(username, password)
        val encUser = SecretStore.encryptToString(username)
        val encPass = SecretStore.encryptToString(password)
        val editor = prefs(context).edit()
            .remove(KEY_USER)
            .remove(KEY_PASS)
        if (encUser != null && encPass != null) {
            editor.putString(KEY_USER_ENC, encUser)
                .putString(KEY_PASS_ENC, encPass)
        } else {
            editor.remove(KEY_USER_ENC).remove(KEY_PASS_ENC)
        }
        editor.commit()
    }

    /** Úplné smazání relace. Nic z hesla nesmí zůstat v prefs. */
    fun end(context: Context) {
        memoryOnly = null
        AuthHandoff.clear(context)
        prefs(context).edit().clear().commit()
        context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences(LEGACY_GATE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        SecretStore.deleteKey()
    }

    /**
     * Vyčistí cookies, HTTP auth cache a data otevřených WebView.
     * NTLM relace v procesu Chromium tím ještě nemusí zmizet — po smazání
     * údajů je potřeba smazat profil a restartovat proces.
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

    private fun migratePlaintext(context: Context) {
        val current = prefs(context)
        val user = current.getString(KEY_USER, null)
        val pass = current.getString(KEY_PASS, null)
        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
            if (current.contains(KEY_USER) || current.contains(KEY_PASS)) {
                current.edit().remove(KEY_USER).remove(KEY_PASS).commit()
            }
            return
        }
        val encUser = SecretStore.encryptToString(user)
        val encPass = SecretStore.encryptToString(pass)
        val editor = current.edit().remove(KEY_USER).remove(KEY_PASS)
        if (encUser != null && encPass != null) {
            editor.putString(KEY_USER_ENC, encUser).putString(KEY_PASS_ENC, encPass)
            memoryOnly = Credentials(user, pass)
        }
        editor.commit()
    }

    /** Převezme údaje ze starého HttpCredentials úložiště, jednorázově. */
    private fun migrateLegacy(context: Context) {
        val current = prefs(context)
        if (current.contains(KEY_USER) || current.contains(KEY_USER_ENC)) return
        val legacy = context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
        val user = legacy.getString("user:*.psst.tudc.cz", null)
            ?: legacy.all.entries.firstOrNull { it.key.startsWith("user:") }?.value as? String
        val pass = legacy.getString("pass:*.psst.tudc.cz", null)
            ?: legacy.all.entries.firstOrNull { it.key.startsWith("pass:") }?.value as? String
        if (!user.isNullOrEmpty() && !pass.isNullOrEmpty()) {
            val encUser = SecretStore.encryptToString(user)
            val encPass = SecretStore.encryptToString(pass)
            if (encUser != null && encPass != null) {
                current.edit()
                    .putString(KEY_USER_ENC, encUser)
                    .putString(KEY_PASS_ENC, encPass)
                    .commit()
                memoryOnly = Credentials(user, pass)
            }
        }
        legacy.edit().clear().commit()
    }
}

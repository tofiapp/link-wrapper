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
 * Přihlášení k PSST (NTLM).
 *
 * Údaje po ověření drží v RAM. Na disk jdou jen šifrovaně, klíčem v
 * Android Keystore. Na pozadí se relace smaže.
 */
object Session {

    private const val PREFS = "session_prefs"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val KEY_USER_ENC = "username_enc"
    private const val KEY_PASS_ENC = "password_enc"

    private const val LEGACY_AUTH_PREFS = "http_auth_prefs"
    private const val KEY_DROPPED_SHARED_AUTH = "dropped_shared_http_auth"

    @Volatile
    private var memoryOnly: Credentials? = null

    @Volatile
    private var boundHost: String? = null

    /**
     * Jednorázově smaže HTTP auth / uložená hesla ve WebView.
     * Chromium si je jinak umí držet i po smazání relace.
     */
    fun dropSharedHttpAuthOnce(context: Context) {
        val current = prefs(context)
        if (current.getBoolean(KEY_DROPPED_SHARED_AUTH, false)) return
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
        current.edit().putBoolean(KEY_DROPPED_SHARED_AUTH, true).commit()
    }

    fun isActive(context: Context): Boolean = credentials(context) != null

    fun credentials(context: Context): Credentials? {
        memoryOnly?.let { return it }
        if (TrialSettings.ephemeralLogin()) return null
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

    /**
     * NTLM na hostitele stejné appky jako přihlášení (PSST produkce i test).
     * Po restartu procesu, kdy `boundHost` ještě není v RAM, bereme
     * hostitele z [Destinations.LOGIN_URL].
     */
    fun credentialsFor(context: Context, host: String?): Credentials? {
        val creds = credentials(context) ?: return null
        if (!TrialSettings.isTrial()) return creds
        val bound = boundHost ?: loginHost()
        if (!TrialIsolation.allowsBoundAuth(host, bound)) return null
        return creds
    }

    private fun loginHost(): String? = try {
        java.net.URI(Destinations.LOGIN_URL).host?.lowercase()?.trim('.')
    } catch (_: Exception) {
        null
    }

    /** Uloží ověřené údaje. Na disk jen šifrovaně; jinak jen v RAM. */
    fun start(context: Context, username: String, password: String) {
        memoryOnly = Credentials(username, password)
        boundHost = loginHost()
        if (TrialSettings.ephemeralLogin()) {
            forgetDisk(context)
            return
        }
        persistMemoryToDisk(context)
    }

    /** Jméno a heslo z RAM; z disku pryč. */
    fun forgetDisk(context: Context) {
        prefs(context).edit()
            .remove(KEY_USER)
            .remove(KEY_PASS)
            .remove(KEY_USER_ENC)
            .remove(KEY_PASS_ENC)
            .commit()
        try {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        } catch (_: Exception) {
        }
    }

    /** RAM údaje se zapíšou šifrovaně na disk, pokud relace na disku je. */
    private fun persistMemoryToDisk(context: Context) {
        val creds = memoryOnly ?: return
        if (TrialSettings.ephemeralLogin()) return
        val encUser = SecretStore.encryptToString(creds.username)
        val encPass = SecretStore.encryptToString(creds.password)
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
        boundHost = null
        AuthHandoff.clear(context)
        PageZoom.clear(context)
        prefs(context).edit().clear().commit()
        context.getSharedPreferences(LEGACY_AUTH_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("session_gate", Context.MODE_PRIVATE)
            .edit().clear().commit()
        SecretStore.deleteKey()
    }

    /**
     * Smaže cookies a HTTP auth. WebView se nenačítají znovu — připnuté
     * karty tak zůstanou vizuálně otevřené.
     */
    fun clearAuthCaches(context: Context) {
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
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
        }
        clearAuthCaches(context)
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

package com.example.linkwrapper

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import java.net.URI

/**
 * Zkušební APK: weby si mezi sebou nepředávají přihlášení.
 *
 * Každý host (psst.tudc.cz, test.psst.tudc.cz, dsd.tudc.cz, …) má vlastní
 * zásobník cookies. Při přepnutí se cookies toho hosta obnoví a cizí
 * zmizí. HTTP auth z PSST jde jen na hostitele, na kterého se uživatel
 * přihlásil. Third-party cookies jsou vypnuté.
 *
 * Běžná APK tohle nedělá.
 */
internal object TrialIsolation {

    private val jars = mutableMapOf<String, String>()
    private var activeKey: String? = null

    fun jarKey(url: String?): String? {
        if (url.isNullOrBlank() || url == Destinations.HOME_URL) return null
        return try {
            URI(url).host?.lowercase()?.trim('.')
        } catch (_: Exception) {
            null
        }
    }

    /** NTLM z appky jen na stejný host, na který šlo přihlášení. */
    fun allowsBoundAuth(authHost: String?, boundHost: String?): Boolean {
        val a = authHost?.lowercase()?.trim('.') ?: return false
        val b = boundHost?.lowercase()?.trim('.') ?: return false
        return a == b
    }

    fun applyThirdPartyCookies(webView: WebView) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            webView,
            !TrialSettings.isTrial()
        )
    }

    fun onNavigate(context: Context, url: String?) {
        if (!TrialSettings.isTrial()) return
        val key = jarKey(url) ?: return
        val cm = CookieManager.getInstance()
        snapshot(cm, activeKey)
        if (key == activeKey) return
        try {
            cm.removeAllCookies(null)
            cm.flush()
        } catch (_: Exception) {
        }
        restore(cm, key)
        activeKey = key
        clearHttpAuth(context)
    }

    fun reset() {
        jars.clear()
        activeKey = null
    }

    private fun snapshot(cm: CookieManager, key: String?) {
        if (key.isNullOrBlank()) return
        val raw = try {
            cm.getCookie("https://$key/") ?: cm.getCookie("https://$key")
        } catch (_: Exception) {
            null
        }
        if (!raw.isNullOrBlank()) jars[key] = raw
    }

    private fun restore(cm: CookieManager, key: String) {
        val raw = jars[key] ?: return
        val url = "https://$key/"
        raw.split(';').forEach { part ->
            val cookie = part.trim()
            if (cookie.isNotEmpty()) {
                try {
                    cm.setCookie(url, cookie)
                } catch (_: Exception) {
                }
            }
        }
        try {
            cm.flush()
        } catch (_: Exception) {
        }
    }

    private fun clearHttpAuth(context: Context) {
        try {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        } catch (_: Exception) {
        }
    }
}

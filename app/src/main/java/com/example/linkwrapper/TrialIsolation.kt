package com.example.linkwrapper

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewDatabase
import java.net.URI

/**
 * Weby si mezi sebou nepředávají přihlášení.
 *
 * Zásobník cookies je podle **cílové appky** (PSST / cizí host),
 * ne podle přesného hostname. `psst.tudc.cz` a `test.psst.tudc.cz` proto
 * sdílí cookies i NTLM — graf ze sdílení je na testovacím hostu, dlaždice
 * na produkčním. Cizí HTTPS host je sám o sobě.
 *
 * Při přepnutí **mezi** zásobníky se cookies toho cíle obnoví a cizí
 * zmizí. HTTP auth se maže jen při přepnutí na jinou appku, ne mezi
 * hostiteli stejné appky. Third-party cookies jsou vypnuté.
 */
internal object TrialIsolation {

    private val jars = mutableMapOf<String, MutableMap<String, String>>()
    private val familyHosts = mutableMapOf<String, MutableSet<String>>()
    private var activeKey: String? = null

    fun jarKey(url: String?): String? {
        if (url.isNullOrBlank() || url == Destinations.HOME_URL) return null
        return try {
            familyKey(URI(url).host)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Skupina zásobníku: `psst`, nebo přesný hostname.
     * PSST produkce i test patří k sobě.
     */
    fun familyKey(host: String?): String? {
        val h = host?.lowercase()?.trim('.') ?: return null
        if (h.isEmpty()) return null
        Destinations.forHost(h)?.id?.let { return it }
        return h
    }

    /** NTLM z appky na všechny hostitele stejné appky (PSST vs cizí). */
    fun allowsBoundAuth(authHost: String?, boundHost: String?): Boolean {
        val a = familyKey(authHost) ?: return false
        val b = familyKey(boundHost) ?: return false
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
        if (url.isNullOrBlank() || url == Destinations.HOME_URL) return
        val host = try {
            URI(url).host?.lowercase()?.trim('.')
        } catch (_: Exception) {
            null
        } ?: return
        val key = familyKey(host) ?: return
        familyHosts.getOrPut(key) { mutableSetOf() }.add(host)
        val cm = CookieManager.getInstance()
        snapshot(cm, activeKey)
        if (key == activeKey) return
        try {
            cm.removeAllCookies(null)
            cm.flush()
        } catch (_: Exception) {
        }
        restore(cm, key)
        val previous = activeKey
        activeKey = key
        if (previous != null) clearHttpAuth(context)
    }

    fun reset() {
        jars.clear()
        familyHosts.clear()
        activeKey = null
    }

    private fun snapshot(cm: CookieManager, key: String?) {
        if (key.isNullOrBlank()) return
        val bag = jars.getOrPut(key) { mutableMapOf() }
        hostsForFamily(key).forEach { host ->
            val raw = try {
                cm.getCookie("https://$host/") ?: cm.getCookie("https://$host")
            } catch (_: Exception) {
                null
            }
            if (!raw.isNullOrBlank()) bag[host] = raw
        }
    }

    private fun restore(cm: CookieManager, key: String) {
        val bag = jars[key] ?: return
        bag.forEach { (host, raw) ->
            val cookieUrl = "https://$host/"
            raw.split(';').forEach { part ->
                val cookie = part.trim()
                if (cookie.isNotEmpty()) {
                    try {
                        cm.setCookie(cookieUrl, cookie)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        try {
            cm.flush()
        } catch (_: Exception) {
        }
    }

    private fun hostsForFamily(family: String): Set<String> {
        val known = when (family) {
            "psst" -> setOf("psst.tudc.cz", "test.psst.tudc.cz")
            else -> setOf(family)
        }
        return (familyHosts[family] ?: emptySet()) + known
    }

    private fun clearHttpAuth(context: Context) {
        try {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        } catch (_: Exception) {
        }
    }
}

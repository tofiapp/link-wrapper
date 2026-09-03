package com.example.linkwrapper

import java.net.URI

/**
 * Dlaždice na nativní obrazovce Domů.
 *
 * PSST používá aplikační přihlášení (NTLM). DSD má vlastní formulář na webu.
 */
internal object Destinations {

    const val PSST_URL = "https://test.psst.tudc.cz/HSI.Psst.Data"
    const val DSD_URL = "https://dsd.tudc.cz/"
    const val LOGIN_URL = PSST_URL

    data class AppLink(
        val id: String,
        val title: String,
        val hostLabel: String,
        val url: String,
        val requiresAppLogin: Boolean
    )

    val apps: List<AppLink> = listOf(
        AppLink("psst", "PSST Data", "test.psst.tudc.cz", PSST_URL, requiresAppLogin = true),
        AppLink("dsd", "DSD", "dsd.tudc.cz", DSD_URL, requiresAppLogin = false)
    )

    fun forHost(host: String?): AppLink? {
        val h = host?.lowercase()?.trim('.') ?: return null
        if (h == "psst.tudc.cz" || h == "test.psst.tudc.cz" || h.endsWith(".psst.tudc.cz")) {
            return apps.first { it.id == "psst" }
        }
        if (h == "dsd.tudc.cz" || h.endsWith(".dsd.tudc.cz")) {
            return apps.first { it.id == "dsd" }
        }
        return null
    }

    fun forUrl(url: String?): AppLink? = forHost(hostOf(url))

    fun sameApp(urlA: String, urlB: String): Boolean {
        val a = forUrl(urlA) ?: return false
        val b = forUrl(urlB) ?: return false
        return a.id == b.id
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            URI(url).host
        } catch (_: Exception) {
            null
        }
    }
}

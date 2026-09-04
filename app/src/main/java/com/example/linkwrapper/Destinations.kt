package com.example.linkwrapper

import java.net.URI

/**
 * Dlaždice na nativní obrazovce Domů.
 *
 * PSST používá aplikační přihlášení (NTLM). DSD má vlastní formulář
 * a údaje z PSST se tam neposílají.
 */
internal object Destinations {

    const val PSST_URL = "https://test.psst.tudc.cz/HSI.Psst.Data"
    const val DSD_URL = "https://dsd.tudc.cz/"
    const val LOGIN_URL = PSST_URL
    const val HOME_URL = "app://home"

    data class AppLink(
        val id: String,
        val title: String,
        val url: String,
        val requiresAppLogin: Boolean
    )

    val apps: List<AppLink> = listOf(
        AppLink("psst", "PSST Data", PSST_URL, requiresAppLogin = true),
        AppLink("dsd", "DSD", DSD_URL, requiresAppLogin = false)
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

    /** Popisek karty: u známé appky jen název (DSD, PSST Data), ne host. */
    fun tabTitle(url: String): String {
        if (url == HOME_URL) return "Domů"
        forUrl(url)?.let { return it.title }
        return hostOf(url) ?: "Karta"
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

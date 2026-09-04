package com.example.linkwrapper

import java.net.URI
import java.net.URLDecoder

/**
 * Dlaždice na nativní obrazovce Domů.
 *
 * PSST používá aplikační přihlášení (NTLM). DSD má vlastní formulář
 * a údaje z PSST se tam neposílají.
 *
 * Adresa s `dmId` na PSST je graf (sdílení / „Otevřít pomocí“).
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

    fun isChart(url: String?): Boolean =
        forUrl(url)?.id == "psst" && !dmId(url).isNullOrBlank()

    fun dmId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val query = try {
            URI(url).query
        } catch (_: Exception) {
            null
        } ?: return null
        query.split('&').forEach { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@forEach
            if (!part.substring(0, i).equals("dmId", ignoreCase = true)) return@forEach
            val raw = part.substring(i + 1)
            if (raw.isBlank()) return@forEach
            return try {
                URLDecoder.decode(raw, Charsets.UTF_8.name())
            } catch (_: Exception) {
                raw
            }
        }
        return null
    }

    /** Popisek karty: graf ze sdílení nese dmId, jinak název appky. */
    fun tabTitle(url: String): String {
        if (url == HOME_URL) return "Domů"
        dmId(url)?.let { id ->
            if (forUrl(url)?.id == "psst") return "Graf $id"
        }
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

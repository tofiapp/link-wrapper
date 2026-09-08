package com.example.linkwrapper

import java.net.URI
import java.net.URLDecoder

/**
 * Dlaždice na nativní obrazovce Domů.
 *
 * PSST používá aplikační přihlášení (NTLM).
 *
 * Adresa s `dmId` na PSST je graf (sdílení / „Otevřít pomocí“).
 */
internal object Destinations {

    const val PSST_URL = "https://psst.tudc.cz/PsstData"
    /** Stejný host a stejná NTLM pravidla (`AuthHosts`) jako dlaždice PSST Data. */
    const val LOGIN_URL = PSST_URL
    const val HOME_URL = "app://home"

    data class AppLink(
        val id: String,
        val title: String,
        val url: String,
        val requiresAppLogin: Boolean
    )

    val apps: List<AppLink> = listOf(
        AppLink("psst", "PSST Data", PSST_URL, requiresAppLogin = true)
    )

    fun forHost(host: String?): AppLink? {
        val h = host?.lowercase()?.trim('.') ?: return null
        if (h == "psst.tudc.cz" || h == "test.psst.tudc.cz" || h.endsWith(".psst.tudc.cz")) {
            return apps.first { it.id == "psst" }
        }
        return null
    }

    fun forUrl(url: String?): AppLink? = forHost(hostOf(url))

    fun isChart(url: String?): Boolean =
        forUrl(url)?.id == "psst" && !dmId(url).isNullOrBlank()

    /** Dlaždice PSST Data — ne graf ze sdílení (`dmId`). */
    fun isPsstDataHome(url: String?): Boolean {
        if (url.isNullOrBlank() || isChart(url)) return false
        if (forUrl(url)?.id != "psst") return false
        val path = try {
            URI(url).path
        } catch (_: Exception) {
            null
        } ?: return false
        val p = path.trim('/').lowercase()
        return p == "psstdata" || p.endsWith("/psstdata")
    }

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

package com.example.linkwrapper

/**
 * Servery, kterým smí aplikace poslat uložené jméno a heslo.
 *
 * Jen PSST. Na jiné hostitele se údaje z PSST neposílají.
 * Catch-all „Otevřít pomocí“ umí načíst i cizí HTTPS — bez tohoto
 * seznamu by WebView na 401 odeslalo účet na útočníkův server.
 */
internal object AuthHosts {

    private val psstExact = setOf(
        "psst.tudc.cz",
        "test.psst.tudc.cz"
    )

    fun allows(host: String?): Boolean {
        val h = host?.lowercase()?.trim('.') ?: return false
        if (h in psstExact) return true
        return psstExact.any { h.endsWith(".$it") }
    }

    fun isTudc(host: String?): Boolean {
        val h = host?.lowercase()?.trim('.') ?: return false
        return h == "tudc.cz" || h.endsWith(".tudc.cz")
    }
}

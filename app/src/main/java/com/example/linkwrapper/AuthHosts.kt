package com.example.linkwrapper

/**
 * Servery, kterým smí aplikace poslat uložené jméno a heslo.
 *
 * Catch-all intent „Otevřít pomocí“ umí načíst i cizí HTTPS. Bez tohoto
 * seznamu by WebView na 401 odeslalo firemní účet na útočníkův server.
 */
internal object AuthHosts {

    private val exact = setOf(
        "psst.tudc.cz",
        "test.psst.tudc.cz"
    )

    fun allows(host: String?): Boolean {
        val h = host?.lowercase()?.trim('.') ?: return false
        if (h in exact) return true
        return exact.any { h.endsWith(".$it") }
    }
}

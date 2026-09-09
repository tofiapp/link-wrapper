package com.example.linkwrapper

/**
 * Servery, kterým smí aplikace poslat uložené jméno a heslo.
 * Jen PSST (`psst.tudc.cz` / `test.psst.tudc.cz`).
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
}

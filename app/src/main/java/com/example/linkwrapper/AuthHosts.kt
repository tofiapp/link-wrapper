package com.example.linkwrapper

/**
 * Servery, kterým smí aplikace poslat uložené jméno a heslo.
 *
 * Běžná APK: jen PSST. Zkušební APK: celé `tudc.cz`.
 * Catch-all „Otevřít pomocí“ umí načíst i cizí HTTPS — bez tohoto
 * seznamu by WebView na 401 odeslalo účet na útočníkův server.
 */
internal object AuthHosts {

    private val psstExact = setOf(
        "psst.tudc.cz",
        "test.psst.tudc.cz"
    )

    fun allows(host: String?): Boolean = allows(host, BuildConfig.TRIAL_HOME_LOGIN)

    fun allows(host: String?, allTudc: Boolean): Boolean {
        val h = host?.lowercase()?.trim('.') ?: return false
        if (allTudc) return isTudc(h)
        if (h in psstExact) return true
        return psstExact.any { h.endsWith(".$it") }
    }

    fun isTudc(host: String?): Boolean {
        val h = host?.lowercase()?.trim('.') ?: return false
        return h == "tudc.cz" || h.endsWith(".tudc.cz")
    }
}

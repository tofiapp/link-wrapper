package com.example.linkwrapper

/**
 * Jen zkušební APK (`systemtrust`).
 *
 * Přihlášení k PSST zůstane jen v RAM — žádný přepínač v menu.
 * Jakmile jde appka na pozadí, relace i cookies se smažou ([TrialIdle]);
 * otevřené karty zůstanou. Běžná APK ukládá údaje na disk.
 */
internal object TrialSettings {

    fun isTrial(): Boolean = BuildConfig.FLAVOR == "systemtrust"

    fun ephemeralLogin(): Boolean = isTrial()
}

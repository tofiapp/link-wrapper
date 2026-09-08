package com.example.linkwrapper

/**
 * Jen zkušební APK (`systemtrust`).
 *
 * Přihlášení k PSST zůstane jen v RAM — žádný přepínač v menu.
 * Po minutě na pozadí se relace i cookies smažou ([TrialIdle]);
 * připnuté karty zůstanou otevřené. Běžná APK ukládá údaje na disk.
 */
internal object TrialSettings {

    fun isTrial(): Boolean = BuildConfig.FLAVOR == "systemtrust"

    fun ephemeralLogin(): Boolean = isTrial()
}

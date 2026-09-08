package com.example.linkwrapper

/**
 * Obě APK (běžná i test) se chovají stejně.
 *
 * Přihlášení k PSST zůstane jen v RAM — žádný přepínač v menu.
 * Jakmile jde appka na pozadí, relace i cookies se smažou ([TrialIdle]);
 * otevřené karty zůstanou. Flavor (`pinned` / `systemtrust`) jen odděluje
 * soubor APK a `applicationId`, ať jdou nainstalovat vedle sebe.
 */
internal object TrialSettings {

    fun isTrial(): Boolean = true

    fun ephemeralLogin(): Boolean = true
}

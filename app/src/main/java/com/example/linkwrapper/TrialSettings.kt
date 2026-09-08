package com.example.linkwrapper

import android.content.Context

/**
 * Jen zkušební APK (`systemtrust`): volba neukládat přihlášení k PSST.
 * Běžná APK volbu nemá — údaje jdou na disk jako doteď.
 *
 * Zapnuté = jméno a heslo zůstanou jen v RAM (stejné `Session.memory`
 * jako když Keystore neumí zapsat). Ve zkušební je to **výchozí**.
 * Po zabití procesu je potřeba přihlášení znovu. Přepínač se pamatuje.
 */
internal object TrialSettings {

    fun isTrial(): Boolean = BuildConfig.FLAVOR == "systemtrust"

    private const val PREFS = "trial_prefs"
    private const val KEY_EPHEMERAL = "ephemeral_login"

    fun ephemeralLogin(context: Context): Boolean =
        isTrial() && prefs(context).getBoolean(KEY_EPHEMERAL, true)

    fun setEphemeralLogin(context: Context, enabled: Boolean) {
        if (!isTrial()) return
        prefs(context).edit().putBoolean(KEY_EPHEMERAL, enabled).commit()
        if (enabled) Session.forgetDisk(context)
        else Session.persistMemoryToDisk(context)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package com.example.linkwrapper

internal object TrialSettings {

    fun isTrial(): Boolean = BuildConfig.FLAVOR != "systemtrust"

    fun ephemeralLogin(): Boolean = BuildConfig.FLAVOR != "systemtrust"
}

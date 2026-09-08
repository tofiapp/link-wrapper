package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Test

class TrialSettingsTest {

    @Test
    fun trialFlagMatchesFlavor() {
        assertEquals(
            BuildConfig.FLAVOR == "systemtrust",
            TrialSettings.isTrial()
        )
    }

    @Test
    fun trialLoginIsAlwaysEphemeral() {
        assertEquals(TrialSettings.isTrial(), TrialSettings.ephemeralLogin())
    }
}

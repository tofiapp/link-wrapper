package com.example.linkwrapper

import org.junit.Assert.assertTrue
import org.junit.Test

class TrialSettingsTest {

    @Test
    fun bothApksWipeSessionOnBackground() {
        assertTrue(TrialSettings.isTrial())
        assertTrue(TrialSettings.ephemeralLogin())
    }
}

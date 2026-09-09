package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Test

class TrialSettingsTest {

    @Test
    fun flavorSessionPolicy() {
        val pinned = BuildConfig.FLAVOR == "pinned"
        assertEquals(pinned, TrialSettings.isTrial())
        assertEquals(pinned, TrialSettings.ephemeralLogin())
    }
}

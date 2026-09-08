package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialPinsTest {

    @Test
    fun roundTripKeepsTitleAndUrl() {
        val items = listOf(
            TrialPin("Graf 12", "https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12"),
            TrialPin("PSST Data", Destinations.PSST_URL)
        )
        assertEquals(items, TrialPins.decode(TrialPins.encode(items)))
    }

    @Test
    fun titleMayContainTabsAndNewlinesAsEscapes() {
        val items = listOf(TrialPin("Graf\t12\nlist", "https://psst.tudc.cz/x"))
        assertEquals(items, TrialPins.decode(TrialPins.encode(items)))
    }

    @Test
    fun emptyAndJunkDecodeToNothing() {
        assertTrue(TrialPins.decode(null).isEmpty())
        assertTrue(TrialPins.decode("").isEmpty())
        assertTrue(TrialPins.decode("not-a-pin").isEmpty())
        assertTrue(TrialPins.decode("Domů\t${Destinations.HOME_URL}").isEmpty())
    }

    @Test
    fun loginOnlyWhenPinnedAndConnectedWithoutSession() {
        assertFalse(TrialPins.shouldPromptLogin(sessionActive = true, connectionOk = true, hasPinnedTabs = true))
        assertFalse(TrialPins.shouldPromptLogin(sessionActive = false, connectionOk = false, hasPinnedTabs = true))
        assertFalse(TrialPins.shouldPromptLogin(sessionActive = false, connectionOk = true, hasPinnedTabs = false))
        assertTrue(TrialPins.shouldPromptLogin(sessionActive = false, connectionOk = true, hasPinnedTabs = true))
    }

    @Test
    fun stripPutsHomeThenPinsThenTheRest() {
        assertEquals(0, TrialPins.stripGroup(isHome = true, pinned = false))
        assertEquals(0, TrialPins.stripGroup(isHome = true, pinned = true))
        assertEquals(1, TrialPins.stripGroup(isHome = false, pinned = true))
        assertEquals(2, TrialPins.stripGroup(isHome = false, pinned = false))
    }
}

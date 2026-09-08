package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialPinsTest {

    @Test
    fun roundTripKeepsTitleUrlAndPinnedFlag() {
        val items = listOf(
            TrialPin("Graf 12", "https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12", pinned = true),
            TrialPin("PSST Data", Destinations.PSST_URL, pinned = false)
        )
        assertEquals(items, TrialPins.decode(TrialPins.encode(items)))
    }

    @Test
    fun legacyTwoFieldLineCountsAsPinned() {
        val restored = TrialPins.decode("Graf%2012\thttps://psst.tudc.cz/x")
        assertEquals(1, restored.size)
        assertTrue(restored[0].pinned)
        assertEquals("Graf 12", restored[0].title)
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
    fun stripPutsHomeThenPinsThenTheRest() {
        assertEquals(0, TrialPins.stripGroup(isHome = true, pinned = false))
        assertEquals(0, TrialPins.stripGroup(isHome = true, pinned = true))
        assertEquals(1, TrialPins.stripGroup(isHome = false, pinned = true))
        assertEquals(2, TrialPins.stripGroup(isHome = false, pinned = false))
    }
}

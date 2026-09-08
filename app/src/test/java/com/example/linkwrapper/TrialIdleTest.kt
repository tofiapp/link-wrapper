package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialIdleTest {

    @Test
    fun wipeAsSoonAsBackgrounded() {
        assertTrue(TrialIdle.isDue(1_000L, 1_000L, 0L, 0L))
        assertTrue(TrialIdle.isDue(1_000L, 1_000L + 1L, 0L, 0L))
        assertTrue(TrialIdle.isDue(1_000L, 1_000L + 60_000L, 0L, 0L))
    }

    @Test
    fun noBackgroundMarkMeansNoWipe() {
        assertFalse(TrialIdle.isDue(0L, 90_000L, 0L, 90_000L))
    }

    @Test
    fun rebootFallsBackToWallClock() {
        assertTrue(TrialIdle.isDue(80_000L, 5_000L, 1_000L, 1_000L))
        assertTrue(TrialIdle.isDue(80_000L, 5_000L, 1_000L, 1_000L + 1L))
    }
}

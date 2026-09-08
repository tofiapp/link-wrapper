package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialIdleTest {

    @Test
    fun wipeAfterOneMinute() {
        assertFalse(TrialIdle.isDue(1_000L, 1_000L + 59_999L, 0L, 0L))
        assertTrue(TrialIdle.isDue(1_000L, 1_000L + 60_000L, 0L, 0L))
        assertTrue(TrialIdle.isDue(1_000L, 1_000L + 120_000L, 0L, 0L))
    }

    @Test
    fun noBackgroundMarkMeansNoWipe() {
        assertFalse(TrialIdle.isDue(0L, 90_000L, 0L, 90_000L))
    }

    @Test
    fun rebootFallsBackToWallClock() {
        assertFalse(TrialIdle.isDue(80_000L, 5_000L, 1_000L, 1_000L + 30_000L))
        assertTrue(TrialIdle.isDue(80_000L, 5_000L, 1_000L, 1_000L + 60_000L))
    }
}

package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTrackTest {

    @Test
    fun bootstrapForcesHighAccuracyAndExposesPush() {
        val js = GeoTrack.BOOTSTRAP_JS
        assertTrue(js.contains("enableHighAccuracy = true"))
        assertTrue(js.contains("maximumAge = 0"))
        assertTrue(js.contains("watchPosition"))
        assertTrue(js.contains("ObalkaGeo.watchStart"))
        assertTrue(js.contains("ObalkaGeo.watchStop"))
        assertTrue(js.contains("window.__obalkaGeoPush"))
        assertFalse(js.contains("maximumAge = 60000"))
        assertTrue(GeoTrack.INTERVAL_MS == 1_000L)
        assertTrue(GeoTrack.MIN_INTERVAL_MS == 500L)
    }
}

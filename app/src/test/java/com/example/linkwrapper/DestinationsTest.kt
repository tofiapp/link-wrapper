package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun mapsKnownHosts() {
        assertEquals("psst", Destinations.forHost("test.psst.tudc.cz")?.id)
        assertEquals("psst", Destinations.forHost("psst.tudc.cz")?.id)
        assertEquals("dsd", Destinations.forHost("dsd.tudc.cz")?.id)
        assertEquals("dsd", Destinations.forHost("www.dsd.tudc.cz")?.id)
        assertNull(Destinations.forHost("evil.com"))
        assertNull(Destinations.forHost("tudc.cz"))
    }

    @Test
    fun sameAppIgnoresPath() {
        assertTrue(
            Destinations.sameApp(
                Destinations.PSST_URL,
                "https://test.psst.tudc.cz/HSI.Psst.Data?dmId=1"
            )
        )
        assertTrue(
            Destinations.sameApp("https://dsd.tudc.cz/foo", Destinations.DSD_URL)
        )
        assertFalse(
            Destinations.sameApp(Destinations.PSST_URL, Destinations.DSD_URL)
        )
    }

    @Test
    fun onlyPsstUsesAppLogin() {
        assertTrue(Destinations.forHost("test.psst.tudc.cz")!!.requiresAppLogin)
        assertFalse(Destinations.forHost("dsd.tudc.cz")!!.requiresAppLogin)
    }

    @Test
    fun homeIsNotAWebHost() {
        assertEquals("app://home", Destinations.HOME_URL)
        assertNull(Destinations.forUrl(Destinations.HOME_URL))
        assertFalse(Destinations.sameApp(Destinations.HOME_URL, Destinations.DSD_URL))
    }
}

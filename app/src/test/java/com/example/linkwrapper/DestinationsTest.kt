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
    fun onlyPsstUsesAppLogin() {
        assertTrue(Destinations.forHost("test.psst.tudc.cz")!!.requiresAppLogin)
        assertFalse(Destinations.forHost("dsd.tudc.cz")!!.requiresAppLogin)
    }

    @Test
    fun homeIsNotAWebHost() {
        assertEquals("app://home", Destinations.HOME_URL)
        assertNull(Destinations.forUrl(Destinations.HOME_URL))
    }

    @Test
    fun tabTitleUsesAppNameNotHost() {
        assertEquals("Domů", Destinations.tabTitle(Destinations.HOME_URL))
        assertEquals("DSD", Destinations.tabTitle(Destinations.DSD_URL))
        assertEquals("DSD", Destinations.tabTitle("https://dsd.tudc.cz/foo?x=1"))
        assertEquals("PSST Data", Destinations.tabTitle(Destinations.PSST_URL))
        assertEquals("example.com", Destinations.tabTitle("https://example.com/path"))
    }

    @Test
    fun chartFromShareUsesDmIdInTabTitle() {
        assertEquals(
            "Graf 12",
            Destinations.tabTitle("https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12")
        )
        assertEquals(
            "Graf 45",
            Destinations.tabTitle("https://psst.tudc.cz/HSI.Psst.Data?foo=1&dmId=45")
        )
        assertTrue(Destinations.isChart("https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12"))
        assertFalse(Destinations.isChart(Destinations.PSST_URL))
        assertFalse(Destinations.isChart("https://dsd.tudc.cz/?dmId=12"))
        assertEquals("12", Destinations.dmId("https://test.psst.tudc.cz/HSI.Psst.Data?dmid=12"))
    }
}

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
        assertNull(Destinations.forHost("evil.com"))
        assertNull(Destinations.forHost("tudc.cz"))
    }

    @Test
    fun homeHasOnlyPsstTile() {
        assertEquals(listOf("psst"), Destinations.apps.map { it.id })
        assertTrue(Destinations.apps.single().requiresAppLogin)
    }

    @Test
    fun onlyPsstUsesAppLogin() {
        assertTrue(Destinations.forHost("test.psst.tudc.cz")!!.requiresAppLogin)
        assertTrue(Destinations.forHost("psst.tudc.cz")!!.requiresAppLogin)
    }

    @Test
    fun homeIsNotAWebHost() {
        assertEquals("app://home", Destinations.HOME_URL)
        assertNull(Destinations.forUrl(Destinations.HOME_URL))
    }

    @Test
    fun homeTileOpensProductionPsstData() {
        assertEquals("https://psst.tudc.cz/PsstData", Destinations.PSST_URL)
        assertEquals(Destinations.PSST_URL, Destinations.LOGIN_URL)
        assertEquals(Destinations.PSST_URL, Destinations.apps.first { it.id == "psst" }.url)
        assertTrue(Destinations.apps.first { it.id == "psst" }.requiresAppLogin)
        assertFalse(Destinations.isChart(Destinations.PSST_URL))
        assertTrue(Destinations.isPsstDataHome(Destinations.PSST_URL))
        assertTrue(Destinations.isPsstDataHome("https://test.psst.tudc.cz/PsstData"))
        assertFalse(Destinations.isPsstDataHome("https://psst.tudc.cz/HSI.Psst.Data?dmId=12"))
        assertFalse(Destinations.isPsstDataHome("https://psst.tudc.cz/PsstData?dmId=12"))
        assertTrue(AuthHosts.allows("psst.tudc.cz"))
    }

    @Test
    fun tabTitleUsesAppNameNotHost() {
        assertEquals("Domů", Destinations.tabTitle(Destinations.HOME_URL))
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
        assertTrue(Destinations.isChart("https://psst.tudc.cz/PsstData?dmId=12"))
        assertFalse(Destinations.isChart(Destinations.PSST_URL))
        assertFalse(Destinations.isChart("https://example.com/?dmId=12"))
        assertEquals("12", Destinations.dmId("https://test.psst.tudc.cz/HSI.Psst.Data?dmid=12"))
    }
}

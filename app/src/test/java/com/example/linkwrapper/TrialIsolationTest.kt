package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialIsolationTest {

    @Test
    fun jarKeyIsExactHost() {
        assertEquals("psst.tudc.cz", TrialIsolation.jarKey("https://psst.tudc.cz/PsstData"))
        assertEquals("test.psst.tudc.cz", TrialIsolation.jarKey("https://test.psst.tudc.cz/HSI.Psst.Data?dmId=1"))
        assertEquals("dsd.tudc.cz", TrialIsolation.jarKey("https://dsd.tudc.cz/"))
        assertNull(TrialIsolation.jarKey(Destinations.HOME_URL))
        assertNull(TrialIsolation.jarKey(null))
    }

    @Test
    fun ntlmStaysOnBoundHostOnly() {
        assertTrue(TrialIsolation.allowsBoundAuth("psst.tudc.cz", "psst.tudc.cz"))
        assertTrue(TrialIsolation.allowsBoundAuth("PSST.TUDC.CZ", "psst.tudc.cz"))
        assertFalse(TrialIsolation.allowsBoundAuth("test.psst.tudc.cz", "psst.tudc.cz"))
        assertFalse(TrialIsolation.allowsBoundAuth("dsd.tudc.cz", "psst.tudc.cz"))
        assertFalse(TrialIsolation.allowsBoundAuth("psst.tudc.cz", null))
        assertFalse(TrialIsolation.allowsBoundAuth(null, "psst.tudc.cz"))
    }
}

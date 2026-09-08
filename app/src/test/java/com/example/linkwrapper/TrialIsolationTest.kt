package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialIsolationTest {

    @Test
    fun jarKeyGroupsPsstFamily() {
        assertEquals("psst", TrialIsolation.jarKey("https://psst.tudc.cz/PsstData"))
        assertEquals("psst", TrialIsolation.jarKey("https://test.psst.tudc.cz/HSI.Psst.Data?dmId=1"))
        assertEquals("example.com", TrialIsolation.jarKey("https://example.com/x"))
        assertNull(TrialIsolation.jarKey(Destinations.HOME_URL))
        assertNull(TrialIsolation.jarKey(null))
    }

    @Test
    fun familyKeyMatchesDestinations() {
        assertEquals("psst", TrialIsolation.familyKey("psst.tudc.cz"))
        assertEquals("psst", TrialIsolation.familyKey("test.psst.tudc.cz"))
        assertEquals("psst", TrialIsolation.familyKey("PSST.TUDC.CZ"))
        assertEquals("evil.example", TrialIsolation.familyKey("evil.example"))
        assertNull(TrialIsolation.familyKey(null))
        assertNull(TrialIsolation.familyKey(""))
    }

    @Test
    fun ntlmFollowsAppFamilyNotExactHost() {
        assertTrue(TrialIsolation.allowsBoundAuth("psst.tudc.cz", "psst.tudc.cz"))
        assertTrue(TrialIsolation.allowsBoundAuth("PSST.TUDC.CZ", "psst.tudc.cz"))
        assertTrue(TrialIsolation.allowsBoundAuth("test.psst.tudc.cz", "psst.tudc.cz"))
        assertTrue(TrialIsolation.allowsBoundAuth("psst.tudc.cz", "test.psst.tudc.cz"))
        assertFalse(TrialIsolation.allowsBoundAuth("evil.example", "psst.tudc.cz"))
        assertFalse(TrialIsolation.allowsBoundAuth("psst.tudc.cz", null))
        assertFalse(TrialIsolation.allowsBoundAuth(null, "psst.tudc.cz"))
    }
}

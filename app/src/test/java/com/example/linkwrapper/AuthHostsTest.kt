package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthHostsTest {

    @Test
    fun allowsPsstHosts() {
        assertTrue(AuthHosts.allows("psst.tudc.cz", allTudc = false))
        assertTrue(AuthHosts.allows("PSST.TUDC.CZ", allTudc = false))
        assertTrue(AuthHosts.allows("test.psst.tudc.cz", allTudc = false))
        assertTrue(AuthHosts.allows("www.test.psst.tudc.cz", allTudc = false))
        assertTrue(AuthHosts.allows("a.psst.tudc.cz", allTudc = false))
    }

    @Test
    fun productionRejectsNonPsst() {
        assertFalse(AuthHosts.allows(null, allTudc = false))
        assertFalse(AuthHosts.allows("", allTudc = false))
        assertFalse(AuthHosts.allows("evil.com", allTudc = false))
        assertFalse(AuthHosts.allows("tudc.cz", allTudc = false))
        assertFalse(AuthHosts.allows("notpsst.tudc.cz", allTudc = false))
        assertFalse(AuthHosts.allows("dsd.tudc.cz", allTudc = false))
        assertFalse(AuthHosts.allows("psst.tudc.cz.attacker.com", allTudc = false))
        assertFalse(AuthHosts.allows("google.com", allTudc = false))
    }

    @Test
    fun trialAllowsWholeTudc() {
        assertTrue(AuthHosts.allows("tudc.cz", allTudc = true))
        assertTrue(AuthHosts.allows("dsd.tudc.cz", allTudc = true))
        assertTrue(AuthHosts.allows("www.dsd.tudc.cz", allTudc = true))
        assertTrue(AuthHosts.allows("psst.tudc.cz", allTudc = true))
        assertTrue(AuthHosts.allows("test.psst.tudc.cz", allTudc = true))
        assertTrue(AuthHosts.isTudc("foo.bar.tudc.cz"))
        assertFalse(AuthHosts.allows("evil.com", allTudc = true))
        assertFalse(AuthHosts.allows("tudc.cz.attacker.com", allTudc = true))
        assertFalse(AuthHosts.allows("nottudc.cz", allTudc = true))
        assertFalse(AuthHosts.isTudc(null))
    }
}

package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthHostsTest {

    @Test
    fun allowsPsstHosts() {
        assertTrue(AuthHosts.allows("psst.tudc.cz"))
        assertTrue(AuthHosts.allows("PSST.TUDC.CZ"))
        assertTrue(AuthHosts.allows("test.psst.tudc.cz"))
        assertTrue(AuthHosts.allows("www.test.psst.tudc.cz"))
        assertTrue(AuthHosts.allows("a.psst.tudc.cz"))
    }

    @Test
    fun rejectsNonPsstIncludingDsd() {
        assertFalse(AuthHosts.allows(null))
        assertFalse(AuthHosts.allows(""))
        assertFalse(AuthHosts.allows("evil.com"))
        assertFalse(AuthHosts.allows("tudc.cz"))
        assertFalse(AuthHosts.allows("notpsst.tudc.cz"))
        assertFalse(AuthHosts.allows("dsd.tudc.cz"))
        assertFalse(AuthHosts.allows("www.dsd.tudc.cz"))
        assertFalse(AuthHosts.allows("psst.tudc.cz.attacker.com"))
        assertFalse(AuthHosts.allows("google.com"))
    }

    @Test
    fun tudcHelper() {
        assertTrue(AuthHosts.isTudc("foo.bar.tudc.cz"))
        assertTrue(AuthHosts.isTudc("dsd.tudc.cz"))
        assertTrue(AuthHosts.isTudc("psst.tudc.cz"))
        assertFalse(AuthHosts.isTudc("evil.com"))
        assertFalse(AuthHosts.isTudc("tudc.cz.attacker.com"))
        assertFalse(AuthHosts.isTudc("nottudc.cz"))
        assertFalse(AuthHosts.isTudc(null))
    }
}

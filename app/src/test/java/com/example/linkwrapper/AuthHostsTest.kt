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
    fun rejectsForeignHosts() {
        assertFalse(AuthHosts.allows(null))
        assertFalse(AuthHosts.allows(""))
        assertFalse(AuthHosts.allows("evil.com"))
        assertFalse(AuthHosts.allows("tudc.cz"))
        assertFalse(AuthHosts.allows("notpsst.tudc.cz"))
        assertFalse(AuthHosts.allows("dsd.tudc.cz"))
        assertFalse(AuthHosts.allows("psst.tudc.cz.attacker.com"))
        assertFalse(AuthHosts.allows("google.com"))
    }
}

class HostnameMatchTest {

    @Test
    fun exactAndWildcard() {
        assertTrue(AuthHosts.hostnameMatches("psst.tudc.cz", "psst.tudc.cz"))
        assertTrue(AuthHosts.hostnameMatches("*.psst.tudc.cz", "app.psst.tudc.cz"))
        assertFalse(AuthHosts.hostnameMatches("*.psst.tudc.cz", "psst.tudc.cz"))
        assertFalse(AuthHosts.hostnameMatches("psst.tudc.cz", "evil.com"))
        assertFalse(AuthHosts.hostnameMatches("psst.tudc.cz", "psst.tudc.cz.evil.com"))
    }
}

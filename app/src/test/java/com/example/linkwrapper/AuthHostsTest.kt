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
    fun rejectsNonPsstHosts() {
        assertFalse(AuthHosts.allows(null))
        assertFalse(AuthHosts.allows(""))
        assertFalse(AuthHosts.allows("evil.com"))
        assertFalse(AuthHosts.allows("tudc.cz"))
        assertFalse(AuthHosts.allows("notpsst.tudc.cz"))
        assertFalse(AuthHosts.allows("portal.tudc.cz"))
        assertFalse(AuthHosts.allows("psst.tudc.cz.attacker.com"))
        assertFalse(AuthHosts.allows("google.com"))
    }
}

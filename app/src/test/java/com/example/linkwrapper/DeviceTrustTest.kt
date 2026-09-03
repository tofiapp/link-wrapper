package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class DeviceTrustTest {

    @Test
    fun handshakeIsUntrusted() {
        assertTrue(DeviceTrust.isUntrustedSsl(SSLHandshakeException("untrusted")))
        assertTrue(DeviceTrust.isUntrustedSsl(SSLPeerUnverifiedException("peer")))
        assertTrue(DeviceTrust.isUntrustedSsl(CertificateException("anchor")))
        assertTrue(
            DeviceTrust.isUntrustedSsl(
                IOException("wrap", SSLHandshakeException("untrusted"))
            )
        )
    }

    @Test
    fun networkBlipsAreNotMissingCerts() {
        assertFalse(DeviceTrust.isUntrustedSsl(UnknownHostException("x")))
        assertFalse(DeviceTrust.isUntrustedSsl(SocketTimeoutException("t")))
        assertFalse(DeviceTrust.isUntrustedSsl(IOException("connection reset")))
    }

    @Test
    fun skipsNonHttpsAndForeignHosts() {
        assertEquals(DeviceTrust.Result.Unknown, DeviceTrust.probe("http://test.psst.tudc.cz/"))
        assertEquals(DeviceTrust.Result.Unknown, DeviceTrust.probe("https://evil.example/"))
        assertEquals(DeviceTrust.Result.Unknown, DeviceTrust.probe("not-a-url"))
    }
}

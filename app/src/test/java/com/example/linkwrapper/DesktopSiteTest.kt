package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSiteTest {

    @Test
    fun userAgentLooksLikeWindowsChrome() {
        val mobile = "Mozilla/5.0 (Linux; Android 13; SM-T870) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36"
        val ua = DesktopSite.userAgent(mobile)
        assertTrue(ua.contains("Windows NT 10.0"))
        assertTrue(ua.contains("Win64"))
        assertTrue(ua.contains("Chrome/120.0.6099.210"))
        assertTrue(ua.contains("AppleWebKit/537.36"))
        assertFalse(ua.contains("Mobile"))
        assertFalse(ua.contains("Android"))
        assertFalse(ua.contains("; wv"))
    }

    @Test
    fun bootstrapForcesDesktopViewport() {
        assertTrue(DesktopSite.BOOTSTRAP_JS.contains("width="))
        assertTrue(DesktopSite.BOOTSTRAP_JS.contains("${DesktopSite.VIEWPORT_WIDTH}"))
        assertTrue(DesktopSite.BOOTSTRAP_JS.contains("viewport"))
        assertTrue(DesktopSite.setJs().contains("${DesktopSite.VIEWPORT_WIDTH}"))
    }
}

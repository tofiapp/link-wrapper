package com.example.linkwrapper

import org.junit.Assert.assertEquals
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
    fun viewportMatchesTabletWidthAtScaleOne() {
        val js = DesktopSite.bootstrapJs(800)
        assertTrue(js.contains("var W = 800"))
        assertTrue(js.contains("width=' + W"))
        assertTrue(js.contains("initial-scale=1"))
        assertTrue(DesktopSite.setJs(800).contains("width=800, initial-scale=1, minimum-scale=1, maximum-scale=1"))
        assertEquals(360, DesktopSite.clampCssWidth(10))
        assertEquals(2000, DesktopSite.clampCssWidth(9999))
        assertEquals(800, DesktopSite.clampCssWidth(800))
    }
}

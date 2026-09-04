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
    fun viewportUsesFixedDesktopWidth() {
        val js = DesktopSite.BOOTSTRAP_JS
        // Rozvržení na pevnou desktopovou šířku, ne na šířku tabletu.
        assertFalse(js.contains("width=device-width"))
        assertTrue(js.contains("var W = ${DesktopSite.LAYOUT_WIDTH}"))
        assertTrue(js.contains("initial-scale=1"))
        assertTrue(js.contains("max-width:none"))
        assertTrue(js.contains("height:auto"))
        assertTrue(js.contains("__obalkaFill"))
        assertFalse(js.contains("minimum-scale"))
        assertFalse(js.contains("maximum-scale"))
    }

    @Test
    fun setJsCarriesTheSameWidth() {
        val js = DesktopSite.setJs()
        assertTrue(js.contains("width=${DesktopSite.LAYOUT_WIDTH}, initial-scale=1"))
        assertFalse(js.contains("device-width"))
        assertTrue(js.contains("__obalkaFill"))
    }

    @Test
    fun layoutWidthIsDesktopSized() {
        assertTrue(DesktopSite.LAYOUT_WIDTH >= 1024)
    }
}

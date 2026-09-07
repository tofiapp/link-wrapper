package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenIdentifiesOverlay() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function identifyOverlay()"))
        assertTrue(js.contains("elementsFromPoint"))
        assertTrue(js.contains("AndroidDebugBridge.showResult"))
        assertTrue(js.contains("setTimeout"))
        assertTrue(js.contains("1500"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("new MutationObserver"))
        assertFalse(js.contains("function step2_height()"))
        assertFalse(js.contains("el.style.setProperty('height'"))
        assertFalse(js.contains("Math.min"))
        assertFalse(js.contains("setInterval"))
    }

    @Test
    fun resetJsUnlocksFitForOrientationChange() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("window.__chartZoom = null"))
        assertFalse(reset.contains("setInterval"))
    }
}

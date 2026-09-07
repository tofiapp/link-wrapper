package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsFitsOnceSequentiallyThenLocks() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height()"))
        assertTrue(js.contains("el.style.removeProperty('height')"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("window.__chartZoom = zoom"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("requestAnimationFrame(function()"))
        assertTrue(js.contains("requestAnimationFrame(step2_height)"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertTrue(js.contains("new MutationObserver"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("Math.min"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("document.documentElement.style.setProperty('zoom'"))
    }

    @Test
    fun resetJsUnlocksFitForOrientationChange() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("window.__chartZoom = null"))
        assertFalse(reset.contains("setInterval"))
    }
}

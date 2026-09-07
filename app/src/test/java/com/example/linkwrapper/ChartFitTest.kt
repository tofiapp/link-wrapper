package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenStretchesHtmlBodyToContent() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height()"))
        assertTrue(js.contains("requestAnimationFrame(step2_height)"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("querySelector('.css-nm4wu0')"))
        assertTrue(js.contains("min-height"))
        assertTrue(js.contains("wrapper.scrollHeight"))
        assertTrue(js.contains("setInterval(enforce, 500)"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("identifyOverlay"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("elementsFromPoint"))
        assertFalse(js.contains("el.style.removeProperty('height')"))
        assertFalse(js.contains("el.style.setProperty('height'"))
        assertFalse(js.contains("Math.min"))
    }

    @Test
    fun resetJsUnlocksFitAndClearsHeightHold() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("window.__chartZoom = null"))
        assertTrue(reset.contains("clearInterval(window.__chartHeightTimer)"))
        assertTrue(reset.contains("__chartHeightObs.disconnect()"))
    }
}

package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenHoldsChartPartHeightFromSvgMaxY() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height()"))
        assertTrue(js.contains("requestAnimationFrame(step2_height)"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("querySelectorAll('[y]')"))
        assertTrue(js.contains("maxY + 40"))
        assertTrue(js.contains("function enforce()"))
        assertTrue(js.contains("el.style.setProperty('height', target + 'px'"))
        assertTrue(js.contains("setInterval(enforce, 300)"))
        assertTrue(js.contains("attributeFilter: ['style']"))
        assertTrue(js.contains("overflow-y', 'auto'"))
        assertTrue(js.contains("height', 'auto'"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("window.__chartZoom"))
        assertFalse(js.contains("window.__chartMaxY"))
        assertFalse(js.contains("__chartTargetH"))
        assertFalse(js.contains("min-height"))
        assertFalse(js.contains("scrollHeight"))
        assertFalse(js.contains("css-nm4wu0"))
        assertFalse(js.contains("guard < 10"))
        assertFalse(js.contains("identifyOverlay"))
        assertFalse(js.contains("elementsFromPoint"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("viewBox"))
        assertFalse(js.contains("pxPerMeter"))
        assertFalse(js.contains("yOffset"))
        assertFalse(js.contains("Math.min"))
    }

    @Test
    fun resetJsUnlocksFitAndClearsHeightHold() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("clearInterval(window.__chartHeightTimer)"))
        assertTrue(reset.contains("__chartHeightObs.disconnect()"))
        assertFalse(reset.contains("__chartZoom"))
        assertFalse(reset.contains("__chartTargetH"))
    }
}

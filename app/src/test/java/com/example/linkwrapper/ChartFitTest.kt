package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenLocksChartPartHeightFromSvgMaxY() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height()"))
        assertTrue(js.contains("requestAnimationFrame(step2_height)"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("querySelectorAll('[y]')"))
        assertTrue(js.contains("maxY + 40"))
        assertTrue(js.contains("var lockedValue = target + 'px'"))
        assertTrue(js.contains("styleObj.setProperty = function(prop, value, priority)"))
        assertTrue(js.contains("Object.defineProperty(styleObj, 'height'"))
        assertTrue(js.contains("overflow-y', 'auto'"))
        assertTrue(js.contains("height', 'auto'"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("window.__chartZoom"))
        assertFalse(js.contains("window.__chartMaxY"))
        assertFalse(js.contains("__chartTargetH"))
        assertFalse(js.contains("__chartHeightObs"))
        assertFalse(js.contains("__chartHeightTimer"))
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
    fun resetJsOnlyUnlocksFitFlag() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertFalse(reset.contains("__chartZoom"))
        assertFalse(reset.contains("__chartHeightObs"))
        assertFalse(reset.contains("__chartHeightTimer"))
        assertFalse(reset.contains("setInterval"))
    }
}

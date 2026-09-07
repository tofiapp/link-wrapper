package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenGrowsChartPartFromSvgMaxY() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height()"))
        assertTrue(js.contains("requestAnimationFrame(step2_height)"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("querySelectorAll('[y]')"))
        assertTrue(js.contains("maxY + 40"))
        assertTrue(js.contains("el.style.setProperty('height', target + 'px'"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("enforce"))
        assertFalse(js.contains("__chartHeightObs"))
        assertFalse(js.contains("__chartTargetH"))
        assertFalse(js.contains("viewBox"))
        assertFalse(js.contains("pxPerMeter"))
        assertFalse(js.contains("identifyOverlay"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("Math.min"))
    }

    @Test
    fun resetJsUnlocksFitForOrientationChange() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("window.__chartZoom = null"))
        assertFalse(reset.contains("setInterval"))
        assertFalse(reset.contains("__chartHeightObs"))
    }
}

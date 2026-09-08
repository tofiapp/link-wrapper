package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun bootstrapHidesOnlyChartPages() {
        val boot = ChartFit.BOOTSTRAP_JS
        assertTrue(boot.contains("dmId="))
        assertTrue(boot.contains("visibility"))
        assertTrue(boot.contains("hidden"))
        assertTrue(boot.contains("__chartFitHide"))
    }

    @Test
    fun jsFitsWidthThenNeverLowersHeight() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("__chartFitFloorH"))
        assertTrue(js.contains("function raiseFloor"))
        assertTrue(js.contains("overflow:visible"))
        assertTrue(js.contains("function showPage()"))
        assertTrue(js.contains("holdLock(2500)"))
        assertTrue(js.contains("function isChartUrl()"))
        assertTrue(js.contains("foundAt"))
        assertTrue(js.contains("Element.prototype.setAttribute"))
        assertTrue(js.contains("CSSStyleDeclaration.prototype"))
        assertTrue(js.contains("querySelectorAll('[y]')"))
        assertTrue(js.contains("getBBox()"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("window.__chartZoom"))
        assertFalse(js.contains("__chartTargetH"))
        assertFalse(js.contains("__chartHeightTimer"))
        assertFalse(js.contains("css-nm4wu0"))
        assertFalse(js.contains("identifyOverlay"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("viewBox"))
        assertFalse(js.contains("pxPerMeter"))
    }

    @Test
    fun resetJsHidesAndClearsFloorForRefit() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("__chartFitLockObs.disconnect()"))
        assertTrue(reset.contains("__chartFitFloorH = 0"))
        assertTrue(reset.contains("visibility"))
        assertTrue(reset.contains("__chartFitHide = true"))
        assertFalse(reset.contains("__chartZoom"))
        assertFalse(reset.contains("setInterval"))
    }
}

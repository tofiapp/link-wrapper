package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun bootstrapHidesChartPartImmediatelyAndOnSpa() {
        val boot = ChartFit.BOOTSTRAP_JS
        assertTrue(boot.contains("dmId="))
        assertTrue(boot.contains(".chart-part"))
        assertTrue(boot.contains("opacity:0"))
        assertTrue(boot.contains("__chartFitHideStyle"))
        assertTrue(boot.contains("history.pushState"))
        assertTrue(boot.contains("popstate"))
        assertFalse(boot.contains("visibility:hidden"))
        assertFalse(boot.contains("documentElement.style.setProperty('visibility'"))
        assertFalse(boot.contains("__chartFitHide ="))
    }

    @Test
    fun jsZoomsWidthThenRevealsWithoutWaitingForSvg() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("function hideChart()"))
        assertTrue(js.contains("function showChart()"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function finishFit()"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("__chartFitSettling"))
        assertTrue(js.contains("__chartFitFailsafe"))
        assertTrue(js.contains("setTimeout"))
        assertTrue(js.contains("1200"))
        assertTrue(js.contains("function raiseFloor"))
        assertTrue(js.contains("__chartFitFloorH"))
        assertTrue(js.contains("overflow:visible"))
        assertTrue(js.contains("Element.prototype.setAttribute"))
        assertTrue(js.contains("window.__chartFitDone = true"))
        assertFalse(js.contains("readyAt"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("window.__chartZoom"))
        assertFalse(js.contains("__chartTargetH"))
        assertFalse(js.contains("__chartHeightTimer"))
        assertFalse(js.contains("css-nm4wu0"))
        assertFalse(js.contains("identifyOverlay"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("viewBox"))
        assertFalse(js.contains("pxPerMeter"))
        assertFalse(js.contains("Math.min"))
    }

    @Test
    fun hideJsOnlyTargetsChartPart() {
        val hide = ChartFit.HIDE_JS
        assertTrue(hide.contains(".chart-part"))
        assertTrue(hide.contains("opacity:0"))
        assertFalse(hide.contains("documentElement.style.setProperty('visibility'"))
    }

    @Test
    fun resetJsUnlocksFitAndHidesChartPartOnly() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("__chartFitLockObs.disconnect()"))
        assertTrue(reset.contains("__chartFitSettling = false"))
        assertTrue(reset.contains("__chartFitFloorH = 0"))
        assertTrue(reset.contains("__chartFitHideStyle"))
        assertTrue(reset.contains(".chart-part"))
        assertTrue(reset.contains("opacity:0"))
        assertFalse(reset.contains("__chartZoom"))
        assertFalse(reset.contains("setInterval"))
    }
}

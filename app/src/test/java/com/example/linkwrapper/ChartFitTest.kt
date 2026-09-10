package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsThenHoldsThenLocksHeightIncludingSetAttribute() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("if (window.__chartFitDone) return"))
        assertTrue(js.contains("function step1_zoom()"))
        assertTrue(js.contains("function step2_height(tries)"))
        assertTrue(js.contains("function applyWidthZoom()"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("winW <= 0"))
        assertTrue(js.contains("stillThisFit"))
        assertTrue(js.contains("__chartFitGen"))
        assertTrue(js.contains("heldFrom"))
        assertTrue(js.contains("2500"))
        assertTrue(js.contains("applyWidthZoom()"))
        assertTrue(js.contains("step2_height(0)"))
        assertTrue(js.contains("querySelectorAll('[y]')"))
        assertTrue(js.contains("maxY + 40"))
        assertTrue(js.contains("(tries || 0) < 20"))
        assertTrue(js.contains("function mergeLocked(css)"))
        assertTrue(js.contains("el.setAttribute = function(name, value)"))
        assertTrue(js.contains("Element.prototype.setAttribute"))
        assertTrue(js.contains("defineProperty(styleObj, 'cssText'"))
        assertTrue(js.contains("styleObj.removeProperty"))
        assertTrue(js.contains("getAttribute('style')"))
        assertTrue(js.contains("window.__chartFitLockObs"))
        assertTrue(js.contains("needed + 'px'"))
        assertTrue(js.contains("offsetTop"))
        assertTrue(js.contains("y + target + 16"))
        assertTrue(js.contains("min-height"))
        assertFalse(js.contains("target + 96"))
        assertFalse(js.contains("extra + 96"))
        assertFalse(js.contains("height', 'auto'"))
        assertFalse(js.contains("opacity:0"))
        assertFalse(js.contains("__chartFitHideStyle"))
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
    fun resetJsUnlocksFitAndDisconnectsLockObserver() {
        val reset = ChartFit.RESET_JS
        assertTrue(reset.contains("window.__chartFitDone = false"))
        assertTrue(reset.contains("window.__chartFitGen"))
        assertTrue(reset.contains("__chartFitLockObs.disconnect()"))
        assertFalse(reset.contains("opacity:0"))
        assertFalse(reset.contains("__chartZoom"))
        assertFalse(reset.contains("setInterval"))
    }
}

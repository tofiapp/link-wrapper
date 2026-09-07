package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsFitsContainerHeightOnly() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("charts.forEach"))
        assertTrue(js.contains("rect.height / el.offsetHeight"))
        assertTrue(js.contains("availableRendered"))
        assertTrue(js.contains("targetCss"))
        assertTrue(js.contains("setProperty('height'"))
        assertTrue(js.contains("'important'"))
        assertTrue(js.contains("dispatchEvent(new Event('resize'))"))
        assertTrue(js.contains("new MutationObserver"))
        assertTrue(js.contains("childList: true"))
        assertTrue(js.contains("subtree: true"))
        assertTrue(js.contains("obs.disconnect()"))
        assertTrue(js.contains("15000"))
        assertTrue(js.contains("AndroidDebugBridge.showResult"))
        assertTrue(js.contains("svgBoundingHeight po 1000ms"))
    }

    @Test
    fun jsDoesNotTouchSvgOrNudge() {
        val js = ChartFit.FIT_JS
        assertFalse(js.contains("svg.style.setProperty"))
        assertFalse(js.contains("viewBox"))
        assertFalse(js.contains("--pxPerMeter"))
        assertFalse(js.contains("--yOffset"))
        assertFalse(js.contains("nudge"))
        assertFalse(js.contains("requestAnimationFrame"))
        assertFalse(js.contains("pickHeight"))
        assertFalse(js.contains("setProperty('width'"))
        assertFalse(js.contains("style.width"))
    }
}

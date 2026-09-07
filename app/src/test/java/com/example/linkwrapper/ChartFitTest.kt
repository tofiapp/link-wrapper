package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsTargetsEveryChartPart() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("charts.forEach"))
        assertTrue(js.contains("parent.clientHeight"))
        assertTrue(js.contains("setProperty('height'"))
        assertTrue(js.contains("'important'"))
        assertTrue(js.contains("requestAnimationFrame"))
        assertTrue(js.contains("dispatchEvent(new Event('resize'))"))
        assertTrue(js.contains("new MutationObserver"))
        assertTrue(js.contains("childList: true"))
        assertTrue(js.contains("subtree: true"))
        assertTrue(js.contains("obs.disconnect()"))
        assertTrue(js.contains("15000"))
        assertTrue(js.contains("target <= 0"))
    }

    @Test
    fun jsDoesNotTouchWidthOrKeepDebugBridge() {
        val js = ChartFit.FIT_JS
        assertFalse(js.contains("width"))
        assertFalse(js.contains("querySelectorAll('.highcharts"))
        assertFalse(js.contains("document.body.style.height"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("showResult"))
        assertFalse(js.contains("offsetHeightBefore"))
    }
}

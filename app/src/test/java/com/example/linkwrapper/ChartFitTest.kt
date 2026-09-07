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
        assertTrue(js.contains("AndroidDebugBridge.showResult"))
        assertTrue(js.contains("setTimeout"))
        assertTrue(js.contains("offsetHeightBefore"))
        assertTrue(js.contains("styleHeightAfter"))
        assertTrue(js.contains("offsetHeightDelayed"))
    }

    @Test
    fun jsDoesNotTouchWidthOrOtherLayout() {
        val js = ChartFit.FIT_JS
        assertFalse(js.contains("width"))
        assertFalse(js.contains("querySelectorAll('.highcharts"))
        assertFalse(js.contains("document.body.style.height"))
    }
}

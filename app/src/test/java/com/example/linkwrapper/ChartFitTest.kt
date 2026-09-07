package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsHoldsChartPartHeightAndUnlocksOverflowX() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("__chartFixActive"))
        assertTrue(js.contains("rect.height / el.offsetHeight"))
        assertTrue(js.contains("setProperty('height'"))
        assertTrue(js.contains("setProperty('max-height'"))
        assertTrue(js.contains("overflow-x"))
        assertTrue(js.contains("attributeFilter: ['style']"))
        assertTrue(js.contains("setInterval"))
        assertTrue(ChartFit.RESET_JS.contains("window.__chartFixActive = false"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("dispatchEvent(new Event('resize'))"))
        assertFalse(js.contains("svg.style.setProperty"))
        assertFalse(js.contains("setProperty('width'"))
    }
}

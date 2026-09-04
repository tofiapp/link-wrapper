package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartPerfTest {

    @Test
    fun chartCssDoesNotClipOrBlockScroll() {
        val js = ChartPerf.BOOTSTRAP_JS
        assertFalse(js.contains("touch-action:none"))
        assertFalse(js.contains("touch-action: none"))
        assertFalse(js.contains("contain:"))
        assertFalse(js.contains("highcharts-scrolling"))
        assertTrue(js.contains("highcharts-container"))
    }
}

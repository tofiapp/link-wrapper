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
        assertTrue(js.contains("highcharts-scrolling"))
        assertTrue(js.contains("overflow:auto"))
        assertTrue(js.contains("setSize"))
        assertTrue(js.contains("dmId"))
        assertTrue(js.contains("vh * 4"))
        assertTrue(js.contains("StockChart"))
        assertTrue(js.contains("__obalkaFitCharts"))
        assertTrue(ChartPerf.APPLY_JS.contains("__obalkaFitCharts"))
    }
}

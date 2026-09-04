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
        assertTrue(js.contains("StockChart"))
        assertTrue(js.contains("__obalkaFitCharts"))
        assertTrue(ChartPerf.APPLY_JS.contains("__obalkaFitCharts"))
    }

    @Test
    fun chartHeightIsFittedNotMultipliedByViewport() {
        val js = ChartPerf.BOOTSTRAP_JS
        // Stará heuristika "vždy čtyři obrazovky na výšku" je pryč.
        assertFalse(js.contains("vh * 4"))
        assertTrue(js.contains("fitHeight"))
        assertTrue(js.contains("MAX_H"))
        assertTrue(js.contains("MIN_H"))
    }

    @Test
    fun chartIsDetectedWithoutDmIdInUrl() {
        val js = ChartPerf.BOOTSTRAP_JS
        // Graf se pozná podle instance Highcharts, ne podle parametru v URL.
        assertFalse(js.contains("isChartPage"))
        assertFalse(js.contains("dmId"))
        assertTrue(js.contains("H.charts"))
    }

    @Test
    fun chartsAreRemeasuredOnRotation() {
        val js = ChartPerf.BOOTSTRAP_JS
        assertTrue(js.contains("orientationchange"))
        assertTrue(js.contains("resize"))
    }
}

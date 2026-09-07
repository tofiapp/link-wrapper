package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsFitsByRenderedScaleNotCssMix() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("charts.forEach"))
        assertTrue(js.contains("getBoundingClientRect()"))
        assertTrue(js.contains("rect.height / el.offsetHeight"))
        assertTrue(js.contains("availableRendered"))
        assertTrue(js.contains("targetCss"))
        assertTrue(js.contains("setProperty('height'"))
        assertTrue(js.contains("'important'"))
        assertTrue(js.contains("requestAnimationFrame"))
        assertTrue(js.contains("dispatchEvent(new Event('resize'))"))
        assertTrue(js.contains("new MutationObserver"))
        assertTrue(js.contains("childList: true"))
        assertTrue(js.contains("subtree: true"))
        assertTrue(js.contains("obs.disconnect()"))
        assertTrue(js.contains("15000"))
        assertTrue(js.contains("AndroidDebugBridge.showResult"))
        assertTrue(js.contains("1000"))
    }

    @Test
    fun jsDropsOldCssPixelFallback() {
        val js = ChartFit.FIT_JS
        assertFalse(js.contains("pickHeight"))
        assertFalse(js.contains("documentElement.clientHeight"))
        assertFalse(js.contains("querySelectorAll('.highcharts"))
        assertFalse(js.contains("document.body.style.height"))
        assertFalse(js.contains("style.width"))
        assertFalse(js.contains("setProperty('width'"))
    }
}

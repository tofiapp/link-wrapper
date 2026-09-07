package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsFitsContainerWithoutResizeEvent() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("charts.forEach"))
        assertTrue(js.contains("rect.height / el.offsetHeight"))
        assertTrue(js.contains("targetCss"))
        assertTrue(js.contains("setProperty('height'"))
        assertTrue(js.contains("min-height"))
        assertTrue(js.contains("max-height"))
        assertTrue(js.contains("new MutationObserver"))
        assertTrue(js.contains("svgBoundingHeight po 1000ms"))
        assertTrue(js.contains("window.innerWidth"))
        assertTrue(js.contains("--scrollLeft"))
        assertFalse(js.contains("dispatchEvent(new Event('resize'))"))
        assertFalse(js.contains("svg.style.setProperty"))
        assertFalse(js.contains("setProperty('width'"))
    }
}

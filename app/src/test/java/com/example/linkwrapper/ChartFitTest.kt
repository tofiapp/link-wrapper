package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsIsDiagnosticOnlyWhileHeightIsCommentedOut() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("HEIGHT INJECTION OFF"))
        assertTrue(js.contains("querySelectorAll('.chart-part')"))
        assertTrue(js.contains("rect.height / el.offsetHeight"))
        assertTrue(js.contains("el.getBoundingClientRect().width"))
        assertTrue(js.contains("svg.getBoundingClientRect().width"))
        assertTrue(js.contains("window.innerWidth"))
        assertTrue(js.contains("visualViewport.height"))
        assertTrue(js.contains("--scrollTop"))
        assertTrue(js.contains("--scrollLeft"))
        assertTrue(js.contains("--pxPerMeter"))
        assertTrue(js.contains("new MutationObserver"))
        assertFalse(js.contains("dispatchEvent(new Event('resize'))"))
        assertFalse(js.contains("svg.style.setProperty"))
        assertFalse(js.contains("setProperty('width'"))
    }
}

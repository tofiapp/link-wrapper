package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsOnlyUnlocksRootOverflow() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelector('.chart-part')"))
        assertTrue(js.contains("document.documentElement.style.setProperty('overflow'"))
        assertTrue(js.contains("document.body.style.setProperty('overflow'"))
        assertTrue(js.contains("'auto'"))
        assertTrue(js.contains("document.documentElement.style.setProperty('height'"))
        assertTrue(js.contains("'100%'"))
        assertTrue(js.contains("new MutationObserver"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("setProperty('max-height'"))
        assertFalse(js.contains("setInterval"))
        assertFalse(js.contains("__chartFixActive"))
        assertFalse(js.contains("chart-part').style"))
        assertFalse(js.contains("el.style.setProperty('height'"))
    }
}

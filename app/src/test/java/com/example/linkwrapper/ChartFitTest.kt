package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartFitTest {

    @Test
    fun jsZoomsBodyFromWidthThenFitsHeight() {
        val js = ChartFit.FIT_JS
        assertTrue(js.contains("querySelector('.chart-part')"))
        assertTrue(js.contains("document.documentElement.style.zoom = ''"))
        assertTrue(js.contains("document.body.style.zoom = ''"))
        assertTrue(js.contains("winW / contentW"))
        assertTrue(js.contains("document.body.style.setProperty('zoom'"))
        assertTrue(js.contains("overflow-y"))
        assertTrue(js.contains("overflow-x"))
        assertTrue(js.contains("'hidden'"))
        assertTrue(js.contains("requestAnimationFrame"))
        assertTrue(js.contains("el.style.setProperty('height'"))
        assertTrue(js.contains("new MutationObserver"))
        assertFalse(js.contains("AndroidDebugBridge"))
        assertFalse(js.contains("Math.min"))
        assertFalse(js.contains("document.documentElement.style.setProperty('zoom'"))
        assertFalse(js.contains("setInterval"))
    }
}

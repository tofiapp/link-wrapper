package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsstDataLayoutTest {

    @Test
    fun fillsViewportAfterPageZoomAndStacksThirdCard() {
        val js = PsstDataLayout.APPLY_JS
        assertTrue(js.contains("innerWidth > window.innerHeight"))
        assertTrue(js.contains("grid-rows-3"))
        assertTrue(js.contains("flex-1"))
        assertTrue(js.contains("grid-template-columns:repeat(2,minmax(0,1fr))"))
        assertTrue(js.contains("grid-template-rows:minmax(0,1fr) minmax(0,1fr)"))
        assertTrue(js.contains("nth-child(3)"))
        assertTrue(js.contains("grid-column:1/-1"))
        assertTrue(js.contains("data-obalka-psst-grid"))
        assertTrue(js.contains("data-slot") && js.contains("card-content"))
        assertTrue(js.contains("flex:1 1 0%"))
        assertTrue(js.contains("visualViewport"))
        assertTrue(js.contains("document.documentElement.style.zoom"))
        assertTrue(js.contains("(vis - top) / zoom"))
        assertTrue(js.contains("h < 320"))
        assertTrue(js.contains("padding-bottom:4px"))
        assertTrue(js.contains("gap:6px"))
        assertTrue(js.contains("box-sizing:border-box"))
        assertFalse(js.contains("innerHeight - top + 32"))
        assertFalse(js.contains("grid-rows-3:repeat"))
    }
}

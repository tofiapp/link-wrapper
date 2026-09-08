package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsstDataLayoutTest {

    @Test
    fun landscapeCssPutsThirdCardUnderTheFirstTwo() {
        val js = PsstDataLayout.APPLY_JS
        assertTrue(js.contains("innerWidth > window.innerHeight"))
        assertTrue(js.contains("grid-rows-3"))
        assertTrue(js.contains("grid-template-columns:repeat(2,minmax(0,1fr))"))
        assertTrue(js.contains("grid-template-rows:minmax(0,1fr) minmax(0,1fr)"))
        assertTrue(js.contains("nth-child(3)"))
        assertTrue(js.contains("grid-column:1/-1"))
        assertFalse(js.contains("repeat(3,minmax(0,1fr))"))
        assertTrue(js.contains("card-header"))
        assertTrue(js.contains("flex:0 0 auto"))
        assertTrue(js.contains("card-content"))
        assertTrue(js.contains("flex:1 1 0%"))
        assertTrue(js.contains("overflow:auto"))
        assertTrue(js.contains("getBoundingClientRect().top"))
        assertTrue(js.contains("innerHeight - top + 32"))
        assertTrue(js.contains("h < 240"))
        assertTrue(js.contains("gap:8px"))
        assertFalse(js.contains("grid-rows-3:repeat"))
    }
}

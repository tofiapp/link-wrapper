package com.example.linkwrapper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsstDataLayoutTest {

    @Test
    fun landscapeCssSwitchesThreeRowsToThreeColumns() {
        val js = PsstDataLayout.APPLY_JS
        assertTrue(js.contains("innerWidth > window.innerHeight"))
        assertTrue(js.contains("grid-rows-3"))
        assertTrue(js.contains("grid-template-columns:repeat(3,minmax(0,1fr))"))
        assertTrue(js.contains("grid-template-rows:minmax(0,1fr)"))
        assertTrue(js.contains("orientationchange"))
        assertFalse(js.contains("grid-rows-3:repeat"))
    }
}

package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {

    @Test
    fun portraitIsCloserThanLandscape() {
        assertEquals(88, PageZoom.percent(landscape = false))
        assertEquals(80, PageZoom.percent(landscape = true))
    }

    @Test
    fun clampKeepsUserSizeInRange() {
        assertEquals(PageZoom.MIN_PERCENT, PageZoom.clamp(10))
        assertEquals(PageZoom.MAX_PERCENT, PageZoom.clamp(400))
        assertEquals(100, PageZoom.clamp(100))
    }

    @Test
    fun jsUsesClampedPercent() {
        val js = PageZoom.setJs(1000)
        assertTrue(js.contains("${PageZoom.MAX_PERCENT}%"))
        val apply = PageZoom.applyJs(1)
        assertTrue(apply.contains("${PageZoom.MIN_PERCENT}%"))
    }
}

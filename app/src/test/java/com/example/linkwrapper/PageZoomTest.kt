package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Test

class PageZoomTest {

    @Test
    fun portraitIsCloserThanLandscape() {
        assertEquals(88, PageZoom.percent(landscape = false))
        assertEquals(80, PageZoom.percent(landscape = true))
    }
}

package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {

    @Test
    fun defaultUserSizeIsNatural() {
        assertEquals(100, PageZoom.percent(landscape = false))
        assertEquals(100, PageZoom.percent(landscape = true))
        assertEquals(100, PageZoom.CHART_PERCENT)
    }

    @Test
    fun chartsAreDetectedFromDmId() {
        assertEquals(
            PageZoom.Kind.Chart,
            PageZoom.kindFor("https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12")
        )
        assertEquals(
            PageZoom.Kind.Psst,
            PageZoom.kindFor(Destinations.PSST_URL)
        )
        assertEquals(
            PageZoom.Kind.Dsd,
            PageZoom.kindFor(Destinations.DSD_URL)
        )
    }

    @Test
    fun clampKeepsUserSizeInRange() {
        assertEquals(PageZoom.MIN_PERCENT, PageZoom.clamp(10))
        assertEquals(PageZoom.MAX_PERCENT, PageZoom.clamp(400))
        assertEquals(100, PageZoom.clamp(100))
    }

    @Test
    fun snapAlignsToStep() {
        assertEquals(86, PageZoom.snap(87))
        assertEquals(88, PageZoom.snap(88))
        assertEquals(PageZoom.MIN_PERCENT, PageZoom.snap(1))
        assertEquals(PageZoom.MAX_PERCENT, PageZoom.snap(999))
    }

    @Test
    fun hundredPercentClearsCssZoom() {
        val js = PageZoom.setJs(100)
        assertTrue(js.contains("removeProperty('zoom')"))
        assertFalse(js.contains("style.zoom='100%'"))
    }

    @Test
    fun nonDefaultZoomOnlyTouchesDocumentElement() {
        val js = PageZoom.setJs(80)
        assertTrue(js.contains("style.zoom='80%'"))
        assertTrue(js.contains("removeProperty('zoom')"))
        val apply = PageZoom.applyJs(1)
        assertTrue(apply.contains("${PageZoom.MIN_PERCENT}%"))
        val picker = PageZoom.pickerJs(100, 100)
        assertTrue(picker.contains("dmId"))
        assertTrue(picker.contains("removeProperty('zoom')"))
        val clamped = PageZoom.setJs(1000)
        assertTrue(clamped.contains("${PageZoom.MAX_PERCENT}%"))
    }
}

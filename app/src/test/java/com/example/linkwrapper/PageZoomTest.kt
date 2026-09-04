package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageZoomTest {

    @Test
    fun defaultUserSizeFillsWidth() {
        assertEquals(100, PageZoom.percent(landscape = false))
        assertEquals(100, PageZoom.percent(landscape = true))
    }

    @Test
    fun chartsAreFixedAt84() {
        assertEquals(84, PageZoom.CHART_PERCENT)
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
    fun visualZoomFitsDesktopViewportToScreen() {
        assertEquals(100, PageZoom.visualPercent(1280f, 100))
        assertEquals(50, PageZoom.visualPercent(640f, 100))
        assertEquals(62, PageZoom.visualPercent(800f, 100))
        assertEquals(42, PageZoom.visualPercent(640f, 84))
        assertEquals(PageZoom.VISUAL_MIN_PERCENT, PageZoom.visualPercent(100f, 100))
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
    fun jsUsesVisualPercentRange() {
        val js = PageZoom.setJs(1000)
        assertTrue(js.contains("${PageZoom.VISUAL_MAX_PERCENT}%"))
        val apply = PageZoom.applyJs(1)
        assertTrue(apply.contains("${PageZoom.VISUAL_MIN_PERCENT}%"))
        val picker = PageZoom.pickerJs(62, 62, 52)
        assertTrue(picker.contains("62%"))
        assertTrue(picker.contains("52%"))
        assertTrue(picker.contains("dmId"))
    }
}

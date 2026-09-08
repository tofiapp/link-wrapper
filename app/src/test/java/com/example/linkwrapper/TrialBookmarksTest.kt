package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialBookmarksTest {

    @Test
    fun roundTripKeepsTitleAndUrl() {
        val items = listOf(
            TrialBookmark("a", "Graf 12", "https://test.psst.tudc.cz/HSI.Psst.Data?dmId=12"),
            TrialBookmark("b", "PSST Data", Destinations.PSST_URL)
        )
        val restored = TrialBookmarks.decode(TrialBookmarks.encode(items))
        assertEquals(items, restored)
    }

    @Test
    fun titleMayContainTabsAndNewlinesAsEscapes() {
        val items = listOf(TrialBookmark("x", "Graf\t12\nlist", "https://psst.tudc.cz/x"))
        val restored = TrialBookmarks.decode(TrialBookmarks.encode(items))
        assertEquals(items, restored)
    }

    @Test
    fun emptyAndJunkDecodeToNothing() {
        assertTrue(TrialBookmarks.decode(null).isEmpty())
        assertTrue(TrialBookmarks.decode("").isEmpty())
        assertTrue(TrialBookmarks.decode("not-a-bookmark").isEmpty())
    }

    @Test
    fun sameUrlIgnoresCaseAndTrim() {
        assertTrue(
            TrialBookmarks.sameUrl(
                " https://psst.tudc.cz/PsstData ",
                "https://psst.tudc.cz/PsstData"
            )
        )
    }
}

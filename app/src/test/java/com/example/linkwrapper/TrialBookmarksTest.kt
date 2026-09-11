package com.example.linkwrapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun roundTripKeepsFolderId() {
        val items = listOf(
            TrialBookmark("a", "Graf 12", "https://psst.tudc.cz/x", "folder-1"),
            TrialBookmark("b", "Bez skupiny", "https://psst.tudc.cz/y")
        )
        val restored = TrialBookmarks.decode(TrialBookmarks.encode(items))
        assertEquals(items, restored)
        assertEquals("folder-1", restored[0].folderId)
        assertNull(restored[1].folderId)
    }

    @Test
    fun legacyThreeFieldLineStaysUnfiled() {
        val restored = TrialBookmarks.decode("a\tGraf\thttps://psst.tudc.cz/x")
        assertEquals(listOf(TrialBookmark("a", "Graf", "https://psst.tudc.cz/x")), restored)
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

    @Test
    fun findByUrlIgnoresCaseAndTrim() {
        val items = listOf(
            TrialBookmark("a", "Graf", "https://psst.tudc.cz/x")
        )
        assertEquals(
            items[0],
            TrialBookmarks.findByUrl(items, " HTTPS://PSST.TUDC.CZ/X ")
        )
    }

    @Test
    fun findByUrlMissesUnknownAndBlank() {
        val items = listOf(
            TrialBookmark("a", "Graf", "https://psst.tudc.cz/x")
        )
        assertEquals(null, TrialBookmarks.findByUrl(items, "https://other.example"))
        assertEquals(null, TrialBookmarks.findByUrl(items, "  "))
        assertEquals(null, TrialBookmarks.findByUrl(emptyList(), "https://psst.tudc.cz/x"))
    }

    @Test
    fun folderRoundTripKeepsCollapsed() {
        val folders = listOf(
            TrialBookmarkFolder("f1", "Ranní", collapsed = true),
            TrialBookmarkFolder("f2", "Večer", collapsed = false)
        )
        val restored = TrialBookmarks.decodeFolders(TrialBookmarks.encodeFolders(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun folderRoundTripKeepsTabGrouped() {
        val folders = listOf(
            TrialBookmarkFolder("f1", "Ranní", collapsed = false, tabGrouped = true),
            TrialBookmarkFolder("f2", "Večer", collapsed = true, tabGrouped = false)
        )
        val restored = TrialBookmarks.decodeFolders(TrialBookmarks.encodeFolders(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun legacyFolderLineDefaultsTabGroupedFalse() {
        val restored = TrialBookmarks.decodeFolders("f1\tRanní\t1")
        assertEquals(listOf(TrialBookmarkFolder("f1", "Ranní", collapsed = true)), restored)
    }

    @Test
    fun folderTitleMayContainTabs() {
        val folders = listOf(TrialBookmarkFolder("f", "Skupina\tA", collapsed = false))
        val restored = TrialBookmarks.decodeFolders(TrialBookmarks.encodeFolders(folders))
        assertEquals(folders, restored)
    }

    @Test
    fun itemsInFolderReturnsOnlyMatchingBookmarks() {
        val items = listOf(
            TrialBookmark("a", "A", "https://a.example", "f1"),
            TrialBookmark("b", "B", "https://b.example"),
            TrialBookmark("c", "C", "https://c.example", "f1")
        )
        val inFolder = TrialBookmarks.itemsInFolder(items, "f1")
        assertEquals(listOf("a", "c"), inFolder.map { it.id })
    }

    @Test
    fun groupedPutsUnfiledFirstThenNamedFolders() {
        val folders = listOf(
            TrialBookmarkFolder("f1", "Ranní"),
            TrialBookmarkFolder("f2", "Večer")
        )
        val items = listOf(
            TrialBookmark("a", "A", "https://a.example", "f2"),
            TrialBookmark("b", "B", "https://b.example"),
            TrialBookmark("c", "C", "https://c.example", "f1"),
            TrialBookmark("d", "D", "https://d.example", "gone")
        )
        val groups = TrialBookmarks.grouped(items, folders, includeEmptyFolders = false)
        assertEquals(3, groups.size)
        assertNull(groups[0].folder)
        assertEquals(listOf("b", "d"), groups[0].items.map { it.id })
        assertEquals("f1", groups[1].folder?.id)
        assertEquals(listOf("c"), groups[1].items.map { it.id })
        assertEquals("f2", groups[2].folder?.id)
        assertEquals(listOf("a"), groups[2].items.map { it.id })
    }

    @Test
    fun groupedCanKeepEmptyNamedFolders() {
        val folders = listOf(TrialBookmarkFolder("empty", "Prázdná"))
        val items = listOf(TrialBookmark("b", "B", "https://b.example"))
        val home = TrialBookmarks.grouped(items, folders, includeEmptyFolders = false)
        assertEquals(1, home.size)
        assertNull(home[0].folder)
        val popup = TrialBookmarks.grouped(
            items,
            folders,
            includeEmptyFolders = true,
            foldersFirst = true
        )
        assertEquals(2, popup.size)
        assertEquals("empty", popup[0].folder?.id)
        assertTrue(popup[0].items.isEmpty())
        assertNull(popup[1].folder)
    }

    @Test
    fun groupedFoldersFirstPutsNamedGroupsAboveUnfiled() {
        val folders = listOf(TrialBookmarkFolder("f1", "Ranní"))
        val items = listOf(
            TrialBookmark("a", "A", "https://a.example", "f1"),
            TrialBookmark("b", "B", "https://b.example")
        )
        val groups = TrialBookmarks.grouped(
            items,
            folders,
            includeEmptyFolders = true,
            foldersFirst = true
        )
        assertEquals("f1", groups[0].folder?.id)
        assertNull(groups[1].folder)
        assertEquals(listOf("b"), groups[1].items.map { it.id })
    }
}

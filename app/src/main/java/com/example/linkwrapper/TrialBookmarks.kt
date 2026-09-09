package com.example.linkwrapper

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class TrialBookmark(
    val id: String,
    val title: String,
    val url: String,
    val folderId: String? = null
)

data class TrialBookmarkFolder(
    val id: String,
    val title: String,
    val collapsed: Boolean = false
)

data class BookmarkGroup(
    val folder: TrialBookmarkFolder?,
    val items: List<TrialBookmark>
)

/**
 * Složka: uložené URL, popisky a volitelné podsložky. Smazání relace tenhle seznam neshodí.
 */
internal object TrialBookmarks {

    const val MAX_ITEMS = 40
    const val MAX_FOLDERS = 20

    private const val PREFS = "trial_bookmarks"
    private const val KEY_ITEMS = "items"
    private const val KEY_FOLDERS = "folders"

    fun load(context: Context): List<TrialBookmark> = decode(prefs(context).getString(KEY_ITEMS, null))

    fun loadFolders(context: Context): List<TrialBookmarkFolder> =
        decodeFolders(prefs(context).getString(KEY_FOLDERS, null))

    fun add(context: Context, title: String, url: String, folderId: String? = null): TrialBookmark? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || cleanUrl == Destinations.HOME_URL) return null
        val cleanTitle = title.trim().ifEmpty { Destinations.tabTitle(cleanUrl) }
        val items = load(context).toMutableList()
        val existing = items.indexOfFirst { sameUrl(it.url, cleanUrl) }
        if (existing >= 0) {
            val updated = items[existing].copy(
                title = cleanTitle,
                folderId = folderId ?: items[existing].folderId
            )
            items[existing] = updated
            persistItems(context, items)
            return updated
        }
        if (items.size >= MAX_ITEMS) return null
        val added = TrialBookmark(
            UUID.randomUUID().toString(),
            cleanTitle,
            cleanUrl,
            normalizeFolderId(folderId)
        )
        items.add(0, added)
        persistItems(context, items)
        return added
    }

    fun rename(context: Context, id: String, title: String, folderId: String? = null): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        val items = load(context).toMutableList()
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return false
        items[i] = items[i].copy(title = clean, folderId = normalizeFolderId(folderId))
        persistItems(context, items)
        return true
    }

    fun remove(context: Context, id: String) {
        persistItems(context, load(context).filterNot { it.id == id })
    }

    fun addFolder(context: Context, title: String): TrialBookmarkFolder? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val folders = loadFolders(context).toMutableList()
        val existing = folders.firstOrNull { it.title.equals(clean, ignoreCase = true) }
        if (existing != null) return existing
        if (folders.size >= MAX_FOLDERS) return null
        val added = TrialBookmarkFolder(UUID.randomUUID().toString(), clean, collapsed = false)
        folders.add(added)
        persistFolders(context, folders)
        return added
    }

    fun renameFolder(context: Context, id: String, title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        val folders = loadFolders(context).toMutableList()
        val i = folders.indexOfFirst { it.id == id }
        if (i < 0) return false
        if (folders.any { it.id != id && it.title.equals(clean, ignoreCase = true) }) return false
        folders[i] = folders[i].copy(title = clean)
        persistFolders(context, folders)
        return true
    }

    fun removeFolder(context: Context, id: String) {
        persistFolders(context, loadFolders(context).filterNot { it.id == id })
        persistItems(
            context,
            load(context).map { item ->
                if (item.folderId == id) item.copy(folderId = null) else item
            }
        )
    }

    fun setFolderCollapsed(context: Context, id: String, collapsed: Boolean) {
        val folders = loadFolders(context).toMutableList()
        val i = folders.indexOfFirst { it.id == id }
        if (i < 0) return
        folders[i] = folders[i].copy(collapsed = collapsed)
        persistFolders(context, folders)
    }

    fun grouped(
        items: List<TrialBookmark>,
        folders: List<TrialBookmarkFolder>,
        includeEmptyFolders: Boolean = false
    ): List<BookmarkGroup> {
        val known = folders.map { it.id }.toSet()
        val unfiled = items.filter { item ->
            val folderId = normalizeFolderId(item.folderId)
            folderId == null || folderId !in known
        }
        val result = mutableListOf<BookmarkGroup>()
        if (unfiled.isNotEmpty()) result.add(BookmarkGroup(null, unfiled))
        folders.forEach { folder ->
            val inFolder = items.filter { it.folderId == folder.id }
            if (inFolder.isNotEmpty() || includeEmptyFolders) {
                result.add(BookmarkGroup(folder, inFolder))
            }
        }
        return result
    }

    fun encode(items: List<TrialBookmark>): String =
        items.joinToString("\n") { item ->
            val cols = mutableListOf(item.id, enc(item.title), enc(item.url))
            val folderId = normalizeFolderId(item.folderId)
            if (folderId != null) cols.add(folderId)
            cols.joinToString("\t")
        }

    fun decode(raw: String?): List<TrialBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val id = parts[0].trim()
            val title = dec(parts[1])
            val url = dec(parts[2])
            val folderId = normalizeFolderId(parts.getOrNull(3))
            if (id.isEmpty() || url.isEmpty()) return@mapNotNull null
            TrialBookmark(id, title.ifEmpty { Destinations.tabTitle(url) }, url, folderId)
        }.toList()
    }

    fun encodeFolders(folders: List<TrialBookmarkFolder>): String =
        folders.joinToString("\n") { folder ->
            listOf(
                folder.id,
                enc(folder.title),
                if (folder.collapsed) "1" else "0"
            ).joinToString("\t")
        }

    fun decodeFolders(raw: String?): List<TrialBookmarkFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val id = parts[0].trim()
            val title = dec(parts[1]).trim()
            if (id.isEmpty() || title.isEmpty()) return@mapNotNull null
            val collapsed = parts.getOrNull(2)?.trim() == "1"
            TrialBookmarkFolder(id, title, collapsed)
        }.toList()
    }

    fun findByUrl(items: List<TrialBookmark>, url: String): TrialBookmark? {
        val clean = url.trim()
        if (clean.isEmpty()) return null
        return items.firstOrNull { sameUrl(it.url, clean) }
    }

    fun findByUrl(context: Context, url: String): TrialBookmark? = findByUrl(load(context), url)

    fun isSaved(context: Context, url: String): Boolean = findByUrl(context, url) != null

    fun sameUrl(a: String, b: String): Boolean =
        a.trim().equals(b.trim(), ignoreCase = true)

    fun normalizeFolderId(folderId: String?): String? =
        folderId?.trim()?.ifEmpty { null }

    private fun persistItems(context: Context, items: List<TrialBookmark>) {
        prefs(context).edit().putString(KEY_ITEMS, encode(items)).commit()
    }

    private fun persistFolders(context: Context, folders: List<TrialBookmarkFolder>) {
        prefs(context).edit().putString(KEY_FOLDERS, encodeFolders(folders)).commit()
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun dec(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

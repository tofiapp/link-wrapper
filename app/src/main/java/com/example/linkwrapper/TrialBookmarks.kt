package com.example.linkwrapper

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class TrialBookmark(
    val id: String,
    val title: String,
    val url: String
)

/**
 * Složka: uložené URL a popisky. Přežije wipe relace ve zkušební APK
 * — smaže se jen přihlášení, ne tenhle seznam.
 */
internal object TrialBookmarks {

    const val MAX_ITEMS = 40

    private const val PREFS = "trial_bookmarks"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): List<TrialBookmark> = decode(prefs(context).getString(KEY_ITEMS, null))

    fun add(context: Context, title: String, url: String): TrialBookmark? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || cleanUrl == Destinations.HOME_URL) return null
        val cleanTitle = title.trim().ifEmpty { Destinations.tabTitle(cleanUrl) }
        val items = load(context).toMutableList()
        val existing = items.indexOfFirst { sameUrl(it.url, cleanUrl) }
        if (existing >= 0) {
            val updated = items[existing].copy(title = cleanTitle)
            items[existing] = updated
            persist(context, items)
            return updated
        }
        if (items.size >= MAX_ITEMS) return null
        val added = TrialBookmark(UUID.randomUUID().toString(), cleanTitle, cleanUrl)
        items.add(0, added)
        persist(context, items)
        return added
    }

    fun rename(context: Context, id: String, title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        val items = load(context).toMutableList()
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return false
        items[i] = items[i].copy(title = clean)
        persist(context, items)
        return true
    }

    fun remove(context: Context, id: String) {
        persist(context, load(context).filterNot { it.id == id })
    }

    fun encode(items: List<TrialBookmark>): String =
        items.joinToString("\n") { item ->
            listOf(item.id, enc(item.title), enc(item.url)).joinToString("\t")
        }

    fun decode(raw: String?): List<TrialBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val id = parts[0].trim()
            val title = dec(parts[1])
            val url = dec(parts[2])
            if (id.isEmpty() || url.isEmpty()) return@mapNotNull null
            TrialBookmark(id, title.ifEmpty { Destinations.tabTitle(url) }, url)
        }.toList()
    }

    fun sameUrl(a: String, b: String): Boolean =
        a.trim().equals(b.trim(), ignoreCase = true)

    private fun persist(context: Context, items: List<TrialBookmark>) {
        prefs(context).edit().putString(KEY_ITEMS, encode(items)).commit()
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

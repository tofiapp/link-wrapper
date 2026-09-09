package com.example.linkwrapper

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TrialPin(
    val title: String,
    val url: String,
    val pinned: Boolean = true
)

/**
 * Otevřené karty (připnuté i ostatní) jen v RAM tohoto procesu.
 * Po úplném vypnutí appky (proces končí) se nenačtou znovu.
 * Na pozadí, dokud proces žije, zůstanou.
 */
internal object TrialPins {

    const val MAX_ITEMS = 7

    private const val PREFS = "trial_pins"
    private const val KEY_ITEMS = "items"

    private var sessionItems: List<TrialPin> = emptyList()
    @Volatile private var diskDropped = false

    fun load(context: Context): List<TrialPin> {
        dropDisk(context)
        return sessionItems
    }

    fun save(context: Context, items: List<TrialPin>) {
        dropDisk(context)
        replaceSession(
            items.take(MAX_ITEMS).filter { it.url.isNotBlank() && it.url != Destinations.HOME_URL }
        )
    }

    fun dropDisk(context: Context) {
        if (diskDropped) return
        diskDropped = true
        prefs(context).edit().remove(KEY_ITEMS).commit()
    }

    fun replaceSession(items: List<TrialPin>) {
        sessionItems = items
    }

    fun peekSession(): List<TrialPin> = sessionItems

    fun clearSession() {
        sessionItems = emptyList()
    }

    fun encode(items: List<TrialPin>): String =
        items.joinToString("\n") { item ->
            listOf(enc(item.title), enc(item.url), if (item.pinned) "1" else "0").joinToString("\t")
        }

    fun decode(raw: String?): List<TrialPin> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val title = dec(parts[0])
            val url = dec(parts[1])
            if (url.isEmpty() || url == Destinations.HOME_URL) return@mapNotNull null
            val pinned = parts.size < 3 || parts[2].trim() != "0"
            TrialPin(title.ifEmpty { Destinations.tabTitle(url) }, url, pinned)
        }.toList()
    }

    fun stripGroup(isHome: Boolean, pinned: Boolean): Int = when {
        isHome -> 0
        pinned -> 1
        else -> 2
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

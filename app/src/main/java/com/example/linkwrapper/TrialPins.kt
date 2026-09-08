package com.example.linkwrapper

import android.content.Context
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TrialPin(
    val title: String,
    val url: String
)

/**
 * Zkušební APK: karty připnuté nahoru. Zůstanou v liště i po minutovém
 * odhlášení a po restartu procesu — smaže se jen relace, ne tenhle seznam.
 */
internal object TrialPins {

    const val MAX_ITEMS = 7

    private const val PREFS = "trial_pins"
    private const val KEY_ITEMS = "items"

    fun load(context: Context): List<TrialPin> =
        decode(prefs(context).getString(KEY_ITEMS, null))

    fun save(context: Context, items: List<TrialPin>) {
        persist(context, items.take(MAX_ITEMS).filter { it.url.isNotBlank() && it.url != Destinations.HOME_URL })
    }

    fun encode(items: List<TrialPin>): String =
        items.joinToString("\n") { item ->
            listOf(enc(item.title), enc(item.url)).joinToString("\t")
        }

    fun decode(raw: String?): List<TrialPin> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val title = dec(parts[0])
            val url = dec(parts[1])
            if (url.isEmpty() || url == Destinations.HOME_URL) return@mapNotNull null
            TrialPin(title.ifEmpty { Destinations.tabTitle(url) }, url)
        }.toList()
    }

    /**
     * Po výpadku relace: přihlášení až když je VPN zpět a nějaká
     * připnutá karta má zůstat otevřená.
     */
    fun shouldPromptLogin(
        sessionActive: Boolean,
        connectionOk: Boolean,
        hasPinnedTabs: Boolean
    ): Boolean {
        if (sessionActive || !hasPinnedTabs) return false
        return connectionOk
    }

    fun stripGroup(isHome: Boolean, pinned: Boolean): Int = when {
        isHome -> 0
        pinned -> 1
        else -> 2
    }

    private fun persist(context: Context, items: List<TrialPin>) {
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

package com.example.linkwrapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Jednoduché lokální úložiště historie otevřených odkazů.
 * Ukládá se do SharedPreferences jako JSON pole [{url, timestamp}, ...].
 * Appka si sama čistí záznamy starší než 24 hodin.
 */
data class HistoryEntry(val url: String, val timestamp: Long)

object LinkHistory {

    private const val PREFS_NAME = "link_history_prefs"
    private const val KEY_ENTRIES = "entries"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun addEntry(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = loadAll(context).toMutableList()
        entries.add(0, HistoryEntry(url, System.currentTimeMillis()))

        val cutoff = System.currentTimeMillis() - DAY_MS
        val trimmed = entries.filter { it.timestamp >= cutoff }

        val array = JSONArray()
        for (entry in trimmed) {
            val obj = JSONObject()
            obj.put("url", entry.url)
            obj.put("timestamp", entry.timestamp)
            array.put(obj)
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    /** Vrátí jen záznamy za posledních 24 hodin, nejnovější první. */
    fun getLastDay(context: Context): List<HistoryEntry> {
        val cutoff = System.currentTimeMillis() - DAY_MS
        return loadAll(context).filter { it.timestamp >= cutoff }
    }

    private fun loadAll(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HistoryEntry(obj.getString("url"), obj.getLong("timestamp"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

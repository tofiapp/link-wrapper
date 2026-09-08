package com.example.linkwrapper

import android.content.Context
import android.os.SystemClock

/**
 * Zkušební APK: po minutě na pozadí pryč relace, cookies i HTTP auth
 * (PSST, grafy, DSD). Připnuté karty zůstanou otevřené v procesu —
 * nesmaže se Chromium profil ani se nerestartuje appka. Čas se píše
 * do prefs, aby to platilo i po zabití procesu. Běžná APK tohle nedělá.
 */
internal object TrialIdle {

    const val TIMEOUT_MS = 60_000L

    private const val PREFS = "trial_idle"
    private const val KEY_ELAPSED = "bg_elapsed"
    private const val KEY_WALL = "bg_wall"

    fun markBackground(
        context: Context,
        elapsedRealtime: Long = SystemClock.elapsedRealtime(),
        wallClock: Long = System.currentTimeMillis()
    ) {
        if (!TrialSettings.isTrial()) return
        prefs(context).edit()
            .putLong(KEY_ELAPSED, elapsedRealtime)
            .putLong(KEY_WALL, wallClock)
            .commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun shouldWipe(
        context: Context,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowWall: Long = System.currentTimeMillis()
    ): Boolean {
        if (!TrialSettings.isTrial()) return false
        val stored = prefs(context)
        return isDue(
            stored.getLong(KEY_ELAPSED, 0L),
            nowElapsed,
            stored.getLong(KEY_WALL, 0L),
            nowWall
        )
    }

    fun isDue(
        backgroundedElapsed: Long,
        nowElapsed: Long,
        backgroundedWall: Long,
        nowWall: Long
    ): Boolean {
        if (backgroundedElapsed > 0L && nowElapsed >= backgroundedElapsed) {
            return nowElapsed - backgroundedElapsed >= TIMEOUT_MS
        }
        if (backgroundedWall > 0L) {
            return nowWall - backgroundedWall >= TIMEOUT_MS
        }
        return false
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

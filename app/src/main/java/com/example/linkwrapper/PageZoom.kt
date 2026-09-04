package com.example.linkwrapper

import android.content.Context

/**
 * Oddálení webu ve WebView. Bez vlastního nastavení je na výšku stránka
 * o něco větší (snadnější klepnutí) a na šířku o něco menší (desktopové
 * weby na širokém displeji jinak působí přiblíženě).
 *
 * Uživatel může v ⋮ zvolit jednu velikost pro DSD, PSST Data i grafy.
 * Ta platí, dokud v nabídce nesmaže údaje.
 */
internal object PageZoom {

    const val PORTRAIT_PERCENT = 88
    const val LANDSCAPE_PERCENT = 80
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 140
    const val STEP_PERCENT = 2

    private const val PREFS = "page_prefs"
    private const val KEY_SIZE = "page_size_percent"

    fun percent(landscape: Boolean): Int =
        if (landscape) LANDSCAPE_PERCENT else PORTRAIT_PERCENT

    fun percent(context: Context, landscape: Boolean): Int =
        storedPercent(context) ?: percent(landscape)

    fun storedPercent(context: Context): Int? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_SIZE)) return null
        return clamp(prefs.getInt(KEY_SIZE, PORTRAIT_PERCENT))
    }

    fun setPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_SIZE, clamp(percent)).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun applyJs(percent: Int): String {
        val z = "${clamp(percent)}%"
        return """
(function(){
  var z = '$z';
  function apply(){
    try { document.documentElement.style.zoom = z; } catch (e) {}
    try { if (document.body) document.body.style.zoom = z; } catch (e) {}
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
"""
    }

    fun setJs(percent: Int): String {
        val z = "${clamp(percent)}%"
        return "try{document.documentElement.style.zoom='$z';if(document.body)document.body.style.zoom='$z';}catch(e){}"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

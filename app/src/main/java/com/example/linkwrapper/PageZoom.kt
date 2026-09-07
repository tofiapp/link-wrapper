package com.example.linkwrapper

import android.content.Context

/**
 * Oddálení webu ve WebView. Bez vlastního nastavení je na výšku stránka
 * o něco větší (snadnější klepnutí) a na šířku o něco menší.
 *
 * Zoom jen na `html` (`documentElement`). Když je i na `body`, Chromium
 * hodnoty násobí (84 % × 84 % = 70,5 %). Starý zoom na body se maže.
 *
 * DSD a PSST Data mají každé svou velikost (v ⋮). Grafy (`dmId`) mají
 * vždy 84 %. Nastavení platí, dokud uživatel nesmaže údaje.
 */
internal object PageZoom {

    enum class Kind { Psst, Dsd, Chart, Other }

    const val PORTRAIT_PERCENT = 88
    const val LANDSCAPE_PERCENT = 80
    const val CHART_PERCENT = 84
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 140
    const val STEP_PERCENT = 2

    private const val PREFS = "page_prefs"
    private const val KEY_SIZE_LEGACY = "page_size_percent"
    private const val KEY_PSST = "page_size_psst"
    private const val KEY_DSD = "page_size_dsd"

    fun percent(landscape: Boolean): Int =
        if (landscape) LANDSCAPE_PERCENT else PORTRAIT_PERCENT

    fun kindFor(url: String?): Kind {
        if (Destinations.isChart(url)) return Kind.Chart
        return when (Destinations.forUrl(url)?.id) {
            "psst" -> Kind.Psst
            "dsd" -> Kind.Dsd
            else -> Kind.Other
        }
    }

    fun percentFor(context: Context, url: String?, landscape: Boolean): Int =
        percentFor(context, kindFor(url), landscape)

    fun percentFor(context: Context, kind: Kind, landscape: Boolean): Int {
        return when (kind) {
            Kind.Chart -> CHART_PERCENT
            Kind.Psst -> storedPercent(context, Kind.Psst) ?: percent(landscape)
            Kind.Dsd -> storedPercent(context, Kind.Dsd) ?: percent(landscape)
            Kind.Other -> percent(landscape)
        }
    }

    fun storedPercent(context: Context, kind: Kind): Int? {
        migrateLegacy(context)
        val key = keyFor(kind) ?: return null
        val prefs = prefs(context)
        if (!prefs.contains(key)) return null
        return snap(prefs.getInt(key, PORTRAIT_PERCENT))
    }

    fun setPercent(context: Context, kind: Kind, percent: Int) {
        val key = keyFor(kind) ?: return
        migrateLegacy(context)
        prefs(context).edit().putInt(key, snap(percent)).commit()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun clamp(percent: Int): Int = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    fun snap(percent: Int): Int {
        val c = clamp(percent)
        val stepped = MIN_PERCENT + ((c - MIN_PERCENT) / STEP_PERCENT) * STEP_PERCENT
        return clamp(stepped)
    }

    fun applyJs(percent: Int): String {
        val z = "${clamp(percent)}%"
        return """
(function(){
  var z = '$z';
  function apply(){
    try { document.documentElement.style.zoom = z; } catch (e) {}
    try { if (document.body) document.body.style.zoom = ''; } catch (e) {}
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
"""
    }

    /** Vybere zoom podle hostitele a dmId — běží na začátku dokumentu. */
    fun pickerJs(psstPercent: Int, dsdPercent: Int): String {
        val psst = "${clamp(psstPercent)}%"
        val dsd = "${clamp(dsdPercent)}%"
        val chart = "$CHART_PERCENT%"
        return """
(function(){
  var PSST = '$psst';
  var DSD = '$dsd';
  var CHART = '$chart';
  function pick(){
    try {
      var h = (location.hostname || '').toLowerCase();
      var q = location.search || '';
      if (/(?:^|[?&])dmId=/i.test(q)) return CHART;
      if (h === 'dsd.tudc.cz' || h.endsWith('.dsd.tudc.cz')) return DSD;
      return PSST;
    } catch (e) { return PSST; }
  }
  function apply(){
    var z = pick();
    try { document.documentElement.style.zoom = z; } catch (e) {}
    try { if (document.body) document.body.style.zoom = ''; } catch (e) {}
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
"""
    }

    fun setJs(percent: Int): String {
        val z = "${clamp(percent)}%"
        return "try{document.documentElement.style.zoom='$z';if(document.body)document.body.style.zoom='';}catch(e){}"
    }

    private fun keyFor(kind: Kind): String? = when (kind) {
        Kind.Psst -> KEY_PSST
        Kind.Dsd -> KEY_DSD
        else -> null
    }

    /** Starší společná velikost se jednorázově zkopíruje na PSST i DSD. */
    private fun migrateLegacy(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_SIZE_LEGACY)) return
        val shared = snap(prefs.getInt(KEY_SIZE_LEGACY, PORTRAIT_PERCENT))
        val editor = prefs.edit().remove(KEY_SIZE_LEGACY)
        if (!prefs.contains(KEY_PSST)) editor.putInt(KEY_PSST, shared)
        if (!prefs.contains(KEY_DSD)) editor.putInt(KEY_DSD, shared)
        editor.commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

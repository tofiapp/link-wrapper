package com.example.linkwrapper

import android.content.Context

/**
 * Oddálení webu ve WebView. Stránka se skládá jako na PC (viewport 1280)
 * a pak se zmenší, aby se vešla na šířku tabletu. 100 % v ⋮ = přesně
 * na šířku obrazovky. DSD a PSST Data mají každé svou velikost; grafy
 * (`dmId`) mají 84 % této přizpůsobené šířky.
 */
internal object PageZoom {

    enum class Kind { Psst, Dsd, Chart, Other }

    const val PORTRAIT_PERCENT = 100
    const val LANDSCAPE_PERCENT = 100
    const val CHART_PERCENT = 84
    const val MIN_PERCENT = 60
    const val MAX_PERCENT = 140
    const val STEP_PERCENT = 2
    const val VISUAL_MIN_PERCENT = 25
    const val VISUAL_MAX_PERCENT = 200

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

    /**
     * Skutečné CSS zoom: stránka se skládá na [DesktopSite.VIEWPORT_WIDTH]
     * a zmenší se na šířku WebView. [userPercent] 100 = přesně na šířku.
     */
    fun visualPercent(cssWidth: Float, userPercent: Int): Int {
        if (cssWidth <= 1f) return clampVisual(userPercent)
        val fit = cssWidth / DesktopSite.VIEWPORT_WIDTH * 100f
        val visual = fit * clamp(userPercent) / 100f
        return clampVisual(kotlin.math.round(visual).toInt())
    }

    fun clampVisual(percent: Int): Int =
        percent.coerceIn(VISUAL_MIN_PERCENT, VISUAL_MAX_PERCENT)

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
        val z = "${clampVisual(percent)}%"
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

    /** Vybere zoom podle hostitele a dmId — běží na začátku dokumentu. */
    fun pickerJs(psstPercent: Int, dsdPercent: Int, chartPercent: Int = CHART_PERCENT): String {
        val psst = "${clampVisual(psstPercent)}%"
        val dsd = "${clampVisual(dsdPercent)}%"
        val chart = "${clampVisual(chartPercent)}%"
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
    try { if (document.body) document.body.style.zoom = z; } catch (e) {}
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
"""
    }

    fun setJs(percent: Int): String {
        val z = "${clampVisual(percent)}%"
        return "try{document.documentElement.style.zoom='$z';if(document.body)document.body.style.zoom='$z';}catch(e){}"
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

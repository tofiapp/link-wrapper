package com.example.linkwrapper

/**
 * Výkon grafu ve WebView. Stránka kreslí Highcharts sama; obálka jí jen
 * nastaví prostředí blíž desktopovému Chrome.
 *
 * Na tabletu je [devicePixelRatio] často 2–3. Highcharts podle něj násobí
 * canvas/SVG. Při posunu se pak překresluje 4–9× víc pixelů než na PC
 * (DPR ~1). [BOOTSTRAP_JS] stropne DPR **než** se Highcharts spustí.
 * `chart.update` / `redraw` tady není — to by graf při posunu znovu složilo.
 */
internal object ChartPerf {

    /** Strop DPR. 1.25 je kompromis ostrost / plynulost na tabletu. */
    private const val DPR_CAP = "1.25"

    /**
     * Běží na začátku dokumentu (WebViewCompat.addDocumentStartJavaScript),
     * dřív než jakýkoliv skript stránky.
     */
    const val BOOTSTRAP_JS = """
(function(){
  var CAP = $DPR_CAP;
  try {
    var orig = window.devicePixelRatio;
    if (typeof orig === 'number' && orig > CAP) {
      Object.defineProperty(window, 'devicePixelRatio', {
        configurable: true,
        enumerable: true,
        get: function() { return CAP; }
      });
    }
  } catch (e) {}

  function css() {
    if (document.getElementById('psst-chart-perf-css')) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var s = document.createElement('style');
    s.id = 'psst-chart-perf-css';
    s.textContent = 'canvas,.highcharts-container,.highcharts-scrolling,.highcharts-root{touch-action:none;-webkit-tap-highlight-color:transparent;contain:layout style paint;}';
    root.appendChild(s);
  }
  css();
  document.addEventListener('DOMContentLoaded', css);

  function applyH(H) {
    if (!H || H.__psstOpts || !H.setOptions) return H;
    H.__psstOpts = 1;
    try {
      H.setOptions({
        chart: { animation: false },
        tooltip: { followTouchMove: false, animation: false },
        plotOptions: {
          series: {
            animation: false,
            stickyTracking: false,
            states: { hover: { enabled: false, halo: false } }
          }
        }
      });
    } catch (e) {}
    return H;
  }

  try {
    var current = window.Highcharts;
    Object.defineProperty(window, 'Highcharts', {
      configurable: true,
      enumerable: true,
      get: function() { return current; },
      set: function(v) { current = applyH(v); }
    });
    if (current) applyH(current);
  } catch (e) {
    try { applyH(window.Highcharts); } catch (e2) {}
  }
})();
"""
}

package com.example.linkwrapper

/**
 * Výkon grafu ve WebView. Stránka kreslí Highcharts sama; obálka jí jen
 * nastaví prostředí blíž desktopovému Chrome.
 *
 * Na tabletu je [devicePixelRatio] často 2–3. Highcharts podle něj násobí
 * canvas/SVG. Při posunu se pak překresluje 4–9× víc pixelů než na PC
 * (DPR ~1). [BOOTSTRAP_JS] stropne DPR **než** se Highcharts spustí.
 *
 * Graf se po načtení přepočítá na šířku a výšku WebView (`setSize`).
 * CSS výšku kontejneru nenafukujeme — to by pod grafem nechalo prázdno.
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
    s.textContent = [
      'canvas,.highcharts-container,.highcharts-root{-webkit-tap-highlight-color:transparent;}',
      '.highcharts-scrolling{overflow:auto!important;touch-action:pan-x pan-y!important;}'
    ].join('');
    root.appendChild(s);
  }
  css();
  document.addEventListener('DOMContentLoaded', css);

  function viewSize() {
    var de = document.documentElement;
    var w = (de && de.clientWidth) || window.innerWidth || 0;
    var h = (de && de.clientHeight) || window.innerHeight || 0;
    return { w: w, h: h };
  }

  function widen(el, w) {
    var n = el;
    var hops = 0;
    while (n && hops < 12) {
      try {
        n.style.setProperty('max-width', 'none', 'important');
        n.style.setProperty('width', w + 'px', 'important');
        n.style.setProperty('margin-left', '0', 'important');
        n.style.setProperty('margin-right', '0', 'important');
        n.style.setProperty('box-sizing', 'border-box', 'important');
      } catch (e) {}
      if (n === document.body || n === document.documentElement) break;
      n = n.parentElement;
      hops++;
    }
  }

  function targetHeight(c, vh) {
    var axes = (c.yAxis && c.yAxis.length) ? c.yAxis.length : 1;
    var series = (c.series && c.series.length) ? c.series.length : 1;
    var panes = axes > 1 ? axes : (series > 6 ? series : 1);
    var h = Math.max(vh, panes * 150);
    if (h > 8000) h = 8000;
    return Math.round(h);
  }

  function fitChart(c) {
    if (!c || !c.setSize || c.__obalkaFit) return;
    var vs = viewSize();
    var w = Math.round(vs.w);
    var h = targetHeight(c, vs.h);
    if (w < 200 || h < 200) return;
    var sameW = Math.abs((c.chartWidth || 0) - w) < 4;
    var sameH = Math.abs((c.chartHeight || 0) - h) < 4;
    if (sameW && sameH) return;
    c.__obalkaFit = 1;
    try {
      var host = c.renderTo || c.container;
      if (host) widen(host, w);
      c.setSize(w, h, false);
    } catch (e) {}
    c.__obalkaFit = 0;
  }

  function fitAll() {
    css();
    try {
      var H = window.Highcharts;
      if (!H || !H.charts) return;
      for (var i = 0; i < H.charts.length; i++) fitChart(H.charts[i]);
    } catch (e) {}
  }

  function applyH(H) {
    if (!H || !H.setOptions) return H;
    if (!H.__psstOpts) {
      H.__psstOpts = 1;
      try {
        H.setOptions({
          chart: {
            animation: false,
            panning: true
          },
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
    }
    if (!H.__obalkaHook && H.addEvent && H.Chart) {
      H.__obalkaHook = 1;
      try {
        H.addEvent(H.Chart, 'load', function() {
          var c = this;
          setTimeout(function(){ fitChart(c); }, 0);
          setTimeout(function(){ fitChart(c); }, 500);
        });
      } catch (e) {}
    }
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

  document.addEventListener('DOMContentLoaded', fitAll);
  window.addEventListener('load', fitAll);
  window.addEventListener('resize', fitAll);
  setTimeout(fitAll, 900);
  setTimeout(fitAll, 2200);
})();
"""
}

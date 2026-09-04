package com.example.linkwrapper

/**
 * Výkon grafu ve WebView. Stránka kreslí Highcharts sama; obálka jí jen
 * nastaví prostředí blíž desktopovému Chrome.
 *
 * Na tabletu je [devicePixelRatio] často 2–3. Highcharts podle něj násobí
 * canvas/SVG. Při posunu se pak překresluje 4–9× víc pixelů než na PC
 * (DPR ~1). [BOOTSTRAP_JS] stropne DPR **než** se Highcharts spustí.
 * `chart.update` / `redraw` tady není — to by graf při posunu znovu složilo.
 * `touch-action: none` / `contain: paint` na `.highcharts-scrolling` taky ne —
 * ořízly by zbytek grafu a zablokovaly posun prstem.
 *
 * Po vykreslení se graf natáhne na šířku kontejneru a oříznutý
 * `.highcharts-scrolling` se zvětší na obsah, ať jde zbytek dojet na stránce.
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
      '.highcharts-container{width:100%!important;max-width:100%!important;overflow:visible!important;max-height:none!important;}',
      '.highcharts-scrolling{overflow:auto!important;max-height:none!important;touch-action:pan-x pan-y!important;}'
    ].join('');
    root.appendChild(s);
  }
  css();
  document.addEventListener('DOMContentLoaded', css);

  function unclipAncestors(el) {
    var n = el;
    var hops = 0;
    while (n && n !== document.documentElement && hops < 14) {
      try {
        n.style.maxHeight = 'none';
        var ov = n.style.overflow || '';
        if (!ov || ov === 'hidden') n.style.overflow = 'visible';
        if (n.style.overflowY === 'hidden') n.style.overflowY = 'visible';
      } catch (e) {}
      n = n.parentElement;
      hops++;
    }
  }

  function unlockScrolling() {
    try {
      var scs = document.querySelectorAll('.highcharts-scrolling');
      for (var i = 0; i < scs.length; i++) {
        var sc = scs[i];
        sc.style.touchAction = 'pan-x pan-y';
        sc.style.maxHeight = 'none';
        var needed = sc.scrollHeight;
        var inner = sc.firstElementChild;
        if (inner) {
          var ih = Math.max(inner.scrollHeight || 0, inner.offsetHeight || 0);
          if (ih > needed) needed = ih;
        }
        if (needed > sc.clientHeight + 8) {
          sc.style.height = needed + 'px';
          sc.style.overflow = 'visible';
          var host = sc.parentElement;
          if (host) {
            host.style.height = 'auto';
            host.style.overflow = 'visible';
          }
        } else {
          sc.style.overflow = 'auto';
        }
        unclipAncestors(sc);
      }
    } catch (e) {}
  }

  function sizeChart(c) {
    if (!c || !c.container) return;
    try {
      unclipAncestors(c.container);
      var parent = c.renderTo || c.container.parentElement;
      var w = parent && parent.clientWidth ? parent.clientWidth : 0;
      if (w > 0 && c.setSize && Math.abs((c.chartWidth || 0) - w) > 8) {
        c.setSize(w, undefined, false);
      }
    } catch (e) {}
    unlockScrolling();
  }

  function sizeAll() {
    css();
    unlockScrolling();
    try {
      var H = window.Highcharts;
      if (!H || !H.charts) return;
      for (var i = 0; i < H.charts.length; i++) sizeChart(H.charts[i]);
    } catch (e) {}
  }

  function applyH(H) {
    if (!H || !H.setOptions) return H;
    if (!H.__psstOpts) {
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
    }
    if (!H.__obalkaHook && H.addEvent && H.Chart) {
      H.__obalkaHook = 1;
      try {
        H.addEvent(H.Chart, 'load', function() {
          var c = this;
          setTimeout(function(){ sizeChart(c); }, 0);
          setTimeout(function(){ sizeChart(c); }, 400);
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

  document.addEventListener('DOMContentLoaded', sizeAll);
  window.addEventListener('load', sizeAll);
  window.addEventListener('resize', sizeAll);
  setTimeout(sizeAll, 800);
  setTimeout(sizeAll, 2500);
})();
"""
}

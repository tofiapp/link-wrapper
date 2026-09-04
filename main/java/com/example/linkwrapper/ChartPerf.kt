package com.example.linkwrapper

/**
 * Výkon a rozměry grafu ve WebView.
 *
 * Dvě opravy oproti původní verzi:
 *
 * 1. **Detekce grafu.** Dřív se poznal jen podle parametru v query. Když se
 *    graf otevřel proklikem uvnitř appky (URL bez toho parametru) nebo přes
 *    hash, kód se nespustil a graf zůstal ve své původní — často poloviční —
 *    velikosti. Nově stačí, že na stránce existuje instance Highcharts.
 *
 * 2. **Výška grafu.** Dřív vždy čtyři obrazovky na výšku. Odtud „graf přes
 *    celou stránku, ale nevejde se". Nově se graf natáhne na šířku svého
 *    kontejneru a na výšku, která zbývá ve viewportu pod horní hranou grafu —
 *    takže se vejde celý. Víc panelů (os nebo sérií) výšku úměrně zvětší, ale
 *    se stropem, ne naslepo.
 */
internal object ChartPerf {

    private const val DPR_CAP = "1.25"

    /** Minimální rozumná výška jednoho grafu v CSS px. */
    private const val MIN_H_PX = "320"

    /** Kolik px výšky přidat za každý panel navíc (víc os / sérií). */
    private const val PANE_H_PX = "180"

    /** Strop, aby se graf nikdy nerozrostl do nekonečna. */
    private const val MAX_H_PX = "4000"

    const val APPLY_JS =
        "try{if(window.__obalkaFitCharts)window.__obalkaFitCharts();}catch(e){}"

    const val BOOTSTRAP_JS = """
(function(){
  if (window.__obalkaChartBoot) return;
  window.__obalkaChartBoot = 1;
  var CAP = $DPR_CAP;
  var MIN_H = $MIN_H_PX;
  var PANE_H = $PANE_H_PX;
  var MAX_H = $MAX_H_PX;

  try {
    var orig = window.devicePixelRatio;
    if (typeof orig === 'number' && orig > CAP) {
      Object.defineProperty(window, 'devicePixelRatio', {
        configurable: true, enumerable: true,
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
      '.highcharts-scrolling{overflow:auto!important;touch-action:pan-x pan-y!important;}',
      '.highcharts-container{height:auto!important;}'
    ].join('');
    root.appendChild(s);
  }
  css();

  function viewH(){
    return Math.round((document.documentElement && document.documentElement.clientHeight)
      || window.innerHeight || 0);
  }

  /** Šířka, kterou má graf reálně k dispozici — z jeho vlastního kontejneru. */
  function hostWidth(c){
    try {
      var host = c.renderTo || c.container;
      if (!host) return 0;
      var parent = host.parentElement || host;
      var w = parent.clientWidth || host.clientWidth || 0;
      if (w < 200) {
        w = (document.documentElement && document.documentElement.clientWidth)
          || window.innerWidth || 0;
      }
      return Math.round(w);
    } catch (e) { return 0; }
  }

  /**
   * Výška = co zbývá ve viewportu od horní hrany grafu dolů, plus přídavek za
   * panely navíc. Graf se tak vejde na obrazovku a přitom není zmáčknutý.
   */
  function fitHeight(c){
    var vh = viewH();
    if (vh < 200) return MIN_H;
    var top = 0;
    try {
      var host = c.renderTo || c.container;
      if (host && host.getBoundingClientRect) {
        top = Math.max(0, host.getBoundingClientRect().top + (window.scrollY || 0));
      }
    } catch (e) {}
    var axes = (c.yAxis && c.yAxis.length) ? c.yAxis.length : 1;
    var series = (c.series && c.series.length) ? c.series.length : 1;
    var panes = Math.max(axes, series, 1);
    var base = vh - top - 24;
    if (base < MIN_H) base = MIN_H;
    var h = base + Math.max(0, panes - 1) * PANE_H;
    if (h > MAX_H) h = MAX_H;
    return Math.round(h);
  }

  function fit(c){
    if (!c || !c.setSize) return;
    var w = hostWidth(c);
    if (w < 200) return;
    var h = fitHeight(c);
    if (Math.abs((c.chartWidth || 0) - w) < 4 && Math.abs((c.chartHeight || 0) - h) < 4) return;
    c.__obalkaH = h;
    try {
      var host = c.renderTo || c.container;
      if (host && host.style) {
        host.style.setProperty('width', '100%', 'important');
        host.style.setProperty('max-width', 'none', 'important');
        host.style.setProperty('height', h + 'px', 'important');
        host.style.setProperty('max-height', 'none', 'important');
      }
      c.setSize(w, h, false);
    } catch (e) {}
  }

  window.__obalkaFitCharts = function(){
    css();
    try {
      var H = window.Highcharts;
      if (H && H.charts) {
        for (var i = 0; i < H.charts.length; i++) {
          if (H.charts[i]) fit(H.charts[i]);
        }
      }
    } catch (e) {}
  };

  function wrapProto(P){
    if (!P || P.__obalkaInit) return;
    P.__obalkaInit = 1;
    if (typeof P.init === 'function') {
      var origInit = P.init;
      P.init = function(){
        var r = origInit.apply(this, arguments);
        var self = this;
        setTimeout(function(){ fit(self); }, 0);
        setTimeout(function(){ fit(self); }, 400);
        setTimeout(function(){ fit(self); }, 1600);
        return r;
      };
    }
    if (typeof P.setSize === 'function') {
      var origSize = P.setSize;
      P.setSize = function(w, h, a){
        if (this.__obalkaSizing) return origSize.call(this, w, h, a);
        // Neblokujeme vlastní resize stránky, jen doplníme chybějící rozměr.
        if (this.__obalkaH) {
          if (!w) w = hostWidth(this) || this.chartWidth;
          if (!h) h = this.__obalkaH;
        }
        this.__obalkaSizing = 1;
        try { return origSize.call(this, w, h, a); }
        finally { this.__obalkaSizing = 0; }
      };
    }
  }

  function applyH(H){
    if (!H || !H.setOptions) return H;
    if (!H.__psstOpts) {
      H.__psstOpts = 1;
      try {
        H.setOptions({
          chart: { animation: false, panning: true },
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
    try {
      wrapProto(H.Chart && H.Chart.prototype);
      wrapProto(H.StockChart && H.StockChart.prototype);
    } catch (e) {}
    return H;
  }

  try {
    var current = window.Highcharts;
    Object.defineProperty(window, 'Highcharts', {
      configurable: true, enumerable: true,
      get: function() { return current; },
      set: function(v) { current = applyH(v); }
    });
    if (current) applyH(current);
  } catch (e) {
    try { applyH(window.Highcharts); } catch (e2) {}
  }

  // Otočení tabletu / změna velikosti okna → přeměřit.
  try {
    window.addEventListener('resize', function(){
      clearTimeout(window.__obalkaRT);
      window.__obalkaRT = setTimeout(window.__obalkaFitCharts, 150);
    });
    window.addEventListener('orientationchange', function(){
      setTimeout(window.__obalkaFitCharts, 300);
    });
  } catch (e) {}

  var n = 0;
  var t = setInterval(function(){
    window.__obalkaFitCharts();
    n++;
    if (n > 24) clearInterval(t);
  }, 400);
})();
"""
}

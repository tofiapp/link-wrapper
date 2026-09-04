package com.example.linkwrapper

/**
 * Výkon grafu ve WebView. Stránka kreslí Highcharts sama; obálka jí jen
 * nastaví prostředí blíž desktopovému Chrome.
 *
 * Na tabletu je [devicePixelRatio] často 2–3. Highcharts podle něj násobí
 * canvas/SVG. [BOOTSTRAP_JS] stropne DPR **než** se Highcharts spustí.
 *
 * Na stránce s `dmId` se graf po vytvoření natáhne na šířku WebView a na
 * výšku aspoň 4 obrazovky (skutečné `setSize`, ne prázdné CSS). Jiné
 * stránky Highcharts nemění — tam by to rozbilo malé grafy.
 */
internal object ChartPerf {

    private const val DPR_CAP = "1.25"

    const val APPLY_JS =
        "try{if(window.__obalkaFitCharts)window.__obalkaFitCharts();}catch(e){}"

    const val BOOTSTRAP_JS = """
(function(){
  if (window.__obalkaChartBoot) return;
  window.__obalkaChartBoot = 1;
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
    s.textContent = 'canvas,.highcharts-container,.highcharts-root{-webkit-tap-highlight-color:transparent;}.highcharts-scrolling{overflow:auto!important;touch-action:pan-x pan-y!important;}';
    root.appendChild(s);
  }
  css();

  function isChartPage(){
    try { return /(?:^|[?&])dmId=/i.test(location.search || ''); }
    catch (e) { return false; }
  }

  function viewW(){
    return Math.round((document.documentElement && document.documentElement.clientWidth) || window.innerWidth || 0);
  }
  function viewH(){
    return Math.round((document.documentElement && document.documentElement.clientHeight) || window.innerHeight || 0);
  }

  function explode(c){
    if (!isChartPage() || !c || !c.setSize) return;
    var w = viewW();
    var vh = viewH();
    if (w < 200 || vh < 200) return;
    var axes = (c.yAxis && c.yAxis.length) ? c.yAxis.length : 1;
    var series = (c.series && c.series.length) ? c.series.length : 1;
    var panes = Math.max(axes, series, 1);
    var h = Math.max(vh * 4, panes * 200, 1600);
    if (h > 20000) h = 20000;
    h = Math.round(h);
    if (Math.abs((c.chartWidth || 0) - w) < 4 && Math.abs((c.chartHeight || 0) - h) < 4) return;
    c.__obalkaH = h;
    try {
      var host = c.renderTo || c.container;
      if (host && host.style) {
        host.style.setProperty('width', w + 'px', 'important');
        host.style.setProperty('max-width', 'none', 'important');
        host.style.setProperty('height', h + 'px', 'important');
        host.style.setProperty('max-height', 'none', 'important');
      }
      c.setSize(w, h, false);
    } catch (e) {}
  }

  window.__obalkaFitCharts = function(){
    css();
    if (!isChartPage()) return;
    try {
      var H = window.Highcharts;
      if (H && H.charts) {
        for (var i = 0; i < H.charts.length; i++) explode(H.charts[i]);
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
        setTimeout(function(){ explode(self); }, 0);
        setTimeout(function(){ explode(self); }, 400);
        setTimeout(function(){ explode(self); }, 1600);
        return r;
      };
    }
    if (typeof P.setSize === 'function') {
      var origSize = P.setSize;
      P.setSize = function(w, h, a){
        if (this.__obalkaSizing) return origSize.call(this, w, h, a);
        if (isChartPage() && this.__obalkaH) {
          var wantW = viewW();
          var wantH = this.__obalkaH;
          if (!w || w < wantW - 8) w = wantW;
          if (!h || h < wantH - 8) h = wantH;
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
      configurable: true,
      enumerable: true,
      get: function() { return current; },
      set: function(v) { current = applyH(v); }
    });
    if (current) applyH(current);
  } catch (e) {
    try { applyH(window.Highcharts); } catch (e2) {}
  }

  var n = 0;
  var t = setInterval(function(){
    window.__obalkaFitCharts();
    n++;
    if (n > 24) clearInterval(t);
  }, 400);
})();
"""
}

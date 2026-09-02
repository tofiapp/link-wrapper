package com.example.linkwrapper

/**
 * Úpravy stránky, které obálka může udělat bez změny HSI.Psst.Data.
 *
 * Posun grafu cuká, když prohlížeč současně skroluje stránku a Highcharts
 * při každém prstu překresluje tooltip / hover. [JS] to vypne; CSS
 * `touch-action: none` na canvasu přenechá gesto jen grafu.
 */
internal object ChartPerf {

    const val JS = """
(function(){
  try {
    var cssId = 'psst-chart-perf-css';
    if (!document.getElementById(cssId)) {
      var s = document.createElement('style');
      s.id = cssId;
      s.textContent = 'canvas,.highcharts-container,.highcharts-scrolling,.highcharts-root{touch-action:none;-webkit-tap-highlight-color:transparent;}';
      (document.head || document.documentElement).appendChild(s);
    }
    var H = window.Highcharts;
    if (!H || !H.setOptions) return;
    if (!H.__psstOpts) {
      H.__psstOpts = 1;
      H.setOptions({
        chart: { animation: false },
        tooltip: { followTouchMove: false, animation: false },
        plotOptions: {
          series: {
            animation: false,
            states: { hover: { halo: false } }
          }
        }
      });
    }
    var charts = H.charts || [];
    for (var i = 0; i < charts.length; i++) {
      var c = charts[i];
      if (!c || c.__psstPerf) continue;
      c.__psstPerf = 1;
      try {
        c.update({
          chart: { animation: false },
          tooltip: { followTouchMove: false, animation: false }
        }, false);
        if (c.redraw) c.redraw(false);
      } catch (e) {}
    }
  } catch (e) {}
})();
"""
}

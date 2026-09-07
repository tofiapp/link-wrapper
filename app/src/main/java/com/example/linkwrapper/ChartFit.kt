package com.example.linkwrapper

/**
 * Výška SVG grafu ve WebView. Šířka `.chart-part` se na tabletu už
 * přizpůsobí; výška zůstává na původní hodnotě ze stránky, takže se
 * scrolluje i když je v rodiči místo.
 *
 * Graf se na stránce kreslí až po `onPageFinished`. [FIT_JS] proto
 * nejdřív zkusí `.chart-part` hned a jinak čeká přes MutationObserver.
 * Úprava platí jen pro tyto kontejnery — šířka a zbytek layoutu se
 * nemění.
 */
internal object ChartFit {

    const val FIT_JS = """
(function(){
  try {
    if (window.__psstChartFitObs) {
      try { window.__psstChartFitObs.disconnect(); } catch (e) {}
      window.__psstChartFitObs = null;
    }
    if (window.__psstChartFitTimer) {
      try { clearTimeout(window.__psstChartFitTimer); } catch (e) {}
      window.__psstChartFitTimer = null;
    }

    function fixCharts() {
      var charts = document.querySelectorAll('.chart-part');
      if (!charts.length) return false;
      var applied = 0;
      charts.forEach(function(el) {
        var parent = el.parentElement;
        if (!parent) return;
        var target = parent.clientHeight;
        if (target <= 0) return;
        el.style.setProperty('height', target + 'px', 'important');
        var orig = parent.style.height;
        parent.style.height = (parent.clientHeight - 1) + 'px';
        requestAnimationFrame(function() {
          parent.style.height = orig || '';
          requestAnimationFrame(function() {
            window.dispatchEvent(new Event('resize'));
          });
        });
        applied += 1;
      });
      return applied > 0;
    }

    if (fixCharts()) return;

    var root = document.body || document.documentElement;
    if (!root) return;

    var obs = new MutationObserver(function() {
      if (fixCharts()) {
        obs.disconnect();
        window.__psstChartFitObs = null;
        if (window.__psstChartFitTimer) {
          clearTimeout(window.__psstChartFitTimer);
          window.__psstChartFitTimer = null;
        }
      }
    });
    window.__psstChartFitObs = obs;
    obs.observe(root, { childList: true, subtree: true });
    window.__psstChartFitTimer = setTimeout(function() {
      try { obs.disconnect(); } catch (e) {}
      window.__psstChartFitObs = null;
      window.__psstChartFitTimer = null;
    }, 15000);
  } catch (e) {}
})();
"""
}

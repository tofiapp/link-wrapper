package com.example.linkwrapper

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Výška kontejneru `.chart-part`. SVG uvnitř je delší než obrazovka
 * a scrolluje se přes `--scrollTop` — do SVG, viewBoxu ani CSS
 * proměnných grafu se nesahá. Šířka se nemění.
 *
 * [FIT_JS] měří scale z getBoundingClientRect / offsetHeight a nastaví
 * jen výšku kontejneru na dostupný viewport.
 */
internal object ChartFit {

    const val FIT_JS = """
(function(){
  function show(msg) {
    try {
      if (window.AndroidDebugBridge && window.AndroidDebugBridge.showResult) {
        window.AndroidDebugBridge.showResult(String(msg));
      }
    } catch (e) {}
  }

  function svgBound(el) {
    var svg = el.querySelector('svg');
    return svg ? svg.getBoundingClientRect().height : 'n/a';
  }

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
      var reports = [];
      var tracked = [];
      charts.forEach(function(el, index) {
        var rect = el.getBoundingClientRect();
        var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
        if (!scale || scale <= 0) scale = 1;
        var viewportHeight = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
        var availableRendered = viewportHeight - rect.top - 8;
        var targetCss = availableRendered / scale;
        if (targetCss > 0) {
          el.style.setProperty('height', targetCss + 'px', 'important');
        }
        reports.push('chart ' + index + '\nscale: ' + scale + '\ntargetCss: ' + targetCss);
        tracked.push(el);
      });
      window.dispatchEvent(new Event('resize'));
      show(reports.join('\n\n'));
      setTimeout(function() {
        var later = [];
        tracked.forEach(function(el, index) {
          later.push(
            'chart ' + index + '\n' +
            'el.getBoundingClientRect().height po 1000ms: ' + el.getBoundingClientRect().height + '\n' +
            'svgBoundingHeight po 1000ms: ' + svgBound(el)
          );
        });
        show('PO 1000ms\n\n' + later.join('\n\n'));
      }, 1000);
      return true;
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
  } catch (e) {
    show('error: ' + e);
  }
})();
"""
}

/** TODO: remove after debugging */
class DebugBridge(private val context: Context) {
    @JavascriptInterface
    fun showResult(result: String) {
        val activity = context as? Activity ?: return
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            Log.d("ChartFit", result)
            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle("Chart debug")
                .setMessage(result)
                .setPositiveButton("OK", null)
                .show()
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setTextIsSelectable(true)
            }
        }
    }
}

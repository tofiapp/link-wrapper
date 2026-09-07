package com.example.linkwrapper

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diagnostika `.chart-part` bez zásahu do výšky.
 * Úprava `height` / `min-height` / `max-height` je zakomentovaná —
 * běží jen výpis po načtení grafu (s opravou zoomu jen na html).
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

  function dump(el, index) {
    var rect = el.getBoundingClientRect();
    var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
    if (!scale || scale <= 0) scale = 1;
    var svg = el.querySelector('svg');
    var sr = svg ? svg.getBoundingClientRect() : null;
    var cs = svg ? getComputedStyle(svg) : null;
    var vv = window.visualViewport ? window.visualViewport.height : 'n/a';
    return [
      'chart ' + index,
      'scale: ' + scale,
      'el.getBoundingClientRect().height: ' + rect.height,
      'el.getBoundingClientRect().width: ' + rect.width,
      'svg.getBoundingClientRect().height: ' + (sr ? sr.height : 'n/a'),
      'svg.getBoundingClientRect().width: ' + (sr ? sr.width : 'n/a'),
      'window.innerWidth: ' + window.innerWidth,
      'visualViewport.height: ' + vv,
      '--scrollTop: ' + (cs ? JSON.stringify(cs.getPropertyValue('--scrollTop')) : 'n/a'),
      '--scrollLeft: ' + (cs ? JSON.stringify(cs.getPropertyValue('--scrollLeft')) : 'n/a'),
      '--pxPerMeter: ' + (cs ? JSON.stringify(cs.getPropertyValue('--pxPerMeter')) : 'n/a')
    ].join('\n');
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
      charts.forEach(function(el, index) {
        var rect = el.getBoundingClientRect();
        var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
        if (!scale || scale <= 0) scale = 1;
        var viewportHeight = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
        var availableRendered = viewportHeight - rect.top - 8;
        var targetCss = availableRendered / scale;
        /*
        HEIGHT INJECTION OFF — baseline bez zásahu do výšky
        if (targetCss > 0) {
          el.style.setProperty('height', targetCss + 'px', 'important');
        }
        el.style.removeProperty('height');
        el.style.setProperty('min-height', targetCss + 'px', 'important');
        el.style.setProperty('max-height', targetCss + 'px', 'important');
        */
      });
      setTimeout(function() {
        var later = [];
        charts.forEach(function(el, index) {
          later.push(dump(el, index));
        });
        show(later.join('\n\n'));
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

package com.example.linkwrapper

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Výška SVG grafu ve WebView. Šířka `.chart-part` se na tabletu už
 * přizpůsobí.
 *
 * CSS zoom je na html i body (viz [PageZoom]) — měřítko se násobí.
 * [FIT_JS] proto bere scale z getBoundingClientRect / offsetHeight
 * a výšku počítá ve vykreslených pixelech od horní hrany elementu
 * po spodek viewportu, pak ji převede zpět na CSS px.
 *
 * Graf se kreslí až po onPageFinished, proto čeká MutationObserver.
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

  function nudge(parent) {
    if (!parent) return;
    var orig = parent.style.height;
    parent.style.height = (parent.clientHeight - 1) + 'px';
    requestAnimationFrame(function() {
      parent.style.height = orig || '';
      requestAnimationFrame(function() {
        window.dispatchEvent(new Event('resize'));
      });
    });
  }

  function fitOne(el, index) {
    var rect = el.getBoundingClientRect();
    var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
    if (!scale || scale <= 0) scale = 1;
    var viewportHeight = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
    var availableRendered = viewportHeight - rect.top - 8;
    var targetCss = availableRendered / scale;
    var lines = [
      'chart ' + index,
      'scale: ' + scale,
      'rect.top: ' + rect.top,
      'viewportHeight: ' + viewportHeight,
      'availableRendered: ' + availableRendered,
      'targetCss: ' + targetCss
    ];
    if (targetCss > 0) {
      el.style.setProperty('height', targetCss + 'px', 'important');
      nudge(el.parentElement);
    }
    return lines.join('\n');
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
        reports.push(fitOne(el, index));
        tracked.push(el);
      });
      show('PŘED / právě nastaveno\n\n' + reports.join('\n\n'));
      setTimeout(function() {
        var later = [];
        tracked.forEach(function(el, index) {
          later.push(
            'chart ' + index + '\n' +
            'el.style.height po 1000ms: ' + JSON.stringify(el.style.height) + '\n' +
            'el.getBoundingClientRect().height po 1000ms: ' + el.getBoundingClientRect().height
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

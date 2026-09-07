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
 * přizpůsobí; výška zůstává na původní hodnotě ze stránky.
 *
 * Graf se kreslí až po onPageFinished, proto [FIT_JS] čeká přes
 * MutationObserver. Dočasně nejdřív ukáže rozměry v dialogu, pak zkusí
 * výšku z rodiče / předků / visualViewport.
 *
 * Měřítko WebView (viz createWebView + [PageZoom]):
 * - setInitialScale: nevolá se
 * - useWideViewPort = true
 * - loadWithOverviewMode = true
 * - textZoom = 100
 * - setSupportZoom = false, builtInZoomControls = false
 * - CSS zoom za běhu: html/body.style.zoom (grafy 84 %, jinak 88/80 %)
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

  function usable(v, current) {
    return typeof v === 'number' && isFinite(v) && v > 0 && v > current;
  }

  function pickHeight(el) {
    var current = el.offsetHeight || 0;
    var parent = el.parentElement;
    if (parent && usable(parent.clientHeight, current)) {
      return { value: parent.clientHeight, source: 'parent.clientHeight' };
    }
    var node = parent;
    var i = 0;
    while (node && i < 6) {
      if (usable(node.clientHeight, current)) {
        return { value: node.clientHeight, source: 'ancestor[' + i + '] ' + node.tagName + ' clientHeight' };
      }
      node = node.parentElement;
      i += 1;
    }
    var vv = (window.visualViewport ? window.visualViewport.height : 0);
    if (usable(vv, current)) {
      return { value: vv, source: 'visualViewport.height' };
    }
    if (usable(document.documentElement.clientHeight, current)) {
      return { value: document.documentElement.clientHeight, source: 'documentElement.clientHeight' };
    }
    return null;
  }

  function collect(el, index) {
    var parent = el.parentElement;
    var lines = ['chart ' + index];
    lines.push('el.style.height: ' + JSON.stringify(el.style.height));
    lines.push('el.offsetHeight: ' + el.offsetHeight);
    lines.push('el.clientHeight: ' + el.clientHeight);
    lines.push('el.getBoundingClientRect().height: ' + el.getBoundingClientRect().height);
    if (!parent) {
      lines.push('parent: none');
      return lines.join('\n');
    }
    lines.push('parentTag: ' + parent.tagName + '.' + (parent.className || '-'));
    lines.push('parent.clientHeight: ' + parent.clientHeight);
    lines.push('parent.offsetHeight: ' + parent.offsetHeight);
    lines.push('parent.getBoundingClientRect().height: ' + parent.getBoundingClientRect().height);
    lines.push('parent computed height: ' + getComputedStyle(parent).height);
    lines.push('parent computed overflow: ' + getComputedStyle(parent).overflow);
    lines.push('window.innerHeight: ' + window.innerHeight);
    lines.push('document.documentElement.clientHeight: ' + document.documentElement.clientHeight);
    lines.push('devicePixelRatio: ' + window.devicePixelRatio);
    lines.push('visualViewport.height: ' + (window.visualViewport ? window.visualViewport.height : 'n/a'));
    lines.push('visualViewport.scale: ' + (window.visualViewport ? window.visualViewport.scale : 'n/a'));
    lines.push('html.style.zoom: ' + JSON.stringify(document.documentElement.style.zoom));
    lines.push('body.style.zoom: ' + JSON.stringify(document.body ? document.body.style.zoom : ''));
    var node = parent;
    var i = 0;
    while (node && i < 6) {
      lines.push('ancestor[' + i + ']: ' + node.tagName + '.' + (node.className || '-') + ' = ' + node.clientHeight + 'px (computed: ' + getComputedStyle(node).height + ')');
      node = node.parentElement;
      i += 1;
    }
    return lines.join('\n');
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
        reports.push(collect(el, index));
        var picked = pickHeight(el);
        if (picked) {
          reports.push('chosen: ' + picked.source + ' = ' + picked.value);
          el.style.setProperty('height', picked.value + 'px', 'important');
          nudge(el.parentElement);
        } else {
          reports.push('chosen: none (no candidate > current height)');
        }
        tracked.push(el);
      });
      show('PŘED / právě nastaveno\n\n' + reports.join('\n\n'));
      setTimeout(function() {
        var later = [];
        tracked.forEach(function(el, index) {
          later.push(
            'chart ' + index + '\n' +
            'el.style.height po 1000ms: ' + JSON.stringify(el.style.height) + '\n' +
            'el.offsetHeight po 1000ms: ' + el.offsetHeight
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

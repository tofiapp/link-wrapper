package com.example.linkwrapper

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Výška kontejneru `.chart-part`. Do SVG se nesahá. Žádný `resize`
 * event — ten spouštěl přepočet, který SVG srazil na výšku kontejneru.
 *
 * Nejdřív se nastaví `height`. Když se SVG do 400 ms zmenší, `height`
 * se sundá a zkusí se `min-height` + `max-height` (některý ResizeObserver
 * čte jen `style.height`).
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

  function svgEl(el) {
    return el.querySelector('svg');
  }

  function svgBoundH(el) {
    var svg = svgEl(el);
    return svg ? svg.getBoundingClientRect().height : 'n/a';
  }

  function svgBoundW(el) {
    var svg = svgEl(el);
    return svg ? svg.getBoundingClientRect().width : 'n/a';
  }

  function svgScrollVars(el) {
    var svg = svgEl(el);
    if (!svg) return 'svg: none';
    var cs = getComputedStyle(svg);
    return '--scrollTop: ' + JSON.stringify(cs.getPropertyValue('--scrollTop')) +
      '\n--scrollLeft: ' + JSON.stringify(cs.getPropertyValue('--scrollLeft'));
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
      var tracked = [];
      charts.forEach(function(el, index) {
        var rect = el.getBoundingClientRect();
        var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
        if (!scale || scale <= 0) scale = 1;
        var viewportHeight = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
        var availableRendered = viewportHeight - rect.top - 8;
        var targetCss = availableRendered / scale;
        var beforeSvg = svgBoundH(el);
        if (targetCss > 0) {
          el.style.setProperty('height', targetCss + 'px', 'important');
        }
        tracked.push({
          el: el,
          index: index,
          scale: scale,
          targetCss: targetCss,
          beforeSvg: beforeSvg,
          applied: 'height (no resize)'
        });
      });
      setTimeout(function() {
        tracked.forEach(function(p) {
          p.afterHeight = svgBoundH(p.el);
          var before = Number(p.beforeSvg);
          var after = Number(p.afterHeight);
          if (p.targetCss > 0 && isFinite(before) && isFinite(after) && after < before * 0.9) {
            p.el.style.removeProperty('height');
            p.el.style.setProperty('min-height', p.targetCss + 'px', 'important');
            p.el.style.setProperty('max-height', p.targetCss + 'px', 'important');
            p.applied = 'min/max-height (SVG shrank after height)';
          }
        });
      }, 400);
      setTimeout(function() {
        var later = [];
        tracked.forEach(function(p) {
          var r = p.el.getBoundingClientRect();
          later.push(
            'chart ' + p.index + '\n' +
            'scale: ' + p.scale + '\n' +
            'targetCss: ' + p.targetCss + '\n' +
            'applied: ' + p.applied + '\n' +
            'svgBoundingHeight BEFORE: ' + p.beforeSvg + '\n' +
            'svgBoundingHeight po height (~400ms): ' + p.afterHeight + '\n' +
            'el.getBoundingClientRect().height po 1000ms: ' + r.height + '\n' +
            'svgBoundingHeight po 1000ms: ' + svgBoundH(p.el) + '\n' +
            'el.getBoundingClientRect().width: ' + r.width + '\n' +
            'window.innerWidth: ' + window.innerWidth + '\n' +
            'svg.getBoundingClientRect().width: ' + svgBoundW(p.el) + '\n' +
            svgScrollVars(p.el)
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

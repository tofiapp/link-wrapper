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
 * [FIT_JS] bere scale z getBoundingClientRect / offsetHeight a výšku
 * počítá ve vykreslených pixelech od horní hrany po spodek viewportu.
 * Měřítko se měří, nehádá — [PageZoom] má zoom jen na html.
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

  function svgDump(el) {
    var svg = el.querySelector('svg');
    if (!svg) return 'svg: none';
    var cs = getComputedStyle(svg);
    return [
      'svgTag: ' + svg.tagName,
      'svgWidthAttr: ' + svg.getAttribute('width'),
      'svgHeightAttr: ' + svg.getAttribute('height'),
      'svgViewBox: ' + svg.getAttribute('viewBox'),
      'svgStyleHeight: ' + JSON.stringify(svg.style.height),
      'svgOffsetHeight: ' + svg.offsetHeight,
      'svgBoundingHeight: ' + svg.getBoundingClientRect().height,
      '--pxPerMeter: ' + JSON.stringify(cs.getPropertyValue('--pxPerMeter')),
      '--yOffset: ' + JSON.stringify(cs.getPropertyValue('--yOffset')),
      '--chartTop: ' + JSON.stringify(cs.getPropertyValue('--chartTop')),
      '--scrollTop: ' + JSON.stringify(cs.getPropertyValue('--scrollTop'))
    ].join('\n');
  }

  function svgBound(el) {
    var svg = el.querySelector('svg');
    return svg ? svg.getBoundingClientRect().height : 'n/a';
  }

  function nudgeParent(parent) {
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

  function measure(el) {
    var rect = el.getBoundingClientRect();
    var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
    if (!scale || scale <= 0) scale = 1;
    var viewportHeight = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
    var availableRendered = viewportHeight - rect.top - 8;
    var targetCss = availableRendered / scale;
    return {
      scale: scale,
      rectTop: rect.top,
      viewportHeight: viewportHeight,
      availableRendered: availableRendered,
      targetCss: targetCss
    };
  }

  function runSteps(el, targetCss, steps) {
    function rec(label) {
      steps.push(label + ': ' + svgBound(el));
    }
    if (targetCss > 0) {
      el.style.setProperty('height', targetCss + 'px', 'important');
      rec('after container height');
      nudgeParent(el.parentElement);
    }
    setTimeout(function() {
      rec('after parent nudge');
      var keep = (targetCss > 0) ? (targetCss + 'px') : el.style.height;
      el.style.setProperty('height', (targetCss - 1) + 'px', 'important');
      requestAnimationFrame(function() {
        el.style.setProperty('height', keep, 'important');
        requestAnimationFrame(function() {
          rec('after self nudge');
          setTimeout(function() {
            var svg = el.querySelector('svg');
            if (svg && targetCss > 0) {
              svg.style.setProperty('height', targetCss + 'px', 'important');
            }
            rec('after svg.style.height');
            setTimeout(function() {
              window.dispatchEvent(new Event('resize'));
              rec('after delayed resize');
            }, 250);
          }, 50);
        });
      });
    }, 50);
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
      var befores = [];
      var plans = [];
      charts.forEach(function(el, index) {
        var m = measure(el);
        befores.push(
          'chart ' + index + '\n' +
          'scale: ' + m.scale + '\n' +
          'rect.top: ' + m.rectTop + '\n' +
          'viewportHeight: ' + m.viewportHeight + '\n' +
          'availableRendered: ' + m.availableRendered + '\n' +
          'targetCss: ' + m.targetCss + '\n' +
          svgDump(el)
        );
        var steps = [];
        plans.push({ el: el, index: index, targetCss: m.targetCss, steps: steps });
        runSteps(el, m.targetCss, steps);
      });
      show('PŘED nastavením výšky\n\n' + befores.join('\n\n'));
      setTimeout(function() {
        var later = [];
        plans.forEach(function(p) {
          later.push(
            'chart ' + p.index + '\n' +
            'el.style.height po 1000ms: ' + JSON.stringify(p.el.style.height) + '\n' +
            'el.getBoundingClientRect().height po 1000ms: ' + p.el.getBoundingClientRect().height + '\n' +
            'kroky svgBoundingHeight:\n' + p.steps.join('\n') + '\n' +
            svgDump(p.el)
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

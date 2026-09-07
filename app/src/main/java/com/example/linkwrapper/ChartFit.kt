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
 * přizpůsobí; výška zůstává na původní hodnotě ze stránky, takže se
 * scrolluje i když je v rodiči místo.
 *
 * [FIT_JS] nastaví jen inline výšku `.chart-part` podle rodiče
 * a šťouchne layout (1 px + `resize`), aby Highcharts překreslil
 * vnitřek. Zbytek stránky a šířka se nemění.
 *
 * Dočasně loguje výšky do dialogu přes [DebugBridge] — po diagnostice
 * most i `AndroidDebugBridge` z WebView odstranit.
 */
internal object ChartFit {

    const val FIT_JS = """
(function(){
  function show(msg) {
    try {
      if (window.AndroidDebugBridge && window.AndroidDebugBridge.showResult) {
        window.AndroidDebugBridge.showResult(msg);
      }
    } catch (e) {}
  }
  try {
    var charts = document.querySelectorAll('.chart-part');
    var results = [];
    if (!charts.length) {
      show('no .chart-part');
      return;
    }
    var pending = charts.length;
    function finish() {
      pending -= 1;
      if (pending === 0) show(results.join('\n\n'));
    }
    charts.forEach(function(el, index) {
      var parent = el.parentElement;
      if (!parent) {
        results.push('chart ' + index + ': no parent');
        finish();
        return;
      }
      var targetHeight = parent.clientHeight;
      var before = {
        parentClientHeight: parent.clientHeight,
        styleHeightBefore: el.style.height,
        offsetHeightBefore: el.offsetHeight
      };

      if (targetHeight > 0) {
        el.style.setProperty('height', targetHeight + 'px', 'important');
        var originalParentHeight = parent.style.height;
        parent.style.height = (parent.clientHeight - 1) + 'px';
        requestAnimationFrame(function() {
          parent.style.height = originalParentHeight || '';
          requestAnimationFrame(function() {
            window.dispatchEvent(new Event('resize'));
          });
        });
      }

      var afterImmediate = {
        styleHeightAfter: el.style.height,
        offsetHeightAfter: el.offsetHeight
      };

      setTimeout(function() {
        var delayed = {
          styleHeightDelayed: el.style.height,
          offsetHeightDelayed: el.offsetHeight
        };
        results.push(
          'chart ' + index + '\n' +
          'parent.clientHeight PŘED nastavením: ' + before.parentClientHeight + '\n' +
          'el.style.height PŘED nastavením: ' + JSON.stringify(before.styleHeightBefore) + '\n' +
          'el.offsetHeight PŘED nastavením: ' + before.offsetHeightBefore + '\n' +
          'targetHeight: ' + targetHeight + '\n' +
          'el.style.height HNED PO nastavení: ' + JSON.stringify(afterImmediate.styleHeightAfter) + '\n' +
          'el.offsetHeight HNED PO nastavení: ' + afterImmediate.offsetHeightAfter + '\n' +
          'el.style.height po 800ms: ' + JSON.stringify(delayed.styleHeightDelayed) + '\n' +
          'el.offsetHeight po 800ms: ' + delayed.offsetHeightDelayed
        );
        finish();
      }, 800);
    });
  } catch (e) {
    show('error: ' + e);
  }
})();
"""
}

/**
 * Dočasný most JS → Android pro diagnostiku výšky grafu.
 * Odstranit spolu s [ChartFit.FIT_JS] debug částí — stránka by jinak
 * mohla volat native kód.
 */
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

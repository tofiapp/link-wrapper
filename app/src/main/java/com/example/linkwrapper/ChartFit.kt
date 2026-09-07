package com.example.linkwrapper

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Zoom grafu podle šířky. Místo roztahování výšky dočasně dumpne
 * elementy pod bodem u spodku obrazovky — hledáme bílý překryv.
 */
internal object ChartFit {

    /** Před opakovaným během (orientace) — jinak `__chartFitDone` injekci no-opne. */
    const val RESET_JS = """
window.__chartFitDone = false;
window.__chartZoom = null;
"""

    const val FIT_JS = """
(function() {
    if (window.__chartFitDone) return;

    function show(msg) {
        try {
            if (window.AndroidDebugBridge && window.AndroidDebugBridge.showResult) {
                window.AndroidDebugBridge.showResult(msg);
            }
        } catch (e) {}
    }

    function identifyOverlay() {
        var x = window.innerWidth / 2;
        var y = window.innerHeight - 60;

        var stack = document.elementsFromPoint(x, y);
        var out = [];
        out.push('zoom=' + window.__chartZoom + ' point=' + Math.round(x) + ',' + Math.round(y));
        stack.slice(0, 8).forEach(function(n, i) {
            var cs = getComputedStyle(n);
            var r = n.getBoundingClientRect();
            out.push(
                i + ': ' + n.tagName + '.' + (n.className || '-') +
                ' | rect ' + Math.round(r.width) + 'x' + Math.round(r.height) +
                ' top=' + Math.round(r.top) +
                ' | bg=' + cs.backgroundColor +
                ' pos=' + cs.position +
                ' z=' + cs.zIndex +
                ' h=' + cs.height
            );
        });
        return out.join('\n\n');
    }

    function step1_zoom() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;

        document.documentElement.style.zoom = '';
        document.body.style.zoom = '';
        el.style.removeProperty('height');

        var contentW = document.documentElement.scrollWidth;
        var winW = window.innerWidth;
        if (contentW <= 0) return false;

        var zoom = winW / contentW;
        if (zoom > 1) zoom = 1;
        window.__chartZoom = zoom;
        document.body.style.setProperty('zoom', zoom, 'important');

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');

        setTimeout(function() {
            try {
                show(identifyOverlay());
            } catch (e) {
                show('identifyOverlay error: ' + e);
            }
            window.__chartFitDone = true;
        }, 1500);
        return true;
    }

    if (!step1_zoom()) {
        var root = document.body || document.documentElement;
        if (!root) return;
        var obs = new MutationObserver(function() {
            if (step1_zoom()) obs.disconnect();
        });
        obs.observe(root, { childList: true, subtree: true });
        setTimeout(function() { obs.disconnect(); }, 20000);
    }
})();
"""
}

/**
 * Dočasný most JS → Android pro dump překryvu.
 * Odstranit spolu s [ChartFit.FIT_JS] debug částí.
 */
class DebugBridge(private val context: Context) {
    @JavascriptInterface
    fun showResult(result: String) {
        val activity = context as? Activity ?: return
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            Log.d("ChartFit", result)
            val dialog = MaterialAlertDialogBuilder(activity)
                .setTitle("Chart overlay")
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

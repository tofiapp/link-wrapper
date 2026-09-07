package com.example.linkwrapper

/**
 * Zoom podle šířky, pak jednou zvětší `.chart-part` podle nejnižšího
 * SVG `y`, ať se graf vykreslí v plné délce a stránka se scrolluje.
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

    function step1_zoom() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;

        document.documentElement.style.zoom = '';
        document.body.style.zoom = '';

        var contentW = document.documentElement.scrollWidth;
        var winW = window.innerWidth;
        if (contentW <= 0) return false;

        var zoom = winW / contentW;
        if (zoom > 1) zoom = 1;
        window.__chartZoom = zoom;
        document.body.style.setProperty('zoom', zoom, 'important');

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');

        requestAnimationFrame(function() {
            requestAnimationFrame(step2_height);
        });
        return true;
    }

    function step2_height() {
        var el = document.querySelector('.chart-part');
        if (!el) return;
        var svg = el.querySelector('svg');
        if (!svg) return;

        var maxY = 0;
        svg.querySelectorAll('[y]').forEach(function(n) {
            var v = parseFloat(n.getAttribute('y'));
            if (!isNaN(v) && v > maxY) maxY = v;
        });
        if (maxY <= 0) return;

        var target = maxY + 40;
        window.__chartMaxY = maxY;

        el.style.setProperty('height', target + 'px', 'important');

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('height', 'auto', 'important');
        document.body.style.setProperty('height', 'auto', 'important');

        window.__chartFitDone = true;
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

package com.example.linkwrapper

/**
 * Jednorázový fit grafu: nejdřív zoom podle čisté šířky, po dvou
 * framech výška `.chart-part`. [RESET_JS] odemkne běh při otočení.
 */
internal object ChartFit {

    /** Před opakovaným fitem (orientace) — jinak `__chartFitDone` injekci no-opne. */
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

        requestAnimationFrame(function() {
            requestAnimationFrame(step2_height);
        });
        return true;
    }

    function step2_height() {
        var el = document.querySelector('.chart-part');
        if (!el) return;

        var rect = el.getBoundingClientRect();
        var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
        if (!scale || scale <= 0) scale = 1;
        var vh = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
        var target = (vh - rect.top - 8) / scale;
        if (target > 0) {
            el.style.setProperty('height', target + 'px', 'important');
        }
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

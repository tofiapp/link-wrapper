package com.example.linkwrapper

/**
 * Zoom grafu podle šířky. `.chart-part` se nemění — html/body
 * dostanou konkrétní výšku obsahu, ať jde doscrollovat.
 */
internal object ChartFit {

    /** Před opakovaným během (orientace) — jinak `__chartFitDone` injekci no-opne. */
    const val RESET_JS = """
if (window.__chartHeightObs) { window.__chartHeightObs.disconnect(); window.__chartHeightObs = null; }
if (window.__chartHeightTimer) { clearInterval(window.__chartHeightTimer); window.__chartHeightTimer = null; }
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

        var wrapper = document.querySelector('.css-nm4wu0') || el.parentElement;
        if (!wrapper) return;

        function enforce() {
            var el = document.querySelector('.chart-part');
            if (!el) return;

            var wrapper = document.querySelector('.css-nm4wu0') || el.parentElement;
            if (!wrapper) return;

            var needed = Math.max(
                wrapper.scrollHeight,
                wrapper.offsetHeight,
                parseFloat(getComputedStyle(wrapper).height) || 0,
                el.scrollHeight,
                el.offsetHeight
            );
            if (!needed || needed <= 0) return;

            var h = document.documentElement;
            var b = document.body;

            h.style.setProperty('height', needed + 'px', 'important');
            h.style.setProperty('min-height', needed + 'px', 'important');
            h.style.setProperty('background', 'transparent', 'important');

            b.style.setProperty('height', needed + 'px', 'important');
            b.style.setProperty('min-height', needed + 'px', 'important');
            b.style.setProperty('background', 'transparent', 'important');
        }

        enforce();

        if (window.__chartHeightObs) window.__chartHeightObs.disconnect();
        window.__chartHeightObs = new MutationObserver(enforce);
        window.__chartHeightObs.observe(wrapper, { attributes: true, attributeFilter: ['style'] });

        if (window.__chartHeightTimer) clearInterval(window.__chartHeightTimer);
        window.__chartHeightTimer = setInterval(enforce, 500);

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

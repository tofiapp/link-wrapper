package com.example.linkwrapper

/**
 * Zoom podle šířky, pak jednou zamkne výšku `.chart-part` na
 * max SVG `y` + 40 — stránka ji nesmí přepsat přes style.height.
 */
internal object ChartFit {

    /** Otočení: odemknout a znovu fitnout (zámek zmizí s elementem). */
    const val RESET_JS = """
window.__chartFitDone = false;
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
        var lockedValue = target + 'px';

        el.style.setProperty('height', lockedValue, 'important');

        try {
            var styleObj = el.style;
            var origSetProperty = styleObj.setProperty.bind(styleObj);
            styleObj.setProperty = function(prop, value, priority) {
                if (prop === 'height' || prop === 'min-height' || prop === 'max-height') {
                    return origSetProperty(prop, lockedValue, 'important');
                }
                return origSetProperty(prop, value, priority);
            };
            Object.defineProperty(styleObj, 'height', {
                configurable: true,
                get: function() { return lockedValue; },
                set: function() { origSetProperty('height', lockedValue, 'important'); }
            });
        } catch (e) {}

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

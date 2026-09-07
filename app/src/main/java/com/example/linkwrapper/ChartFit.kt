package com.example.linkwrapper

/**
 * Graf na šířku do WebView: zoom jen na `body` podle
 * `innerWidth / scrollWidth` (max 1). Svisle se scrolluje.
 * Výška `.chart-part` se nastaví až po zoomu.
 */
internal object ChartFit {

    const val FIT_JS = """
(function() {
    function fit() {
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
        document.body.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');

        requestAnimationFrame(function() {
            var rect = el.getBoundingClientRect();
            var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
            if (!scale || scale <= 0) scale = 1;
            var vh = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
            var target = (vh - rect.top - 8) / scale;
            if (target > 0) {
                el.style.setProperty('height', target + 'px', 'important');
            }
        });

        return true;
    }

    if (!fit()) {
        var root = document.body || document.documentElement;
        if (!root) return;
        var obs = new MutationObserver(function() { if (fit()) obs.disconnect(); });
        obs.observe(root, { childList: true, subtree: true });
        setTimeout(function() { obs.disconnect(); }, 20000);
    }
})();
"""
}

package com.example.linkwrapper

/**
 * Zoom podle šířky, pak jednou spočítá cílovou výšku `.chart-part`
 * z nejnižšího SVG `y` a tu hodnotu drží proti přepisu stránkou.
 */
internal object ChartFit {

    /** Otočení: odpojit hold, odemknout, znovu fitnout. */
    const val RESET_JS = """
if (window.__chartHeightObs) { window.__chartHeightObs.disconnect(); window.__chartHeightObs = null; }
if (window.__chartHeightTimer) { clearInterval(window.__chartHeightTimer); window.__chartHeightTimer = null; }
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

        function enforce() {
            var cur = parseFloat(el.style.height);
            if (isNaN(cur) || Math.abs(cur - target) > 1) {
                el.style.setProperty('height', target + 'px', 'important');
            }
        }

        enforce();

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('height', 'auto', 'important');
        document.body.style.setProperty('height', 'auto', 'important');

        if (window.__chartHeightObs) window.__chartHeightObs.disconnect();
        window.__chartHeightObs = new MutationObserver(enforce);
        window.__chartHeightObs.observe(el, { attributes: true, attributeFilter: ['style'] });

        if (window.__chartHeightTimer) clearInterval(window.__chartHeightTimer);
        window.__chartHeightTimer = setInterval(enforce, 300);

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

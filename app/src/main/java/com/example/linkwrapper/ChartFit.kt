package com.example.linkwrapper

/**
 * Drží výšku `.chart-part`: stránka ji přepisuje, observer a interval
 * ji vracejí. Do SVG se nesahá. Při rotaci se flag vynuluje a blok
 * běží znovu. Horizontální overflow se odemkne (`overflow-x: auto`).
 */
internal object ChartFit {

    const val RESET_JS = """
(function(){
  window.__chartFixActive = false;
  if (window.__chartFixIntervals) {
    window.__chartFixIntervals.forEach(function(id) { clearInterval(id); });
    window.__chartFixIntervals = [];
  }
  if (window.__chartFixStyleObs) {
    window.__chartFixStyleObs.forEach(function(o) { try { o.disconnect(); } catch (e) {} });
    window.__chartFixStyleObs = [];
  }
})();
"""

    const val FIT_JS = """
(function() {
    if (window.__chartFixActive) return;
    window.__chartFixActive = true;
    window.__chartFixIntervals = window.__chartFixIntervals || [];
    window.__chartFixStyleObs = window.__chartFixStyleObs || [];

    var applying = false;

    function computeTarget(el) {
        var rect = el.getBoundingClientRect();
        var scale = (el.offsetHeight > 0) ? (rect.height / el.offsetHeight) : 1;
        if (!scale || scale <= 0) scale = 1;
        var vh = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
        var avail = vh - rect.top - 8;
        return (avail > 0) ? (avail / scale) : 0;
    }

    function apply(el) {
        if (applying) return;
        applying = true;
        var t = computeTarget(el);
        if (t > 0) {
            el.style.setProperty('height', t + 'px', 'important');
            el.style.setProperty('max-height', t + 'px', 'important');
        }
        [document.documentElement, document.body, el.parentElement, el].forEach(function(n) {
            if (!n) return;
            n.style.setProperty('overflow-x', 'auto', 'important');
            n.style.setProperty('max-width', 'none', 'important');
        });
        applying = false;
    }

    function guard(el) {
        apply(el);
        var obs = new MutationObserver(function() { apply(el); });
        obs.observe(el, { attributes: true, attributeFilter: ['style'] });
        window.__chartFixStyleObs.push(obs);
        window.__chartFixIntervals.push(setInterval(function() { apply(el); }, 500));
    }

    function init() {
        var charts = document.querySelectorAll('.chart-part');
        if (!charts.length) return false;
        charts.forEach(guard);
        return true;
    }

    if (init()) return;
    var root = document.body || document.documentElement;
    if (!root) return;
    var boot = new MutationObserver(function() { if (init()) boot.disconnect(); });
    boot.observe(root, { childList: true, subtree: true });
    setTimeout(function() { boot.disconnect(); }, 20000);
})();
"""
}

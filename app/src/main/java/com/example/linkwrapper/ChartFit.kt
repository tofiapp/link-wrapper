package com.example.linkwrapper

/**
 * Zoom podle šířky, pak zamkne výšku `.chart-part` i proti
 * `setAttribute('style', …)`.
 *
 * Fit běží až po načtení stránky, ne na document-start — globální
 * prototype háčky a `visibility:hidden` na celém html bránily
 * grafu v načtení. Flash „plný graf → bílá“ řeší jen schování
 * `.chart-part`; zbytek stránky zůstane vidět.
 */
internal object ChartFit {

    private const val HIDE_STYLE_JS = """
(function(){
  if (document.getElementById('__chartFitHideStyle')) return;
  var s = document.createElement('style');
  s.id = '__chartFitHideStyle';
  s.textContent = '.chart-part{opacity:0 !important;}';
  (document.documentElement || document.head).appendChild(s);
})();
"""

    /** Otočení: odpojit zámek, schovat graf, znovu fitnout. */
    val RESET_JS = """
if (window.__chartFitLockObs) { window.__chartFitLockObs.disconnect(); window.__chartFitLockObs = null; }
window.__chartFitDone = false;
$HIDE_STYLE_JS
"""

    /**
     * Na začátku dokumentu, jen u grafu (`dmId`). Schová `.chart-part`
     * dřív než první snímek — ne celé html.
     */
    val BOOTSTRAP_JS = """
(function(){
  try {
    if (!/(?:^|[?&])dmId=/i.test(location.search || '')) return;
  } catch (e) { return; }
  $HIDE_STYLE_JS
})();
"""

    const val FIT_JS = """
(function() {
    if (window.__chartFitDone) return;

    function showChart() {
        var s = document.getElementById('__chartFitHideStyle');
        if (s && s.parentNode) s.parentNode.removeChild(s);
    }

    function svgMaxY(el) {
        var svg = el.querySelector('svg');
        if (!svg) return 0;
        var maxY = 0;
        svg.querySelectorAll('[y]').forEach(function(n) {
            var v = parseFloat(n.getAttribute('y'));
            if (!isNaN(v) && v > maxY) maxY = v;
        });
        return maxY;
    }

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

        waitStarted = Date.now();
        requestAnimationFrame(function() {
            requestAnimationFrame(step2_height);
        });
        return true;
    }

    var waitStarted = Date.now();
    var locking = false;

    function step2_height() {
        if (window.__chartFitDone || locking) return;
        var el = document.querySelector('.chart-part');
        var maxY = el ? svgMaxY(el) : 0;
        if (!el || maxY <= 0) {
            if (Date.now() - waitStarted > 2500) {
                window.__chartFitDone = true;
                showChart();
            } else {
                requestAnimationFrame(step2_height);
            }
            return;
        }

        locking = true;
        var target = maxY + 40;
        var lockedValue = target + 'px';
        var origSetAttribute = el.setAttribute.bind(el);
        var origSetAttributeNS = el.setAttributeNS.bind(el);
        var origRemoveAttribute = el.removeAttribute.bind(el);
        var styleObj = el.style;
        var origSetProperty = styleObj.setProperty.bind(styleObj);
        var origRemoveProperty = styleObj.removeProperty.bind(styleObj);

        function mergeLocked(css) {
            var s = String(css == null ? '' : css);
            s = s.replace(/(?:^|;)\s*(?:min-|max-)?height\s*:[^;]*/gi, '');
            s = s.replace(/;;+/g, ';').replace(/^;|;${'$'}/g, '');
            return (s ? s + ';' : '') +
                'height:' + lockedValue + ' !important;' +
                'min-height:' + lockedValue + ' !important;' +
                'max-height:none !important';
        }

        function attrHeight() {
            var m = String(el.getAttribute('style') || '').match(/(?:^|;)\s*height\s*:\s*([^;!]+)/i);
            return m ? parseFloat(m[1]) : NaN;
        }

        var applying = false;
        function applyLocked(fromCss) {
            if (applying) return;
            applying = true;
            try {
                origSetAttribute('style', mergeLocked(fromCss != null ? fromCss : el.getAttribute('style')));
            } finally {
                applying = false;
            }
        }

        applyLocked();

        window.__chartFitLockedEls = window.__chartFitLockedEls || new WeakMap();
        window.__chartFitLockedEls.set(el, applyLocked);
        if (!window.__chartFitProtoHooked) {
            window.__chartFitProtoHooked = true;
            var pSet = Element.prototype.setAttribute;
            Element.prototype.setAttribute = function(name, value) {
                var lock = window.__chartFitLockedEls.get(this);
                if (lock && String(name).toLowerCase() === 'style') {
                    lock(value);
                    return;
                }
                return pSet.apply(this, arguments);
            };
            var pSetNS = Element.prototype.setAttributeNS;
            Element.prototype.setAttributeNS = function(ns, name, value) {
                var lock = window.__chartFitLockedEls.get(this);
                var local = String(name || '').split(':').pop();
                if (lock && String(local).toLowerCase() === 'style') {
                    lock(value);
                    return;
                }
                return pSetNS.apply(this, arguments);
            };
            var pRem = Element.prototype.removeAttribute;
            Element.prototype.removeAttribute = function(name) {
                var lock = window.__chartFitLockedEls.get(this);
                if (lock && String(name).toLowerCase() === 'style') {
                    lock('');
                    return;
                }
                return pRem.apply(this, arguments);
            };
        }

        try {
            el.setAttribute = function(name, value) {
                if (String(name).toLowerCase() === 'style') {
                    applyLocked(value);
                    return;
                }
                return origSetAttribute(name, value);
            };
            el.setAttributeNS = function(ns, name, value) {
                var local = String(name || '').split(':').pop();
                if (String(local).toLowerCase() === 'style') {
                    applyLocked(value);
                    return;
                }
                return origSetAttributeNS(ns, name, value);
            };
            el.removeAttribute = function(name) {
                if (String(name).toLowerCase() === 'style') {
                    applyLocked('');
                    return;
                }
                return origRemoveAttribute(name);
            };
            styleObj.setProperty = function(prop, value, priority) {
                if (prop === 'height' || prop === 'min-height' || prop === 'max-height') {
                    applyLocked();
                    return;
                }
                return origSetProperty(prop, value, priority);
            };
            styleObj.removeProperty = function(prop) {
                if (prop === 'height' || prop === 'min-height' || prop === 'max-height') {
                    applyLocked();
                    return '';
                }
                return origRemoveProperty(prop);
            };
            Object.defineProperty(styleObj, 'height', {
                configurable: true,
                get: function() { return lockedValue; },
                set: function() { applyLocked(); }
            });
            Object.defineProperty(styleObj, 'cssText', {
                configurable: true,
                get: function() { return el.getAttribute('style') || ''; },
                set: function(v) { applyLocked(v); }
            });
        } catch (e) {}

        if (window.__chartFitLockObs) window.__chartFitLockObs.disconnect();
        window.__chartFitLockObs = new MutationObserver(function() {
            if (applying) return;
            var cur = attrHeight();
            if (isNaN(cur) || Math.abs(cur - target) > 1) applyLocked();
        });
        window.__chartFitLockObs.observe(el, { attributes: true, attributeFilter: ['style'] });

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('height', 'auto', 'important');
        document.body.style.setProperty('height', 'auto', 'important');

        window.__chartFitDone = true;
        showChart();
    }

    if (!step1_zoom()) {
        var root = document.body || document.documentElement;
        if (!root) {
            showChart();
            return;
        }
        var obs = new MutationObserver(function() {
            if (step1_zoom()) obs.disconnect();
        });
        obs.observe(root, { childList: true, subtree: true });
        setTimeout(function() {
            obs.disconnect();
            if (!window.__chartFitDone) showChart();
        }, 20000);
    }
})();
"""
}

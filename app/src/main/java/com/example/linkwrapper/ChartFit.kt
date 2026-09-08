package com.example.linkwrapper

/**
 * Graf: skrýt stránku než sedí šířka i výška, a **nesnižovat**
 * `.chart-part`. Předchozí zámek po `onPageFinished` přišel pozdě —
 * graf se v plné výšce jen problikl a stránka ho srazila (bílá dole,
 * pravítko pořád sahalo pod ni).
 *
 * Háčky jdou na document-start, dřív než React zapíše `style`.
 * Výška se jen zvedá (nejvyšší zápis / SVG / layout), nikdy dolů.
 */
internal object ChartFit {

    /** Otočení: odpojit, schovat, změřit znovu. */
    const val RESET_JS = """
if (window.__chartFitLockObs) { window.__chartFitLockObs.disconnect(); window.__chartFitLockObs = null; }
if (window.__chartFitDomObs) { window.__chartFitDomObs.disconnect(); window.__chartFitDomObs = null; }
window.__chartFitDone = false;
window.__chartFitZoomed = false;
window.__chartFitSettling = false;
window.__chartFitFloorH = 0;
window.__chartFitHide = true;
try { document.documentElement.style.setProperty('visibility', 'hidden', 'important'); } catch (e) {}
"""

    /**
     * Na začátku dokumentu, jen u grafu (`dmId`). Schová html dřív
     * než první snímek, ať není vidět plný graf a pak bílá.
     */
    const val BOOTSTRAP_JS = """
(function(){
  try {
    if (!/(?:^|[?&])dmId=/i.test(location.search || '')) return;
  } catch (e) { return; }
  window.__chartFitHide = true;
  try { document.documentElement.style.setProperty('visibility', 'hidden', 'important'); } catch (e) {}
})();
"""

    const val FIT_JS = """
(function() {
    function isChartPart(el) {
        return !!(el && el.nodeType === 1 && el.classList && el.classList.contains('chart-part'));
    }

    function showPage() {
        window.__chartFitHide = false;
        try { document.documentElement.style.removeProperty('visibility'); } catch (e) {}
    }

    function parseHeight(css) {
        var m = String(css == null ? '' : css).match(/(?:^|;)\s*height\s*:\s*([^;!]+)/i);
        return m ? parseFloat(m[1]) : NaN;
    }

    function svgContentHeight(el) {
        var svg = el.querySelector && el.querySelector('svg');
        if (!svg) return 0;
        var maxY = 0;
        svg.querySelectorAll('[y]').forEach(function(n) {
            var v = parseFloat(n.getAttribute('y'));
            if (!isNaN(v) && v > maxY) maxY = v;
        });
        try {
            var b = svg.getBBox();
            if (b && (b.y + b.height) > maxY) maxY = b.y + b.height;
        } catch (e) {}
        return maxY > 0 ? maxY + 40 : 0;
    }

    function raiseFloor(el, fromCss) {
        var floor = window.__chartFitFloorH || 0;
        var requested = parseHeight(fromCss != null ? fromCss : (el.getAttribute && el.getAttribute('style')));
        if (!isNaN(requested) && requested > floor) floor = requested;
        var svgH = svgContentHeight(el);
        if (svgH > floor) floor = svgH;
        try {
            var rectH = el.getBoundingClientRect().height;
            if (rectH > floor) floor = rectH;
        } catch (e) {}
        window.__chartFitFloorH = floor;
        return floor;
    }

    function mergeLocked(css, h) {
        var s = String(css == null ? '' : css);
        s = s.replace(/(?:^|;)\s*(?:min-|max-)?height\s*:[^;]*/gi, '');
        s = s.replace(/(?:^|;)\s*overflow(?:-x|-y)?\s*:[^;]*/gi, '');
        s = s.replace(/;;+/g, ';').replace(/^;|;${'$'}/g, '');
        var px = h + 'px';
        return (s ? s + ';' : '') +
            'height:' + px + ' !important;' +
            'min-height:' + px + ' !important;' +
            'max-height:none !important;' +
            'overflow:visible !important';
    }

    window.__chartFitStyleToEl = window.__chartFitStyleToEl || new WeakMap();
    window.__chartFitApplying = window.__chartFitApplying || new WeakSet();

    function applyLocked(el, fromCss) {
        if (!isChartPart(el) || window.__chartFitApplying.has(el)) return;
        var h = raiseFloor(el, fromCss);
        if (h <= 0) return false;
        window.__chartFitApplying.add(el);
        try {
            var proto = window.__chartFitSetAttr || Element.prototype.setAttribute;
            proto.call(el, 'style', mergeLocked(fromCss != null ? fromCss : el.getAttribute('style'), h));
            var svg = el.querySelector('svg');
            if (svg) {
                try { svg.style.setProperty('overflow', 'visible', 'important'); } catch (e) {}
            }
            try {
                document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
                document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
                document.documentElement.style.setProperty('height', 'auto', 'important');
                document.body && document.body.style.setProperty('height', 'auto', 'important');
                document.documentElement.style.setProperty('min-height', h + 'px', 'important');
            } catch (e) {}
        } finally {
            window.__chartFitApplying.delete(el);
        }
        return true;
    }

    if (!window.__chartFitProtoHooked) {
        window.__chartFitProtoHooked = true;
        window.__chartFitSetAttr = Element.prototype.setAttribute;
        Element.prototype.setAttribute = function(name, value) {
            if (isChartPart(this) && String(name).toLowerCase() === 'style') {
                if (applyLocked(this, value)) return;
            }
            return window.__chartFitSetAttr.apply(this, arguments);
        };
        var pSetNS = Element.prototype.setAttributeNS;
        Element.prototype.setAttributeNS = function(ns, name, value) {
            var local = String(name || '').split(':').pop();
            if (isChartPart(this) && String(local).toLowerCase() === 'style') {
                if (applyLocked(this, value)) return;
            }
            return pSetNS.apply(this, arguments);
        };
        var pRem = Element.prototype.removeAttribute;
        Element.prototype.removeAttribute = function(name) {
            if (isChartPart(this) && String(name).toLowerCase() === 'style') {
                if (applyLocked(this, '')) return;
            }
            return pRem.apply(this, arguments);
        };
        var styleProto = CSSStyleDeclaration.prototype;
        var origSetProperty = styleProto.setProperty;
        styleProto.setProperty = function(prop, value, priority) {
            var el = window.__chartFitStyleToEl.get(this);
            if (el && (prop === 'height' || prop === 'min-height' || prop === 'max-height' || prop === 'overflow')) {
                if (applyLocked(el)) return;
            }
            return origSetProperty.call(this, prop, value, priority);
        };
        var origRemoveProperty = styleProto.removeProperty;
        styleProto.removeProperty = function(prop) {
            var el = window.__chartFitStyleToEl.get(this);
            if (el && (prop === 'height' || prop === 'min-height' || prop === 'max-height' || prop === 'overflow')) {
                if (applyLocked(el)) return '';
            }
            return origRemoveProperty.call(this, prop);
        };
        var cssTextDesc = Object.getOwnPropertyDescriptor(styleProto, 'cssText');
        if (cssTextDesc && cssTextDesc.configurable) {
            Object.defineProperty(styleProto, 'cssText', {
                configurable: true,
                get: cssTextDesc.get,
                set: function(v) {
                    var el = window.__chartFitStyleToEl.get(this);
                    if (el && applyLocked(el, v)) return;
                    return cssTextDesc.set.call(this, v);
                }
            });
        }
        var heightDesc = Object.getOwnPropertyDescriptor(styleProto, 'height');
        if (heightDesc && heightDesc.configurable) {
            Object.defineProperty(styleProto, 'height', {
                configurable: true,
                get: heightDesc.get,
                set: function(v) {
                    var el = window.__chartFitStyleToEl.get(this);
                    if (el && applyLocked(el, 'height:' + v)) return;
                    return heightDesc.set.call(this, v);
                }
            });
        }
    }

    function remember(el) {
        if (!isChartPart(el)) return;
        try { window.__chartFitStyleToEl.set(el.style, el); } catch (e) {}
        applyLocked(el);
        if (window.__chartFitLockObs) {
            try { window.__chartFitLockObs.observe(el, { attributes: true, attributeFilter: ['style'] }); } catch (e) {}
        }
    }

    function holdLock(ms) {
        var t0 = Date.now();
        function tick() {
            var part = document.querySelector('.chart-part');
            if (part) remember(part);
            if (Date.now() - t0 < ms) requestAnimationFrame(tick);
        }
        tick();
    }

    function finishFit() {
        var part = document.querySelector('.chart-part');
        if (part) remember(part);
        window.__chartFitDone = true;
        showPage();
        holdLock(2500);
    }

    function step1_zoom() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;
        remember(el);

        if (window.__chartFitSettling) return true;
        window.__chartFitSettling = true;

        if (!window.__chartFitZoomed) {
            document.documentElement.style.zoom = '';
            if (document.body) document.body.style.zoom = '';
            var contentW = document.documentElement.scrollWidth;
            var winW = window.innerWidth;
            if (contentW > 0) {
                var zoom = winW / contentW;
                if (zoom > 1) zoom = 1;
                if (document.body) document.body.style.setProperty('zoom', zoom, 'important');
            }
            document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
            document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
            window.__chartFitZoomed = true;
        }

        var foundAt = Date.now();
        function settle() {
            var part = document.querySelector('.chart-part');
            if (part) remember(part);
            if (Date.now() - foundAt < 1000) {
                requestAnimationFrame(settle);
            } else {
                finishFit();
            }
        }
        requestAnimationFrame(settle);
        return true;
    }

    if (!window.__chartFitLockObs) {
        window.__chartFitLockObs = new MutationObserver(function(records) {
            for (var i = 0; i < records.length; i++) {
                var t = records[i].target;
                if (isChartPart(t) && !window.__chartFitApplying.has(t)) applyLocked(t);
            }
        });
    }

    if (window.__chartFitDone && !window.__chartFitHide) return;

    function isChartUrl() {
        try { return /(?:^|[?&])dmId=/i.test(location.search || ''); } catch (e) { return false; }
    }

    if (!step1_zoom()) {
        if (!isChartUrl()) {
            showPage();
            return;
        }
        var root = document.documentElement || document.body;
        if (!root) {
            document.addEventListener('DOMContentLoaded', function() { step1_zoom(); });
        } else if (!window.__chartFitDomObs) {
            window.__chartFitDomObs = new MutationObserver(function() {
                document.querySelectorAll('.chart-part').forEach(remember);
                if (!window.__chartFitSettling) step1_zoom();
            });
            window.__chartFitDomObs.observe(root, { childList: true, subtree: true });
        }
        setTimeout(function() {
            if (!window.__chartFitDone) {
                step1_zoom();
                window.__chartFitDone = true;
            }
            showPage();
        }, 8000);
    }
})();
"""
}

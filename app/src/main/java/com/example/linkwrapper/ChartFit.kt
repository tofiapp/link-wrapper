package com.example.linkwrapper

/**
 * Zoom podle šířky, pak po krátké prodlevě zamkne výšku `.chart-part`
 * i proti `setAttribute('style', …)`.
 *
 * Schovávání grafu (`opacity:0`) se nepoužívá — knihovna do neviditelného
 * boxu nic nenakreslí a nejde scrollovat. Graf se proto nejdřív ukáže
 * celý (držet ~2,5 s), pak zámek výšky podle SVG (může se smrsknout).
 *
 * ## Srovnání na šířku — neměnit, na tabletu sedí
 *
 * 1. Zruší se `zoom` na `html` i `body`, ať `scrollWidth` je skutečná
 *    šířka obsahu, ne už zmenšená.
 * 2. `contentW` = `document.documentElement.scrollWidth`.
 * 3. `winW` = `window.innerWidth`.
 * 4. `zoom = winW / contentW`, strop 1 — jen zmenšuje.
 * 5. Zoom na **body**; `PageZoom` z ⋮ je na **html** (ať Chromium
 *    hodnoty nenasobí).
 * 6. `overflow-x: hidden`, `overflow-y: auto`.
 * 7. Po ~2,5 s se totéž srovnání na šířku spočítá znovu (graf už bývá
 *    dokreslený) a teprve pak se zamkne výška. Staré čekání se zahodí,
 *    když přijde nové fitnutí (otočení, změna velikosti WebView).
 * 8. Po zamčení výšky dostanou `html`/`body` výšku od začátku stránky
 *    po konec `.chart-part` (offset + zamčená výška) plus malou rezervu.
 *    Nebere se `scrollHeight` obalu — ten na stránce nese velký prázdný
 *    spodní okraj a přidal by se k dokumentu.
 */
internal object ChartFit {

    /** Otočení / nové fitnutí: zrušit staré čekání, odpojit zámek, odemknout. */
    const val RESET_JS = """
window.__chartFitGen = (window.__chartFitGen || 0) + 1;
if (window.__chartFitLockObs) { window.__chartFitLockObs.disconnect(); window.__chartFitLockObs = null; }
window.__chartFitDone = false;
"""

    const val FIT_JS = """
(function() {
    if (window.__chartFitDone) return;
    window.__chartFitGen = (window.__chartFitGen || 0) + 1;
    var gen = window.__chartFitGen;

    /**
     * Šířka — vzorec neměnit. Nejdřív čistý zoom, pak scrollWidth / innerWidth,
     * výsledek max 1 na body. Viz komentář u ChartFit.
     */
    function applyWidthZoom() {
        document.documentElement.style.zoom = '';
        document.body.style.zoom = '';

        var contentW = document.documentElement.scrollWidth;
        var winW = window.innerWidth;
        if (contentW <= 0 || winW <= 0) return false;

        var zoom = winW / contentW;
        if (zoom > 1) zoom = 1;
        document.body.style.setProperty('zoom', zoom, 'important');

        document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
        document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
        return true;
    }

    function stillThisFit() {
        return gen === window.__chartFitGen && !window.__chartFitDone;
    }

    function step1_zoom() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;
        if (!applyWidthZoom()) return false;

        var heldFrom = Date.now();
        function holdThenLock() {
            if (!stillThisFit()) return;
            if (Date.now() - heldFrom < 2500) {
                requestAnimationFrame(holdThenLock);
                return;
            }
            applyWidthZoom();
            requestAnimationFrame(function() {
                requestAnimationFrame(function() {
                    if (!stillThisFit()) return;
                    step2_height(0);
                });
            });
        }
        holdThenLock();
        return true;
    }

    function step2_height(tries) {
        if (!stillThisFit()) return;
        var el = document.querySelector('.chart-part');
        if (!el) return;
        var svg = el.querySelector('svg');
        var maxY = 0;
        if (svg) {
            svg.querySelectorAll('[y]').forEach(function(n) {
                var v = parseFloat(n.getAttribute('y'));
                if (!isNaN(v) && v > maxY) maxY = v;
            });
        }
        if (!svg || maxY <= 0) {
            if ((tries || 0) < 20) {
                setTimeout(function() {
                    if (!stillThisFit()) return;
                    applyWidthZoom();
                    step2_height((tries || 0) + 1);
                }, 250);
            }
            return;
        }

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
        document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
        var needed = target + 16;
        try {
            var y = 0;
            var n = el;
            while (n) {
                y += n.offsetTop || 0;
                n = n.offsetParent;
            }
            needed = y + target + 16;
        } catch (e2) {}
        var hEl = document.documentElement;
        var bEl = document.body;
        hEl.style.setProperty('height', needed + 'px', 'important');
        hEl.style.setProperty('min-height', needed + 'px', 'important');
        bEl.style.setProperty('height', needed + 'px', 'important');
        bEl.style.setProperty('min-height', needed + 'px', 'important');
        bEl.style.setProperty('overflow-y', 'visible', 'important');

        if (!stillThisFit()) return;
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

    /**
     * Sebere stav grafu pro [tools/chart-fit-simulator] — JSON nebo null.
     * Volá se z menu ⋮ → „Stav grafu (simulátor)“.
     */
    const val SNAPSHOT_JS = """
(function() {
    function num(v) {
        var n = parseFloat(v);
        return isNaN(n) ? null : n;
    }
    function readZoom(node) {
        var z = '';
        try { z = (node && node.style.zoom) || ''; } catch (e) {}
        if (!z && node) {
            try { z = getComputedStyle(node).zoom || ''; } catch (e2) {}
        }
        if (!z || z === 'normal') return 1;
        var n = parseFloat(String(z));
        return (n > 0 && n <= 3) ? n : 1;
    }
    var el = document.querySelector('.chart-part');
    if (!el) return null;
    var svg = el.querySelector('svg');
    var cs = svg ? getComputedStyle(svg) : null;
    var maxY = 0;
    if (svg) {
        svg.querySelectorAll('[y]').forEach(function(n) {
            var v = parseFloat(n.getAttribute('y'));
            if (!isNaN(v) && v > maxY) maxY = v;
        });
    }
    var clipRect = null;
    var boundaryBottomY = null;
    if (svg) {
        clipRect = svg.querySelector('[id*="boundaries-clip-path"] rect') ||
            svg.querySelector('clipPath rect');
        svg.querySelectorAll('g.boundaries [y], g.boundaries line').forEach(function(n) {
            var ys = [n.getAttribute('y'), n.getAttribute('y1'), n.getAttribute('y2')];
            for (var i = 0; i < ys.length; i++) {
                var v = num(ys[i]);
                if (v != null && (boundaryBottomY == null || v > boundaryBottomY)) {
                    boundaryBottomY = v;
                }
            }
        });
    }
    var rect = el.getBoundingClientRect();
    var svgRect = svg ? svg.getBoundingClientRect() : null;
    var styleH = String(el.getAttribute('style') || '').match(/height\s*:\s*([^;!]+)/i);
    var ppm = cs ? cs.getPropertyValue('--pxPerMeter').trim() : '';
    var yOff = cs ? cs.getPropertyValue('--yOffset').trim() : '';
    return JSON.stringify({
        v: 1,
        ts: Date.now(),
        url: location.href,
        viewport: {
            innerWidth: window.innerWidth,
            innerHeight: window.innerHeight,
            bodyZoom: readZoom(document.body),
            htmlZoom: readZoom(document.documentElement),
            scrollWidth: document.documentElement.scrollWidth
        },
        chartPart: {
            clientWidth: el.clientWidth,
            clientHeight: el.clientHeight,
            offsetWidth: el.offsetWidth,
            offsetHeight: el.offsetHeight,
            scrollHeight: el.scrollHeight,
            lockedHeightPx: styleH ? num(styleH[1]) : null,
            rectWidth: rect.width,
            rectHeight: rect.height
        },
        svg: svg ? {
            widthAttr: svg.getAttribute('width'),
            heightAttr: svg.getAttribute('height'),
            clientWidth: svg.clientWidth,
            clientHeight: svg.clientHeight,
            rectWidth: svgRect ? svgRect.width : null,
            rectHeight: svgRect ? svgRect.height : null,
            pxPerMeter: ppm || null,
            yOffset: yOff || null,
            maxYAttr: maxY > 0 ? maxY : null,
            clipPathHeight: clipRect ? num(clipRect.getAttribute('height')) : null,
            clipPathWidth: clipRect ? num(clipRect.getAttribute('width')) : null,
            boundaryBottomY: boundaryBottomY,
            chartFitDone: !!window.__chartFitDone
        } : null
    });
})();
"""

    /** Zkopíruje snapshot do schránky přes [ObalkaChartFit.onSnapshot]. */
    const val COPY_SNAPSHOT_JS = """
(function() {
    try {
        var json = ($SNAPSHOT_JS);
        if (!json) return 'no-chart';
        if (window.ObalkaChartFit && window.ObalkaChartFit.onSnapshot) {
            window.ObalkaChartFit.onSnapshot(json);
            return 'ok';
        }
        return 'no-bridge';
    } catch (e) {
        return 'error:' + (e && e.message ? e.message : String(e));
    }
})();
"""
}

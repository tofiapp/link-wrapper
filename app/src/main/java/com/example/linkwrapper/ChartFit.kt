package com.example.linkwrapper

/**
 * Graf: schovat `.chart-part`, srovnat šířku, zamknout výšku, teprve pak ukázat.
 *
 * Celé html se neschovává (to bránilo načtení). Háčky na výšku jdou až
 * když `.chart-part` existuje, ne na document-start.
 *
 * ## Srovnání na šířku — neměnit, na tabletu sedí
 *
 * 1. Zruší se `zoom` na `html` i `body`, ať `scrollWidth` je skutečná
 *    šířka obsahu, ne už zmenšená.
 * 2. `contentW` = `document.documentElement.scrollWidth` (stránka včetně
 *    grafu).
 * 3. `winW` = `window.innerWidth` (viditelná plocha WebView).
 * 4. `zoom = winW / contentW`, strop 1 — jen se zmenšuje, nikdy
 *    nezvětšuje (široký graf se vejde, úzký zůstane 1:1).
 * 5. Ten zoom jde na **body**. Velikost stránek z ⋮ (`PageZoom`) je na
 *    **html**. Kdyby byly oba na stejném prvku, Chromium by je násobil
 *    (84 % × fit).
 * 6. `overflow-x: hidden`, `overflow-y: auto` — pryč vodorovný posun,
 *    svislý zůstane.
 *
 * Výška je jiný problém: stránka po prvním snímku srazí `.chart-part`
 * (celý graf → pryč). Proto se graf drží neviditelný, výška se jen
 * zvedá, a odhalí se až po zámku.
 */
internal object ChartFit {

    const val HIDE_JS = """
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
if (window.__chartFitDomObs) { window.__chartFitDomObs.disconnect(); window.__chartFitDomObs = null; }
window.__chartFitDone = false;
window.__chartFitFloorH = 0;
window.__chartFitDmId = null;
$HIDE_JS
"""

    /**
     * Document-start u `dmId` (i po SPA `pushState`): schová `.chart-part`
     * hned, ať první snímek není celý graf. Nečeká na FIT.
     */
    val BOOTSTRAP_JS = """
(function(){
  function isChart(){
    try { return /(?:^|[?&])dmId=/i.test(location.search || ''); } catch (e) { return false; }
  }
  function hide(){
    if (document.getElementById('__chartFitHideStyle')) return;
    var s = document.createElement('style');
    s.id = '__chartFitHideStyle';
    s.textContent = '.chart-part{opacity:0 !important;}';
    (document.documentElement || document.head).appendChild(s);
  }
  function maybe(){ if (isChart()) hide(); }
  maybe();
  try {
    var push = history.pushState;
    history.pushState = function(){
      var r = push.apply(this, arguments);
      maybe();
      return r;
    };
    var replace = history.replaceState;
    history.replaceState = function(){
      var r = replace.apply(this, arguments);
      maybe();
      return r;
    };
    window.addEventListener('popstate', maybe);
  } catch (e) {}
})();
"""

    const val FIT_JS = """
(function() {
    function isChartUrl() {
        try { return /(?:^|[?&])dmId=/i.test(location.search || ''); } catch (e) { return false; }
    }
    function currentDmId() {
        try {
            var m = (location.search || '').match(/[?&]dmId=([^&]*)/i);
            return m ? m[1] : '';
        } catch (e) { return ''; }
    }
    function hideChart() {
        if (document.getElementById('__chartFitHideStyle')) return;
        var s = document.createElement('style');
        s.id = '__chartFitHideStyle';
        s.textContent = '.chart-part{opacity:0 !important;}';
        try { (document.documentElement || document.head).appendChild(s); } catch (e) {}
    }
    function showChart() {
        var s = document.getElementById('__chartFitHideStyle');
        if (s && s.parentNode) s.parentNode.removeChild(s);
    }

    var dm = currentDmId();
    if (window.__chartFitDmId && window.__chartFitDmId !== dm) {
        window.__chartFitDone = false;
        window.__chartFitFloorH = 0;
    }
    window.__chartFitDmId = dm;
    if (window.__chartFitDone) return;
    if (isChartUrl()) hideChart();

    function isChartPart(el) {
        return !!(el && el.nodeType === 1 && el.classList && el.classList.contains('chart-part'));
    }

    function parseHeight(css) {
        var m = String(css == null ? '' : css).match(/(?:^|;)\s*height\s*:\s*([^;!]+)/i);
        return m ? parseFloat(m[1]) : NaN;
    }

    function svgMaxY(el) {
        var svg = el.querySelector && el.querySelector('svg');
        if (!svg) return 0;
        var maxY = 0;
        svg.querySelectorAll('[y]').forEach(function(n) {
            var v = parseFloat(n.getAttribute('y'));
            if (!isNaN(v) && v > maxY) maxY = v;
        });
        return maxY;
    }

    function raiseFloor(el, fromCss) {
        var floor = window.__chartFitFloorH || 0;
        var requested = parseHeight(fromCss != null ? fromCss : (el.getAttribute && el.getAttribute('style')));
        if (!isNaN(requested) && requested > floor) floor = requested;
        var svgH = svgMaxY(el);
        if (svgH > 0 && (svgH + 40) > floor) floor = svgH + 40;
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
    window.__chartFitStyled = window.__chartFitStyled || new WeakSet();

    function applyLocked(el, fromCss) {
        if (!isChartPart(el) || window.__chartFitApplying.has(el)) return false;
        var h = raiseFloor(el, fromCss);
        if (h <= 0) return false;
        window.__chartFitApplying.add(el);
        try {
            var proto = window.__chartFitSetAttr || Element.prototype.setAttribute;
            proto.call(el, 'style', mergeLocked(fromCss != null ? fromCss : el.getAttribute('style'), h));
            try {
                document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
                document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
                document.documentElement.style.setProperty('height', 'auto', 'important');
                document.body && document.body.style.setProperty('height', 'auto', 'important');
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
    }

    function remember(el) {
        if (!isChartPart(el)) return;
        try { window.__chartFitStyleToEl.set(el.style, el); } catch (e) {}
        if (window.__chartFitStyled.has(el)) {
            applyLocked(el);
            if (window.__chartFitLockObs) {
                try { window.__chartFitLockObs.observe(el, { attributes: true, attributeFilter: ['style'] }); } catch (e) {}
            }
            return;
        }
        window.__chartFitStyled.add(el);
        try {
            var styleObj = el.style;
            var origSetProperty = styleObj.setProperty.bind(styleObj);
            var origRemoveProperty = styleObj.removeProperty.bind(styleObj);
            styleObj.setProperty = function(prop, value, priority) {
                if (prop === 'height' || prop === 'min-height' || prop === 'max-height' || prop === 'overflow') {
                    applyLocked(el);
                    return;
                }
                return origSetProperty(prop, value, priority);
            };
            styleObj.removeProperty = function(prop) {
                if (prop === 'height' || prop === 'min-height' || prop === 'max-height' || prop === 'overflow') {
                    applyLocked(el);
                    return '';
                }
                return origRemoveProperty(prop);
            };
            Object.defineProperty(styleObj, 'height', {
                configurable: true,
                get: function() { return (window.__chartFitFloorH || 0) + 'px'; },
                set: function() { applyLocked(el); }
            });
            Object.defineProperty(styleObj, 'cssText', {
                configurable: true,
                get: function() { return el.getAttribute('style') || ''; },
                set: function(v) { applyLocked(el, v); }
            });
        } catch (e) {}
        applyLocked(el);
        if (window.__chartFitLockObs) {
            try { window.__chartFitLockObs.observe(el, { attributes: true, attributeFilter: ['style'] }); } catch (e) {}
        }
    }

    if (!window.__chartFitLockObs) {
        window.__chartFitLockObs = new MutationObserver(function(records) {
            for (var i = 0; i < records.length; i++) {
                var t = records[i].target;
                if (isChartPart(t) && !window.__chartFitApplying.has(t)) applyLocked(t);
            }
        });
    }

    /**
     * Šířka — neměnit. Nejdřív čistý zoom, pak scrollWidth / innerWidth,
     * výsledek max 1 na body. Viz komentář u ChartFit.
     */
    function step1_zoom() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;
        hideChart();

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
    var readyAt = 0;
    var locking = false;

    function finishLocked() {
        if (locking) return;
        var el = document.querySelector('.chart-part');
        if (el) remember(el);
        if (!(window.__chartFitFloorH > 0)) {
            showChart();
            window.__chartFitDone = true;
            return;
        }
        locking = true;
        window.__chartFitDone = true;
        showChart();
    }

    function step2_height() {
        if (window.__chartFitDone) return;
        var el = document.querySelector('.chart-part');
        if (el) remember(el);
        var floor = window.__chartFitFloorH || 0;
        if (floor > 0 && !readyAt) readyAt = Date.now();
        var waited = Date.now() - waitStarted;
        var settled = readyAt && (Date.now() - readyAt > 400);
        if (settled || waited > 2500) {
            finishLocked();
            return;
        }
        requestAnimationFrame(step2_height);
    }

    if (!step1_zoom()) {
        if (!isChartUrl()) {
            showChart();
            return;
        }
        var root = document.body || document.documentElement;
        if (!root) {
            showChart();
            return;
        }
        if (!window.__chartFitDomObs) {
            window.__chartFitDomObs = new MutationObserver(function() {
                document.querySelectorAll('.chart-part').forEach(remember);
                if (!window.__chartFitDone) step1_zoom();
            });
            window.__chartFitDomObs.observe(root, { childList: true, subtree: true });
        }
        setTimeout(function() {
            if (window.__chartFitDomObs) {
                window.__chartFitDomObs.disconnect();
                window.__chartFitDomObs = null;
            }
            if (!window.__chartFitDone) {
                step1_zoom();
                finishLocked();
            }
        }, 20000);
    }
})();
"""
}

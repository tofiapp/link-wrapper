package com.example.linkwrapper

/**
 * Graf: krátce schovat `.chart-part`, srovnat šířku, zamknout výšku, hned ukázat.
 *
 * Čekat na SVG **pod** `opacity:0` nejde — knihovna do neviditelného
 * kontejneru graf nenakreslí, zámek se nedočká a stránka zůstane bez
 * grafu. Odhalení je proto po dvou snímcích (jako dřív), ne po SVG.
 * Výška se po odhalení dál jen zvedá, ať stránka graf znovu nesrazí.
 *
 * Celé html se neschovává. Háčky na výšku až když `.chart-part` existuje.
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
if (window.__chartFitFailsafe) { clearTimeout(window.__chartFitFailsafe); window.__chartFitFailsafe = null; }
window.__chartFitDone = false;
window.__chartFitSettling = false;
window.__chartFitFloorH = 0;
window.__chartFitDmId = null;
$HIDE_JS
"""

    /**
     * Document-start u `dmId` (i po SPA `pushState`): schová `.chart-part`
     * před prvním snímkem. FIT ho zase ukáže — nesmí čekat na SVG.
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
        if (window.__chartFitFailsafe) {
            clearTimeout(window.__chartFitFailsafe);
            window.__chartFitFailsafe = null;
        }
    }

    var dm = currentDmId();
    if (window.__chartFitDmId && window.__chartFitDmId !== dm) {
        window.__chartFitDone = false;
        window.__chartFitSettling = false;
        window.__chartFitFloorH = 0;
    }
    window.__chartFitDmId = dm;
    if (window.__chartFitDone) return;
    if (window.__chartFitSettling) return;
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

    window.__chartFitApplying = window.__chartFitApplying || new WeakSet();

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
    }

    function remember(el) {
        if (!isChartPart(el)) return;
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

    function finishFit() {
        var el = document.querySelector('.chart-part');
        if (el) remember(el);
        window.__chartFitDone = true;
        window.__chartFitSettling = false;
        showChart();
        var t0 = Date.now();
        function hold() {
            var part = document.querySelector('.chart-part');
            if (part) remember(part);
            if (Date.now() - t0 < 2000) requestAnimationFrame(hold);
        }
        requestAnimationFrame(hold);
    }

    /**
     * Šířka — neměnit. Nejdřív čistý zoom, pak scrollWidth / innerWidth,
     * výsledek max 1 na body. Viz komentář u ChartFit.
     */
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
            requestAnimationFrame(finishFit);
        });
        return true;
    }

    if (isChartUrl() && !window.__chartFitFailsafe) {
        window.__chartFitFailsafe = setTimeout(function() {
            window.__chartFitFailsafe = null;
            if (!window.__chartFitDone) finishFit();
        }, 1200);
    }

    window.__chartFitSettling = true;
    if (!step1_zoom()) {
        window.__chartFitSettling = false;
        if (!isChartUrl()) {
            showChart();
            window.__chartFitDone = true;
            return;
        }
        var root = document.body || document.documentElement;
        if (!root) {
            showChart();
            window.__chartFitDone = true;
            return;
        }
        if (!window.__chartFitDomObs) {
            window.__chartFitDomObs = new MutationObserver(function() {
                if (window.__chartFitDone || window.__chartFitSettling) return;
                if (step1_zoom()) {
                    window.__chartFitSettling = true;
                    window.__chartFitDomObs.disconnect();
                    window.__chartFitDomObs = null;
                }
            });
            window.__chartFitDomObs.observe(root, { childList: true, subtree: true });
        }
    }
})();
"""
}

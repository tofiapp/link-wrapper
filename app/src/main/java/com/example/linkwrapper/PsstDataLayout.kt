package com.example.linkwrapper

/**
 * Zkušební APK: na PSST Data jsou tři flex karty (`grid-rows-3`).
 * Na šířku se smrskne hlavně `[data-slot=card-content]` (tabulka),
 * hlavička karty drží. V landscape dvě karty vedle sebe a třetí pod nimi
 * přes celou šířku. Grid má danou výšku a content bere zbytek s interním
 * scrollem.
 */
internal object PsstDataLayout {

    const val APPLY_JS = """
(function() {
    var STYLE_ID = 'obalka-psst-data-landscape';
    var ATTR = 'data-obalka-psst-grid';
    function landscape() {
        return window.innerWidth > window.innerHeight;
    }
    function grids() {
        return document.querySelectorAll('div.grid[class*="grid-rows-3"]');
    }
    function clear() {
        var el = document.getElementById(STYLE_ID);
        if (el) el.remove();
        grids().forEach(function(g) {
            g.style.removeProperty('height');
            g.style.removeProperty('max-height');
            g.removeAttribute(ATTR);
        });
        document.querySelectorAll('[' + ATTR + ']').forEach(function(g) {
            g.style.removeProperty('height');
            g.style.removeProperty('max-height');
            g.removeAttribute(ATTR);
        });
    }
    function apply() {
        if (!landscape()) {
            clear();
            return;
        }
        var el = document.getElementById(STYLE_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = STYLE_ID;
            (document.head || document.documentElement).appendChild(el);
        }
        el.textContent =
            'div.grid[class*="grid-rows-3"]{' +
            'grid-template-columns:repeat(2,minmax(0,1fr))!important;' +
            'grid-template-rows:minmax(0,1fr) minmax(0,1fr)!important;' +
            'gap:8px!important;' +
            'min-height:0!important;' +
            '}' +
            'div.grid[class*="grid-rows-3"]>:nth-child(3){' +
            'grid-column:1/-1!important;' +
            '}' +
            'div.grid[class*="grid-rows-3"]>[data-slot="card"]{' +
            'min-height:0!important;' +
            'height:100%!important;' +
            'max-height:100%!important;' +
            'display:flex!important;' +
            'flex-direction:column!important;' +
            'overflow:hidden!important;' +
            '}' +
            'div.grid[class*="grid-rows-3"]>[data-slot="card"]>[data-slot="card-header"]{' +
            'flex:0 0 auto!important;' +
            '}' +
            'div.grid[class*="grid-rows-3"]>[data-slot="card"]>[data-slot="card-content"]{' +
            'flex:1 1 0%!important;' +
            'min-height:0!important;' +
            'overflow:auto!important;' +
            '}';
        grids().forEach(function(grid) {
            grid.setAttribute(ATTR, '1');
            var top = grid.getBoundingClientRect().top;
            var h = Math.floor(window.innerHeight - top + 32);
            if (h < 240) h = 240;
            grid.style.setProperty('height', h + 'px', 'important');
            grid.style.setProperty('max-height', h + 'px', 'important');
        });
    }
    apply();
    if (!window.__obalkaPsstLayoutBound) {
        window.__obalkaPsstLayoutBound = true;
        window.addEventListener('resize', apply);
        window.addEventListener('orientationchange', apply);
    }
})();
"""
}

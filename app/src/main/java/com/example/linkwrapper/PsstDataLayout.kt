package com.example.linkwrapper

/**
 * PSST Data: `div.grid.flex-1.grid-rows-3` se třemi `[data-slot=card]`.
 * Stránka počítá s flex výškou rodiče; po zoomu obálky (`html { zoom }`)
 * `flex-1` nevyplní displej. Grid proto dostane výšku z visualViewport
 * dělenou zoomem. V landscape dvě karty vedle sebe, třetí pod nimi.
 */
internal object PsstDataLayout {

    const val APPLY_JS = """
(function() {
    var STYLE_ID = 'obalka-psst-data-landscape';
    var ATTR = 'data-obalka-psst-grid';
    function landscape() {
        return window.innerWidth > window.innerHeight;
    }
    function readZoom() {
        var z = '';
        try { z = document.documentElement.style.zoom || ''; } catch (e) {}
        if (!z) {
            try { z = getComputedStyle(document.documentElement).zoom || ''; } catch (e2) {}
        }
        if (!z || z === 'normal') return 1;
        var s = String(z);
        var n = s.indexOf('%') >= 0 ? parseFloat(s) / 100 : parseFloat(s);
        return (n > 0.2 && n <= 3) ? n : 1;
    }
    function visHeight() {
        if (window.visualViewport && window.visualViewport.height)
            return window.visualViewport.height;
        return window.innerHeight;
    }
    function grids() {
        var list = [];
        document.querySelectorAll('div.grid.flex-1[class*="grid-rows-3"]').forEach(function(g) {
            list.push(g);
        });
        if (list.length) return list;
        document.querySelectorAll('div.grid[class*="grid-rows-3"]').forEach(function(g) {
            list.push(g);
        });
        if (list.length) return list;
        document.querySelectorAll('div.grid').forEach(function(g) {
            var cards = 0;
            for (var i = 0; i < g.children.length; i++) {
                if (g.children[i].getAttribute('data-slot') === 'card') cards++;
            }
            if (cards === 3) list.push(g);
        });
        return list;
    }
    function fillChain(grid, cssH) {
        var n = grid.parentElement;
        var guard = 0;
        while (n && n !== document.documentElement && guard < 12) {
            n.style.setProperty('min-height', '0', 'important');
            n.style.setProperty('overflow', 'hidden', 'important');
            n.style.setProperty('padding-bottom', '0px', 'important');
            n.style.setProperty('margin-bottom', '0px', 'important');
            if (n.tagName === 'BODY') break;
            n = n.parentElement;
            guard++;
        }
        var vis = visHeight();
        var zoom = readZoom();
        var bodyH = Math.floor(vis / zoom);
        try {
            document.documentElement.style.setProperty('height', bodyH + 'px', 'important');
            document.documentElement.style.setProperty('overflow', 'hidden', 'important');
            document.body.style.setProperty('height', bodyH + 'px', 'important');
            document.body.style.setProperty('min-height', '0', 'important');
            document.body.style.setProperty('overflow', 'hidden', 'important');
            document.body.style.setProperty('margin-bottom', '0', 'important');
        } catch (e) {}
        grid.style.setProperty('height', cssH + 'px', 'important');
        grid.style.setProperty('max-height', cssH + 'px', 'important');
        grid.style.setProperty('flex', '1 1 auto', 'important');
    }
    function apply() {
        var land = landscape();
        var el = document.getElementById(STYLE_ID);
        if (!el) {
            el = document.createElement('style');
            el.id = STYLE_ID;
            (document.head || document.documentElement).appendChild(el);
        }
        var cols = land
            ? 'grid-template-columns:repeat(2,minmax(0,1fr))!important;' +
              'grid-template-rows:minmax(0,1fr) minmax(0,1fr)!important;'
            : 'grid-template-columns:minmax(0,1fr)!important;' +
              'grid-template-rows:repeat(3,minmax(0,1fr))!important;';
        var third = land
            ? '[data-obalka-psst-grid]>:nth-child(3){grid-column:1/-1!important;}'
            : '[data-obalka-psst-grid]>:nth-child(3){grid-column:auto!important;}';
        el.textContent =
            '[data-obalka-psst-grid]{' +
            cols +
            'box-sizing:border-box!important;' +
            'gap:6px!important;' +
            'padding-top:4px!important;' +
            'padding-right:4px!important;' +
            'padding-bottom:4px!important;' +
            'padding-left:4px!important;' +
            'min-height:0!important;' +
            'margin:0!important;' +
            '}' +
            third +
            '[data-obalka-psst-grid]>[data-slot="card"]{' +
            'min-height:0!important;' +
            'height:100%!important;' +
            'max-height:100%!important;' +
            'display:flex!important;' +
            'flex-direction:column!important;' +
            'overflow:hidden!important;' +
            '}' +
            '[data-obalka-psst-grid]>[data-slot="card"]>[data-slot="card-header"]{' +
            'flex:0 0 auto!important;' +
            '}' +
            '[data-obalka-psst-grid]>[data-slot="card"]>[data-slot="card-content"]{' +
            'flex:1 1 0%!important;' +
            'min-height:0!important;' +
            'overflow:auto!important;' +
            '}';
        var zoom = readZoom();
        var vis = visHeight();
        grids().forEach(function(grid) {
            grid.setAttribute(ATTR, '1');
            var top = grid.getBoundingClientRect().top;
            var h = Math.floor((vis - top) / zoom);
            if (h < 320) h = 320;
            fillChain(grid, h);
        });
    }
    apply();
    if (!window.__obalkaPsstLayoutBound) {
        window.__obalkaPsstLayoutBound = true;
        window.addEventListener('resize', apply);
        window.addEventListener('orientationchange', apply);
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', apply);
        }
    }
})();
"""
}

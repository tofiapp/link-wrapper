package com.example.linkwrapper

/**
 * Zkušební APK: na PSST Data jsou tři flex karty (`grid-rows-3`).
 * Na výšku to sedí, na šířku se každá smrskne na třetinu nízkého
 * okna. V landscape proto tři sloupce vedle sebe — každá karta dostane
 * celou výšku, tabulky scrollují uvnitř.
 */
internal object PsstDataLayout {

    const val APPLY_JS = """
(function() {
    var ID = 'obalka-psst-data-landscape';
    function landscape() {
        return window.innerWidth > window.innerHeight;
    }
    function apply() {
        var el = document.getElementById(ID);
        if (!landscape()) {
            if (el) el.remove();
            return;
        }
        if (!el) {
            el = document.createElement('style');
            el.id = ID;
            (document.head || document.documentElement).appendChild(el);
        }
        el.textContent =
            'div.grid[class*="grid-rows-3"]{' +
            'grid-template-columns:repeat(3,minmax(0,1fr))!important;' +
            'grid-template-rows:minmax(0,1fr)!important;' +
            '}' +
            'div.grid[class*="grid-rows-3"]>[data-slot="card"]{' +
            'min-height:0!important;' +
            '}';
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

package com.example.linkwrapper

/**
 * Stránka s grafem má overflow:visible na html/body, takže obsah
 * přetéká, ale nejde scrollovat. [FIT_JS] jen zapne overflow:auto
 * a height:100 % na kořeni. Výšku `.chart-part` nemění.
 */
internal object ChartFit {

    const val FIT_JS = """
(function() {
    function fix() {
        var el = document.querySelector('.chart-part');
        if (!el) return false;

        document.documentElement.style.setProperty('overflow', 'auto', 'important');
        document.body.style.setProperty('overflow', 'auto', 'important');
        document.documentElement.style.setProperty('height', '100%', 'important');
        document.body.style.setProperty('height', '100%', 'important');

        return true;
    }

    if (!fix()) {
        var root = document.body || document.documentElement;
        if (!root) return;
        var obs = new MutationObserver(function() { if (fix()) obs.disconnect(); });
        obs.observe(root, { childList: true, subtree: true });
        setTimeout(function() { obs.disconnect(); }, 20000);
    }
})();
"""
}

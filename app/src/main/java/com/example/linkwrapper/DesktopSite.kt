package com.example.linkwrapper

/**
 * Stránky mají vypadat jako na PC, ne v tabletovém / mobilním rozložení.
 *
 * WebView jinak posílá Android UA (`Mobile`) a CSS vidí úzký displej —
 * DSD i PSST pak nasadí „blbou tabletovou formu“. Přepíšeme UA na Chrome
 * na Windows a na začátku dokumentu nastavíme viewport na desktopovou šířku.
 */
internal object DesktopSite {

    const val VIEWPORT_WIDTH = 1280

    fun userAgent(defaultUa: String?): String {
        val chrome = defaultUa
            ?.let { Regex("Chrome/[\\d.]+").find(it)?.value }
            ?: "Chrome/120.0.0.0"
        val webkit = defaultUa
            ?.let { Regex("AppleWebKit/[\\d.]+").find(it)?.value }
            ?: "AppleWebKit/537.36"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) $webkit " +
            "(KHTML, like Gecko) $chrome Safari/537.36"
    }

    /**
     * Běží na začátku dokumentu, dřív než CSS a skripty stránky.
     * Přepíše `width=device-width` (tablet) na pevnou PC šířku.
     */
    const val BOOTSTRAP_JS = """
(function(){
  var W = $VIEWPORT_WIDTH;
  function viewport(){
    try {
      var head = document.head || document.documentElement;
      if (!head) return;
      var m = document.querySelector('meta[name="viewport"]');
      if (!m) {
        m = document.createElement('meta');
        m.setAttribute('name', 'viewport');
        head.insertBefore(m, head.firstChild);
      }
      m.setAttribute('content', 'width=' + W + ', initial-scale=1');
    } catch (e) {}
  }
  viewport();
  document.addEventListener('DOMContentLoaded', viewport);

  try {
    Object.defineProperty(navigator, 'platform', {
      configurable: true,
      get: function() { return 'Win32'; }
    });
  } catch (e) {}
  try {
    var uad = navigator.userAgentData;
    if (uad && uad.mobile) {
      Object.defineProperty(navigator, 'userAgentData', {
        configurable: true,
        get: function() {
          return {
            mobile: false,
            platform: 'Windows',
            brands: uad.brands || []
          };
        }
      });
    }
  } catch (e) {}
})();
"""

    fun setJs(): String =
        "try{var m=document.querySelector('meta[name=\"viewport\"]');" +
            "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');" +
            "(document.head||document.documentElement).appendChild(m);}" +
            "m.setAttribute('content','width=$VIEWPORT_WIDTH, initial-scale=1');}catch(e){}"
}

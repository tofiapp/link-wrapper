package com.example.linkwrapper

/**
 * Desktopové zobrazení ve WebView.
 *
 * Zásadní změna oproti původní verzi: stránka se **nerenderuje na šířku
 * tabletu**, ale na pevnou desktopovou šířku [LAYOUT_WIDTH]. WebView ji pak
 * (díky useWideViewPort + loadWithOverviewMode) sám zmenší tak, aby se vešla
 * na obrazovku. Tím dostane desktopový layout přesně ten prostor, se kterým
 * počítal jeho autor — včetně grafů.
 *
 * Původní `width=device-width` znamenal, že se desktopová stránka mačkala do
 * ~800 CSS px a rozvržení se rozpadalo.
 */
internal object DesktopSite {

    /** Šířka, na kterou se stránka vykresluje. Odpovídá běžnému monitoru. */
    const val LAYOUT_WIDTH = 1280

    const val BOOTSTRAP_JS = """
(function(){
  if (window.__obalkaDesk) return;
  window.__obalkaDesk = 1;

  var W = $LAYOUT_WIDTH;

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
      // Pevná šířka rozvržení; zmenšení na displej řeší WebView.
      m.setAttribute('content', 'width=' + W + ', initial-scale=1, shrink-to-fit=yes');
    } catch (e) {}
  }

  function css(){
    try {
      if (document.getElementById('obalka-scroll')) return;
      var s = document.createElement('style');
      s.id = 'obalka-scroll';
      s.textContent = [
        'html,body{',
          'overflow-x:hidden!important;',
          'overflow-y:auto!important;',
          'max-height:none!important;',
          'height:auto!important;',
          'min-width:0!important;',
        '}',
        '.highcharts-container,.highcharts-root{',
          'max-width:none!important;',
        '}'
      ].join('');
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }

  /**
   * Roztažení už NEDĚLÁME naslepo přes všechny potomky body. Původní kód
   * nastavoval width:<clientWidth>px !important každému prvku, který byl užší
   * než viewport — tím rozbil sloupcové layouty i kontejnery grafů.
   * Nově jen uvolníme umělé stropy na hlavních obalech.
   */
  function relax(el){
    if (!el || !el.style) return;
    el.style.setProperty('max-width', 'none', 'important');
    el.style.setProperty('max-height', 'none', 'important');
    el.style.setProperty('height', 'auto', 'important');
  }

  window.__obalkaFill = function(){
    viewport();
    css();
    try {
      relax(document.documentElement);
      relax(document.body);
      if (document.forms && document.forms[0]) relax(document.forms[0]);
    } catch (e) {}
  };

  viewport();
  css();
  document.addEventListener('DOMContentLoaded', window.__obalkaFill);
  window.addEventListener('load', window.__obalkaFill);

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
          return { mobile: false, platform: 'Windows', brands: uad.brands || [] };
        }
      });
    }
  } catch (e) {}
})();
"""

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

    fun setJs(): String =
        "try{var m=document.querySelector('meta[name=\"viewport\"]');" +
            "if(!m){m=document.createElement('meta');m.setAttribute('name','viewport');" +
            "(document.head||document.documentElement).appendChild(m);}" +
            "m.setAttribute('content','width=$LAYOUT_WIDTH, initial-scale=1, shrink-to-fit=yes');" +
            "if(window.__obalkaFill)window.__obalkaFill();}catch(e){}"
}

package com.example.linkwrapper

/**
 * Desktopové menu (Chrome na Windows). Šířka = WebView, výška není
 * omezená na obrazovku — stránka může růst a jít scrollovat.
 */
internal object DesktopSite {

    const val BOOTSTRAP_JS = """
(function(){
  if (window.__obalkaDesk) return;
  window.__obalkaDesk = 1;

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
      m.setAttribute('content', 'width=device-width, initial-scale=1');
    } catch (e) {}
  }

  function css(){
    try {
      if (document.getElementById('obalka-scroll')) return;
      var s = document.createElement('style');
      s.id = 'obalka-scroll';
      s.textContent = [
        'html,body,form{',
          'overflow:visible!important;',
          'max-height:none!important;',
          'height:auto!important;',
          'width:100%!important;',
          'max-width:none!important;',
        '}'
      ].join('');
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }

  function stretch(el, w){
    if (!el || !el.style) return;
    el.style.setProperty('max-width', 'none', 'important');
    el.style.setProperty('width', w + 'px', 'important');
    el.style.setProperty('margin-left', '0', 'important');
    el.style.setProperty('margin-right', '0', 'important');
    el.style.setProperty('box-sizing', 'border-box', 'important');
    el.style.setProperty('height', 'auto', 'important');
    el.style.setProperty('max-height', 'none', 'important');
    el.style.setProperty('overflow', 'visible', 'important');
  }

  window.__obalkaFill = function(){
    viewport();
    css();
    try {
      var w = (document.documentElement && document.documentElement.clientWidth) || window.innerWidth || 0;
      if (w < 200) return;
      stretch(document.documentElement, w);
      stretch(document.body, w);
      var roots = [];
      if (document.body) roots.push(document.body);
      if (document.forms && document.forms[0]) roots.push(document.forms[0]);
      for (var r = 0; r < roots.length; r++) {
        var kids = roots[r].children || [];
        for (var i = 0; i < kids.length; i++) {
          var el = kids[i];
          var cw = el.offsetWidth || 0;
          if (cw > 40 && cw < w - 8) stretch(el, w);
        }
      }
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
            "m.setAttribute('content','width=device-width, initial-scale=1');" +
            "if(window.__obalkaFill)window.__obalkaFill();}catch(e){}"
}

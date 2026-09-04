package com.example.linkwrapper

/**
 * Stránky mají desktopové menu (Chrome na Windows), ale skládají se na
 * šířku WebView (`width=device-width`, měřítko 1). 1 CSS pixel = 1 dp,
 * stránka může být delší a jít scrollovat — bez smrskávání prvků.
 */
internal object DesktopSite {

    const val BOOTSTRAP_JS = """
(function(){
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
  function unclip(){
    try {
      if (document.getElementById('obalka-scroll')) return;
      var s = document.createElement('style');
      s.id = 'obalka-scroll';
      s.textContent = 'html,body,form{overflow:visible!important;max-height:none!important;}';
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }
  viewport();
  unclip();
  document.addEventListener('DOMContentLoaded', function(){ viewport(); unclip(); });

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
            "m.setAttribute('content','width=device-width, initial-scale=1');}catch(e){}"
}

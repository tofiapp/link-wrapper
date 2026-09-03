package com.example.linkwrapper

/**
 * Oddálení webu ve WebView. Na výšku je stránka o něco větší (snadnější
 * klepnutí), na šířku o něco menší (desktopové weby na širokém displeji
 * jinak působí přiblíženě).
 */
internal object PageZoom {

    const val PORTRAIT_PERCENT = 88
    const val LANDSCAPE_PERCENT = 80

    fun percent(landscape: Boolean): Int =
        if (landscape) LANDSCAPE_PERCENT else PORTRAIT_PERCENT

    fun applyJs(percent: Int): String {
        val z = "$percent%"
        return """
(function(){
  var z = '$z';
  function apply(){
    try { document.documentElement.style.zoom = z; } catch (e) {}
    try { if (document.body) document.body.style.zoom = z; } catch (e) {}
  }
  apply();
  document.addEventListener('DOMContentLoaded', apply);
})();
"""
    }

    fun setJs(percent: Int): String {
        val z = "$percent%"
        return "try{document.documentElement.style.zoom='$z';if(document.body)document.body.style.zoom='$z';}catch(e){}"
    }
}

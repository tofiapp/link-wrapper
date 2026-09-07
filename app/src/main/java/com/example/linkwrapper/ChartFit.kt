package com.example.linkwrapper

/**
 * Výška SVG grafu ve WebView. Šířka `.chart-part` se na tabletu už
 * přizpůsobí; výška zůstává na původní hodnotě ze stránky, takže se
 * scrolluje i když je v rodiči místo.
 *
 * [FIT_JS] nastaví jen inline výšku `.chart-part` podle rodiče
 * a šťouchne layout (1 px + `resize`), aby Highcharts překreslil
 * vnitřek. Zbytek stránky a šířka se nemění.
 */
internal object ChartFit {

    const val FIT_JS = """
(function(){
  try {
    var charts = document.querySelectorAll('.chart-part');
    charts.forEach(function(el) {
      var parent = el.parentElement;
      if (!parent) return;
      var targetHeight = parent.clientHeight;
      if (targetHeight <= 0) return;

      el.style.setProperty('height', targetHeight + 'px', 'important');

      var originalParentHeight = parent.style.height;
      parent.style.height = (parent.clientHeight - 1) + 'px';
      requestAnimationFrame(function() {
        parent.style.height = originalParentHeight || '';
        requestAnimationFrame(function() {
          window.dispatchEvent(new Event('resize'));
        });
      });
    });
  } catch (e) {}
})();
"""
}

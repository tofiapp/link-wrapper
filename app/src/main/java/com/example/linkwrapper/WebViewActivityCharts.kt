package com.example.linkwrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Message
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.system.exitProcess

internal fun WebViewActivity.shouldDeferChartFit(view: WebView): Boolean {
        if (prefetchViews.values.any { it === view }) return true
        return view.visibility != View.VISIBLE && view.parent === webContainer
    }

internal fun WebViewActivity.installChartPerfBootstrap(webView: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                pageZoomJs() + ChartPerf.BOOTSTRAP_JS + GeoTrack.BOOTSTRAP_JS,
                setOf("*")
            )
            chartPerfInjected.add(webView)
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.injectChartFit(webView: WebView, unlock: Boolean = false) {
        try {
            val js = if (unlock) ChartFit.RESET_JS + ChartFit.FIT_JS else ChartFit.FIT_JS
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.injectChartFitIntoAllTabs(unlock: Boolean = false) {
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            injectChartFit(wv, unlock)
        }
    }

internal fun WebViewActivity.injectPsstDataLayout(webView: WebView, url: String? = webView.url) {
        val resolved = url ?: tabs.find { it.webView === webView }?.url
        if (!Destinations.isPsstDataHome(resolved)) return
        try {
            webView.evaluateJavascript(PsstDataLayout.APPLY_JS, null)
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.injectPsstDataLayoutIntoAllTabs() {
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            injectPsstDataLayout(wv)
        }
    }

internal fun WebViewActivity.scheduleChartFit() {
        mainHandler.removeCallbacks(chartFitRunnable)
        mainHandler.postDelayed(chartFitRunnable, WebViewActivityConstants.CHART_FIT_DELAY_MS)
    }

internal fun WebViewActivity.injectChartPerfFallback(webView: WebView) {
        if (webView in chartPerfInjected) return
        try {
            webView.evaluateJavascript(
                pageZoomJs() + ChartPerf.BOOTSTRAP_JS + GeoTrack.BOOTSTRAP_JS,
                null
            )
            chartPerfInjected.add(webView)
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.pageZoomPercentFor(url: String?): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return PageZoom.percentFor(this, url, landscape)
    }

internal fun WebViewActivity.pageZoomJs(): String {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return PageZoom.pickerJs(
            PageZoom.percentFor(this, PageZoom.Kind.Psst, landscape)
        )
    }

internal fun WebViewActivity.applyPageZoomToAllTabs() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            val percent = PageZoom.percentFor(this, tab.url, landscape)
            wv.evaluateJavascript(PageZoom.setJs(percent), null)
        }
    }

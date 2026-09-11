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

/** Jedna karta — WebView, nebo nativní Domů bez WebView. */
private class BrowserTab(
    val id: Long,
    var webView: WebView?,
    var title: String,
    var url: String,
    var isHome: Boolean = false,
    var pinned: Boolean = false
)

private sealed class TabStripEntry {
    data class Single(val tab: BrowserTab) : TabStripEntry()
    data class FolderGroup(
        val folderId: String,
        val title: String,
        val tabs: List<BrowserTab>
    ) : TabStripEntry()
}

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_URL = Destinations.PSST_URL
        private const val MAX_TABS = 8
        private const val MAX_AUTH_ROUNDS = 16
        private const val LOGIN_TIMEOUT_MS = 15_000L
        private const val CHART_FIT_DELAY_MS = 300L
        private const val MAX_PREFETCH = 8
        private const val FOLDER_NONE = "Bez skupiny"
        private const val FOLDER_NEW = "Nová skupina…"
    }

    private enum class Gate { BROWSER, HOME, LOGIN }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webContainer: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView

    private lateinit var loginOverlay: View
    private lateinit var loginTitle: View
    private lateinit var loginFormColumn: View
    private lateinit var connectionBanner: View
    private lateinit var loginFormScroll: View
    private lateinit var certBanner: View
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var loginEphemeralHint: TextView

    private lateinit var homeOverlay: View
    private lateinit var homeAppList: LinearLayout
    private lateinit var homeBookmarkList: LinearLayout
    private lateinit var homeVersion: TextView

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: Long = -1L
    private var nextTabId = 1L

    private val activeTab: BrowserTab?
        get() = tabs.find { it.id == activeTabId }

    private val activeWebView: WebView?
        get() = activeTab?.webView

    private var gate = Gate.HOME
    private var lastContentGate = Gate.HOME
    private var dialogShown = false
    private var urlDialog: AlertDialog? = null
    private var warningDialog: AlertDialog? = null
    private var bookmarkPopup: PopupWindow? = null
    private var bookmarkLabelDialog: AlertDialog? = null
    private var actionDialog: AlertDialog? = null
    /** Po wipe na pozadí znovu připojit viditelný pinnutý WebView — jinak zbělá. */
    private var needsPinnedWebViewReveal = false

    /** Čekající HTTP auth, když uživatel právě vyplňuje formulář. */
    private var pendingAuthHandler: HttpAuthHandler? = null
    private var awaitingHttpAuth = false

    /** Údaje z formuláře, dokud je server neověří. Pak jdou do [Session]. */
    private var pendingCredentials: Credentials? = null
    private var verifyingLogin = false

    private var pendingStartUrl: String? = null
    private var pendingResumeUrl: String? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var trustProbeSeq = 0
    private var trustProbeInFlight = false
    private var lastTrustProbeAt = 0L
    private var lastTrustResult: DeviceTrust.Result? = null
    private var connectionBannerVisible = false
    /** Bez VPN/internetu: jen připnuté karty, ovládání lišty vypnuté. */
    private var chromeOffline = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vpnCheckRunnable = Runnable { refreshConnectionBanner() }
    private val loginTimeoutRunnable = Runnable {
        if (verifyingLogin) failLogin("Přihlášení vypršelo. Zkuste to znovu.")
    }
    private val chartFitRunnable = Runnable { injectChartFitIntoAllTabs(unlock = true) }

    private val authChallengeCounts = IdentityHashMap<WebView, MutableMap<String, Int>>()
    private val authFailedViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())
    private val chartPerfInjected = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())
    private val prefetchViews = LinkedHashMap<String, WebView>()
    private val prefetchQueue = ArrayDeque<String>()
    private var prefetchLoading = false
    private val prefetchRunnable = Runnable { pumpBookmarkPrefetch() }
    private val folderTabFocus = mutableMapOf<String, Long>()

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var geoWatchCount = 0
    private var geoManager: LocationManager? = null
    private val geoBridge = GeoBridge()
    private val geoListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            pushGeoToTabs(location)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        finishGeolocationRequest(allowed)
    }

    private val authProbeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (!verifyingLogin) return@registerForActivityResult
        if (result.resultCode == RESULT_OK) {
            succeedLogin()
        } else {
            failLogin(result.data?.getStringExtra(AuthProbeActivity.EXTRA_ERROR))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_webview)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.navigationIcon = null
        toolbar.title = null
        toolbar.setContentInsetsAbsolute(0, 0)

        progressBar = findViewById(R.id.progressBar)
        webContainer = findViewById(R.id.webContainer)
        webContainer.setBackgroundColor(Color.TRANSPARENT)
        webContainer.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (gate != Gate.BROWSER) return@addOnLayoutChangeListener
            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return@addOnLayoutChangeListener
            if (w == oldRight - oldLeft && h == oldBottom - oldTop) return@addOnLayoutChangeListener
            scheduleChartFit()
        }
        tabStrip = findViewById(R.id.tabStrip)
        tabScroll = findViewById(R.id.tabScroll)
        bindLoginUi()
        bindHomeUi()
        bindImeInsets()

        CookieManager.getInstance().setAcceptCookie(true)
        WebView.setWebContentsDebuggingEnabled(false)
        Session.dropSharedHttpAuthOnce(this)
        if (TrialSettings.ephemeralLogin()) Session.forgetDisk(this)

        pendingStartUrl = explicitUrlFromIntent(intent)
        TrialPins.dropDisk(this)
        refreshGate()
        refreshConnectionBanner()
    }

    override fun onStart() {
        super.onStart()
        consumeTrialIdleTimeout()
        if (isFinishing) return
        registerVpnMonitor()
        scheduleVpnCheck()
        maybePromptLoginForActiveUnpinnedTab()
    }

    override fun onResume() {
        super.onResume()
        val tab = activeTab
        val wv = tab?.webView
        if (tab != null && !tab.isHome && wv != null && gate == Gate.BROWSER &&
            (needsPinnedWebViewReveal || wv.parent == null)
        ) {
            needsPinnedWebViewReveal = false
            revealActiveBrowserWebView(wv)
            syncGeoUpdates()
            return
        }
        needsPinnedWebViewReveal = false
        (activeWebView ?: tabs.firstNotNullOfOrNull { it.webView })?.resumeTimers()
        activeWebView?.onResume()
        syncGeoUpdates()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPageZoomToAllTabs()
        dismissBookmarkPopup()
        dismissActionSheet()
        injectPsstDataLayoutIntoAllTabs()
        // Zámek zmizí s elementem; odemknout a znovu fitnout po ~300 ms.
        scheduleChartFit()
    }

    override fun onPause() {
        dismissBookmarkPopup()
        dismissActionSheet()
        tabs.forEach { it.webView?.onPause() }
        tabs.firstNotNullOfOrNull { it.webView }?.pauseTimers()
        stopGeoUpdates()
        super.onPause()
    }

    override fun onStop() {
        if (!isFinishing && TrialSettings.isTrial() && !verifyingLogin) {
            TrialIdle.markBackground(this)
            endTrialSessionKeepTabs()
        }
        unregisterVpnMonitor()
        mainHandler.removeCallbacks(vpnCheckRunnable)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dialogShown = false

        val url = explicitUrlFromIntent(intent)
        if (url == null) {
            if (tabs.isEmpty() && gate == Gate.BROWSER) {
                presentHome()
            }
            return
        }
        pendingResumeUrl = url
        pendingStartUrl = url
        if (verifyingLogin) {
            return
        }
        refreshGate()
    }

    private fun openUrlFromExternal(url: String) {
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            selectTab(existing.id)
            return
        }
        if (tabs.size >= MAX_TABS) {
            loadInActiveTab(url)
            return
        }
        openInNewTab(url)
    }

    private fun samePage(a: String, b: String): Boolean {
        fun norm(raw: String): String {
            val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
            val path = (u.path ?: "").trimEnd('/')
            val q = u.encodedQuery ?: ""
            return "${u.scheme}://${u.host}$path?$q"
        }
        return norm(a).equals(norm(b), ignoreCase = true)
    }

    override fun onDestroy() {
        unregisterVpnMonitor()
        mainHandler.removeCallbacks(vpnCheckRunnable)
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        mainHandler.removeCallbacks(chartFitRunnable)
        mainHandler.removeCallbacks(prefetchRunnable)
        dismissBookmarkPopup()
        bookmarkLabelDialog?.dismiss()
        bookmarkLabelDialog = null
        dismissActionSheet()
        trustProbeSeq++
        trustProbeInFlight = false
        AuthProbe.kill(this)
        destroyPrefetchViews()
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        stopGeoUpdates()
        super.onDestroy()
    }

    /**
     * Domů i bez relace; přihlášení k PSST až po dlaždici.
     * Výpadek VPN/sítě neschová obrazovku — jen pruh dole.
     */
    private fun refreshGate() {
        if (isFinishing) return
        refreshConnectionBanner()
        if (verifyingLogin) {
            presentLogin()
            return
        }
        val open = pendingResumeUrl ?: pendingStartUrl
        if (open != null && needsAppLogin(open)) {
            presentLogin()
            return
        }
        pendingResumeUrl = null
        if (open != null) {
            pendingStartUrl = null
            enterBrowser()
            openUrlFromExternal(open)
            return
        }
        if (lastContentGate == Gate.BROWSER) {
            presentBrowser()
            return
        }
        presentHome()
    }

    private fun needsAppLogin(url: String): Boolean {
        if (Session.isActive(this)) return false
        return Destinations.forUrl(url)?.requiresAppLogin == true
    }

    private fun presentLogin() {
        gate = Gate.LOGIN
        progressBar.visibility = View.GONE
        loginOverlay.visibility = View.VISIBLE
        loginOverlay.bringToFront()
        raiseConnectionBanner()
        loginFormColumn.visibility = View.VISIBLE
        loginFormScroll.visibility = View.VISIBLE
        loginTitle.visibility = View.VISIBLE
        refreshCertBanner()
        loginEphemeralHint.visibility =
            if (TrialSettings.ephemeralLogin()) View.VISIBLE else View.GONE
        updateLoginButton()
        if (!verifyingLogin && loginButton.isEnabled) {
            if (usernameInput.text.isNullOrEmpty()) usernameInput.requestFocus()
            else passwordInput.requestFocus()
        }
        attachImeLayoutListener(true)
        setSensitiveScreen(true)
    }

    private fun presentHome() {
        restorePinnedTabs()
        if (tabs.isEmpty() || activeTab == null) {
            openNewHomeTab()
            return
        }
        if (activeTab?.isHome == true) {
            selectTab(activeTab!!.id)
            return
        }
        val existing = tabs.firstOrNull { it.isHome }
        if (existing != null) selectTab(existing.id)
        else openNewHomeTab()
    }

    private fun enterBrowser() {
        hideLoginOverlay()
        attachImeLayoutListener(false)
        setSensitiveScreen(false)
    }

    private fun presentBrowser() {
        if (!verifyingLogin) hideLoginOverlay()
        attachImeLayoutListener(false)
        setSensitiveScreen(false)
        if (tabs.isEmpty()) {
            restoreSavedTabsOrStart()
        }
        restorePinnedTabs()
        if (tabs.isEmpty()) {
            openNewHomeTab()
            return
        }
        val target = activeTab ?: tabs.last()
        selectTab(target.id)
    }

    private fun restoreSavedTabsOrStart() {
        val open = pendingResumeUrl ?: pendingStartUrl
        pendingResumeUrl = null
        pendingStartUrl = null
        if (!open.isNullOrBlank() && open != Destinations.HOME_URL) {
            openInNewTab(open)
        } else if (tabs.isEmpty()) {
            openNewHomeTab()
        }
    }

    /** Domeček: aktuální karta (PSST, graf) se změní na Domů. */
    private fun openHomeWindow() {
        val tab = activeTab
        if (tab?.pinned == true) {
            val home = tabs.firstOrNull { it.isHome }
            if (home != null) selectTab(home.id)
            else openNewHomeTab()
            return
        }
        convertActiveTabToHome()
    }

    private fun convertActiveTabToHome() {
        val tab = activeTab
        if (tab == null) {
            openNewHomeTab()
            return
        }
        if (tab.pinned) {
            openHomeWindow()
            return
        }
        if (tab.isHome) {
            selectTab(tab.id)
            return
        }
        destroyWebView(tab)
        hideKeyboard()
        tab.isHome = true
        tab.pinned = false
        tab.title = "Domů"
        tab.url = Destinations.HOME_URL
        selectTab(tab.id)
    }

    private fun openNewHomeTab() {
        if (tabs.size >= MAX_TABS) {
            val existing = tabs.firstOrNull { it.isHome }
            if (existing != null) {
                selectTab(existing.id)
                return
            }
            Toast.makeText(this, "Maximum je $MAX_TABS karet", Toast.LENGTH_SHORT).show()
            return
        }
        val tab = BrowserTab(
            id = nextTabId++,
            webView = null,
            title = "Domů",
            url = Destinations.HOME_URL,
            isHome = true
        )
        val at = tabs.indexOfLast { it.isHome } + 1
        tabs.add(at.coerceAtLeast(0), tab)
        selectTab(tab.id)
    }

    // ── Karty ───────────────────────────────────────────────────────────

    private fun openInNewTab(url: String) {
        if (url == Destinations.HOME_URL) {
            openNewHomeTab()
            return
        }
        val existing = tabs.filter { it.pinned }.find { samePage(it.url, url) }
        if (existing != null) {
            selectTab(existing.id)
            return
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        val tab = addTabForUrl(url, select = true)
        if (tab == null && tabs.size >= MAX_TABS) {
            Toast.makeText(this, "Maximum je $MAX_TABS karet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTabForUrl(url: String, select: Boolean): BrowserTab? {
        if (url.isBlank() || url == Destinations.HOME_URL) return null
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            if (select) selectTab(existing.id)
            return existing
        }
        if (tabs.size >= MAX_TABS) return null
        val webView = takePrefetch(url) ?: createWebView()
        val tab = BrowserTab(
            id = nextTabId++,
            webView = webView,
            title = tabLabel(url),
            url = url
        )
        tabs.add(tab)
        persistOpenTabsFromTabs()
        if (select) selectTab(tab.id)
        if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
            TrialIsolation.onNavigate(this, url)
            webView.loadUrl(url)
        }
        return tab
    }

    private fun selectTab(tabId: Long) {
        val target = tabs.find { it.id == tabId } ?: return
        if (chromeOffline && !target.pinned) {
            val pin = tabs.firstOrNull { it.pinned }
            if (pin != null) {
                if (pin.id != tabId) selectTab(pin.id)
                return
            }
            if (activeTabId != -1L && tabId != activeTabId) return
        }
        if (tabId != activeTabId) hideKeyboard()
        activeTabId = tabId
        val promptLogin = shouldPromptLoginForTab(target)
        if (!verifyingLogin && !promptLogin) hideLoginOverlay()
        if (target.isHome) {
            gate = Gate.HOME
            lastContentGate = Gate.HOME
            hideKeyboard()
            progressBar.visibility = View.GONE
            if (::homeOverlay.isInitialized) {
                homeOverlay.visibility = View.VISIBLE
                homeOverlay.bringToFront()
            }
            populateHomeBookmarks()
            raiseConnectionBanner()
            tabs.forEach { tab ->
                val wv = tab.webView ?: return@forEach
                parkBackgroundWebView(wv, keepAlive = tab.pinned)
            }
            attachImeLayoutListener(false)
            setSensitiveScreen(false)
            refreshTabStrip()
            return
        }

        if (target.webView == null && !promptLogin) {
            val webView = takePrefetch(target.url) ?: createWebView()
            target.webView = webView
            TrialIsolation.onNavigate(this, target.url)
            webView.loadUrl(target.url)
        }

        gate = Gate.BROWSER
        lastContentGate = Gate.BROWSER
        hideHomeOverlay()
        attachImeLayoutListener(false)
        setSensitiveScreen(false)
        TrialIsolation.onNavigate(this, target.url)
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            val selected = tab.id == tabId
            if (selected) {
                if (wv.parent == null) {
                    webContainer.addView(
                        wv,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }
                wv.visibility = View.VISIBLE
                wv.onResume()
                wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            } else {
                parkBackgroundWebView(wv, keepAlive = tab.pinned)
            }
        }
        tabFolderId(target)?.let { folderTabFocus[it] = target.id }
        refreshTabStrip()
        if (promptLogin) {
            pendingStartUrl = target.url
            presentLogin()
        }
    }

    private fun parkBackgroundWebView(wv: WebView, keepAlive: Boolean = false) {
        wv.onPause()
        if (keepAlive) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        } else {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
    }

    /**
     * Po minimalizaci Chromium nechá odpojený (nebo i připojený) povrch bílý.
     * Stejný tah jako přepnutí karty pryč a zpět: znovu vložit do kontejneru.
     */
    private fun revealActiveBrowserWebView(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        webContainer.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        wv.visibility = View.VISIBLE
        wv.onResume()
        wv.resumeTimers()
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        wv.invalidate()
        scheduleChartFit()
        injectPsstDataLayout(wv)
        try {
            wv.evaluateJavascript(
                "try{window.dispatchEvent(new Event('resize'))}catch(e){}",
                null
            )
        } catch (_: Exception) {
        }
    }

    private fun closeTab(tabId: Long) {
        if (tabs.size <= 1) return
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closing = tabs[index]
        if (closing.pinned) return
        tabs.removeAt(index)
        destroyTab(closing)

        if (tabs.isEmpty()) {
            openNewHomeTab()
            return
        }
        if (activeTabId == tabId) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.last()
            selectTab(next.id)
        } else {
            refreshTabStrip()
        }
        persistOpenTabsFromTabs()
    }

    private fun destroyWebView(tab: BrowserTab) {
        val wv = tab.webView ?: return
        authFailedViews.remove(wv)
        authChallengeCounts.remove(wv)
        chartPerfInjected.remove(wv)
        webContainer.removeView(wv)
        wv.stopLoading()
        wv.onPause()
        wv.webChromeClient = null
        wv.destroy()
        tab.webView = null
    }

    private fun destroyTab(tab: BrowserTab) {
        destroyWebView(tab)
    }

    private fun destroyAllTabs() {
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        activeTabId = -1L
        destroyPrefetchViews()
    }

    private fun takePrefetch(url: String): WebView? {
        val key = prefetchViews.keys.firstOrNull { samePage(it, url) } ?: return null
        return prefetchViews.remove(key)
    }

    private fun destroyPrefetchViews() {
        prefetchLoading = false
        mainHandler.removeCallbacks(prefetchRunnable)
        prefetchQueue.clear()
        prefetchViews.values.toList().forEach { wv -> destroyOrphanWebView(wv) }
        prefetchViews.clear()
    }

    private fun destroyOrphanWebView(wv: WebView) {
        authFailedViews.remove(wv)
        authChallengeCounts.remove(wv)
        chartPerfInjected.remove(wv)
        (wv.parent as? ViewGroup)?.removeView(wv)
        try {
            wv.stopLoading()
            wv.onPause()
            wv.webChromeClient = null
            wv.destroy()
        } catch (_: Exception) {
        }
    }

    private fun startBookmarkPrefetch() {
        if (!Session.isActive(this)) return
        mainHandler.removeCallbacks(prefetchRunnable)
        mainHandler.postDelayed(prefetchRunnable, 400)
    }

    private fun queueBookmarkPrefetch(urls: Collection<String>) {
        val seen = mutableSetOf<String>()
        urls.forEach { raw ->
            val url = raw.trim()
            if (url.isEmpty() || url == Destinations.HOME_URL) return@forEach
            val key = url.lowercase()
            if (!seen.add(key)) return@forEach
            if (tabs.any { !it.isHome && samePage(it.url, url) }) return@forEach
            if (prefetchViews.keys.any { samePage(it, url) }) return@forEach
            if (prefetchQueue.any { samePage(it, url) }) return@forEach
            prefetchQueue.addLast(url)
        }
        startBookmarkPrefetch()
    }

    /** Načte uložené grafy na pozadí, ať po výpadku sítě zůstanou v RAM. */
    private fun pumpBookmarkPrefetch() {
        if (prefetchLoading) return
        if (!isConnectionOk()) return
        if (prefetchViews.size >= MAX_PREFETCH) return
        val next = prefetchQueue.removeFirstOrNull()
            ?: TrialBookmarks.load(this).map { it.url }.firstOrNull { url ->
                tabs.none { !it.isHome && samePage(it.url, url) } &&
                    prefetchViews.keys.none { samePage(it, url) }
            }
            ?: return
        val wv = createWebView()
        prefetchViews[next] = wv
        prefetchLoading = true
        wv.visibility = View.INVISIBLE
        webContainer.addView(
            wv,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        TrialIsolation.onNavigate(this, next)
        wv.loadUrl(next)
        mainHandler.postDelayed({
            if (prefetchViews[next] === wv && prefetchLoading) {
                prefetchLoading = false
                try { wv.onPause() } catch (_: Exception) {}
                pumpBookmarkPrefetch()
            }
        }, 20_000L)
    }

    private fun onPrefetchFinished(view: WebView) {
        if (prefetchViews.values.none { it === view }) return
        prefetchLoading = false
        view.postDelayed({
            if (prefetchViews.values.none { it === view }) return@postDelayed
            try {
                view.onPause()
                view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
            } catch (_: Exception) {
            }
        }, 3500)
        pumpBookmarkPrefetch()
    }

    private fun bumpAuthCount(webView: WebView): Int {
        val map = authChallengeCounts.getOrPut(webView) { mutableMapOf() }
        val n = (map["session"] ?: 0) + 1
        map["session"] = n
        return n
    }

    private fun resetAuthCount(webView: WebView) {
        authChallengeCounts[webView]?.remove("session")
    }

    private fun tabFolderId(tab: BrowserTab): String? {
        if (tab.isHome) return null
        return TrialBookmarks.findByUrl(this, tab.url)?.folderId
    }

    private fun buildTabStripEntries(source: List<BrowserTab>): List<TabStripEntry> {
        val folders = TrialBookmarks.loadFolders(this).associateBy { it.id }
        val consumedGroups = mutableSetOf<String>()
        val entries = mutableListOf<TabStripEntry>()
        for (tab in source) {
            if (tab.isHome) {
                entries.add(TabStripEntry.Single(tab))
                continue
            }
            val folderId = tabFolderId(tab)
            val folder = folderId?.let { folders[it] }
            if (folder != null && folder.tabGrouped) {
                if (folderId in consumedGroups) continue
                consumedGroups.add(folderId)
                val groupTabs = source.filter { candidate ->
                    !candidate.isHome && tabFolderId(candidate) == folderId
                }
                if (groupTabs.isNotEmpty()) {
                    entries.add(TabStripEntry.FolderGroup(folderId, folder.title, groupTabs))
                }
                continue
            }
            entries.add(TabStripEntry.Single(tab))
        }
        return entries
    }

    private fun selectFolderGroup(folderId: String, groupTabs: List<BrowserTab>) {
        if (groupTabs.isEmpty()) return
        val current = activeTab
        val inGroup = current != null && groupTabs.any { it.id == current.id }
        val target = if (inGroup) {
            val idx = groupTabs.indexOfFirst { it.id == current!!.id }
            groupTabs[(idx + 1) % groupTabs.size]
        } else {
            folderTabFocus[folderId]?.let { id -> groupTabs.find { it.id == id } } ?: groupTabs.first()
        }
        folderTabFocus[folderId] = target.id
        selectTab(target.id)
    }

    private fun showFolderTabActions(folderId: String, folderTitle: String, groupTabs: List<BrowserTab>) {
        if (chromeOffline) return
        val labels = groupTabs.map { tab ->
            val mark = if (tab.id == activeTabId) " •" else ""
            tab.title + mark
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(folderTitle)
            .setItems(labels) { _, which ->
                val tab = groupTabs[which]
                folderTabFocus[folderId] = tab.id
                selectTab(tab.id)
            }
            .setNeutralButton("Rozbalit na liště") { _, _ ->
                TrialBookmarks.setFolderTabGrouped(this, folderId, false)
                refreshTabStrip()
                populateHomeBookmarks()
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun bindTabStripItem(
        inflater: LayoutInflater,
        tab: BrowserTab,
        selected: Boolean,
        showClose: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ): View {
        val item = inflater.inflate(R.layout.item_browser_tab, tabStrip, false)
        val root = item.findViewById<View>(R.id.tabRoot)
        val content = item.findViewById<View>(R.id.tabContent)
        val title = item.findViewById<TextView>(R.id.tabTitle)
        val pin = item.findViewById<ImageView>(R.id.tabPin)
        val close = item.findViewById<ImageButton>(R.id.tabClose)
        val savedMark = item.findViewById<View>(R.id.tabSavedMark)

        title.text = tab.title
        title.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.accent else R.color.ink_soft)
        )
        pin.visibility = if (tab.pinned) View.VISIBLE else View.GONE
        pin.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.saved)
        )
        savedMark.visibility =
            if (!tab.isHome && TrialBookmarks.isSaved(this, tab.url)) View.VISIBLE else View.GONE
        root.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab
        )
        root.clipToOutline = true
        root.invalidateOutline()
        root.setOnClickListener { onClick() }
        root.setOnLongClickListener {
            onLongClick()
            true
        }
        close.visibility = if (showClose) View.VISIBLE else View.GONE
        val padEnd = ((if (showClose) 4 else 14) * resources.displayMetrics.density).toInt()
        content.setPaddingRelative(
            content.paddingStart,
            content.paddingTop,
            padEnd,
            content.paddingBottom
        )
        if (showClose) {
            close.setOnClickListener { closeTab(tab.id) }
        } else {
            close.setOnClickListener(null)
        }
        return item
    }

    private fun refreshTabStrip() {
        tabStrip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val source = if (chromeOffline) tabs.filter { it.pinned } else tabs
        val shown = source.sortedWith(
            compareBy<BrowserTab> { TrialPins.stripGroup(it.isHome, it.pinned) }
                .thenBy { tabs.indexOf(it) }
        )
        val entries = buildTabStripEntries(shown)
        var titlesSynced = false
        var activeEntryIndex = -1
        entries.forEachIndexed { index, entry ->
            when (entry) {
                is TabStripEntry.Single -> {
                    val tab = entry.tab
                    if (tab.id == activeTabId) activeEntryIndex = index
                    val savedTitle = TrialBookmarks.findByUrl(this, tab.url)?.title?.trim().orEmpty()
                    if (!tab.isHome && savedTitle.isNotEmpty() && tab.title != savedTitle) {
                        tab.title = savedTitle
                        titlesSynced = true
                    }
                    val showClose = tabs.size > 1 && !tab.pinned
                    val item = bindTabStripItem(
                        inflater = inflater,
                        tab = tab,
                        selected = tab.id == activeTabId,
                        showClose = showClose,
                        onClick = { selectTab(tab.id) },
                        onLongClick = {
                            if (!chromeOffline) showTabActions(tab)
                        }
                    )
                    tabStrip.addView(item)
                }
                is TabStripEntry.FolderGroup -> {
                    if (entry.tabs.any { it.id == activeTabId }) activeEntryIndex = index
                    entry.tabs.forEach { tab ->
                        val savedTitle = TrialBookmarks.findByUrl(this, tab.url)?.title?.trim().orEmpty()
                        if (savedTitle.isNotEmpty() && tab.title != savedTitle) {
                            tab.title = savedTitle
                            titlesSynced = true
                        }
                    }
                    val groupTab = BrowserTab(
                        id = entry.tabs.first().id,
                        webView = null,
                        title = getString(R.string.folder_tab_title, entry.title, entry.tabs.size),
                        url = entry.tabs.first().url,
                        pinned = entry.tabs.any { it.pinned }
                    )
                    val selected = entry.tabs.any { it.id == activeTabId }
                    val item = bindTabStripItem(
                        inflater = inflater,
                        tab = groupTab,
                        selected = selected,
                        showClose = false,
                        onClick = { selectFolderGroup(entry.folderId, entry.tabs) },
                        onLongClick = {
                            if (!chromeOffline) {
                                showFolderTabActions(entry.folderId, entry.title, entry.tabs)
                            }
                        }
                    )
                    tabStrip.addView(item)
                }
            }
        }
        if (titlesSynced) persistOpenTabsFromTabs()
        tabScroll.post {
            if (activeEntryIndex >= 0 && activeEntryIndex < tabStrip.childCount) {
                val child = tabStrip.getChildAt(activeEntryIndex)
                tabScroll.smoothScrollTo((child.left - 24).coerceAtLeast(0), 0)
            }
        }
    }

    private fun tabLabel(url: String): String {
        val saved = TrialBookmarks.findByUrl(this, url)?.title?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved
        return Destinations.tabTitle(url)
    }

    /** Uložený popisek přenese i na otevřené karty se stejnou adresou. */
    private fun applySavedTitleToTabs(url: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        tabs.filter { !it.isHome && samePage(it.url, url) }.forEach { tab ->
            tab.title = clean
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
    }

    private fun updateTabMeta(webView: WebView, url: String?) {
        val tab = tabs.find { it.webView === webView } ?: return
        var stripChanged = false
        if (!url.isNullOrBlank()) {
            tab.url = url
            val label = tabLabel(url)
            if (tab.title != label) {
                tab.title = label
                stripChanged = true
            }
            persistOpenTabsFromTabs()
        }
        if (stripChanged) refreshTabStrip()
    }

    // ── WebView ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
        // Měřítko: setInitialScale se nevolá. Zoom stránky jde přes CSS
        // (PageZoom → jen html.style.zoom), ne přes WebSettings.zoom.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        webView.settings.allowUniversalAccessFromFileURLs = false
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.settings.setSupportZoom(false)
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        webView.settings.textZoom = 100
        webView.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = false
        }
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        @Suppress("DEPRECATION")
        webView.settings.saveFormData = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        // Chromium má vlastní compositor. Hardware vrstva kolem WebView
        // při posunu grafu pokaždé nahrává celou texturu → cukání.
        webView.setLayerType(View.LAYER_TYPE_NONE, null)
        // Default WebView is opaque white; page JS cannot paint that away.
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.offscreenPreRaster = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isNestedScrollingEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        webView.isHapticFeedbackEnabled = false
        webView.isSoundEffectsEnabled = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            webView.settings.forceDark = WebSettings.FORCE_DARK_OFF
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        TrialIsolation.applyThirdPartyCookies(webView)
        webView.setOnLongClickListener { view ->
            handleWebViewLongClick(view as WebView)
            true
        }
        webView.setOnTouchListener { v, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
        webView.addJavascriptInterface(geoBridge, "ObalkaGeo")
        installChartPerfBootstrap(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                return !isAllowedWebUri(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
                return !isAllowedWebUri(uri)
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                SslPolicy.handleSslError(this@WebViewActivity, handler, error) {
                    if (view === activeWebView && !shouldSuppressPageErrorDialogs()) {
                        showCertWarning(error)
                    }
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                awaitingHttpAuth = true
                if (handler == null || host.isNullOrEmpty()) {
                    handler?.cancel()
                    awaitingHttpAuth = false
                    return
                }
                if (!AuthHosts.allows(host)) {
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }
                val webView = view ?: run {
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }
                // Nová výzva = předchozí 401 byl handshake (NTLM), ne konečné odmítnutí.
                authFailedViews.remove(webView)

                val creds = pendingCredentials ?: Session.credentialsFor(this@WebViewActivity, host)
                val canAuto = creds != null && (verifyingLogin || gate == Gate.BROWSER)
                if (canAuto) {
                    val count = bumpAuthCount(webView)
                    if (count <= MAX_AUTH_ROUNDS) {
                        handler.proceed(creds!!.username, creds.password)
                        return
                    }
                    resetAuthCount(webView)
                    if (verifyingLogin) {
                        pendingAuthHandler = handler
                        failLogin("Neplatné jméno nebo heslo")
                        return
                    }
                    // Relace PSST platí, dokud uživatel nesmaže údaje.
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }

                // Připnutý graf už je v paměti — 401 po odhlášení ho neschová
                // za přihlášení. Dialog až u další (nepřipnuté) stránky PSST.
                if (isLoadedPinnedView(webView)) {
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }

                // Bez proceed/cancel WebView visí na 401 (bílá / zamrzlý graf).
                // I když Session.isActive — např. relace je, ale údaje na
                // tohoto hostitele nesedí — musí přijít dialog, ne ticho.
                pendingAuthHandler = handler
                if (verifyingLogin) {
                    failLogin("Neplatné jméno nebo heslo")
                    return
                }
                pendingStartUrl = webView.url ?: Destinations.PSST_URL
                presentLogin()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame != true) return
                if (errorResponse?.statusCode != 401) return
                val webView = view ?: return
                authFailedViews.add(webView)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (view !== activeWebView) return
                if (request?.isForMainFrame != true) return
                if (verifyingLogin) {
                    failLogin("Stránku se nepodařilo načíst. Zkontrolujte VPN a zkuste to znovu.")
                    return
                }
                if (shouldSuppressPageErrorDialogs()) return
                showNetworkWarning(error?.errorCode, request.url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (view != null) {
                    TrialIsolation.onNavigate(this@WebViewActivity, url)
                    injectChartPerfFallback(view)
                    view.evaluateJavascript(PageZoom.setJs(pageZoomPercentFor(url)), null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                awaitingHttpAuth = false
                val failedAuth = view != null && view in authFailedViews
                if (view != null && !failedAuth) resetAuthCount(view)
                if (verifyingLogin && view != null && view === activeWebView) {
                    val finishedUrl = url
                    view.post {
                        if (!verifyingLogin) return@post
                        if (awaitingHttpAuth) return@post
                        if (failedAuth || view in authFailedViews) {
                            failLogin("Neplatné jméno nebo heslo")
                            return@post
                        }
                        if (finishedUrl.isNullOrBlank() || finishedUrl == "about:blank") return@post
                        succeedLogin()
                    }
                }
                if (view != null) authFailedViews.remove(view)
                if (view != null) {
                    view.setBackgroundColor(Color.TRANSPARENT)
                    view.evaluateJavascript(PageZoom.setJs(pageZoomPercentFor(url)), null)
                    injectChartFit(view)
                    injectPsstDataLayout(view, url)
                    onPrefetchFinished(view)
                }
                updateTabMeta(view ?: return, url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view !== activeWebView) return
                if (gate != Gate.BROWSER && !verifyingLogin) return
                // Overlay progress — bez animace a bez GONE/VISIBLE na každý procent.
                if (newProgress in 1..99) {
                    if (progressBar.visibility != View.VISIBLE) {
                        progressBar.visibility = View.VISIBLE
                    }
                    progressBar.setProgressCompat(newProgress, false)
                } else if (progressBar.visibility != View.GONE) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                handleGeolocationPrompt(origin, callback)
            }
        }

        return webView
    }

    // ── URL ─────────────────────────────────────────────────────────────

    private fun explicitUrlFromIntent(intent: Intent?): String? {
        intent?.data?.toString()?.let { return it }
        intent?.getStringExtra(EXTRA_URL)?.let { return it }

        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            return shared.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: shared.takeIf { it.isNotEmpty() }?.let { "https://$it" }
        }
        return null
    }

    private fun normalizeUrl(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        if (Uri.parse(text).host.isNullOrEmpty()) return null
        return text
    }

    private fun loadInActiveTab(url: String) {
        val tab = activeTab
        if (tab == null) {
            openInNewTab(url)
            return
        }
        dialogShown = false
        if (tab.pinned) {
            if (samePage(tab.url, url)) {
                selectTab(tab.id)
                return
            }
            openInNewTab(url)
            return
        }
        if (tab.isHome) {
            if (needsAppLogin(url)) {
                pendingStartUrl = url
                presentLogin()
                return
            }
            val webView = takePrefetch(url) ?: createWebView()
            tab.webView = webView
            tab.isHome = false
            tab.url = url
            tab.title = tabLabel(url)
            selectTab(tab.id)
            if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                TrialIsolation.onNavigate(this, url)
                webView.loadUrl(url)
            }
            return
        }
        tab.url = url
        tab.title = tabLabel(url)
        selectTab(tab.id)
        TrialIsolation.onNavigate(this, url)
        tab.webView?.loadUrl(url)
    }

    private fun showOpenUrlDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_url, null)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.urlLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        val current = activeTab?.url?.takeUnless { it == Destinations.HOME_URL }
        urlInput.setText(current ?: DEFAULT_URL)
        urlInput.setSelection(urlInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Otevřít URL adresu")
            .setView(view)
            .setPositiveButton("Otevřít URL", null)
            .setNegativeButton("Zrušit", null)
            .create()
        urlDialog = dialog

        fun tryOpen() {
            val normalized = normalizeUrl(urlInput.text?.toString().orEmpty())
            if (normalized == null) {
                urlLayout.error = "Tohle nevypadá jako adresa"
                return
            }
            urlLayout.error = null
            hideKeyboard(urlInput)
            dialog.dismiss()
            dialogShown = false
            if (needsAppLogin(normalized)) {
                pendingStartUrl = normalized
                presentLogin()
            } else {
                enterBrowser()
                loadInActiveTab(normalized)
            }
        }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                tryOpen()
                true
            } else false
        }

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { tryOpen() }
        }
        dialog.setOnDismissListener {
            hideKeyboard(urlInput)
            urlDialog = null
            dialogShown = false
            window.decorView.post { hideKeyboard() }
        }
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        dialog.show()
    }

    /**
     * Dlouhé podržení odkazu ve stránce. Systémovou Chromium nabídku
     * nenecháme — místo ní náš dialog (otevřít na nové kartě / uložit).
     */
    private fun handleWebViewLongClick(webView: WebView): Boolean {
        if (chromeOffline) return true
        val result = webView.hitTestResult
        when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val fallback = result.extra
                val handler = Handler(Looper.getMainLooper()) { msg ->
                    val href = msg.data.getString("url")
                        ?: msg.data.getString("src")
                        ?: fallback
                    if (!href.isNullOrBlank()) {
                        val uri = runCatching { Uri.parse(href) }.getOrNull()
                        if (uri != null && isAllowedWebUri(uri)) {
                            showLinkOrTabActions(href, "Odkaz")
                        }
                    }
                    true
                }
                webView.requestFocusNodeHref(Message.obtain(handler))
            }
        }
        return true
    }

    private fun showLinkOrTabActions(url: String, heading: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) {
            Toast.makeText(this, "Domů nelze otevřít na nové kartě", Toast.LENGTH_SHORT).show()
            return
        }
        val already = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        showActionSheet(heading, url, pageActionRows(url, already))
    }

    private fun showTabActions(tab: BrowserTab) {
        if (tab.isHome) {
            Toast.makeText(this, "Domů nelze otevřít na nové kartě", Toast.LENGTH_SHORT).show()
            return
        }
        showActionSheet(tab.title, tab.url, pageActionRows(tab.url, tab))
    }

    private fun pageActionRows(url: String, tab: BrowserTab?): List<ActionRow> {
        val rows = mutableListOf<ActionRow>()
        val openTab = tab
        rows.add(
            if (openTab != null && openTab.pinned) {
                ActionRow("Odepnout", R.drawable.ic_wifi_off) { setTabPinned(openTab, false) }
            } else {
                ActionRow("Připnout na lištu", R.drawable.ic_wifi_off) {
                    if (openTab != null) setTabPinned(openTab, true)
                    else togglePinForUrl(url)
                }
            }
        )
        rows.add(ActionRow("Otevřít na nové kartě", R.drawable.ic_add) { openOnNewTab(url) })
        if (TrialBookmarks.isSaved(this, url)) {
            rows.add(ActionRow("Přejmenovat", R.drawable.ic_edit) { renameSavedPage(url) })
            rows.add(ActionRow("Odebrat", R.drawable.ic_close) { removeSavedPage(url) })
        } else {
            rows.add(ActionRow("Uložit", R.drawable.ic_bookmark) { savePageThenRename(url) })
        }
        return rows
    }

    private data class ActionRow(
        val title: String,
        val icon: Int,
        val run: () -> Unit
    )

    private fun showActionSheet(title: String, subtitle: String?, rows: List<ActionRow>) {
        dismissActionSheet()
        val view = layoutInflater.inflate(R.layout.popup_action_sheet, null)
        view.findViewById<TextView>(R.id.actionSheetTitle).text = title
        val sub = view.findViewById<TextView>(R.id.actionSheetSubtitle)
        if (subtitle.isNullOrBlank()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = subtitle
        }
        val list = view.findViewById<LinearLayout>(R.id.actionSheetList)
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        val inflater = LayoutInflater.from(this)
        rows.forEach { row ->
            val item = inflater.inflate(R.layout.item_action_row, list, false)
            item.findViewById<ImageView>(R.id.actionRowIcon).setImageResource(row.icon)
            item.findViewById<TextView>(R.id.actionRowTitle).text = row.title
            item.setOnClickListener {
                dialog.dismiss()
                row.run()
            }
            list.addView(item)
        }
        actionDialog = dialog
        dialog.setOnDismissListener {
            if (actionDialog === dialog) actionDialog = null
        }
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (320 * resources.displayMetrics.density).toInt()
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            val loc = IntArray(2)
            val chromeBottom = if (::tabScroll.isInitialized) {
                tabScroll.getLocationOnScreen(loc)
                loc[1] + tabScroll.height
            } else {
                (72 * resources.displayMetrics.density).toInt()
            }
            win.decorView.getLocationOnScreen(loc)
            val gap = (8 * resources.displayMetrics.density).toInt()
            win.attributes = win.attributes.apply {
                y = (chromeBottom - loc[1] + gap).coerceAtLeast(gap)
            }
        }
    }

    private fun dismissActionSheet() {
        try {
            actionDialog?.dismiss()
        } catch (_: Exception) {
        }
        actionDialog = null
    }

    private fun setTabPinned(tab: BrowserTab, pinned: Boolean, quiet: Boolean = false) {
        if (tab.isHome) return
        if (pinned == tab.pinned) return
        if (pinned) {
            if (tabs.count { it.pinned } >= TrialPins.MAX_ITEMS) {
                if (!quiet) {
                    Toast.makeText(
                        this,
                        "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            tab.pinned = true
            tabs.remove(tab)
            val at = tabs.indexOfLast { it.isHome || it.pinned } + 1
            tabs.add(at.coerceAtLeast(0), tab)
            if (!quiet) {
                Toast.makeText(this, "Karta zůstane otevřená", Toast.LENGTH_SHORT).show()
            }
        } else {
            tab.pinned = false
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
    }

    private fun persistOpenTabsFromTabs() {
        TrialPins.save(
            this,
            tabs.filter { !it.isHome }.map { TrialPin(it.title, it.url, it.pinned) }
        )
    }

    /** Obnoví karty z RAM tohoto procesu. Po úplném vypnutí appky je seznam prázdný. */
    private fun restorePinnedTabs() {
        val saved = TrialPins.load(this)
        if (saved.isEmpty()) return
        val load = Session.isActive(this)
        for (item in saved) {
            val existing = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (existing != null) {
                existing.pinned = item.pinned
                continue
            }
            if (tabs.size >= MAX_TABS) break
            val needsView = load || item.pinned
            val webView = if (!needsView) null
            else if (load) takePrefetch(item.url) ?: createWebView()
            else createWebView()
            val tab = BrowserTab(
                id = nextTabId++,
                webView = webView,
                title = item.title.ifBlank { tabLabel(item.url) },
                url = item.url,
                pinned = item.pinned
            )
            if (item.pinned) {
                val at = tabs.indexOfLast { it.isHome || it.pinned } + 1
                tabs.add(at.coerceAtLeast(0), tab)
            } else {
                tabs.add(tab)
            }
            if (webView != null) {
                try {
                    webView.onPause()
                    webView.setRendererPriorityPolicy(
                        if (item.pinned) WebView.RENDERER_PRIORITY_IMPORTANT
                        else WebView.RENDERER_PRIORITY_WAIVED,
                        !item.pinned
                    )
                } catch (_: Exception) {
                }
                if (load && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
                    TrialIsolation.onNavigate(this, item.url)
                    webView.loadUrl(item.url)
                }
            }
        }
        persistOpenTabsFromTabs()
    }

    private fun loadUnloadedTabsAfterLogin() {
        tabs.filter { !it.isHome }.forEach { tab ->
            var wv = tab.webView
            if (wv == null) {
                wv = takePrefetch(tab.url) ?: createWebView()
                tab.webView = wv
            }
            if (tab.pinned && !wv.url.isNullOrBlank() && wv.url != "about:blank") return@forEach
            TrialIsolation.onNavigate(this, tab.url)
            wv.loadUrl(tab.url)
        }
    }

    private fun isLoadedPinnedView(webView: WebView): Boolean {
        val tab = tabs.find { it.webView === webView } ?: return false
        if (!tab.pinned) return false
        val current = webView.url
        return !current.isNullOrBlank() && current != "about:blank"
    }

    private fun shouldPromptLoginForTab(tab: BrowserTab): Boolean {
        if (!TrialSettings.isTrial() || Session.isActive(this)) return false
        if (tab.isHome || tab.pinned) return false
        if (tab.url.isBlank() || tab.url == Destinations.HOME_URL) return false
        return Destinations.forUrl(tab.url)?.requiresAppLogin == true
    }

    private fun maybePromptLoginForActiveUnpinnedTab() {
        if (verifyingLogin) return
        val tab = activeTab ?: return
        if (!shouldPromptLoginForTab(tab)) return
        pendingStartUrl = tab.url
        presentLogin()
    }

    /**
     * Otevře adresu na nové kartě a přepne na ni.
     * Při maximu karet použije volnou kartu Domů, jinak oznámí limit.
     */
    private fun openOnNewTab(url: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) return
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        val spareHome = tabs.firstOrNull { it.isHome && it.id != activeTabId }
        when {
            tabs.size < MAX_TABS -> {
                val webView = takePrefetch(url) ?: createWebView()
                val tab = BrowserTab(
                    id = nextTabId++,
                    webView = webView,
                    title = tabLabel(url),
                    url = url
                )
                tabs.add(tab)
                persistOpenTabsFromTabs()
                selectTab(tab.id)
                if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                    TrialIsolation.onNavigate(this, url)
                    webView.loadUrl(url)
                }
            }
            spareHome != null -> {
                loadUrlIntoTab(spareHome, url)
                selectTab(spareHome.id)
            }
            else -> {
                Toast.makeText(this, "Maximum je $MAX_TABS karet", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Otevřeno na nové kartě", Toast.LENGTH_SHORT).show()
    }

    private fun togglePinForUrl(url: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) return
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            setTabPinned(existing, !existing.pinned)
            return
        }
        pinUrl(url)
    }

    private fun pinUrl(url: String, quiet: Boolean = false, select: Boolean = true): Boolean {
        if (url.isBlank() || url == Destinations.HOME_URL) return false
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            if (!existing.pinned) setTabPinned(existing, true, quiet)
            if (select) selectTab(existing.id)
            return true
        }
        if (tabs.count { it.pinned } >= TrialPins.MAX_ITEMS) {
            if (!quiet) {
                Toast.makeText(
                    this,
                    "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return false
        }
        if (tabs.size >= MAX_TABS) {
            val home = tabs.firstOrNull { it.isHome }
            if (home == null) {
                if (!quiet) {
                    Toast.makeText(this, "Maximum je $MAX_TABS karet", Toast.LENGTH_SHORT).show()
                }
                return false
            }
            loadUrlIntoTab(home, url)
            setTabPinned(home, true, quiet)
            if (select) selectTab(home.id)
            return true
        }
        val tab = addTabForUrl(url, select = select) ?: return false
        setTabPinned(tab, true, quiet)
        return true
    }

    private fun loadUrlIntoTab(tab: BrowserTab, url: String) {
        if (tab.pinned && samePage(tab.url, url)) {
            selectTab(tab.id)
            return
        }
        if (needsAppLogin(url) && !tab.pinned) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        if (tab.isHome) {
            val webView = createWebView()
            tab.webView = webView
            tab.isHome = false
        }
        tab.url = url
        tab.title = tabLabel(url)
        tab.webView?.loadUrl(url)
        refreshTabStrip()
    }

    // ── Poloha ──────────────────────────────────────────────────────────

    private fun handleGeolocationPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (callback == null) return
        val originHost = origin?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        if (Destinations.forHost(originHost) == null) {
            callback.invoke(origin, false, false)
            return
        }
        if (hasLocationPermission()) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeoOrigin = origin
        pendingGeoCallback = callback
        MaterialAlertDialogBuilder(this)
            .setTitle("Přístup k poloze")
            .setMessage(
                "Stránka ${origin ?: "web"} chce použít polohu zařízení " +
                    "(např. mapa nebo GPS funkce)."
            )
            .setPositiveButton("Povolit") { _, _ ->
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Odmítnout") { _, _ -> finishGeolocationRequest(false) }
            .setCancelable(false)
            .show()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun finishGeolocationRequest(allowed: Boolean) {
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        pendingGeoOrigin = null
        pendingGeoCallback = null
        callback?.invoke(origin, allowed, false)
        if (!allowed) {
            Toast.makeText(this, "Poloha nebyla povolena", Toast.LENGTH_SHORT).show()
        } else {
            syncGeoUpdates()
        }
    }

    private inner class GeoBridge {
        @JavascriptInterface
        fun watchStart() {
            mainHandler.post { onGeoWatchDelta(1) }
        }

        @JavascriptInterface
        fun watchStop() {
            mainHandler.post { onGeoWatchDelta(-1) }
        }
    }

    private fun onGeoWatchDelta(delta: Int) {
        geoWatchCount = (geoWatchCount + delta).coerceAtLeast(0)
        syncGeoUpdates()
    }

    private fun syncGeoUpdates() {
        if (geoWatchCount > 0 && !isFinishing && hasLocationPermission()) {
            startGeoUpdates()
        } else {
            stopGeoUpdates()
        }
    }

    private fun startGeoUpdates() {
        if (geoManager != null) return
        geoManager = GeoTrack.startUpdates(this, geoListener)
    }

    private fun stopGeoUpdates() {
        GeoTrack.stopUpdates(geoManager, geoListener)
        geoManager = null
    }

    private fun pushGeoToTabs(location: Location) {
        val alt = if (location.hasAltitude()) location.altitude.toString() else "null"
        val spd = if (location.hasSpeed()) location.speed.toString() else "null"
        val hdg = if (location.hasBearing()) location.bearing.toString() else "null"
        val js =
            "try{window.__obalkaGeoPush(${location.latitude},${location.longitude}," +
                "${location.accuracy},$alt,$spd,$hdg,${location.time})}catch(e){}"
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            try {
                wv.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun isAllowedWebUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "https" || scheme == "about"
    }

    private fun setSensitiveScreen(on: Boolean) {
        if (on) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // ── VPN / síť ───────────────────────────────────────────────────────

    private fun isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    /** VPN i běžný internet — bez toho interní stránky nejedou. */
    private fun isConnectionOk(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshConnectionBanner() {
        if (isConnectionOk()) hideConnectionBanner()
        else showConnectionBanner()
        applyOfflineChrome()
    }

    /**
     * Offline: jen připnuté karty na liště, zbytek ovládání zašedne.
     * Přepíná se jen při změně stavu, nebo když je potřeba skočit na pin.
     */
    private fun applyOfflineChrome() {
        val offline = !isConnectionOk()
        val changed = chromeOffline != offline
        chromeOffline = offline
        if (offline) {
            val current = activeTab
            if (current != null && !current.pinned) {
                val firstPin = tabs.firstOrNull { it.pinned }
                if (firstPin != null) {
                    if (changed) {
                        invalidateOptionsMenu()
                        if (::toolbar.isInitialized) applyChromeMenu(toolbar.menu)
                    }
                    selectTab(firstPin.id)
                    return
                }
            }
        }
        if (changed) {
            refreshTabStrip()
            invalidateOptionsMenu()
            if (::toolbar.isInitialized) applyChromeMenu(toolbar.menu)
        }
    }

    private fun applyChromeMenu(menu: Menu?) {
        val usable = !chromeOffline
        val accent = ContextCompat.getColor(this, if (usable) R.color.accent else R.color.ink_faint)
        val inkSoft = ContextCompat.getColor(this, if (usable) R.color.ink_soft else R.color.ink_faint)
        val alert = ContextCompat.getColor(this, R.color.alert)
        val alpha = if (usable) 255 else 90

        fun tint(id: Int, color: Int) {
            menu?.findItem(id)?.let { item ->
                item.isEnabled = usable
                item.icon?.mutate()?.let { icon ->
                    icon.setTint(color)
                    icon.alpha = alpha
                    item.icon = icon
                }
            }
        }
        tint(R.id.action_new_tab, accent)
        tint(R.id.action_reload, inkSoft)
        tint(R.id.action_folders, accent)
        tint(R.id.action_home, accent)
        tint(R.id.action_open_url, inkSoft)
        tint(R.id.action_link_settings, inkSoft)
        menu?.findItem(R.id.action_logout)?.let { item ->
            item.isEnabled = usable
            item.icon?.mutate()?.let { icon ->
                icon.setTint(alert)
                icon.alpha = alpha
                item.icon = icon
            }
        }
        if (::toolbar.isInitialized) {
            toolbar.isEnabled = usable
            toolbar.overflowIcon?.mutate()?.let { icon ->
                icon.setTint(accent)
                icon.alpha = alpha
                toolbar.overflowIcon = icon
            }
        }
    }

    private fun showConnectionBanner() {
        if (!::connectionBanner.isInitialized) return
        connectionBanner.bringToFront()
        if (connectionBannerVisible && connectionBanner.visibility == View.VISIBLE) return
        connectionBannerVisible = true
        connectionBanner.animate().cancel()
        connectionBanner.visibility = View.VISIBLE
        connectionBanner.post {
            if (!connectionBannerVisible) return@post
            val h = connectionBanner.height.toFloat()
            if (h <= 0f) {
                connectionBanner.translationY = 0f
                return@post
            }
            connectionBanner.translationY = h
            connectionBanner.animate().translationY(0f).setDuration(220).start()
        }
    }

    private fun hideConnectionBanner() {
        if (!::connectionBanner.isInitialized) return
        if (!connectionBannerVisible && connectionBanner.visibility != View.VISIBLE) return
        connectionBannerVisible = false
        connectionBanner.animate().cancel()
        val h = connectionBanner.height.toFloat()
        if (h <= 0f || connectionBanner.visibility != View.VISIBLE) {
            connectionBanner.visibility = View.GONE
            connectionBanner.translationY = 0f
            return
        }
        connectionBanner.animate()
            .translationY(h)
            .setDuration(180)
            .withEndAction {
                if (!connectionBannerVisible) {
                    connectionBanner.visibility = View.GONE
                    connectionBanner.translationY = 0f
                }
            }
            .start()
    }

    private fun raiseConnectionBanner() {
        if (::connectionBanner.isInitialized && connectionBannerVisible) {
            connectionBanner.bringToFront()
        }
    }

    private fun registerVpnMonitor() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleVpnCheck()
            override fun onLost(network: Network) = scheduleVpnCheck()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = scheduleVpnCheck()
        }
        networkCallback = callback
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            try {
                cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            } catch (_: Exception) {
                networkCallback = null
            }
        }
    }

    private fun unregisterVpnMonitor() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    private fun scheduleVpnCheck() {
        mainHandler.removeCallbacks(vpnCheckRunnable)
        mainHandler.postDelayed(vpnCheckRunnable, 350)
    }

    // ── Přihlášení ──────────────────────────────────────────────────────

    private fun installChartPerfBootstrap(webView: WebView) {
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

    private fun injectChartFit(webView: WebView, unlock: Boolean = false) {
        try {
            val js = if (unlock) ChartFit.RESET_JS + ChartFit.FIT_JS else ChartFit.FIT_JS
            webView.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }

    private fun injectChartFitIntoAllTabs(unlock: Boolean = false) {
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            injectChartFit(wv, unlock)
        }
    }

    private fun injectPsstDataLayout(webView: WebView, url: String? = webView.url) {
        val resolved = url ?: tabs.find { it.webView === webView }?.url
        if (!Destinations.isPsstDataHome(resolved)) return
        try {
            webView.evaluateJavascript(PsstDataLayout.APPLY_JS, null)
        } catch (_: Exception) {
        }
    }

    private fun injectPsstDataLayoutIntoAllTabs() {
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            injectPsstDataLayout(wv)
        }
    }

    private fun scheduleChartFit() {
        mainHandler.removeCallbacks(chartFitRunnable)
        mainHandler.postDelayed(chartFitRunnable, CHART_FIT_DELAY_MS)
    }

    /** Starší WebView bez document-start — stihne to jen další grafy, ne první canvas. */
    private fun injectChartPerfFallback(webView: WebView) {
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

    private fun pageZoomPercentFor(url: String?): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return PageZoom.percentFor(this, url, landscape)
    }

    private fun pageZoomJs(): String {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return PageZoom.pickerJs(
            PageZoom.percentFor(this, PageZoom.Kind.Psst, landscape)
        )
    }

    private fun applyPageZoomToAllTabs() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            val percent = PageZoom.percentFor(this, tab.url, landscape)
            wv.evaluateJavascript(PageZoom.setJs(percent), null)
        }
    }

    private fun bindLoginUi() {
        loginOverlay = findViewById(R.id.loginOverlay)
        loginTitle = findViewById(R.id.loginTitle)
        loginFormColumn = findViewById(R.id.loginFormColumn)
        connectionBanner = findViewById(R.id.connectionBanner)
        loginFormScroll = findViewById(R.id.loginFormScroll)
        certBanner = findViewById(R.id.certBanner)
        usernameLayout = findViewById(R.id.usernameLayout)
        usernameInput = findViewById(R.id.usernameInput)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        loginEphemeralHint = findViewById(R.id.loginEphemeralHint)

        loginButton.setOnClickListener { submitLogin() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else false
        }
    }

    private fun bindHomeUi() {
        homeOverlay = findViewById(R.id.homeOverlay)
        homeAppList = findViewById(R.id.homeAppList)
        homeBookmarkList = findViewById(R.id.homeBookmarkList)
        homeVersion = findViewById(R.id.homeVersion)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        homeVersion.text = getString(R.string.version_label, version ?: "—")
        populateHomeApps()
    }

    private fun populateHomeApps() {
        homeAppList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val gap = (10 * resources.displayMetrics.density).toInt()
        Destinations.apps.forEachIndexed { index, app ->
            val item = inflater.inflate(R.layout.item_home_app, homeAppList, false)
            item.findViewById<TextView>(R.id.homeAppTitle).text = app.title
            item.isLongClickable = false
            item.setOnLongClickListener { true }
            item.findViewById<TextView>(R.id.homeAppTitle).setOnLongClickListener { true }
            val lp = if (Destinations.apps.size == 1) {
                val half = ((homeAppList.width.takeIf { it > 0 }
                    ?: (resources.displayMetrics.widthPixels
                        - (80 * resources.displayMetrics.density).toInt())) / 2)
                    .coerceAtLeast((240 * resources.displayMetrics.density).toInt())
                LinearLayout.LayoutParams(half, LinearLayout.LayoutParams.MATCH_PARENT)
            } else {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index > 0) marginStart = gap
                }
            }
            item.layoutParams = lp
            item.setOnClickListener { openDestination(app) }
            homeAppList.addView(item)
        }
        populateHomeBookmarks()
    }

    private fun populateHomeBookmarks() {
        if (!::homeBookmarkList.isInitialized) return
        homeBookmarkList.removeAllViews()
        val items = TrialBookmarks.load(this)
        if (items.isEmpty()) {
            homeBookmarkList.visibility = View.GONE
            return
        }
        homeBookmarkList.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        val gap = (10 * resources.displayMetrics.density).toInt()
        val groups = TrialBookmarks.grouped(items, TrialBookmarks.loadFolders(this))
        renderBookmarkGroups(
            parent = homeBookmarkList,
            groups = groups,
            inflater = inflater,
            foldersFirst = false,
            hideCollapsedItems = false,
            onFolderHeader = { header, folder ->
                header.findViewById<TextView>(R.id.groupHeaderTitle).text = folder.title
                header.findViewById<View>(R.id.groupHeaderChevron).visibility = View.GONE
                header.isClickable = false
                header.background = null
                bindFolderHeaderActions(header, folder)
            },
            onFolderClick = null,
            onFolderLongClick = null,
            onItems = { items -> addHomeBookmarkRows(homeBookmarkList, items, gap, inflater) }
        )
    }

    private fun addHomeBookmarkRows(
        parent: LinearLayout,
        items: List<TrialBookmark>,
        gap: Int,
        inflater: LayoutInflater
    ) {
        items.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            }
            rowItems.forEachIndexed { index, bookmark ->
                val card = inflater.inflate(R.layout.item_home_bookmark, row, false)
                card.findViewById<TextView>(R.id.homeBookmarkTitle).text = bookmark.title
                val lp = LinearLayout.LayoutParams(
                    0,
                    (72 * resources.displayMetrics.density).toInt(),
                    1f
                )
                if (index > 0) lp.marginStart = gap
                card.layoutParams = lp
                card.setOnClickListener { openSavedUrl(bookmark.url) }
                card.findViewById<View>(R.id.homeBookmarkMore).setOnClickListener { more ->
                    showHomeBookmarkMenu(more, bookmark)
                }
                row.addView(card)
            }
            if (rowItems.size == 1) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = gap
                }
                row.addView(spacer)
            }
            parent.addView(row)
        }
    }

    private fun hideHomeOverlay() {
        if (!::homeOverlay.isInitialized) return
        homeOverlay.visibility = View.GONE
    }

    private fun openDestination(app: Destinations.AppLink) {
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, app.url) }
        if (existing != null) {
            enterBrowser()
            selectTab(existing.id)
            return
        }
        if (app.requiresAppLogin && !Session.isActive(this)) {
            pendingStartUrl = app.url
            presentLogin()
            return
        }
        enterBrowser()
        loadInActiveTab(app.url)
    }

    private fun updateLoginButton() {
        if (!::loginButton.isInitialized) return
        if (verifyingLogin) {
            loginButton.isEnabled = false
            loginButton.alpha = 0.7f
            loginButton.text = "Přihlašuji…"
            return
        }
        loginButton.isEnabled = true
        loginButton.alpha = 1f
        loginButton.text = "Přihlásit"
    }

    private fun hideLoginOverlay() {
        hideKeyboard()
        loginOverlay.visibility = View.GONE
        loginFormColumn.visibility = View.GONE
        loginFormScroll.visibility = View.GONE
        setCertBannerVisible(false)
        updateLoginButton()
    }

    private fun submitLogin() {
        if (verifyingLogin) return

        val user = usernameInput.text?.toString()?.trim().orEmpty()
        val pass = passwordInput.text?.toString().orEmpty()
        if (user.isEmpty()) {
            usernameLayout.error = "Zadejte jméno"
            usernameInput.requestFocus()
            return
        }
        usernameLayout.error = null
        if (pass.isEmpty()) {
            passwordLayout.error = "Zadejte heslo"
            passwordInput.requestFocus()
            return
        }
        passwordLayout.error = null
        hideKeyboard()

        pendingCredentials = Credentials(user, pass)
        verifyingLogin = true
        authChallengeCounts.clear()
        authFailedViews.clear()
        cancelPendingAuth()
        updateLoginButton()
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        mainHandler.postDelayed(loginTimeoutRunnable, LOGIN_TIMEOUT_MS)

        val url = Destinations.LOGIN_URL
        AuthProbe.kill(this)
        if (!AuthHandoff.put(this, user, pass, url)) {
            failLogin("Přihlášení se nepodařilo připravit. Zkuste to znovu.")
            return
        }
        val probeIntent = AuthProbeActivity.intent(this)
        mainHandler.postDelayed({
            if (!verifyingLogin) {
                AuthHandoff.clear(this)
                return@postDelayed
            }
            authProbeLauncher.launch(probeIntent)
        }, 150)
    }

    private fun succeedLogin() {
        if (!verifyingLogin) return
        val creds = pendingCredentials ?: return
        verifyingLogin = false
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        Session.start(this, creds.username, creds.password)
        pendingCredentials = null
        cancelPendingAuth()
        usernameLayout.error = null
        passwordLayout.error = null
        usernameInput.setText("")
        passwordInput.setText("")
        AuthProbe.kill(this)
        loadUnloadedTabsAfterLogin()
        val url = pendingStartUrl
        pendingStartUrl = null
        pendingResumeUrl = null
        if (url.isNullOrBlank() || url == Destinations.HOME_URL) {
            presentHome()
            startBookmarkPrefetch()
            return
        }
        enterBrowser()
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            selectTab(existing.id)
        } else if (activeTab?.isHome == true) {
            loadInActiveTab(url)
        } else {
            openInNewTab(url)
        }
        startBookmarkPrefetch()
    }

    private fun failLogin(message: String?, stayOnForm: Boolean = true) {
        if (isFinishing) return
        val missingCerts = SslMessages.isMissingDeviceCerts(message)
        if (missingCerts) {
            lastTrustResult = DeviceTrust.Result.Untrusted
            setCertBannerVisible(true)
        }
        if (!verifyingLogin && pendingCredentials == null) {
            if (message != null && !missingCerts) passwordLayout.error = message
            return
        }
        verifyingLogin = false
        pendingCredentials = null
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        cancelPendingAuth()
        AuthProbe.kill(this)
        updateLoginButton()
        if (message != null && !missingCerts) {
            passwordLayout.error = message
            passwordInput.requestFocus()
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        } else if (missingCerts) {
            passwordLayout.error = null
        }
        if (stayOnForm) presentLogin()
    }

    private fun setCertBannerVisible(visible: Boolean) {
        if (!::certBanner.isInitialized) return
        certBanner.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Před přihlášením zkusí HTTPS proti systémovým CA. Když tablet
     * serveru nedůvěřuje, banner se objeví u nápisu Přihlášení.
     */
    private fun refreshCertBanner() {
        if (!::certBanner.isInitialized) return
        if (!isVpnActive()) {
            setCertBannerVisible(false)
            return
        }
        when (lastTrustResult) {
            DeviceTrust.Result.Untrusted -> setCertBannerVisible(true)
            DeviceTrust.Result.Trusted -> setCertBannerVisible(false)
            else -> Unit
        }
        if (verifyingLogin || trustProbeInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (lastTrustResult != null && now - lastTrustProbeAt < 4_000L) return

        trustProbeInFlight = true
        val seq = ++trustProbeSeq
        val url = Destinations.LOGIN_URL
        Thread({
            val result = DeviceTrust.probe(url)
            mainHandler.post {
                if (seq != trustProbeSeq) return@post
                trustProbeInFlight = false
                lastTrustProbeAt = SystemClock.elapsedRealtime()
                lastTrustResult = result
                if (gate != Gate.LOGIN || loginOverlay.visibility != View.VISIBLE) return@post
                when (result) {
                    DeviceTrust.Result.Untrusted -> setCertBannerVisible(true)
                    DeviceTrust.Result.Trusted -> setCertBannerVisible(false)
                    DeviceTrust.Result.Unknown -> Unit
                }
            }
        }, "os-trust-probe").start()
    }

    // ── IME ─────────────────────────────────────────────────────────────

    private val visibleFrame = Rect()
    private var imeLayoutAttached = false
    private var imeLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    private fun bindImeInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (gate == Gate.BROWSER || urlDialog?.isShowing == true) {
                setImePadding(v, 0)
            } else {
                setImePadding(v, imeBottom)
            }
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.NONE)
                .build()
        }
        imeLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            applyVisibleFrameImePadding(root)
        }
        attachImeLayoutListener(true)
        ViewCompat.requestApplyInsets(root)
    }

    private fun attachImeLayoutListener(attach: Boolean) {
        val root = findViewById<View>(R.id.root) ?: return
        val listener = imeLayoutListener ?: return
        if (attach == imeLayoutAttached) return
        if (attach) {
            root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        } else {
            root.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            setImePadding(root, 0)
        }
        imeLayoutAttached = attach
    }

    private fun applyVisibleFrameImePadding(root: View) {
        if (urlDialog?.isShowing == true) {
            setImePadding(root, 0)
            return
        }
        val fromInsets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        if (fromInsets > 0) {
            setImePadding(root, fromInsets)
            return
        }
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val overlap = (loc[1] + root.height - visibleFrame.bottom).coerceAtLeast(0)
        val minKeyboard = (80 * resources.displayMetrics.density).toInt()
        setImePadding(root, if (overlap > minKeyboard) overlap else 0)
    }

    private fun setImePadding(v: View, imeBottom: Int) {
        if (v.paddingBottom == imeBottom) return
        v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, imeBottom)
    }

    private fun hideKeyboard(from: View? = null) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val dialogWindow = urlDialog?.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.ime())
            dialogWindow?.insetsController?.hide(android.view.WindowInsets.Type.ime())
        }
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
        dialogWindow?.let { w ->
            WindowInsetsControllerCompat(w, w.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }

        val tokens = listOfNotNull(
            from?.windowToken,
            dialogWindow?.decorView?.windowToken,
            currentFocus?.windowToken,
            if (::loginOverlay.isInitialized) loginOverlay.windowToken else null,
            window.decorView.windowToken
        ).distinct()
        tokens.forEach { token -> imm.hideSoftInputFromWindow(token, 0) }

        if (::usernameInput.isInitialized) usernameInput.clearFocus()
        if (::passwordInput.isInitialized) passwordInput.clearFocus()
        from?.clearFocus()
        activeWebView?.clearFocus()
        if (gate == Gate.HOME) {
            if (::homeOverlay.isInitialized) homeOverlay.requestFocus()
        } else if (gate != Gate.BROWSER) {
            if (::loginOverlay.isInitialized) loginOverlay.requestFocus()
        } else if (::webContainer.isInitialized) {
            webContainer.isFocusableInTouchMode = true
            webContainer.requestFocus()
        }
        findViewById<View>(R.id.root)?.let { setImePadding(it, 0) }
    }

    private fun isImeVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) return true
        val root = window.decorView.findViewById<View>(R.id.root)
        val minKeyboard = (80 * resources.displayMetrics.density).toInt()
        return root != null && root.paddingBottom > minKeyboard
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN &&
            gate != Gate.BROWSER &&
            isImeVisible() &&
            urlDialog?.isShowing != true
        ) {
            val root = urlDialog?.window?.decorView ?: window.decorView
            if (!isTouchOnEditText(root, ev)) hideKeyboard()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchOnEditText(view: View, ev: MotionEvent): Boolean {
        if (view is android.widget.EditText && view.isShown) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val x = ev.rawX
            val y = ev.rawY
            if (x >= loc[0] && x <= loc[0] + view.width &&
                y >= loc[1] && y <= loc[1] + view.height
            ) {
                return true
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (isTouchOnEditText(view.getChildAt(i), ev)) return true
            }
        }
        return false
    }

    // ── Dialogy ─────────────────────────────────────────────────────────

    private fun showCertWarning(error: SslError?) {
        if (dialogShown) return
        if (shouldSuppressPageErrorDialogs()) return
        dialogShown = true
        progressBar.visibility = View.GONE
        SslPolicy.showSslRejected(this, error) { dialogShown = false }
    }

    private fun showNetworkWarning(code: Int?, url: Uri?) {
        if (dialogShown) return
        if (shouldSuppressPageErrorDialogs()) return
        dialogShown = true
        progressBar.visibility = View.GONE

        val vpnHint = if (isVpnActive()) {
            "VPN je připojená. Server možná neběží nebo je adresa chybná."
        } else {
            "VPN není připojená. Interní stránky jsou dostupné jen přes " +
                "Cisco AnyConnect — připojte se a zkuste to znovu."
        }

        val detail = when (code) {
            WebViewClient.ERROR_HOST_LOOKUP -> "Adresu serveru se nepodařilo přeložit."
            WebViewClient.ERROR_CONNECT -> "K serveru se nepodařilo připojit."
            WebViewClient.ERROR_TIMEOUT -> "Server neodpověděl včas."
            WebViewClient.ERROR_PROXY_AUTHENTICATION -> "Firemní proxy vyžaduje přihlášení."
            else -> "Stránku se nepodařilo načíst."
        }

        warningDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Nepodařilo se připojit")
            .setMessage("$detail\n\n$vpnHint\n\nAdresa: ${url?.host ?: "neznámá"}")
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                activeWebView?.reload()
            }
            .setNegativeButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener {
                dialogShown = false
                warningDialog = null
            }
            .show()
    }

    private fun shouldSuppressPageErrorDialogs(): Boolean {
        return gate != Gate.BROWSER || !isConnectionOk() || verifyingLogin
    }

    private fun dismissWarningDialog() {
        warningDialog?.setOnDismissListener(null)
        warningDialog?.dismiss()
        warningDialog = null
        dialogShown = false
    }

    private fun confirmLogout() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null)
        MaterialAlertDialogBuilder(this, R.style.LogoutDialog)
            .setView(view)
            .setPositiveButton("Smazat") { _, _ -> clearStoredData() }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    /** 401 handler musí dostat proceed nebo cancel, jinak WebView visí. */
    private fun cancelPendingAuth() {
        try {
            pendingAuthHandler?.cancel()
        } catch (_: Exception) {
        }
        pendingAuthHandler = null
        awaitingHttpAuth = false
    }

    /**
     * Smaže cookies, PSST údaje i Chromium profil a restartuje proces.
     * NTLM jinak zůstane v connection poolu a stránky by zůstaly přihlášené.
     */
    private fun clearStoredData() {
        cancelPendingAuth()
        pendingCredentials = null
        verifyingLogin = false
        pendingResumeUrl = null
        pendingStartUrl = null
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        AuthProbe.kill(this)
        TrialIdle.clear(this)

        Session.end(this)
        TrialIsolation.reset()
        Session.wipeBrowser(this, tabs.mapNotNull { it.webView })
        destroyAllTabs()
        Session.deleteChromiumProfile(this)

        val intent = Intent(this, WebViewActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            usernameInput.setText("")
            passwordInput.setText("")
            lastContentGate = Gate.HOME
            refreshGate()
            return
        }
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }

    private fun openLinkSettings() {
        val steps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "1. Klepni na „Otevírání odkazů\"\n" +
                "2. Zapni „Otevírat podporované odkazy\"\n" +
                "3. V „Podporované webové adresy\" zaškrtni psst.tudc.cz a " +
                "test.psst.tudc.cz"
        } else {
            "1. Klepni na „Otevírat ve výchozím nastavení\"\n" +
                "2. Zvol „Otevírat v této aplikaci\""
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Nastavit otevírání odkazů")
            .setMessage(
                "Android sám nenabídne aplikaci u odkazů, dokud ji nepovolíš " +
                    "v nastavení.\n\n$steps\n\n" +
                    "Pokud Outlook odkazy i tak otevírá sám, vypni jeho vestavěný " +
                    "prohlížeč: Outlook → Nastavení → Obecné → Otevírat odkazy."
            )
            .setPositiveButton("Otevřít nastavení") { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Zavřít", null)
            .show()
    }

    /**
     * Na pozadí hned pryč relace a cookies.
     * Připnuté karty zůstanou načtené; ostatní jen jako URL v liště.
     * U nepřipnuté otevřené karty po návratu vyskočí přihlášení.
     */
    private fun consumeTrialIdleTimeout(): Boolean {
        if (!TrialSettings.isTrial() || verifyingLogin) return false
        if (!TrialIdle.shouldWipe(this)) {
            TrialIdle.clear(this)
            return false
        }
        TrialIdle.clear(this)
        endTrialSessionKeepTabs()
        return true
    }

    private fun endTrialSessionKeepTabs() {
        cancelPendingAuth()
        pendingCredentials = null
        verifyingLogin = false
        pendingResumeUrl = null
        pendingStartUrl = null
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        AuthProbe.kill(this)

        Session.end(this)
        TrialIsolation.reset()
        Session.clearAuthCaches(this)
        destroyPrefetchViews()
        val keepAttached = activeTab?.takeIf {
            it.pinned && !it.isHome && it.webView != null && gate == Gate.BROWSER
        }
        tabs.filter { !it.isHome && !it.pinned }.forEach { destroyWebView(it) }
        tabs.filter { it.pinned }.forEach { tab ->
            if (tab.id == keepAttached?.id) {
                tab.webView?.onPause()
                return@forEach
            }
            tab.webView?.let { parkBackgroundWebView(it, keepAlive = true) }
        }
        if (keepAttached != null) needsPinnedWebViewReveal = true
        persistOpenTabsFromTabs()
        if (tabs.isEmpty()) {
            openNewHomeTab()
        } else {
            refreshTabStrip()
        }
    }

    private fun currentSaveableUrl(): String? {
        if (gate != Gate.BROWSER) return null
        val url = activeTab?.takeUnless { it.isHome }?.url ?: return null
        if (url.isBlank() || url == Destinations.HOME_URL) return null
        return url
    }

    private fun openSavedUrl(url: String) {
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            enterBrowser()
            selectTab(existing.id)
            return
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        enterBrowser()
        if (activeTab?.isHome == true) loadInActiveTab(url)
        else openInNewTab(url)
    }

    private fun showTrialBookmarkMenu() {
        dismissBookmarkPopup()
        val content = layoutInflater.inflate(R.layout.popup_trial_bookmarks, null)
        val density = resources.displayMetrics.density
        val width = minOf(
            (resources.displayMetrics.widthPixels - (24 * density).toInt()),
            (400 * density).toInt()
        )
        val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = 12f * density
        popup.setOnDismissListener { bookmarkPopup = null }
        bookmarkPopup = popup
        bindTrialBookmarkMenu(content, popup)
        val anchor = toolbar.findViewById<View>(R.id.action_folders) ?: toolbar
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    private fun bindTrialBookmarkMenu(content: View, popup: PopupWindow) {
        val save = content.findViewById<TextView>(R.id.bookmarkSave)
        val divider = content.findViewById<View>(R.id.bookmarkDivider)
        val empty = content.findViewById<TextView>(R.id.bookmarkEmpty)
        val list = content.findViewById<LinearLayout>(R.id.bookmarkList)
        val scroll = content.findViewById<View>(R.id.bookmarkScroll)
        content.findViewById<View>(R.id.bookmarkAddFolder).setOnClickListener {
            promptFolderName("Nová skupina", "", "Přidat") { name ->
                val folder = TrialBookmarks.addFolder(this, name)
                if (folder == null) {
                    Toast.makeText(this, "Maximum je ${TrialBookmarks.MAX_FOLDERS} skupin", Toast.LENGTH_SHORT).show()
                    return@promptFolderName
                }
                populateHomeBookmarks()
                if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
            }
        }
        val saveUrl = currentSaveableUrl()?.takeIf { !TrialBookmarks.isSaved(this, it) }
        save.visibility = if (saveUrl != null) View.VISIBLE else View.GONE
        save.setOnClickListener {
            val url = currentSaveableUrl() ?: return@setOnClickListener
            popup.dismiss()
            savePageThenRename(url)
        }
        val items = TrialBookmarks.load(this)
        val folders = TrialBookmarks.loadFolders(this)
        val groups = TrialBookmarks.grouped(
            items,
            folders,
            includeEmptyFolders = true,
            foldersFirst = true
        )
        list.removeAllViews()
        empty.visibility = if (items.isEmpty() && folders.isEmpty()) View.VISIBLE else View.GONE
        divider.visibility = if (saveUrl != null && (items.isNotEmpty() || folders.isNotEmpty())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val density = resources.displayMetrics.density
        val maxH = minOf(
            (resources.displayMetrics.heightPixels * 0.55f).toInt(),
            (480 * density).toInt()
        )
        val longList = items.size + folders.size > 5
        scroll.layoutParams = scroll.layoutParams.apply {
            height = if (longList) maxH else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val inflater = LayoutInflater.from(this)
        renderBookmarkGroups(
            parent = list,
            groups = groups,
            inflater = inflater,
            foldersFirst = true,
            onFolderHeader = { header, folder ->
                header.findViewById<TextView>(R.id.groupHeaderTitle).text = folder.title
                val chevron = header.findViewById<ImageView>(R.id.groupHeaderChevron)
                chevron.rotation = if (folder.collapsed) 0f else 180f
                bindFolderHeaderActions(header, folder, popup, content)
            },
            onFolderClick = { folder ->
                TrialBookmarks.setFolderCollapsed(this, folder.id, !folder.collapsed)
                if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
            },
            onFolderLongClick = { folder ->
                showFolderActions(folder, content, popup)
            },
            onItems = { items ->
                items.forEach { item ->
                    val row = inflater.inflate(R.layout.item_trial_bookmark, list, false)
                    row.findViewById<TextView>(R.id.bookmarkTitle).text = item.title
                    row.findViewById<TextView>(R.id.bookmarkUrl).text =
                        item.url.removePrefix("https://").removePrefix("http://")
                    row.setOnClickListener {
                        dismissBookmarkPopup()
                        openSavedUrl(item.url)
                    }
                    bindBookmarkActions(
                        pin = row.findViewById(R.id.bookmarkPin),
                        rename = row.findViewById(R.id.bookmarkRename),
                        delete = row.findViewById(R.id.bookmarkDelete),
                        item = item,
                        afterChange = {
                            if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                        }
                    )
                    list.addView(row)
                }
            }
        )
    }

    private fun renderBookmarkGroups(
        parent: LinearLayout,
        groups: List<BookmarkGroup>,
        inflater: LayoutInflater,
        foldersFirst: Boolean,
        hideCollapsedItems: Boolean = true,
        onFolderHeader: (View, TrialBookmarkFolder) -> Unit,
        onFolderClick: ((TrialBookmarkFolder) -> Unit)?,
        onFolderLongClick: ((TrialBookmarkFolder) -> Unit)?,
        onItems: (List<TrialBookmark>) -> Unit
    ) {
        val hasFoldered = groups.any { it.folder != null && it.items.isNotEmpty() }
        val hasUnfiled = groups.any { it.folder == null && it.items.isNotEmpty() }
        var sectionDividerShown = false
        groups.forEach { group ->
            val folder = group.folder
            if (!sectionDividerShown && hasFoldered && hasUnfiled) {
                val showDivider = if (foldersFirst) folder == null else folder != null
                if (showDivider) {
                    parent.addView(
                        inflater.inflate(R.layout.item_bookmark_section_divider, parent, false)
                    )
                    sectionDividerShown = true
                }
            }
            if (folder != null) {
                val header = inflater.inflate(R.layout.item_bookmark_group_header, parent, false)
                onFolderHeader(header, folder)
                onFolderClick?.let { click ->
                    header.setOnClickListener { click(folder) }
                }
                onFolderLongClick?.let { longClick ->
                    header.setOnLongClickListener {
                        longClick(folder)
                        true
                    }
                }
                parent.addView(header)
                if (hideCollapsedItems && folder.collapsed) return@forEach
            }
            if (group.items.isNotEmpty()) onItems(group.items)
        }
    }

    private fun folderItems(folderId: String): List<TrialBookmark> =
        TrialBookmarks.itemsInFolder(this, folderId)

    private fun bindFolderHeaderActions(
        header: View,
        folder: TrialBookmarkFolder,
        popup: PopupWindow? = null,
        popupContent: View? = null
    ) {
        val items = folderItems(folder.id)
        val openAll = header.findViewById<ImageButton>(R.id.groupHeaderOpenAll)
        val pinAll = header.findViewById<ImageButton>(R.id.groupHeaderPinAll)
        val tabStack = header.findViewById<ImageButton>(R.id.groupHeaderTabStack)
        val show = items.isNotEmpty()
        openAll.visibility = if (show) View.VISIBLE else View.GONE
        pinAll.visibility = if (show) View.VISIBLE else View.GONE
        tabStack.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        openAll.setOnClickListener {
            openFolderInTabs(folder)
            popup?.dismiss()
        }
        pinAll.setOnClickListener {
            pinFolderInTabs(folder)
            populateHomeBookmarks()
            if (popup?.isShowing == true && popupContent != null) {
                bindTrialBookmarkMenu(popupContent, popup)
            }
        }
        tabStack.setOnClickListener {
            toggleFolderTabGrouped(folder)
            if (popup?.isShowing == true && popupContent != null) {
                bindTrialBookmarkMenu(popupContent, popup)
            }
        }
        updateFolderPinGlyph(pinAll, folder)
        updateFolderStackGlyph(tabStack, folder)
    }

    private fun updateFolderPinGlyph(pin: ImageButton, folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        val allPinned = items.isNotEmpty() && items.all { isUrlPinned(it.url) }
        pin.contentDescription = getString(
            if (allPinned) R.string.folder_unpin_all else R.string.folder_pin_all
        )
        ImageViewCompat.setImageTintList(
            pin,
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (allPinned) R.color.saved else R.color.ink_soft)
            )
        )
    }

    private fun updateFolderStackGlyph(stack: ImageButton, folder: TrialBookmarkFolder) {
        stack.contentDescription = getString(
            if (folder.tabGrouped) R.string.folder_tab_stack_off else R.string.folder_tab_stack_on
        )
        ImageViewCompat.setImageTintList(
            stack,
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (folder.tabGrouped) R.color.saved else R.color.ink_soft
                )
            )
        )
    }

    private fun toggleFolderTabGrouped(folder: TrialBookmarkFolder) {
        val grouped = !folder.tabGrouped
        TrialBookmarks.setFolderTabGrouped(this, folder.id, grouped)
        refreshTabStrip()
        populateHomeBookmarks()
        Toast.makeText(
            this,
            getString(if (grouped) R.string.folder_tab_stacked else R.string.folder_tab_expanded),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openFolderInTabs(folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.folder_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val needsLogin = items.firstOrNull { needsAppLogin(it.url) && !Session.isActive(this) }
        if (needsLogin != null) {
            pendingStartUrl = needsLogin.url
            presentLogin()
            return
        }
        dismissBookmarkPopup()
        var opened = 0
        var alreadyOpen = 0
        var focusId: Long? = null
        for (item in items) {
            val existing = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (existing != null) {
                alreadyOpen++
                if (focusId == null) focusId = existing.id
                continue
            }
            if (tabs.size >= MAX_TABS) break
            val tab = addTabForUrl(item.url, select = false) ?: break
            opened++
            if (focusId == null) focusId = tab.id
        }
        if (focusId != null) {
            enterBrowser()
            selectTab(focusId)
        }
        val total = opened + alreadyOpen
        val message = when {
            opened == 0 && alreadyOpen > 0 ->
                getString(R.string.folder_tabs_already_open)
            total < items.size ->
                getString(R.string.folder_opened_tabs, total, items.size) +
                    " — maximum je $MAX_TABS karet"
            opened > 0 ->
                getString(R.string.folder_opened_tabs, opened, items.size)
            else -> "Maximum je $MAX_TABS karet"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun pinFolderInTabs(folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.folder_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val allPinned = items.all { isUrlPinned(it.url) }
        if (allPinned) {
            unpinFolderInTabs(items)
            return
        }
        val needsLogin = items.firstOrNull { needsAppLogin(it.url) && !Session.isActive(this) }
        if (needsLogin != null) {
            pendingStartUrl = needsLogin.url
            presentLogin()
            return
        }
        dismissBookmarkPopup()
        var pinned = 0
        for (item in items) {
            if (isUrlPinned(item.url)) continue
            if (!pinUrl(item.url, quiet = true, select = false)) break
            pinned++
        }
        queueBookmarkPrefetch(items.map { it.url })
        enterBrowser()
        refreshTabStrip()
        populateHomeBookmarks()
        val message = when {
            pinned > 0 -> getString(R.string.folder_pinned_tabs, pinned, items.size)
            else -> "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun unpinFolderInTabs(items: List<TrialBookmark>) {
        dismissBookmarkPopup()
        var unpinned = 0
        for (item in items) {
            val tab = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (tab != null && tab.pinned) {
                setTabPinned(tab, false, quiet = true)
                unpinned++
            }
        }
        refreshTabStrip()
        populateHomeBookmarks()
        Toast.makeText(
            this,
            getString(R.string.folder_unpinned_tabs, unpinned),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showFolderActions(folder: TrialBookmarkFolder, content: View, popup: PopupWindow) {
        MaterialAlertDialogBuilder(this)
            .setTitle(folder.title)
            .setItems(
                arrayOf(
                    "Otevřít vše v kartách",
                    if (folderItems(folder.id).all { isUrlPinned(it.url) }) {
                        "Odepnout vše"
                    } else {
                        "Připnout a přednačíst vše"
                    },
                    if (folder.tabGrouped) "Rozbalit na liště" else "Smrštit na liště do jedné karty",
                    "Přejmenovat",
                    "Odebrat skupinu"
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        openFolderInTabs(folder)
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    1 -> {
                        pinFolderInTabs(folder)
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    2 -> {
                        toggleFolderTabGrouped(folder)
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    3 -> promptFolderName("Přejmenovat skupinu", folder.title, "Hotovo") { name ->
                        if (!TrialBookmarks.renameFolder(this, folder.id, name)) {
                            Toast.makeText(this, "Skupinu nelze přejmenovat", Toast.LENGTH_SHORT).show()
                            return@promptFolderName
                        }
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    4 -> {
                        TrialBookmarks.removeFolder(this, folder.id)
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    @SuppressLint("RestrictedApi")
    private fun showHomeBookmarkMenu(anchor: View, item: TrialBookmark) {
        val popup = PopupMenu(this, anchor, Gravity.END)
        val pinTitle = if (isUrlPinned(item.url)) "Odepnout" else "Připnout na lištu"
        popup.menu.add(0, 1, 0, pinTitle).setIcon(R.drawable.ic_wifi_off)
        popup.menu.add(0, 2, 1, "Přejmenovat").setIcon(R.drawable.ic_edit)
        popup.menu.add(0, 3, 2, "Odebrat").setIcon(R.drawable.ic_close)
        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    togglePinForUrl(item.url)
                    populateHomeBookmarks()
                }
                2 -> promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
                    TrialBookmarks.rename(this, item.id, title, folderId)
                    populateHomeBookmarks()
                    applySavedTitleToTabs(item.url, title)
                }
                3 -> {
                    TrialBookmarks.remove(this, item.id)
                    populateHomeBookmarks()
                    forgetSavedTitleOnTabs(item.url)
                }
            }
            true
        }
        popup.show()
    }

    private fun bindBookmarkActions(
        pin: ImageButton,
        rename: View,
        delete: View,
        item: TrialBookmark,
        afterChange: () -> Unit = {}
    ) {
        bindBookmarkPin(pin, item.url, afterChange)
        rename.setOnClickListener {
            promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
                TrialBookmarks.rename(this, item.id, title, folderId)
                populateHomeBookmarks()
                applySavedTitleToTabs(item.url, title)
                afterChange()
            }
        }
        delete.setOnClickListener {
            TrialBookmarks.remove(this, item.id)
            populateHomeBookmarks()
            forgetSavedTitleOnTabs(item.url)
            afterChange()
        }
    }

    private fun bindBookmarkPin(pin: ImageButton, url: String, afterChange: () -> Unit = {}) {
        updatePinGlyph(pin, url)
        pin.setOnClickListener {
            togglePinForUrl(url)
            populateHomeBookmarks()
            afterChange()
        }
    }

    private fun updatePinGlyph(pin: ImageButton, url: String) {
        val pinned = isUrlPinned(url)
        pin.contentDescription = if (pinned) "Odepnout" else "Připnout na lištu"
        ImageViewCompat.setImageTintList(
            pin,
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (pinned) R.color.saved else R.color.ink_soft)
            )
        )
    }

    private fun isUrlPinned(url: String): Boolean =
        tabs.any { !it.isHome && it.pinned && samePage(it.url, url) }

    /** Uloží stránku hned a nabídne přejmenování. */
    private fun savePageThenRename(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed == Destinations.HOME_URL) {
            Toast.makeText(this, "Tuhle stránku nelze uložit", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = TrialBookmarks.findByUrl(this, trimmed)
        if (existing != null) {
            renameSavedPage(trimmed)
            return
        }
        val suggested = tabs.filter { !it.isHome }.find { samePage(it.url, trimmed) }
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: tabLabel(trimmed)
        val added = TrialBookmarks.add(this, suggested, trimmed)
        if (added == null) {
            Toast.makeText(this, "Složka je plná", Toast.LENGTH_SHORT).show()
            return
        }
        populateHomeBookmarks()
        applySavedTitleToTabs(trimmed, added.title)
        promptBookmarkLabel("Přejmenovat", added.title, added.folderId, "Hotovo") { title, folderId ->
            TrialBookmarks.rename(this, added.id, title, folderId)
            populateHomeBookmarks()
            applySavedTitleToTabs(trimmed, title)
        }
    }

    /** Jen změní popisek uložené stránky, nic nového nepřidá. */
    private fun renameSavedPage(url: String) {
        val item = TrialBookmarks.findByUrl(this, url)
        if (item == null) {
            Toast.makeText(this, "Stránka není uložená", Toast.LENGTH_SHORT).show()
            return
        }
        promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
            TrialBookmarks.rename(this, item.id, title, folderId)
            populateHomeBookmarks()
            applySavedTitleToTabs(url, title)
        }
    }

    /** Odebere stránku ze složky. Karta zůstane otevřená, jen bez uloženého jména. */
    private fun removeSavedPage(url: String) {
        val item = TrialBookmarks.findByUrl(this, url) ?: return
        TrialBookmarks.remove(this, item.id)
        populateHomeBookmarks()
        forgetSavedTitleOnTabs(url)
    }

    private fun forgetSavedTitleOnTabs(url: String) {
        tabs.filter { !it.isHome && samePage(it.url, url) }.forEach { tab ->
            tab.title = Destinations.tabTitle(tab.url)
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
    }

    private fun promptBookmarkLabel(
        title: String,
        initial: String,
        currentFolderId: String?,
        confirmLabel: String = "Uložit",
        onSave: (String, String?) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_bookmark_label, null)
        val layout = view.findViewById<TextInputLayout>(R.id.bookmarkLabelLayout)
        val input = view.findViewById<TextInputEditText>(R.id.bookmarkLabelInput)
        val folderLayout = view.findViewById<TextInputLayout>(R.id.bookmarkFolderLayout)
        val folderInput = view.findViewById<AutoCompleteTextView>(R.id.bookmarkFolderInput)
        val newLayout = view.findViewById<TextInputLayout>(R.id.bookmarkFolderNewLayout)
        val newInput = view.findViewById<TextInputEditText>(R.id.bookmarkFolderNewInput)
        val confirm = view.findViewById<MaterialButton>(R.id.bookmarkDialogConfirm)
        val cancel = view.findViewById<View>(R.id.bookmarkDialogCancel)
        view.findViewById<TextView>(R.id.bookmarkDialogTitle).text = title
        confirm.text = confirmLabel
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        val folders = TrialBookmarks.loadFolders(this)
        val labels = mutableListOf(FOLDER_NONE)
        labels.addAll(folders.map { it.title })
        labels.add(FOLDER_NEW)
        folderInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
        folderInput.threshold = 0
        folderInput.keyListener = null
        val currentTitle = folders.find { it.id == currentFolderId }?.title ?: FOLDER_NONE
        folderInput.setText(currentTitle, false)
        fun syncNewFolderField() {
            val selected = folderInput.text?.toString().orEmpty()
            newLayout.visibility = if (selected == FOLDER_NEW) View.VISIBLE else View.GONE
            if (selected != FOLDER_NEW) newLayout.error = null
        }
        syncNewFolderField()
        folderInput.setOnClickListener { folderInput.showDropDown() }
        folderInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) folderInput.showDropDown() }
        folderInput.setOnItemClickListener { _, _, _, _ ->
            syncNewFolderField()
            if (newLayout.visibility == View.VISIBLE) {
                view.post {
                    (view as? ScrollView)?.smoothScrollTo(0, newLayout.bottom)
                }
            }
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        bookmarkLabelDialog?.dismiss()
        bookmarkLabelDialog = dialog
        cancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            val label = input.text?.toString().orEmpty().trim()
            if (label.isEmpty()) {
                layout.error = "Zadejte popisek"
                return@setOnClickListener
            }
            layout.error = null
            val selected = folderInput.text?.toString().orEmpty()
            val folderId = when (selected) {
                FOLDER_NONE, "" -> null
                FOLDER_NEW -> {
                    val name = newInput.text?.toString().orEmpty().trim()
                    if (name.isEmpty()) {
                        newLayout.error = "Zadejte název skupiny"
                        return@setOnClickListener
                    }
                    val folder = TrialBookmarks.addFolder(this, name)
                    if (folder == null) {
                        newLayout.error = "Maximum je ${TrialBookmarks.MAX_FOLDERS} skupin"
                        return@setOnClickListener
                    }
                    folder.id
                }
                else -> folders.find { it.title == selected }?.id
                    ?: TrialBookmarks.addFolder(this, selected)?.id
            }
            folderLayout.error = null
            newLayout.error = null
            dialog.dismiss()
            onSave(label, folderId)
        }
        dialog.setOnShowListener {
            bindDialogIme(dialog, view)
            polishBookmarkDialogWindow(dialog)
        }
        dialog.setOnDismissListener {
            if (bookmarkLabelDialog === dialog) bookmarkLabelDialog = null
        }
        bindDialogIme(dialog, view)
        dialog.show()
        polishBookmarkDialogWindow(dialog)
        input.requestFocus()
    }

    private fun promptFolderName(
        title: String,
        initial: String,
        confirmLabel: String,
        onSave: (String) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_bookmark_label, null)
        val layout = view.findViewById<TextInputLayout>(R.id.bookmarkLabelLayout)
        val input = view.findViewById<TextInputEditText>(R.id.bookmarkLabelInput)
        val confirm = view.findViewById<MaterialButton>(R.id.bookmarkDialogConfirm)
        view.findViewById<View>(R.id.bookmarkFolderLayout).visibility = View.GONE
        view.findViewById<View>(R.id.bookmarkFolderNewLayout).visibility = View.GONE
        view.findViewById<TextView>(R.id.bookmarkDialogTitle).text = title
        layout.hint = "Název skupiny"
        confirm.text = confirmLabel
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        bookmarkLabelDialog?.dismiss()
        bookmarkLabelDialog = dialog
        view.findViewById<View>(R.id.bookmarkDialogCancel).setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            val name = input.text?.toString().orEmpty().trim()
            if (name.isEmpty()) {
                layout.error = "Zadejte název skupiny"
                return@setOnClickListener
            }
            layout.error = null
            dialog.dismiss()
            onSave(name)
        }
        dialog.setOnShowListener {
            bindDialogIme(dialog, view)
            polishBookmarkDialogWindow(dialog)
        }
        dialog.setOnDismissListener {
            if (bookmarkLabelDialog === dialog) bookmarkLabelDialog = null
        }
        bindDialogIme(dialog, view)
        dialog.show()
        polishBookmarkDialogWindow(dialog)
        input.requestFocus()
    }

    private fun polishBookmarkDialogWindow(dialog: AlertDialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val density = resources.displayMetrics.density
        val width = minOf(
            (400 * density).toInt(),
            resources.displayMetrics.widthPixels - (48 * density).toInt()
        )
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Klávesnice dialog nezakryje: zmenší okno a obsah jde scrollovat. */
    private fun bindDialogIme(dialog: AlertDialog, scroll: View) {
        val window = dialog.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        fun capScroll() {
            val ime = ViewCompat.getRootWindowInsets(window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val density = resources.displayMetrics.density
            val chrome = (200 * density).toInt()
            val cap = (resources.displayMetrics.heightPixels - ime - chrome)
                .coerceAtLeast((120 * density).toInt())
            val childH = (scroll as? ViewGroup)?.getChildAt(0)?.measuredHeight
                ?: scroll.measuredHeight
            val lp = scroll.layoutParams ?: return
            val next = if (ime > 0 && childH > cap) cap else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != next) {
                lp.height = next
                scroll.layoutParams = lp
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            scroll.post { capScroll() }
            insets
        }
        scroll.post { capScroll() }
    }

    private fun dismissBookmarkPopup() {
        try {
            bookmarkPopup?.dismiss()
        } catch (_: Exception) {
        }
        bookmarkPopup = null
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
            menu.setGroupDividerEnabled(true)
        }
        menu?.findItem(R.id.action_folders)?.isVisible = true
        menu?.setGroupVisible(R.id.group_logout, !TrialSettings.isTrial())
        if (!TrialSettings.isTrial()) {
            menu?.findItem(R.id.action_logout)?.let { item ->
                val title = SpannableString("Vymazat údaje")
                title.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.alert)),
                    0,
                    title.length,
                    0
                )
                item.title = title
            }
        }
        applyChromeMenu(menu)
        toolbar.post {
            val lp = toolbar.layoutParams
            if (lp.width != LinearLayout.LayoutParams.WRAP_CONTENT) {
                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                toolbar.layoutParams = lp
            }
            applyChromeMenu(menu)
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_folders)?.isVisible = true
        menu?.setGroupVisible(R.id.group_logout, !TrialSettings.isTrial())
        applyChromeMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        hideKeyboard()
        if (chromeOffline) return true
        if (item.itemId == R.id.action_logout) {
            confirmLogout()
            return true
        }
        if (item.itemId == R.id.action_home) {
            if (verifyingLogin) failLogin(null, stayOnForm = false)
            openHomeWindow()
            return true
        }
        if (item.itemId == R.id.action_folders) {
            if (verifyingLogin) return true
            showTrialBookmarkMenu()
            return true
        }
        if (item.itemId == R.id.action_reload) {
            if (verifyingLogin || gate == Gate.LOGIN) return true
            if (gate != Gate.BROWSER) return true
            dialogShown = false
            activeWebView?.reload()
            return true
        }
        if (gate == Gate.LOGIN && item.itemId != R.id.action_link_settings) {
            return true
        }
        return when (item.itemId) {
            R.id.action_open_url -> {
                showOpenUrlDialog()
                true
            }
            R.id.action_new_tab -> {
                if (verifyingLogin) failLogin(null, stayOnForm = false)
                openNewHomeTab()
                true
            }
            R.id.action_link_settings -> {
                openLinkSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isImeVisible()) {
            hideKeyboard()
            return
        }
        if (bookmarkPopup?.isShowing == true) {
            dismissBookmarkPopup()
            return
        }
        if (actionDialog?.isShowing == true) {
            dismissActionSheet()
            return
        }
        if (gate == Gate.HOME) {
            super.onBackPressed()
            return
        }
        if (gate == Gate.LOGIN) {
            if (verifyingLogin) failLogin(null, stayOnForm = false)
            presentHome()
            return
        }
        val wv = activeWebView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            openHomeWindow()
        }
    }
}

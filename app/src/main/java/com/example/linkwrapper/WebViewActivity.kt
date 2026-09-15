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

class WebViewActivity : AppCompatActivity() {



    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_URL = Destinations.PSST_URL
    }

    internal lateinit var toolbar: MaterialToolbar
    internal lateinit var progressBar: LinearProgressIndicator
    internal lateinit var webContainer: FrameLayout
    internal lateinit var tabStrip: LinearLayout
    internal lateinit var tabScroll: HorizontalScrollView

    internal lateinit var loginOverlay: View
    internal lateinit var loginTitle: View
    internal lateinit var loginFormColumn: View
    internal lateinit var connectionBanner: View
    internal lateinit var loginFormScroll: View
    internal lateinit var certBanner: View
    internal lateinit var usernameLayout: TextInputLayout
    internal lateinit var usernameInput: TextInputEditText
    internal lateinit var passwordLayout: TextInputLayout
    internal lateinit var passwordInput: TextInputEditText
    internal lateinit var loginButton: MaterialButton
    internal lateinit var loginEphemeralHint: TextView

    internal lateinit var homeOverlay: View
    internal lateinit var homeAppList: LinearLayout
    internal lateinit var homeBookmarkList: LinearLayout
    internal lateinit var homeVersion: TextView

    internal val tabs = mutableListOf<BrowserTab>()
    internal var activeTabId: Long = -1L
    internal var nextTabId = 1L

    internal val activeTab: BrowserTab?
        get() = tabs.find { it.id == activeTabId }

    internal val activeWebView: WebView?
        get() = activeTab?.webView

    internal var gate = Gate.HOME
    internal var lastContentGate = Gate.HOME
    internal var dialogShown = false
    internal var urlDialog: AlertDialog? = null
    internal var warningDialog: AlertDialog? = null
    internal var bookmarkPopup: PopupWindow? = null
    internal var bookmarkLabelDialog: AlertDialog? = null
    internal var actionDialog: AlertDialog? = null
    /** Po wipe na pozadí znovu připojit viditelný pinnutý WebView — jinak zbělá. */
    internal var needsPinnedWebViewReveal = false

    /** Čekající HTTP auth, když uživatel právě vyplňuje formulář. */
    internal var pendingAuthHandler: HttpAuthHandler? = null
    internal var awaitingHttpAuth = false

    /** Údaje z formuláře, dokud je server neověří. Pak jdou do [Session]. */
    internal var pendingCredentials: Credentials? = null
    internal var verifyingLogin = false

    internal var pendingStartUrl: String? = null
    internal var pendingResumeUrl: String? = null

    internal var networkCallback: ConnectivityManager.NetworkCallback? = null
    internal var trustProbeSeq = 0
    internal var trustProbeInFlight = false
    internal var lastTrustProbeAt = 0L
    internal var lastTrustResult: DeviceTrust.Result? = null
    internal var connectionBannerVisible = false
    /** Bez VPN/internetu: jen připnuté karty, ovládání lišty vypnuté. */
    internal var chromeOffline = false
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val vpnCheckRunnable = Runnable { refreshConnectionBanner() }
    internal val loginTimeoutRunnable = Runnable {
        if (verifyingLogin) failLogin("Přihlášení vypršelo. Zkuste to znovu.")
    }
    internal val chartFitRunnable = Runnable { injectChartFitIntoAllTabs(unlock = true) }

    internal val authChallengeCounts = IdentityHashMap<WebView, MutableMap<String, Int>>()
    internal val authFailedViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())
    internal val chartPerfInjected = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())
    internal val prefetchViews = LinkedHashMap<String, WebView>()
    internal val prefetchQueue = ArrayDeque<String>()
    internal var prefetchLoading = false
    internal var prefetchActiveView: WebView? = null
    internal val prefetchRunnable = Runnable { pumpBookmarkPrefetch() }
    internal val folderTabFocus = mutableMapOf<String, Long>()
    /** Skupiny smrštěné na liště, které uživatel dočasně rozbalil. */
    internal val expandedTabGroups = mutableSetOf<String>()
    /** Karty, které se nepodařilo dokončit — po obnovení sítě znovu načíst. */
    internal val tabsPendingReload = mutableSetOf<Long>()

    internal var pendingGeoOrigin: String? = null
    internal var pendingGeoCallback: GeolocationPermissions.Callback? = null
    internal var geoWatchCount = 0
    internal var geoManager: LocationManager? = null

    internal val loginButtonReady get() = ::loginButton.isInitialized
    internal val certBannerReady get() = ::certBanner.isInitialized
    internal val homeBookmarkListReady get() = ::homeBookmarkList.isInitialized
    internal val homeOverlayReady get() = ::homeOverlay.isInitialized
    internal val loginOverlayReady get() = ::loginOverlay.isInitialized
    internal val usernameInputReady get() = ::usernameInput.isInitialized
    internal val passwordInputReady get() = ::passwordInput.isInitialized
    internal val webContainerReady get() = ::webContainer.isInitialized
    internal val toolbarReady get() = ::toolbar.isInitialized
    internal val connectionBannerReady get() = ::connectionBanner.isInitialized
    internal val tabScrollReady get() = ::tabScroll.isInitialized

    internal val geoBridge = GeoBridge(this)
    internal val geoListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            pushGeoToTabs(location)
        }
    }

    internal val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        finishGeolocationRequest(allowed)
    }

    internal val authProbeLauncher = registerForActivityResult(
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
            (needsPinnedWebViewReveal || wv.parent == null || wv.visibility != View.VISIBLE)
        ) {
            needsPinnedWebViewReveal = false
            reviveBrowserWebView(wv, tab.url)
            syncGeoUpdates()
            return
        }
        needsPinnedWebViewReveal = false
        (activeWebView ?: tabs.firstNotNullOfOrNull { it.webView })?.resumeTimers()
        activeWebView?.onResume()
        syncGeoUpdates()
        if (!chromeOffline && isConnectionOk() &&
            tabs.any { !it.isHome && tabNeedsReconnectReload(it) }
        ) {
            reloadTabsAfterReconnect()
        }
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

    /** Domeček: aktuální karta (PSST, graf) se změní na Domů. */

    // ── Karty ───────────────────────────────────────────────────────────

    /**
     * Po minimalizaci Chromium nechá odpojený (nebo i připojený) povrch bílý.
     * Stejný tah jako přepnutí karty pryč a zpět: znovu vložit do kontejneru.
     */

    /** Znovu načte připnuté karty skupiny; prefetch jen pro URL bez otevřené karty. */

    /** Načte kartu připojenou (neviditelně), aby ChartFit neběžel v odpojeném WebView. */

    /** Načte uložené grafy na pozadí, ať po výpadku sítě zůstanou v RAM. */

    /** Uložený popisek přenese i na otevřené karty se stejnou adresou. */

    // ── WebView ─────────────────────────────────────────────────────────

    // ── URL ─────────────────────────────────────────────────────────────

    /**
     * Dlouhé podržení odkazu ve stránce. Systémovou Chromium nabídku
     * nenecháme — místo ní náš dialog (otevřít na nové kartě / uložit).
     */

    /** Po změně připnutí znovu aplikuje parkování / prioritu rendereru podle tab.pinned. */

    /** Obnoví karty z RAM tohoto procesu. Po úplném vypnutí appky je seznam prázdný. */

    /**
     * Otevře adresu na nové kartě a přepne na ni.
     * Při maximu karet použije volnou kartu Domů, jinak oznámí limit.
     */

    // ── Poloha ──────────────────────────────────────────────────────────


    // ── VPN / síť ───────────────────────────────────────────────────────

    /** VPN i běžný internet — bez toho interní stránky nejedou. */

    /**
     * Offline: jen připnuté karty na liště, zbytek ovládání zašedne.
     * Přepíná se jen při změně stavu, nebo když je potřeba skočit na pin.
     */

    // ── Přihlášení ──────────────────────────────────────────────────────

    /** Starší WebView bez document-start — stihne to jen další grafy, ne první canvas. */

    /**
     * Před přihlášením zkusí HTTPS proti systémovým CA. Když tablet
     * serveru nedůvěřuje, banner se objeví u nápisu Přihlášení.
     */

    // ── IME ─────────────────────────────────────────────────────────────

    internal val visibleFrame = Rect()
    internal var imeLayoutAttached = false
    internal var imeLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

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

    // ── Dialogy ─────────────────────────────────────────────────────────

    /** 401 handler musí dostat proceed nebo cancel, jinak WebView visí. */

    /**
     * Smaže cookies, PSST údaje i Chromium profil a restartuje proces.
     * NTLM jinak zůstane v connection poolu a stránky by zůstaly přihlášené.
     */

    /**
     * Na pozadí hned pryč relace a cookies.
     * Připnuté karty zůstanou načtené; ostatní jen jako URL v liště.
     * U nepřipnuté otevřené karty po návratu vyskočí přihlášení.
     */

    /** Uloží stránku hned a nabídne přejmenování. */

    /** Jen změní popisek uložené stránky, nic nového nepřidá. */

    /** Odebere stránku ze složky. Karta zůstane otevřená, jen bez uloženého jména. */

    /** Klávesnice dialog nezakryje: zmenší okno a obsah jde scrollovat. */

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
        if (item.itemId == R.id.action_reload) {
            if (verifyingLogin || gate == Gate.LOGIN) return true
            if (gate != Gate.BROWSER) return true
            dialogShown = false
            val wv = activeWebView
            if (chromeOffline) {
                if (activeTab?.pinned == true && wv != null) {
                    reviveBrowserWebView(wv, activeTab?.url)
                }
                return true
            }
            wv?.reload()
            return true
        }
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

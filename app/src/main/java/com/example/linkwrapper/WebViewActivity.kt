package com.example.linkwrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
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
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.system.exitProcess

/** Jedna karta prohlížeče — vlastní WebView, název a adresa. */
private class BrowserTab(
    val id: Long,
    val webView: WebView,
    var title: String,
    var url: String
)

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_URL = "https://test.psst.tudc.cz/HSI.Psst.Data"
        private const val MAX_TABS = 8
        private const val MAX_AUTH_ROUNDS = 16
        private const val LOGIN_TIMEOUT_MS = 15_000L
    }

    private enum class Gate { BROWSER, LOGIN, VPN }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webContainer: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView

    private lateinit var loginOverlay: View
    private lateinit var loginTitle: View
    private lateinit var loginFormColumn: View
    private lateinit var vpnOnlyPanel: View
    private lateinit var loginFormScroll: View
    private lateinit var loggedOutBanner: View
    private lateinit var vpnBanner: View
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: Long = -1L
    private var nextTabId = 1L

    private val activeTab: BrowserTab?
        get() = tabs.find { it.id == activeTabId }

    private val activeWebView: WebView?
        get() = activeTab?.webView

    private var gate = Gate.LOGIN
    private var showSignedOutBanner = false
    private var dialogShown = false
    private var urlDialog: AlertDialog? = null
    private var warningDialog: AlertDialog? = null

    /** Čekající HTTP auth, když uživatel právě vyplňuje formulář. */
    private var pendingAuthHandler: HttpAuthHandler? = null
    private var awaitingHttpAuth = false

    /** Údaje z formuláře, dokud je server neověří. Pak jdou do [Session]. */
    private var pendingCredentials: Credentials? = null
    private var verifyingLogin = false

    private var pendingStartUrl: String? = null
    private var pendingResumeUrl: String? = null
    private var savedTabUrls: List<String> = emptyList()
    private var savedActiveTabIndex: Int = 0

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vpnCheckRunnable = Runnable { refreshGate() }
    private val loginTimeoutRunnable = Runnable {
        if (verifyingLogin) failLogin("Přihlášení vypršelo. Zkuste to znovu.")
    }

    private val authChallengeCounts = IdentityHashMap<WebView, MutableMap<String, Int>>()
    private val authFailedViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())

    private var currentHostForUi: String? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        finishGeolocationRequest(allowed)
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
        tabStrip = findViewById(R.id.tabStrip)
        tabScroll = findViewById(R.id.tabScroll)
        bindLoginUi()
        bindImeInsets()

        CookieManager.getInstance().setAcceptCookie(true)

        pendingStartUrl = resolveUrlFromIntent(intent) ?: DEFAULT_URL
        showSignedOutBanner = Session.consumeSignedOutBanner(this)
        refreshGate()
    }

    override fun onStart() {
        super.onStart()
        registerVpnMonitor()
        scheduleVpnCheck()
    }

    override fun onResume() {
        super.onResume()
        WebView.resumeTimers()
        activeWebView?.onResume()
    }

    override fun onPause() {
        tabs.forEach { it.webView.onPause() }
        WebView.pauseTimers()
        super.onPause()
    }

    override fun onStop() {
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
            if (tabs.isEmpty() && gate == Gate.BROWSER && isVpnActive() && Session.isActive(this)) {
                openInNewTab(DEFAULT_URL)
            }
            return
        }
        if (gate != Gate.BROWSER) {
            pendingResumeUrl = url
            pendingStartUrl = url
            return
        }
        openUrlFromExternal(url)
    }

    private fun openUrlFromExternal(url: String) {
        val existing = tabs.find { samePage(it.url, url) }
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
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        super.onDestroy()
    }

    /**
     * Jediná brána: bez VPN → VPN obrazovka; bez relace → přihlášení;
     * jinak home. Home se nenačte, dokud [Session] neexistuje.
     */
    private fun refreshGate() {
        if (isFinishing) return
        if (!isVpnActive()) {
            enterVpnGate()
            return
        }
        vpnBanner.visibility = View.GONE
        if (verifyingLogin) {
            presentLogin()
            return
        }
        if (!Session.isActive(this)) {
            presentLogin()
            return
        }
        presentBrowser()
    }

    private fun presentLogin() {
        gate = Gate.LOGIN
        progressBar.visibility = View.GONE
        loginOverlay.visibility = View.VISIBLE
        loginOverlay.bringToFront()
        vpnOnlyPanel.visibility = View.GONE
        loginFormColumn.visibility = View.VISIBLE
        loginFormScroll.visibility = View.VISIBLE
        loginTitle.visibility = View.VISIBLE
        loggedOutBanner.visibility = if (showSignedOutBanner) View.VISIBLE else View.GONE
        vpnBanner.visibility = if (!isVpnActive()) View.VISIBLE else View.GONE
        updateLoginButton()
        if (!verifyingLogin && loginButton.isEnabled) {
            if (usernameInput.text.isNullOrEmpty()) usernameInput.requestFocus()
            else passwordInput.requestFocus()
        }
    }

    private fun presentBrowser() {
        gate = Gate.BROWSER
        showSignedOutBanner = false
        hideLoginOverlay()
        if (tabs.isEmpty()) {
            restoreSavedTabsOrStart()
        } else {
            activeWebView?.onResume()
        }
    }

    private fun enterVpnGate() {
        dismissWarningDialog()
        tabs.forEach { tab ->
            tab.webView.stopLoading()
            tab.webView.onPause()
        }
        if (gate == Gate.BROWSER && tabs.isNotEmpty()) {
            savedTabUrls = tabs.map { it.url }
            savedActiveTabIndex = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
        }
        if (verifyingLogin) {
            failLogin(null)
        }
        gate = Gate.VPN
        hideKeyboard()
        loginOverlay.visibility = View.VISIBLE
        loginOverlay.bringToFront()
        progressBar.visibility = View.GONE
        vpnOnlyPanel.visibility = View.VISIBLE
        loginFormColumn.visibility = View.GONE
        loginFormScroll.visibility = View.GONE
        loginTitle.visibility = View.GONE
        loggedOutBanner.visibility = View.GONE
        vpnBanner.visibility = View.GONE
    }

    private fun restoreSavedTabsOrStart() {
        val urls = savedTabUrls
        if (urls.isNotEmpty()) {
            urls.forEach { openInNewTab(it) }
            val idx = savedActiveTabIndex.coerceIn(0, tabs.lastIndex)
            selectTab(tabs[idx].id)
        } else {
            openInNewTab(pendingResumeUrl ?: pendingStartUrl ?: DEFAULT_URL)
        }
    }

    // ── Karty ───────────────────────────────────────────────────────────

    private fun openInNewTab(url: String) {
        if (tabs.size >= MAX_TABS) {
            Toast.makeText(this, "Maximum je $MAX_TABS karet", Toast.LENGTH_SHORT).show()
            return
        }
        val webView = createWebView()
        val tab = BrowserTab(
            id = nextTabId++,
            webView = webView,
            title = tabLabel(url),
            url = url
        )
        tabs.add(tab)
        webContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        selectTab(tab.id)
        webView.loadUrl(url)
    }

    private fun selectTab(tabId: Long) {
        if (tabId != activeTabId) hideKeyboard()
        activeTabId = tabId
        tabs.forEach { tab ->
            val selected = tab.id == tabId
            tab.webView.visibility = if (selected) View.VISIBLE else View.GONE
            // Skrytá karta s grafem jinak pořád kreslí a žere GPU/CPU.
            if (selected) tab.webView.onResume() else tab.webView.onPause()
        }
        activeTab?.let { applyChrome(it) }
        refreshTabStrip()
    }

    private fun closeTab(tabId: Long) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closing = tabs.removeAt(index)
        destroyTab(closing)

        if (tabs.isEmpty()) {
            openInNewTab(DEFAULT_URL)
            return
        }
        if (activeTabId == tabId) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.last()
            selectTab(next.id)
        } else {
            refreshTabStrip()
        }
    }

    private fun destroyTab(tab: BrowserTab) {
        authFailedViews.remove(tab.webView)
        authChallengeCounts.remove(tab.webView)
        webContainer.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.onPause()
        tab.webView.webChromeClient = null
        tab.webView.destroy()
    }

    private fun destroyAllTabs() {
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        activeTabId = -1L
        refreshTabStrip()
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

    private fun refreshTabStrip() {
        tabStrip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (tab in tabs) {
            val item = inflater.inflate(R.layout.item_browser_tab, tabStrip, false)
            val root = item.findViewById<View>(R.id.tabRoot)
            val title = item.findViewById<TextView>(R.id.tabTitle)
            val close = item.findViewById<ImageButton>(R.id.tabClose)
            val selected = tab.id == activeTabId

            title.text = tab.title
            title.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.accent else R.color.ink_soft)
            )
            root.setBackgroundResource(
                if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab
            )
            root.setOnClickListener { selectTab(tab.id) }
            close.setOnClickListener { closeTab(tab.id) }
            tabStrip.addView(item)
        }
        tabScroll.post {
            val idx = tabs.indexOfFirst { it.id == activeTabId }
            if (idx >= 0 && idx < tabStrip.childCount) {
                val child = tabStrip.getChildAt(idx)
                tabScroll.smoothScrollTo((child.left - 24).coerceAtLeast(0), 0)
            }
        }
    }

    private fun applyChrome(tab: BrowserTab) {
        currentHostForUi = runCatching { Uri.parse(tab.url).host }.getOrNull()
    }

    private fun tabLabel(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            if (isHomeUrl(uri)) return@runCatching "Home"
            uri.getQueryParameter("dmId")
                ?.takeIf { it.isNotBlank() }
                ?: uri.getQueryParameter("dmid")
                    ?.takeIf { it.isNotBlank() }
                ?: uri.host
        }.getOrNull() ?: "Karta"
    }

    private fun isHomeUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (host != "test.psst.tudc.cz" && host != "psst.tudc.cz") return false
        val path = uri.path?.trimEnd('/') ?: ""
        return path.equals("/HSI.Psst.Data", ignoreCase = true) &&
            uri.getQueryParameter("dmId").isNullOrBlank() &&
            uri.getQueryParameter("dmid").isNullOrBlank()
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
        }
        if (tab.id == activeTabId) applyChrome(tab)
        if (stripChanged) refreshTabStrip()
    }

    // ── WebView ─────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.settings.offscreenPreRaster = true
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setOnLongClickListener { true }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                if (CertPinning.isIssuedByCorporateCa(this@WebViewActivity, error?.certificate)) {
                    handler?.proceed()
                } else {
                    handler?.cancel()
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
                val webView = view ?: run {
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }
                // Nová výzva = předchozí 401 byl handshake (NTLM), ne konečné odmítnutí.
                authFailedViews.remove(webView)

                val creds = pendingCredentials ?: Session.credentials(this@WebViewActivity)
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
                    // Relace zůstává, dokud uživatel nestiskne Odhlásit.
                    handler.cancel()
                    awaitingHttpAuth = false
                    return
                }

                pendingAuthHandler = handler
                if (!verifyingLogin && !Session.isActive(this@WebViewActivity)) {
                    presentLogin()
                }
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
                if (verifyingLogin) return
                if (webView !== activeWebView) return
                val failedUrl = request.url
                webView.post {
                    if (awaitingHttpAuth || gate != Gate.BROWSER || dialogShown) return@post
                    if (shouldSuppressPageErrorDialogs()) return@post
                    showUnauthorizedWarning(failedUrl)
                }
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

    private fun resolveUrlFromIntent(intent: Intent?): String? {
        explicitUrlFromIntent(intent)?.let { return it }
        if (intent?.action == Intent.ACTION_MAIN) return DEFAULT_URL
        return null
    }

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
        val tab = activeTab ?: run {
            openInNewTab(url)
            return
        }
        dialogShown = false
        tab.url = url
        val label = tabLabel(url)
        if (tab.title != label) {
            tab.title = label
            applyChrome(tab)
            refreshTabStrip()
        } else {
            applyChrome(tab)
        }
        tab.webView.loadUrl(url)
    }

    private fun showOpenUrlDialog(openAsNewTab: Boolean = false) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_url, null)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.urlLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        urlInput.setText(activeTab?.url ?: DEFAULT_URL)
        urlInput.setSelection(urlInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (openAsNewTab) "Nová karta" else "Otevřít URL adresu")
            .setView(view)
            .setPositiveButton(if (openAsNewTab) "Otevřít URL v kartě" else "Otevřít URL", null)
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
            if (openAsNewTab) openInNewTab(normalized) else loadInActiveTab(normalized)
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

    // ── Poloha ──────────────────────────────────────────────────────────

    private fun handleGeolocationPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (callback == null) return
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
        }
    }

    // ── VPN ─────────────────────────────────────────────────────────────

    private fun isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
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

    private fun bindLoginUi() {
        loginOverlay = findViewById(R.id.loginOverlay)
        loginTitle = findViewById(R.id.loginTitle)
        loginFormColumn = findViewById(R.id.loginFormColumn)
        vpnOnlyPanel = findViewById(R.id.vpnOnlyPanel)
        loginFormScroll = findViewById(R.id.loginFormScroll)
        loggedOutBanner = findViewById(R.id.loggedOutBanner)
        vpnBanner = findViewById(R.id.vpnBanner)
        usernameLayout = findViewById(R.id.usernameLayout)
        usernameInput = findViewById(R.id.usernameInput)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener { submitLogin() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else false
        }
    }

    private fun updateLoginButton() {
        if (!::loginButton.isInitialized) return
        if (verifyingLogin) {
            loginButton.isEnabled = false
            loginButton.alpha = 0.7f
            loginButton.text = "Přihlašuji…"
            return
        }
        val vpnOn = isVpnActive()
        loginButton.isEnabled = vpnOn
        loginButton.alpha = if (vpnOn) 1f else 0.45f
        loginButton.text = "Přihlásit"
    }

    private fun hideLoginOverlay() {
        hideKeyboard()
        loginOverlay.visibility = View.GONE
        vpnOnlyPanel.visibility = View.GONE
        loginFormColumn.visibility = View.GONE
        loginFormScroll.visibility = View.GONE
        loggedOutBanner.visibility = View.GONE
        vpnBanner.visibility = View.GONE
        pendingAuthHandler = null
        updateLoginButton()
    }

    private fun submitLogin() {
        if (verifyingLogin) return
        if (!isVpnActive()) {
            vpnBanner.visibility = View.VISIBLE
            Toast.makeText(this, "Nejdřív připojte VPN (Cisco AnyConnect)", Toast.LENGTH_SHORT)
                .show()
            return
        }

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
        updateLoginButton()
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        mainHandler.postDelayed(loginTimeoutRunnable, LOGIN_TIMEOUT_MS)

        val handler = pendingAuthHandler
        pendingAuthHandler = null
        if (handler != null) {
            awaitingHttpAuth = true
            handler.proceed(user, pass)
        } else {
            val url = pendingResumeUrl ?: pendingStartUrl ?: DEFAULT_URL
            if (tabs.isEmpty()) openInNewTab(url) else loadInActiveTab(url)
        }
    }

    private fun succeedLogin() {
        if (!verifyingLogin) return
        val creds = pendingCredentials ?: return
        verifyingLogin = false
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        Session.start(this, creds.username, creds.password)
        pendingCredentials = null
        showSignedOutBanner = false
        usernameLayout.error = null
        passwordLayout.error = null
        refreshGate()
    }

    private fun failLogin(message: String?) {
        if (!verifyingLogin && pendingCredentials == null) {
            if (message != null) passwordLayout.error = message
            return
        }
        verifyingLogin = false
        pendingCredentials = null
        mainHandler.removeCallbacks(loginTimeoutRunnable)
        pendingAuthHandler?.cancel()
        pendingAuthHandler = null
        awaitingHttpAuth = false
        destroyAllTabs()
        updateLoginButton()
        if (message != null) {
            passwordLayout.error = message
            passwordInput.requestFocus()
            passwordInput.setSelection(passwordInput.text?.length ?: 0)
        }
        if (isVpnActive()) presentLogin()
    }

    // ── IME ─────────────────────────────────────────────────────────────

    private val visibleFrame = Rect()

    private fun bindImeInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            if (urlDialog?.isShowing == true) {
                setImePadding(v, 0)
            } else {
                setImePadding(v, imeBottom)
            }
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.NONE)
                .build()
        }
        root.viewTreeObserver.addOnGlobalLayoutListener {
            // Při kreslení grafu WebView pořád mění layout. Padding kvůli
            // klávesnici řešíme jen na přihlášení, ne na home.
            if (gate == Gate.BROWSER && urlDialog?.isShowing != true) return@addOnGlobalLayoutListener
            applyVisibleFrameImePadding(root)
        }
        ViewCompat.requestApplyInsets(root)
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
        if (gate != Gate.BROWSER) {
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

    private fun showUnauthorizedWarning(url: Uri?) {
        if (dialogShown) return
        if (shouldSuppressPageErrorDialogs()) return
        dialogShown = true
        progressBar.visibility = View.GONE
        val host = url?.host ?: currentHostForUi ?: "server"
        MaterialAlertDialogBuilder(this)
            .setTitle("Přístup odepřen (401)")
            .setMessage(
                "Server $host odmítl přihlášení.\n\n" +
                    "Uložené údaje zůstávají, dokud se neodhlásíte. " +
                    "Zkuste stránku znovu, nebo se odhlaste a zadejte jiné jméno a heslo."
            )
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                activeWebView?.let { resetAuthCount(it) }
                activeWebView?.reload()
            }
            .setNegativeButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { dialogShown = false }
            .show()
    }

    private fun showCertWarning(error: SslError?) {
        if (dialogShown) return
        if (shouldSuppressPageErrorDialogs()) return
        dialogShown = true
        progressBar.visibility = View.GONE

        val detail = when (error?.primaryError) {
            SslError.SSL_UNTRUSTED ->
                "Certifikát stránky nevydala firemní certifikační autorita " +
                    "(SZT Root BAU ECC CA) ani jiná autorita, které zařízení důvěřuje."
            SslError.SSL_EXPIRED -> "Certifikát stránky vypršel."
            SslError.SSL_IDMISMATCH ->
                "Certifikát patří jiné adrese, než na kterou se připojujete."
            SslError.SSL_NOTYETVALID -> "Certifikát zatím není platný."
            SslError.SSL_DATE_INVALID -> "Certifikát má neplatné datum."
            else -> "Certifikát stránky se nepodařilo ověřit."
        }

        val loadIssue = CertPinning.loadError()
        val diag = buildString {
            append("\n\nDetail: kód ")
            append(error?.primaryError ?: -1)
            error?.url?.let { append(", ").append(Uri.parse(it).host ?: it) }
            if (loadIssue != null) append("\n").append(loadIssue)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Spojení nebylo ověřeno")
            .setMessage(
                detail +
                    "\n\nStránka nebyla načtena. Pokud je to očekávané (např. byla " +
                    "vyměněna firemní CA), obraťte se na IT — do aplikace je potřeba " +
                    "doplnit nový certifikát." +
                    "\n\nPokud jste tuto hlášku nečekali, nepokračujte a nezadávejte " +
                    "na této stránce žádné přihlašovací údaje." +
                    diag
            )
            .setPositiveButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { dialogShown = false }
            .show()
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
        return gate != Gate.BROWSER || !isVpnActive() || verifyingLogin
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
            .setPositiveButton("Odhlásit") { _, _ -> performLogout() }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    /**
     * Odhlásit = smazat relaci úplně a restartovat proces.
     * Chromium drží NTLM v connection poolu procesu; bez restartu by
     * další uživatel (nebo totéž špatné heslo) pořád viděl starou session.
     */
    private fun performLogout() {
        pendingAuthHandler?.cancel()
        pendingAuthHandler = null
        pendingCredentials = null
        verifyingLogin = false
        showSignedOutBanner = true
        pendingResumeUrl = DEFAULT_URL
        pendingStartUrl = DEFAULT_URL
        savedTabUrls = emptyList()
        savedActiveTabIndex = 0
        mainHandler.removeCallbacks(loginTimeoutRunnable)

        Session.end(this)
        Session.wipeBrowser(this, tabs.map { it.webView })
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
            refreshGate()
            return
        }
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(0)
    }

    private fun showCertInfo() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ověřování certifikátů")
            .setMessage(CertPinning.describeChain(this))
            .setPositiveButton("Zavřít", null)
            .show()
    }

    private fun openLinkSettings() {
        val steps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "1. Klepni na „Otevírání odkazů\"\n" +
                "2. Zapni „Otevírat podporované odkazy\"\n" +
                "3. V „Podporované webové adresy\" zaškrtni psst.tudc.cz " +
                "a test.psst.tudc.cz"
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

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
            menu.setGroupDividerEnabled(true)
        }
        val accent = ContextCompat.getColor(this, R.color.accent)
        val inkSoft = ContextCompat.getColor(this, R.color.ink_soft)
        val alert = ContextCompat.getColor(this, R.color.alert)

        menu?.findItem(R.id.action_new_tab)?.icon?.mutate()?.setTint(accent)
        menu?.findItem(R.id.action_home)?.icon?.mutate()?.setTint(inkSoft)

        listOf(
            R.id.action_open_url,
            R.id.action_reload,
            R.id.action_cert_info,
            R.id.action_link_settings
        ).forEach { id ->
            menu?.findItem(id)?.icon?.mutate()?.setTint(inkSoft)
        }

        menu?.findItem(R.id.action_logout)?.let { item ->
            val title = SpannableString("Odhlásit")
            title.setSpan(ForegroundColorSpan(alert), 0, title.length, 0)
            item.title = title
            item.icon?.mutate()?.setTint(alert)
        }
        toolbar.post {
            val lp = toolbar.layoutParams
            if (lp.width != LinearLayout.LayoutParams.WRAP_CONTENT) {
                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                toolbar.layoutParams = lp
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        hideKeyboard()
        if (gate != Gate.BROWSER && item.itemId != R.id.action_logout) {
            return true
        }
        return when (item.itemId) {
            R.id.action_open_url -> {
                showOpenUrlDialog(openAsNewTab = false)
                true
            }
            R.id.action_new_tab -> {
                openInNewTab(DEFAULT_URL)
                true
            }
            R.id.action_home -> {
                loadInActiveTab(DEFAULT_URL)
                true
            }
            R.id.action_reload -> {
                dialogShown = false
                activeWebView?.reload()
                true
            }
            R.id.action_logout -> {
                confirmLogout()
                true
            }
            R.id.action_cert_info -> {
                showCertInfo()
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
        if (gate != Gate.BROWSER) {
            if (verifyingLogin) failLogin(null)
            return
        }
        val wv = activeWebView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (tabs.size > 1) {
            closeTab(activeTabId)
        } else {
            super.onBackPressed()
        }
    }
}

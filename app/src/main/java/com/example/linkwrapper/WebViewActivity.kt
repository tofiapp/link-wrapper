package com.example.linkwrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Collections
import java.util.IdentityHashMap

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
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webContainer: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabId: Long = -1L
    private var nextTabId = 1L

    private val activeTab: BrowserTab?
        get() = tabs.find { it.id == activeTabId }

    private val activeWebView: WebView?
        get() = activeTab?.webView

    // Aby po chybě nevyskočilo víc dialogů za sebou.
    private var dialogShown = false
    private var authDialogShowing = false
    private var awaitingHttpAuth = false

    /**
     * Počet HTTP auth challenge za sebou pro daný klíč.
     * Uložené heslo posíláme automaticky (bez dialogu); když jich je moc
     * (špatné heslo), teprve pak ukážeme dialog.
     */
    private val authChallengeCounts = mutableMapOf<String, Int>()

    /** WebView, které právě dostaly 401 — nesmíme resetovat počítadlo auth. */
    private val authFailedViews = Collections.newSetFromMap(IdentityHashMap<WebView, Boolean>())

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
        setContentView(R.layout.activity_webview)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.navigationIcon = null
        toolbar.title = null

        progressBar = findViewById(R.id.progressBar)
        webContainer = findViewById(R.id.webContainer)
        tabStrip = findViewById(R.id.tabStrip)
        tabScroll = findViewById(R.id.tabScroll)

        CookieManager.getInstance().setAcceptCookie(true)

        val startUrl = resolveUrlFromIntent(intent) ?: DEFAULT_URL
        openInNewTab(startUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dialogShown = false
        awaitingHttpAuth = false
        val url = resolveUrlFromIntent(intent) ?: return
        // Externí odkaz / sdílení / historie → nová karta.
        openInNewTab(url)
    }

    override fun onDestroy() {
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        super.onDestroy()
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
            title = hostLabel(url),
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
        LinkHistory.addEntry(this, url)
        webView.loadUrl(url)
        refreshTabStrip()
    }

    private fun selectTab(tabId: Long) {
        activeTabId = tabId
        tabs.forEach { tab ->
            tab.webView.visibility = if (tab.id == tabId) View.VISIBLE else View.GONE
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
        webContainer.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.webChromeClient = null
        tab.webView.destroy()
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
        // Název stránky je na kartě v liště — toolbar title nepoužíváme.
        currentHostForUi = runCatching { Uri.parse(tab.url).host }.getOrNull()
    }

    private var currentHostForUi: String? = null

    private fun hostLabel(url: String): String {
        return runCatching { Uri.parse(url).host }.getOrNull() ?: "Karta"
    }

    private fun updateTabMeta(webView: WebView, url: String?, title: String?) {
        val tab = tabs.find { it.webView === webView } ?: return
        if (!url.isNullOrBlank()) {
            tab.url = url
            if (title.isNullOrBlank()) tab.title = hostLabel(url)
        }
        if (!title.isNullOrBlank()) {
            tab.title = title.take(40)
        }
        if (tab.id == activeTabId) applyChrome(tab)
        refreshTabStrip()
    }

    // ── WebView factory ─────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
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
                    if (view === activeWebView) showCertWarning(error)
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

                val key = HttpCredentials.authKey(host)
                val saved = HttpCredentials.get(this@WebViewActivity, host)
                if (saved != null) {
                    val count = (authChallengeCounts[key] ?: 0) + 1
                    authChallengeCounts[key] = count
                    // NTLM může mít několik kol; po větším počtu je heslo asi špatně.
                    if (count <= 12) {
                        handler.proceed(saved.username, saved.password)
                        return
                    }
                    // Přestaň smyčku — nech uživatele zadat údaje znovu.
                    HttpCredentials.clear(this@WebViewActivity, host)
                    authChallengeCounts.remove(key)
                }

                showHttpAuthDialog(handler, host, realm)
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
                if (webView !== activeWebView) return
                val failedUrl = request.url
                webView.post {
                    if (awaitingHttpAuth || authDialogShowing || dialogShown) return@post
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
                showNetworkWarning(error?.errorCode, request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                awaitingHttpAuth = false
                if (view != null && view !in authFailedViews) {
                    // Skutečně načtená stránka — příští navigace znovu auto-přihlásí.
                    runCatching { Uri.parse(url ?: "").host }.getOrNull()
                        ?.let { authChallengeCounts.remove(HttpCredentials.authKey(it)) }
                }
                if (view != null) authFailedViews.remove(view)
                updateTabMeta(view ?: return, url, view.title)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view !== activeWebView) return
                progressBar.visibility = View.VISIBLE
                progressBar.setProgressCompat(newProgress, true)
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                updateTabMeta(view ?: return, view.url, title)
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

    // ── URL helpers ─────────────────────────────────────────────────────

    private fun resolveUrlFromIntent(intent: Intent?): String? {
        intent?.data?.toString()?.let { return it }
        intent?.getStringExtra(EXTRA_URL)?.let { return it }

        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            return shared.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: shared.takeIf { it.isNotEmpty() }?.let { "https://$it" }
        }

        // Studený start z ikony — bez EXTRA/data.
        if (intent?.action == Intent.ACTION_MAIN) return DEFAULT_URL
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
        LinkHistory.addEntry(this, url)
        tab.url = url
        tab.title = hostLabel(url)
        applyChrome(tab)
        refreshTabStrip()
        tab.webView.loadUrl(url)
    }

    private fun showOpenUrlDialog(openAsNewTab: Boolean = false) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_url, null)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.urlLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        urlInput.setText(activeTab?.url ?: DEFAULT_URL)
        urlInput.setSelection(urlInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (openAsNewTab) "Nová karta" else "Otevřít adresu")
            .setView(view)
            .setPositiveButton(if (openAsNewTab) "Otevřít v kartě" else "Otevřít", null)
            .setNegativeButton("Zrušit", null)
            .create()

        fun tryOpen() {
            val normalized = normalizeUrl(urlInput.text?.toString().orEmpty())
            if (normalized == null) {
                urlLayout.error = "Tohle nevypadá jako adresa"
                return
            }
            urlLayout.error = null
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

    // ── Síť / VPN ───────────────────────────────────────────────────────

    private fun isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    // ── HTTP auth ───────────────────────────────────────────────────────

    private fun showHttpAuthDialog(
        handler: HttpAuthHandler,
        host: String,
        realm: String?
    ) {
        if (authDialogShowing || isFinishing) {
            handler.cancel()
            return
        }
        authDialogShowing = true
        progressBar.visibility = View.GONE

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_http_auth, null)
        val realmView = view.findViewById<TextView>(R.id.authRealm)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val rememberCheck = view.findViewById<MaterialCheckBox>(R.id.rememberCheck)

        val shared = HttpCredentials.isPsstHost(host)
        val realmLabel = realm?.takeIf { it.isNotBlank() }
        realmView.text = buildString {
            append("Server ")
            append(host)
            append(" vyžaduje přihlášení.")
            if (shared) {
                append("\n\nÚdaje budou platit pro všechny stránky *.psst.tudc.cz ")
                append("ve všech kartách, dokud se neodhlásíte.")
            }
            if (realmLabel != null) {
                append("\nOblast: ")
                append(realmLabel)
            }
        }

        HttpCredentials.get(this, host)?.let {
            usernameInput.setText(it.username)
            passwordInput.setText(it.password)
            rememberCheck.isChecked = true
        }
        // U PSST je zapamatování výchozí a dává největší smysl.
        if (shared) rememberCheck.isChecked = true

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Přihlášení")
            .setView(view)
            .setPositiveButton("Přihlásit", null)
            .setNegativeButton("Zrušit") { _, _ ->
                handler.cancel()
                authDialogShowing = false
                awaitingHttpAuth = false
            }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener {
                    val user = usernameInput.text?.toString()?.trim().orEmpty()
                    val pass = passwordInput.text?.toString().orEmpty()
                    if (user.isEmpty()) {
                        usernameInput.error = "Zadejte jméno"
                        return@setOnClickListener
                    }
                    if (rememberCheck.isChecked || shared) {
                        HttpCredentials.save(this, host, user, pass)
                    } else {
                        HttpCredentials.clear(this, host)
                    }
                    authChallengeCounts[HttpCredentials.authKey(host)] = 0
                    authDialogShowing = false
                    awaitingHttpAuth = true
                    dialog.dismiss()
                    handler.proceed(user, pass)
                }
        }

        dialog.show()
    }

    private fun showUnauthorizedWarning(url: Uri?) {
        if (dialogShown) return
        dialogShown = true
        progressBar.visibility = View.GONE

        val host = url?.host ?: currentHostForUi ?: "server"
        val hasSaved = HttpCredentials.hasFor(this, host)

        val extra = if (hasSaved) {
            "\n\nUložené přihlášení můžete smazat v menu (⋮ → Odhlásit) a zkusit jiné údaje."
        } else {
            "\n\nPokud se nepřihlašovací dialog vůbec neobjevil, server pravděpodobně " +
                "používá Windows Integrated Authentication (Kerberos), které Android " +
                "neumí stejně jako firemní PC."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Přístup odepřen (401)")
            .setMessage(
                "Server $host odmítl přihlášení.\n\n" +
                    "Zadejte jméno a heslo ručně (často DOMÉNA\\uživatel). " +
                    "Pro *.psst.tudc.cz stačí jednou — platí ve všech kartách." +
                    extra
            )
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                authChallengeCounts.clear()
                activeWebView?.reload()
            }
            .setNegativeButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { dialogShown = false }
            .show()
    }

    private fun showCertWarning(error: SslError?) {
        if (dialogShown) return
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

        MaterialAlertDialogBuilder(this)
            .setTitle("Nepodařilo se připojit")
            .setMessage("$detail\n\n$vpnHint\n\nAdresa: ${url?.host ?: "neznámá"}")
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                activeWebView?.reload()
            }
            .setNegativeButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { dialogShown = false }
            .show()
    }

    /**
     * Odhlášení: smaže uložené heslo (sdílené pro PSST), cookies a session data,
     * pak přenačte všechny karty — další přístup znovu vyžádá přihlášení.
     */
    private fun confirmLogout() {
        if (!HttpCredentials.hasAny(this) && tabs.isEmpty()) {
            Toast.makeText(this, "Nejste přihlášeni", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Odhlásit")
            .setMessage(
                "Smaže uložené jméno a heslo pro *.psst.tudc.cz i cookies " +
                    "ve všech kartách. Při dalším načtení bude potřeba se znovu přihlásit."
            )
            .setPositiveButton("Odhlásit") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun performLogout() {
        HttpCredentials.clearAll(this)
        authChallengeCounts.clear()
        awaitingHttpAuth = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (_: Exception) {
            // starší WebView — ignorovat
        }

        tabs.forEach { tab ->
            tab.webView.clearCache(true)
            tab.webView.clearFormData()
            dialogShown = false
            tab.webView.reload()
        }
        Toast.makeText(this, "Odhlášeno", Toast.LENGTH_SHORT).show()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
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

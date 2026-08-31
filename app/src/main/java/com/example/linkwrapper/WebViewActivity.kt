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
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"

        /** Stránka, která se otevře hned po spuštění aplikace. */
        const val DEFAULT_URL = "https://test.psst.tudc.cz/HSI.Psst.Data"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator

    // Aby po chybě nevyskočilo víc dialogů za sebou.
    private var dialogShown = false
    private var authDialogShowing = false

    /** WebView právě řeší HTTP auth challenge (Basic/Digest/NTLM). */
    private var awaitingHttpAuth = false

    /** Host, pro který jsme právě zkusili uložené heslo — při dalším 401 už dialog. */
    private var autoAuthTriedHost: String? = null

    private var currentHost: String? = null

    /** Čekající žádost stránky o geolokaci (dokud uživatel neudělí oprávnění). */
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        finishGeolocationRequest(allowed)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // Žádná šipka zpět — jen nabídka ⋮.
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.navigationIcon = null

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
        // Některé firemní stránky očekávají „plný“ prohlížeč.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true

        webView.webViewClient = object : WebViewClient() {

            /**
             * Certifikát, kterému zařízení systémově nedůvěřuje.
             * Spojení pokračuje jen tehdy, když certifikát pochází
             * z firemního řetězce CA (viz CertPinning.kt).
             */
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                if (CertPinning.isIssuedByCorporateCa(this@WebViewActivity, error?.certificate)) {
                    handler?.proceed()
                } else {
                    handler?.cancel()
                    showCertWarning(error)
                }
            }

            /**
             * HTTP Basic / Digest / (někdy) NTLM — server chce jméno a heslo.
             * Na PC to často pošle Windows samo; tady musí uživatel zadat údaje.
             */
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

                // Jednou zkus uložené heslo; když server znovu požádá, ukaž dialog.
                val saved = HttpCredentials.get(this@WebViewActivity, host)
                if (saved != null && autoAuthTriedHost != host) {
                    autoAuthTriedHost = host
                    handler.proceed(saved.username, saved.password)
                    return
                }

                showHttpAuthDialog(handler, host, realm)
            }

            /**
             * 401 bez volání onReceivedHttpAuthRequest — typicky Windows Integrated
             * Auth (Negotiate/Kerberos), které Android WebView neumí jako PC.
             */
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame != true) return
                if (errorResponse?.statusCode != 401) return
                // Auth callback může dorazit ve stejném „kole“ — počkej na UI thread.
                val failedUrl = request.url
                view?.post {
                    if (awaitingHttpAuth || authDialogShowing || dialogShown) return@post
                    showUnauthorizedWarning(failedUrl)
                }
            }

            /**
             * Síťové chyby (DNS, timeout, nedostupný server).
             * Typicky znamenají, že neběží VPN.
             */
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Chyby podřízených požadavků (obrázky, skripty) ignorujeme.
                if (request?.isForMainFrame != true) return
                showNetworkWarning(error?.errorCode, request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Úspěšné načtení — příští návštěva může znovu použít uložené heslo.
                autoAuthTriedHost = null
                awaitingHttpAuth = false
                url?.let { applyTitle(it) }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = View.VISIBLE
                progressBar.setProgressCompat(newProgress, true)
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }

            /**
             * Stránka volá navigator.geolocation — Android vyžaduje runtime oprávnění.
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                handleGeolocationPrompt(origin, callback)
            }
        }

        webView.setOnLongClickListener { true }

        loadResolvedUrl(resolveUrl())
    }

    /**
     * singleTask: další odkaz (Outlook / Sdílet) přijde sem znovu přes onNewIntent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dialogShown = false
        autoAuthTriedHost = null
        awaitingHttpAuth = false
        loadResolvedUrl(resolveUrl())
    }

    private fun loadResolvedUrl(url: String?) {
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "Nebyla předána žádná adresa", Toast.LENGTH_SHORT).show()
            return
        }
        applyTitle(url)
        LinkHistory.addEntry(this, url)
        webView.loadUrl(url)
    }

    private fun applyTitle(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        currentHost = uri?.host
        supportActionBar?.title = currentHost ?: "Link Wrapper"
    }

    /**
     * Odkaz: ACTION_VIEW / EXTRA_URL / ACTION_SEND, jinak výchozí PSST stránka.
     */
    private fun resolveUrl(): String? {
        intent?.data?.toString()?.let { return it }
        intent?.getStringExtra(EXTRA_URL)?.let { return it }

        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            // Sdílený text bývá věta s odkazem uvnitř, ne holá adresa.
            return shared.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: shared.takeIf { it.isNotEmpty() }?.let { "https://$it" }
        }

        // Spuštění z ikony aplikace — rovnou domovská PSST adresa.
        return DEFAULT_URL
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

    private fun showOpenUrlDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_url, null)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.urlLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        urlInput.setText(webView.url ?: DEFAULT_URL)
        urlInput.setSelection(urlInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Otevřít adresu")
            .setView(view)
            .setPositiveButton("Otevřít", null)
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
            autoAuthTriedHost = null
            loadResolvedUrl(normalized)
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
            .setNegativeButton("Odmítnout") { _, _ ->
                finishGeolocationRequest(false)
            }
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

    /** Je aktivní VPN spojení? */
    private fun isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

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
        val realmView = view.findViewById<android.widget.TextView>(R.id.authRealm)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val rememberCheck = view.findViewById<MaterialCheckBox>(R.id.rememberCheck)

        val realmLabel = realm?.takeIf { it.isNotBlank() }
        realmView.text = buildString {
            append("Server ")
            append(host)
            append(" vyžaduje přihlášení.")
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
                    if (rememberCheck.isChecked) {
                        HttpCredentials.save(this, host, user, pass)
                    } else {
                        HttpCredentials.clear(this, host)
                    }
                    autoAuthTriedHost = host
                    authDialogShowing = false
                    awaitingHttpAuth = true
                    dialog.dismiss()
                    handler.proceed(user, pass)
                }
        }

        dialog.show()
    }

    /**
     * 401, které WebView nevyřešilo dialogem — typicky Negotiate/Kerberos
     * (Integrated Windows Auth), které na Androidu nefunguje jako na PC.
     */
    private fun showUnauthorizedWarning(url: Uri?) {
        if (dialogShown) return
        dialogShown = true
        progressBar.visibility = View.GONE

        val host = url?.host ?: currentHost ?: "server"
        val hasSaved = HttpCredentials.get(this, host) != null

        val extra = if (hasSaved) {
            "\n\nUložené přihlášení pro tuto adresu můžete smazat v menu " +
                "(⋮ → Zapomenout přihlášení) a zkusit jiné údaje."
        } else {
            "\n\nPokud se nepřihlašovací dialog vůbec neobjevil, server pravděpodobně " +
                "používá Windows Integrated Authentication (Kerberos), které Android " +
                "neumí stejně jako firemní PC. Pak je potřeba na straně serveru " +
                "povolit NTLM nebo Basic Auth, případně jiné SSO pro mobilní klienty."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Přístup odepřen (401)")
            .setMessage(
                "Server $host odmítl přihlášení.\n\n" +
                    "Na firemním PC to často projde samo přes doménový účet. " +
                    "V této aplikaci je potřeba zadat jméno a heslo ručně " +
                    "(často ve tvaru DOMÉNA\\uživatel)." +
                    extra
            )
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                autoAuthTriedHost = null
                webView.reload()
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

        // Diagnostika: pokud selhalo načtení CA z aplikace, řekni to rovnou.
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
            WebViewClient.ERROR_HOST_LOOKUP ->
                "Adresu serveru se nepodařilo přeložit."
            WebViewClient.ERROR_CONNECT ->
                "K serveru se nepodařilo připojit."
            WebViewClient.ERROR_TIMEOUT ->
                "Server neodpověděl včas."
            WebViewClient.ERROR_PROXY_AUTHENTICATION ->
                "Firemní proxy vyžaduje přihlášení."
            else -> "Stránku se nepodařilo načíst."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Nepodařilo se připojit")
            .setMessage("$detail\n\n$vpnHint\n\nAdresa: ${url?.host ?: "neznámá"}")
            .setPositiveButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                webView.reload()
            }
            .setNegativeButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { dialogShown = false }
            .show()
    }

    private fun confirmClearCredentials() {
        val host = currentHost
        val clearHostOnly = host != null && HttpCredentials.get(this, host) != null
        val message = when {
            clearHostOnly -> "Smazat uložené jméno a heslo pro $host?"
            HttpCredentials.hasAny(this) ->
                "Smazat všechna uložená přihlášení v této aplikaci?"
            else -> {
                Toast.makeText(this, "Žádné uložené přihlášení", Toast.LENGTH_SHORT).show()
                return
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Zapomenout přihlášení")
            .setMessage(message)
            .setPositiveButton("Smazat") { _, _ ->
                if (clearHostOnly) HttpCredentials.clear(this, host!!)
                else HttpCredentials.clearAll(this)
                autoAuthTriedHost = null
                Toast.makeText(this, "Přihlášení smazáno", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zrušit", null)
            .show()
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
                showOpenUrlDialog()
                true
            }
            R.id.action_home -> {
                dialogShown = false
                autoAuthTriedHost = null
                loadResolvedUrl(DEFAULT_URL)
                true
            }
            R.id.action_reload -> {
                dialogShown = false
                autoAuthTriedHost = null
                webView.reload()
                true
            }
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.action_clear_credentials -> {
                confirmClearCredentials()
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
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

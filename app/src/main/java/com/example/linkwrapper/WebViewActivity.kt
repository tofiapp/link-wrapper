package com.example.linkwrapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: LinearProgressIndicator

    // Aby po chybě nevyskočilo víc dialogů za sebou.
    private var dialogShown = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

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
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = View.VISIBLE
                progressBar.setProgressCompat(newProgress, true)
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }
        }

        webView.setOnLongClickListener { true }

        val url = resolveUrl()

        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "Nebyla předána žádná adresa", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull()
        supportActionBar?.title = uri?.host ?: "Odkaz"

        LinkHistory.addEntry(this, url)
        webView.loadUrl(url)
    }

    /**
     * Odkaz může přijít třemi cestami: kliknutím v jiné aplikaci (ACTION_VIEW),
     * sdílením (ACTION_SEND) nebo z domovské obrazovky aplikace.
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
        return null
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
            .setPositiveButton("Zavřít") { _, _ -> finish() }
            .setCancelable(false)
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
            .setPositiveButton("Zavřít") { _, _ -> finish() }
            .setNegativeButton("Zkusit znovu") { _, _ ->
                dialogShown = false
                webView.reload()
            }
            .setCancelable(false)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                dialogShown = false
                webView.reload()
                true
            }
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

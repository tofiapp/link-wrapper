package com.example.linkwrapper

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {

            /**
             * Zavolá se, když zařízení certifikátu nedůvěřuje.
             *
             * Appka NEPOVOLUJE spojení plošně. Pokračuje pouze tehdy, když
             * otisk certifikátu přesně odpovídá napinovanému firemnímu
             * certifikátu (viz CertPinning.kt). Ve všech ostatních případech
             * spojení zruší a uživateli vysvětlí proč.
             */
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val cert = error?.certificate
                if (CertPinning.isIssuedByCorporateCa(this@WebViewActivity, cert)) {
                    // Certifikát vydala firemní CA – pokračujeme.
                    handler?.proceed()
                } else {
                    // Certifikát nevydala firemní CA – spojení se ruší.
                    handler?.cancel()
                    showCertWarning(error)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                supportActionBar?.subtitle = url
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = newProgress
                if (newProgress >= 100) progressBar.visibility = View.GONE
            }
        }

        val url = intent?.data?.toString() ?: intent?.getStringExtra(EXTRA_URL)

        if (url.isNullOrEmpty()) {
            Toast.makeText(this, "Nebyla předána žádná adresa", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        LinkHistory.addEntry(this, url)
        webView.loadUrl(url)
    }

    /** Srozumitelné vysvětlení místo prázdné bílé stránky. */
    private fun showCertWarning(error: SslError?) {
        progressBar.visibility = View.GONE

        val detail = when (error?.primaryError) {
            SslError.SSL_UNTRUSTED ->
                "Certifikát stránky nevydala firemní certifikační autorita " +
                "(SZT Root BAU ECC CA) ani jiná autorita, které zařízení důvěřuje."
            SslError.SSL_EXPIRED -> "Certifikát stránky vypršel."
            SslError.SSL_IDMISMATCH -> "Certifikát patří jiné adrese, než na kterou se připojujete."
            SslError.SSL_NOTYETVALID -> "Certifikát zatím není platný."
            else -> "Certifikát stránky se nepodařilo ověřit."
        }

        AlertDialog.Builder(this)
            .setTitle("Spojení nebylo ověřeno")
            .setMessage(
                "$detail\n\n" +
                "Stránka nebyla načtena. Pokud je to očekávané (např. byla " +
                "vyměněna firemní kořenová CA), obraťte se na IT — do aplikace " +
                "je potřeba doplnit nový certifikát CA.\n\n" +
                "Pokud jste tuto hlášku nečekali, nepokračujte a nezadávejte " +
                "na této stránce žádné přihlašovací údaje."
            )
            .setPositiveButton("Zavřít") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_webview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> { webView.reload(); true }
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

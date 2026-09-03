package com.example.linkwrapper

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Ověření jména a hesla v **jiném procesu**, než je prohlížeč.
 *
 * Špatné NTLM údaje Chromium ukládá do connection poolu procesu. Když se
 * ověřovalo ve stejném WebView jako home, další pokus se správným heslem
 * pořád posílal to špatné. Tady má každé ověření vlastní proces a vlastní
 * datový adresář; po výsledku proces zmizí. Hlavní WebView špatné heslo
 * nikdy neuvidí.
 */
object AuthProbe {
    const val PROCESS_SUFFIX = "authprobe"
    const val DATA_DIR_SUFFIX = "authprobe"

    fun kill(context: Context) {
        val target = context.packageName + ":" + PROCESS_SUFFIX
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.forEach { proc ->
                if (proc.processName == target) {
                    android.os.Process.killProcess(proc.pid)
                }
            }
        } catch (_: Exception) {
        }
        try {
            File("/proc").listFiles()?.forEach { dir ->
                val pid = dir.name.toIntOrNull() ?: return@forEach
                if (pid == android.os.Process.myPid()) return@forEach
                val cmd = try {
                    File(dir, "cmdline").readBytes().toString(Charsets.UTF_8)
                } catch (_: Exception) {
                    return@forEach
                }
                if (cmd.contains(target)) {
                    android.os.Process.killProcess(pid)
                }
            }
        } catch (_: Exception) {
        }
        deleteProfile(context)
        AuthHandoff.clear(context)
    }

    fun deleteProfile(context: Context) {
        val dataDir = context.applicationInfo.dataDir
        listOf(
            File(dataDir, "app_webview_$DATA_DIR_SUFFIX"),
            File(context.cacheDir, "WebView_$DATA_DIR_SUFFIX")
        ).forEach { dir ->
            try {
                if (dir.exists()) dir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }
}

class AuthProbeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR = "extra_error"

        private const val MAX_AUTH_ROUNDS = 16
        private const val TIMEOUT_MS = 14_000L
        private const val SETTLE_MS = 700L

        fun intent(context: Context): Intent {
            return Intent(context, AuthProbeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var username: String = ""
    private var password: String = ""
    private var settled = false
    private var awaitingAuth = false
    private var authRounds = 0
    private var lastMainStatus = 0

    private val timeoutRunnable = Runnable {
        finishProbe(false, "Přihlášení vypršelo. Zkuste to znovu.")
    }

    private val fail401Runnable = Runnable {
        if (!settled && !awaitingAuth) {
            finishProbe(false, "Neplatné jméno nebo heslo")
        }
    }

    private val succeedRunnable = Runnable {
        if (settled || awaitingAuth) return@Runnable
        if (lastMainStatus == 401) return@Runnable
        finishProbe(true, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        try {
            WebView.setDataDirectorySuffix(AuthProbe.DATA_DIR_SUFFIX)
        } catch (_: IllegalStateException) {
        }

        val request = AuthHandoff.take(this)
        username = request?.username.orEmpty()
        password = request?.password.orEmpty()
        val url = request?.url.orEmpty()
        if (username.isEmpty() || password.isEmpty() || url.isEmpty()) {
            finishProbe(false, "Neplatné jméno nebo heslo")
            return
        }

        val webView = WebView(this)
        this.webView = webView
        webView.alpha = 0f
        setContentView(
            FrameLayout(this).apply {
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
        configureWebView(webView)
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        WebView.setWebContentsDebuggingEnabled(false)
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                SslPolicy.handleSslError(this@AuthProbeActivity, handler, error) {
                    finishProbe(false, SslPolicy.PROBE_SSL_FAILURE)
                }
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: HttpAuthHandler?,
                host: String?,
                realm: String?
            ) {
                if (handler == null || host.isNullOrEmpty() || !AuthHosts.allows(host)) {
                    handler?.cancel()
                    finishProbe(false, "Neplatné jméno nebo heslo")
                    return
                }
                awaitingAuth = true
                lastMainStatus = 0
                mainHandler.removeCallbacks(fail401Runnable)
                mainHandler.removeCallbacks(succeedRunnable)
                authRounds++
                if (authRounds > MAX_AUTH_ROUNDS) {
                    handler.cancel()
                    finishProbe(false, "Neplatné jméno nebo heslo")
                    return
                }
                handler.proceed(username, password)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame != true) return
                val code = errorResponse?.statusCode ?: return
                lastMainStatus = code
                if (code != 401) return
                // 401 během NTLM handshake následuje nová výzva. Konečné
                // odmítnutí je 401, po kterém už žádná výzva nepřijde.
                awaitingAuth = false
                mainHandler.removeCallbacks(fail401Runnable)
                mainHandler.removeCallbacks(succeedRunnable)
                mainHandler.postDelayed(fail401Runnable, SETTLE_MS)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                finishProbe(
                    false,
                    "Stránku se nepodařilo načíst. Zkontrolujte VPN a zkuste to znovu."
                )
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url.isNullOrBlank() || url == "about:blank") return
                awaitingAuth = false
                mainHandler.removeCallbacks(succeedRunnable)
                if (lastMainStatus == 401) {
                    mainHandler.removeCallbacks(fail401Runnable)
                    mainHandler.postDelayed(fail401Runnable, SETTLE_MS)
                    return
                }
                mainHandler.postDelayed(succeedRunnable, SETTLE_MS)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishProbe(false, null)
    }

    private fun finishProbe(ok: Boolean, error: String?) {
        if (settled) return
        settled = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            webView?.stopLoading()
            webView?.destroy()
        } catch (_: Exception) {
        }
        webView = null
        username = ""
        password = ""
        AuthHandoff.clear(this)
        setResult(
            if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Intent().putExtra(EXTRA_ERROR, error)
        )
        finish()
    }
}

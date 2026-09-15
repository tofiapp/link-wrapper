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

internal fun WebViewActivity.showCertWarning(error: SslError?) {
        if (dialogShown) return
        if (shouldSuppressPageErrorDialogs()) return
        dialogShown = true
        progressBar.visibility = View.GONE
        SslPolicy.showSslRejected(this, error) { dialogShown = false }
    }

internal fun WebViewActivity.showNetworkWarning(code: Int?, url: Uri?) {
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

internal fun WebViewActivity.shouldSuppressPageErrorDialogs(): Boolean {
        return gate != Gate.BROWSER || !isConnectionOk() || verifyingLogin
    }

internal fun WebViewActivity.dismissWarningDialog() {
        warningDialog?.setOnDismissListener(null)
        warningDialog?.dismiss()
        warningDialog = null
        dialogShown = false
    }

internal fun WebViewActivity.confirmLogout() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_logout, null)
        MaterialAlertDialogBuilder(this, R.style.LogoutDialog)
            .setView(view)
            .setPositiveButton("Smazat") { _, _ -> clearStoredData() }
            .setNegativeButton("Zrušit", null)
            .show()
    }

internal fun WebViewActivity.cancelPendingAuth() {
        try {
            pendingAuthHandler?.cancel()
        } catch (_: Exception) {
        }
        pendingAuthHandler = null
        awaitingHttpAuth = false
    }

internal fun WebViewActivity.clearStoredData() {
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

internal fun WebViewActivity.openLinkSettings() {
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

internal fun WebViewActivity.consumeTrialIdleTimeout(): Boolean {
        if (!TrialSettings.isTrial() || verifyingLogin) return false
        if (!TrialIdle.shouldWipe(this)) {
            TrialIdle.clear(this)
            return false
        }
        TrialIdle.clear(this)
        endTrialSessionKeepTabs()
        return true
    }

internal fun WebViewActivity.endTrialSessionKeepTabs() {
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

internal fun WebViewActivity.currentSaveableUrl(): String? {
        if (gate != Gate.BROWSER) return null
        val url = activeTab?.takeUnless { it.isHome }?.url ?: return null
        if (url.isBlank() || url == Destinations.HOME_URL) return null
        return url
    }

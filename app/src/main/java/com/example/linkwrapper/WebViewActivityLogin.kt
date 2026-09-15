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

internal fun WebViewActivity.bindLoginUi() {
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

internal fun WebViewActivity.updateLoginButton() {
        if (!loginButtonReady) return
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

internal fun WebViewActivity.hideLoginOverlay() {
        hideKeyboard()
        loginOverlay.visibility = View.GONE
        loginFormColumn.visibility = View.GONE
        loginFormScroll.visibility = View.GONE
        setCertBannerVisible(false)
        updateLoginButton()
    }

internal fun WebViewActivity.submitLogin() {
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
        mainHandler.postDelayed(loginTimeoutRunnable, WebViewActivityConstants.LOGIN_TIMEOUT_MS)

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

internal fun WebViewActivity.succeedLogin() {
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

internal fun WebViewActivity.failLogin(message: String?, stayOnForm: Boolean = true) {
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

internal fun WebViewActivity.setCertBannerVisible(visible: Boolean) {
        if (!certBannerReady) return
        certBanner.visibility = if (visible) View.VISIBLE else View.GONE
    }

internal fun WebViewActivity.refreshCertBanner() {
        if (!certBannerReady) return
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

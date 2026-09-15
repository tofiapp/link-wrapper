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

internal fun WebViewActivity.openUrlFromExternal(url: String) {
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            selectTab(existing.id)
            return
        }
        if (tabs.size >= WebViewActivityConstants.MAX_TABS) {
            loadInActiveTab(url)
            return
        }
        openInNewTab(url)
    }

internal fun WebViewActivity.samePage(a: String, b: String): Boolean {
        fun norm(raw: String): String {
            val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
            val path = (u.path ?: "").trimEnd('/')
            val q = u.encodedQuery ?: ""
            return "${u.scheme}://${u.host}$path?$q"
        }
        return norm(a).equals(norm(b), ignoreCase = true)
    }

internal fun WebViewActivity.refreshGate() {
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

internal fun WebViewActivity.needsAppLogin(url: String): Boolean {
        if (Session.isActive(this)) return false
        return Destinations.forUrl(url)?.requiresAppLogin == true
    }

internal fun WebViewActivity.presentLogin() {
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

internal fun WebViewActivity.presentHome() {
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

internal fun WebViewActivity.enterBrowser() {
        hideLoginOverlay()
        attachImeLayoutListener(false)
        setSensitiveScreen(false)
    }

internal fun WebViewActivity.presentBrowser() {
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

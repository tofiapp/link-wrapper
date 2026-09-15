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

internal fun WebViewActivity.isAllowedWebUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "https" || scheme == "about"
    }

internal fun WebViewActivity.setSensitiveScreen(on: Boolean) {
        if (on) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

internal fun WebViewActivity.isVpnActive(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

internal fun WebViewActivity.isConnectionOk(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (_: Exception) {
            false
        }
    }

internal fun WebViewActivity.refreshConnectionBanner() {
        if (isConnectionOk()) hideConnectionBanner()
        else showConnectionBanner()
        applyOfflineChrome()
    }

internal fun WebViewActivity.applyOfflineChrome() {
        val offline = !isConnectionOk()
        val changed = chromeOffline != offline
        if (offline && changed) markInterruptedTabLoads()
        chromeOffline = offline
        if (offline) {
            val current = activeTab
            if (current != null && !current.pinned) {
                val firstPin = tabs.firstOrNull { it.pinned }
                if (firstPin != null) {
                    if (changed) {
                        invalidateOptionsMenu()
                        if (toolbarReady) applyChromeMenu(toolbar.menu)
                    }
                    selectTab(firstPin.id)
                    return
                }
            }
        }
        if (changed) {
            if (!offline) reloadTabsAfterReconnect()
            refreshTabStrip()
            invalidateOptionsMenu()
            if (toolbarReady) applyChromeMenu(toolbar.menu)
        }
    }

internal fun WebViewActivity.applyChromeMenu(menu: Menu?) {
        val usable = !chromeOffline
        val reloadEnabled = usable ||
            (chromeOffline && gate == Gate.BROWSER && activeTab?.pinned == true)
        val accent = ContextCompat.getColor(this, if (usable) R.color.accent else R.color.ink_faint)
        val inkSoft = ContextCompat.getColor(this, if (usable) R.color.ink_soft else R.color.ink_faint)
        val alert = ContextCompat.getColor(this, R.color.alert)
        val alpha = if (usable) 255 else 90

        fun tint(id: Int, color: Int, enabled: Boolean = usable) {
            menu?.findItem(id)?.let { item ->
                item.isEnabled = enabled
                item.icon?.mutate()?.let { icon ->
                    icon.setTint(color)
                    icon.alpha = if (enabled) 255 else alpha
                    item.icon = icon
                }
            }
        }
        tint(R.id.action_new_tab, accent)
        tint(R.id.action_reload, inkSoft, reloadEnabled)
        tint(R.id.action_folders, accent)
        tint(R.id.action_home, accent)
        tint(R.id.action_open_url, inkSoft)
        tint(R.id.action_link_settings, inkSoft)
        menu?.findItem(R.id.action_logout)?.let { item ->
            item.isEnabled = usable
            item.icon?.mutate()?.let { icon ->
                icon.setTint(alert)
                icon.alpha = alpha
                item.icon = icon
            }
        }
        if (toolbarReady) {
            toolbar.isEnabled = usable
            toolbar.overflowIcon?.mutate()?.let { icon ->
                icon.setTint(accent)
                icon.alpha = alpha
                toolbar.overflowIcon = icon
            }
        }
    }

internal fun WebViewActivity.showConnectionBanner() {
        if (!connectionBannerReady) return
        connectionBanner.bringToFront()
        if (connectionBannerVisible && connectionBanner.visibility == View.VISIBLE) return
        connectionBannerVisible = true
        connectionBanner.animate().cancel()
        connectionBanner.visibility = View.VISIBLE
        connectionBanner.post {
            if (!connectionBannerVisible) return@post
            val h = connectionBanner.height.toFloat()
            if (h <= 0f) {
                connectionBanner.translationY = 0f
                return@post
            }
            connectionBanner.translationY = h
            connectionBanner.animate().translationY(0f).setDuration(220).start()
        }
    }

internal fun WebViewActivity.hideConnectionBanner() {
        if (!connectionBannerReady) return
        if (!connectionBannerVisible && connectionBanner.visibility != View.VISIBLE) return
        connectionBannerVisible = false
        connectionBanner.animate().cancel()
        val h = connectionBanner.height.toFloat()
        if (h <= 0f || connectionBanner.visibility != View.VISIBLE) {
            connectionBanner.visibility = View.GONE
            connectionBanner.translationY = 0f
            return
        }
        connectionBanner.animate()
            .translationY(h)
            .setDuration(180)
            .withEndAction {
                if (!connectionBannerVisible) {
                    connectionBanner.visibility = View.GONE
                    connectionBanner.translationY = 0f
                }
            }
            .start()
    }

internal fun WebViewActivity.raiseConnectionBanner() {
        if (connectionBannerReady && connectionBannerVisible) {
            connectionBanner.bringToFront()
        }
    }

internal fun WebViewActivity.registerVpnMonitor() {
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

internal fun WebViewActivity.unregisterVpnMonitor() {
        val callback = networkCallback ?: return
        networkCallback = null
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.scheduleVpnCheck() {
        mainHandler.removeCallbacks(vpnCheckRunnable)
        mainHandler.postDelayed(vpnCheckRunnable, 350)
    }

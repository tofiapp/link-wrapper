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

internal fun WebViewActivity.setTabPinned(tab: BrowserTab, pinned: Boolean, quiet: Boolean = false) {
        if (tab.isHome) return
        if (pinned == tab.pinned) return
        if (pinned) {
            if (tabs.count { it.pinned } >= TrialPins.MAX_ITEMS) {
                if (!quiet) {
                    Toast.makeText(
                        this,
                        "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            tab.pinned = true
            tabs.remove(tab)
            val at = tabs.indexOfLast { it.isHome || it.pinned } + 1
            tabs.add(at.coerceAtLeast(0), tab)
            if (!quiet) {
                Toast.makeText(this, "Karta zůstane otevřená", Toast.LENGTH_SHORT).show()
            }
        } else {
            tab.pinned = false
            cancelPrefetchForUrl(tab.url)
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
        syncOpenTabWebViews()
    }

internal fun WebViewActivity.syncOpenTabWebViews() {
        if (activeTabId < 0) return
        selectTab(activeTabId)
    }

internal fun WebViewActivity.persistOpenTabsFromTabs() {
        TrialPins.save(
            this,
            tabs.filter { !it.isHome }.map { TrialPin(it.title, it.url, it.pinned) }
        )
    }

internal fun WebViewActivity.restorePinnedTabs() {
        val saved = TrialPins.load(this)
        if (saved.isEmpty()) return
        val load = Session.isActive(this)
        for (item in saved) {
            val existing = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (existing != null) {
                existing.pinned = item.pinned
                continue
            }
            if (tabs.size >= WebViewActivityConstants.MAX_TABS) break
            val needsView = load || item.pinned
            val webView = if (!needsView) null
            else if (load) takePrefetch(item.url) ?: createWebView()
            else createWebView()
            val tab = BrowserTab(
                id = nextTabId++,
                webView = webView,
                title = item.title.ifBlank { tabLabel(item.url) },
                url = item.url,
                pinned = item.pinned
            )
            if (item.pinned) {
                val at = tabs.indexOfLast { it.isHome || it.pinned } + 1
                tabs.add(at.coerceAtLeast(0), tab)
            } else {
                tabs.add(tab)
            }
            if (webView != null) {
                try {
                    webView.onPause()
                    webView.setRendererPriorityPolicy(
                        if (item.pinned) WebView.RENDERER_PRIORITY_IMPORTANT
                        else WebView.RENDERER_PRIORITY_WAIVED,
                        !item.pinned
                    )
                } catch (_: Exception) {
                }
                if (load && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
                    TrialIsolation.onNavigate(this, item.url)
                    webView.loadUrl(item.url)
                }
            }
        }
        persistOpenTabsFromTabs()
    }

internal fun WebViewActivity.loadUnloadedTabsAfterLogin() {
        tabs.filter { !it.isHome }.forEach { ensureTabLoaded(it, reviveIfActive = true) }
    }

internal fun WebViewActivity.tabNeedsReconnectReload(tab: BrowserTab): Boolean {
        if (tab.isHome) return false
        if (tab.id in tabsPendingReload) return true
        val wv = tab.webView ?: return true
        val current = wv.url
        return current.isNullOrBlank() || current == "about:blank"
    }

internal fun WebViewActivity.ensureTabLoaded(tab: BrowserTab, reviveIfActive: Boolean = false) {
        if (tab.isHome) return
        var wv = tab.webView
        if (wv == null) {
            wv = takePrefetch(tab.url) ?: createWebView()
            tab.webView = wv
        }
        val current = wv.url
        if (!current.isNullOrBlank() && current != "about:blank" && tab.id !in tabsPendingReload) return
        TrialIsolation.onNavigate(this, tab.url)
        wv.loadUrl(tab.url)
        tabsPendingReload.remove(tab.id)
        if (reviveIfActive && tab.id == activeTabId && gate == Gate.BROWSER &&
            (wv.parent == null || wv.visibility != View.VISIBLE)
        ) {
            reviveBrowserWebView(wv, tab.url)
        }
    }

internal fun WebViewActivity.markInterruptedTabLoads() {
        tabs.filter { !it.isHome }.forEach { tab ->
            val wv = tab.webView
            if (wv == null) {
                tabsPendingReload.add(tab.id)
                return@forEach
            }
            val current = wv.url
            if (current.isNullOrBlank() || current == "about:blank") {
                tabsPendingReload.add(tab.id)
                return@forEach
            }
            if (wv.progress in 1..99) {
                tabsPendingReload.add(tab.id)
            }
        }
    }

internal fun WebViewActivity.reloadTabsAfterReconnect() {
        if (!isConnectionOk()) return
        tabs.filter { !it.isHome && tabNeedsReconnectReload(it) }.forEach {
            ensureTabLoaded(it, reviveIfActive = true)
        }
        startBookmarkPrefetch()
    }

internal fun WebViewActivity.isLoadedPinnedView(webView: WebView): Boolean {
        val tab = tabs.find { it.webView === webView } ?: return false
        if (!tab.pinned) return false
        val current = webView.url
        return !current.isNullOrBlank() && current != "about:blank"
    }

internal fun WebViewActivity.shouldPromptLoginForTab(tab: BrowserTab): Boolean {
        if (!TrialSettings.isTrial() || Session.isActive(this)) return false
        if (tab.isHome || tab.pinned) return false
        if (tab.url.isBlank() || tab.url == Destinations.HOME_URL) return false
        return Destinations.forUrl(tab.url)?.requiresAppLogin == true
    }

internal fun WebViewActivity.maybePromptLoginForActiveUnpinnedTab() {
        if (verifyingLogin) return
        val tab = activeTab ?: return
        if (!shouldPromptLoginForTab(tab)) return
        pendingStartUrl = tab.url
        presentLogin()
    }

internal fun WebViewActivity.openOnNewTab(url: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) return
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        val spareHome = tabs.firstOrNull { it.isHome && it.id != activeTabId }
        when {
            tabs.size < WebViewActivityConstants.MAX_TABS -> {
                val webView = takePrefetch(url) ?: createWebView()
                val tab = BrowserTab(
                    id = nextTabId++,
                    webView = webView,
                    title = tabLabel(url),
                    url = url
                )
                tabs.add(tab)
                persistOpenTabsFromTabs()
                selectTab(tab.id)
                if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                    TrialIsolation.onNavigate(this, url)
                    webView.loadUrl(url)
                }
            }
            spareHome != null -> {
                loadUrlIntoTab(spareHome, url)
                selectTab(spareHome.id)
            }
            else -> {
                Toast.makeText(this, "Maximum je $WebViewActivityConstants.MAX_TABS karet", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Otevřeno na nové kartě", Toast.LENGTH_SHORT).show()
    }

internal fun WebViewActivity.togglePinForUrl(url: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) return
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            setTabPinned(existing, !existing.pinned)
            return
        }
        pinUrl(url)
    }

internal fun WebViewActivity.pinUrl(url: String, quiet: Boolean = false, select: Boolean = true): Boolean {
        if (url.isBlank() || url == Destinations.HOME_URL) return false
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            if (!existing.pinned) setTabPinned(existing, true, quiet)
            if (select) selectTab(existing.id)
            return true
        }
        if (tabs.count { it.pinned } >= TrialPins.MAX_ITEMS) {
            if (!quiet) {
                Toast.makeText(
                    this,
                    "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return false
        }
        if (tabs.size >= WebViewActivityConstants.MAX_TABS) {
            val home = tabs.firstOrNull { it.isHome }
            if (home == null) {
                if (!quiet) {
                    Toast.makeText(this, "Maximum je $WebViewActivityConstants.MAX_TABS karet", Toast.LENGTH_SHORT).show()
                }
                return false
            }
            loadUrlIntoTab(home, url)
            setTabPinned(home, true, quiet)
            if (select) selectTab(home.id)
            return true
        }
        val tab = addTabForUrl(url, select = select) ?: return false
        setTabPinned(tab, true, quiet)
        return true
    }

internal fun WebViewActivity.loadUrlIntoTab(tab: BrowserTab, url: String) {
        if (tab.pinned && samePage(tab.url, url)) {
            selectTab(tab.id)
            return
        }
        if (needsAppLogin(url) && !tab.pinned) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        if (tab.isHome) {
            val webView = createWebView()
            tab.webView = webView
            tab.isHome = false
        }
        tab.url = url
        tab.title = tabLabel(url)
        tab.webView?.loadUrl(url)
        refreshTabStrip()
    }

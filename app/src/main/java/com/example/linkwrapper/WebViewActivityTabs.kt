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

internal fun WebViewActivity.restoreSavedTabsOrStart() {
        val open = pendingResumeUrl ?: pendingStartUrl
        pendingResumeUrl = null
        pendingStartUrl = null
        if (!open.isNullOrBlank() && open != Destinations.HOME_URL) {
            openInNewTab(open)
        } else if (tabs.isEmpty()) {
            openNewHomeTab()
        }
    }

internal fun WebViewActivity.openHomeWindow() {
        val tab = activeTab
        if (tab?.pinned == true) {
            val home = tabs.firstOrNull { it.isHome }
            if (home != null) selectTab(home.id)
            else openNewHomeTab()
            return
        }
        convertActiveTabToHome()
    }

internal fun WebViewActivity.convertActiveTabToHome() {
        val tab = activeTab
        if (tab == null) {
            openNewHomeTab()
            return
        }
        if (tab.pinned) {
            openHomeWindow()
            return
        }
        if (tab.isHome) {
            selectTab(tab.id)
            return
        }
        destroyWebView(tab)
        hideKeyboard()
        tab.isHome = true
        tab.pinned = false
        tab.title = "Domů"
        tab.url = Destinations.HOME_URL
        selectTab(tab.id)
    }

internal fun WebViewActivity.openNewHomeTab() {
        if (tabs.size >= WebViewActivityConstants.MAX_TABS) {
            val existing = tabs.firstOrNull { it.isHome }
            if (existing != null) {
                selectTab(existing.id)
                return
            }
            Toast.makeText(this, "Maximum je $WebViewActivityConstants.MAX_TABS karet", Toast.LENGTH_SHORT).show()
            return
        }
        val tab = BrowserTab(
            id = nextTabId++,
            webView = null,
            title = "Domů",
            url = Destinations.HOME_URL,
            isHome = true
        )
        val at = tabs.indexOfLast { it.isHome } + 1
        tabs.add(at.coerceAtLeast(0), tab)
        selectTab(tab.id)
    }

internal fun WebViewActivity.openInNewTab(url: String) {
        if (url == Destinations.HOME_URL) {
            openNewHomeTab()
            return
        }
        val existing = tabs.filter { it.pinned }.find { samePage(it.url, url) }
        if (existing != null) {
            selectTab(existing.id)
            return
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        val tab = addTabForUrl(url, select = true)
        if (tab == null && tabs.size >= WebViewActivityConstants.MAX_TABS) {
            Toast.makeText(this, "Maximum je $WebViewActivityConstants.MAX_TABS karet", Toast.LENGTH_SHORT).show()
        }
    }

internal fun WebViewActivity.addTabForUrl(url: String, select: Boolean): BrowserTab? {
        if (url.isBlank() || url == Destinations.HOME_URL) return null
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            if (select) selectTab(existing.id)
            return existing
        }
        if (tabs.size >= WebViewActivityConstants.MAX_TABS) return null
        val webView = takePrefetch(url) ?: createWebView()
        val tab = BrowserTab(
            id = nextTabId++,
            webView = webView,
            title = tabLabel(url),
            url = url
        )
        tabs.add(tab)
        persistOpenTabsFromTabs()
        if (select) selectTab(tab.id)
        if (select && (webView.url.isNullOrBlank() || webView.url == "about:blank")) {
            TrialIsolation.onNavigate(this, url)
            webView.loadUrl(url)
        }
        return tab
    }

internal fun WebViewActivity.selectTab(tabId: Long) {
        val target = tabs.find { it.id == tabId } ?: return
        if (chromeOffline && !target.pinned) {
            val pin = tabs.firstOrNull { it.pinned }
            if (pin != null) {
                if (pin.id != tabId) selectTab(pin.id)
                return
            }
            if (activeTabId != -1L && tabId != activeTabId) return
        }
        if (tabId != activeTabId) hideKeyboard()
        activeTabId = tabId
        val promptLogin = shouldPromptLoginForTab(target)
        if (!verifyingLogin && !promptLogin) hideLoginOverlay()
        if (target.isHome) {
            gate = Gate.HOME
            lastContentGate = Gate.HOME
            hideKeyboard()
            progressBar.visibility = View.GONE
            if (homeOverlayReady) {
                homeOverlay.visibility = View.VISIBLE
                homeOverlay.bringToFront()
            }
            populateHomeBookmarks()
            raiseConnectionBanner()
            tabs.forEach { tab ->
                val wv = tab.webView ?: return@forEach
                parkBackgroundWebView(wv, keepAlive = tab.pinned)
            }
            attachImeLayoutListener(false)
            setSensitiveScreen(false)
            refreshTabStrip()
            return
        }

        if (target.webView == null && !promptLogin) {
            val webView = takePrefetch(target.url) ?: createWebView()
            target.webView = webView
            TrialIsolation.onNavigate(this, target.url)
            webView.loadUrl(target.url)
        }

        gate = Gate.BROWSER
        lastContentGate = Gate.BROWSER
        hideHomeOverlay()
        attachImeLayoutListener(false)
        setSensitiveScreen(false)
        TrialIsolation.onNavigate(this, target.url)
        tabs.forEach { tab ->
            val wv = tab.webView ?: return@forEach
            val selected = tab.id == tabId
            if (selected) {
                if (wv.parent == null || wv.visibility != View.VISIBLE) {
                    reviveBrowserWebView(wv, tab.url)
                } else {
                    wv.onResume()
                    wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
            } else {
                parkBackgroundWebView(wv, keepAlive = tab.pinned)
            }
        }
        refreshTabStrip()
        if (promptLogin) {
            pendingStartUrl = target.url
            presentLogin()
        }
    }

internal fun WebViewActivity.parkBackgroundWebView(wv: WebView, keepAlive: Boolean = false) {
        wv.onPause()
        if (keepAlive) {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        } else {
            wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
    }

internal fun WebViewActivity.reviveBrowserWebView(wv: WebView, url: String? = null) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        webContainer.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        wv.visibility = View.VISIBLE
        wv.onResume()
        wv.resumeTimers()
        wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        wv.invalidate()
        injectChartFit(wv, unlock = true)
        injectPsstDataLayout(wv, url ?: tabs.find { it.webView === wv }?.url)
        scheduleChartFit()
        try {
            wv.evaluateJavascript(
                "try{window.dispatchEvent(new Event('resize'))}catch(e){}",
                null
            )
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.closeTab(tabId: Long) {
        if (tabs.size <= 1) return
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val closing = tabs[index]
        if (closing.pinned) return
        tabs.removeAt(index)
        destroyTab(closing)

        if (tabs.isEmpty()) {
            openNewHomeTab()
            return
        }
        if (activeTabId == tabId) {
            val next = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex)) ?: tabs.last()
            selectTab(next.id)
        } else {
            refreshTabStrip()
        }
        persistOpenTabsFromTabs()
    }

internal fun WebViewActivity.destroyWebView(tab: BrowserTab) {
        val wv = tab.webView ?: return
        authFailedViews.remove(wv)
        authChallengeCounts.remove(wv)
        chartPerfInjected.remove(wv)
        webContainer.removeView(wv)
        wv.stopLoading()
        wv.onPause()
        wv.webChromeClient = null
        wv.destroy()
        tab.webView = null
    }

internal fun WebViewActivity.destroyTab(tab: BrowserTab) {
        destroyWebView(tab)
    }

internal fun WebViewActivity.destroyAllTabs() {
        tabs.toList().forEach { destroyTab(it) }
        tabs.clear()
        activeTabId = -1L
        destroyPrefetchViews()
    }

internal fun WebViewActivity.takePrefetch(url: String): WebView? {
        val key = prefetchViews.keys.firstOrNull { samePage(it, url) } ?: return null
        val wv = prefetchViews.remove(key) ?: return null
        if (prefetchActiveView === wv) {
            prefetchLoading = false
            prefetchActiveView = null
            mainHandler.post { pumpBookmarkPrefetch() }
        }
        return wv
    }

internal fun WebViewActivity.destroyPrefetchViews() {
        prefetchLoading = false
        prefetchActiveView = null
        mainHandler.removeCallbacks(prefetchRunnable)
        prefetchQueue.clear()
        prefetchViews.values.toList().forEach { wv -> destroyOrphanWebView(wv) }
        prefetchViews.clear()
    }

internal fun WebViewActivity.destroyOrphanWebView(wv: WebView) {
        authFailedViews.remove(wv)
        authChallengeCounts.remove(wv)
        chartPerfInjected.remove(wv)
        (wv.parent as? ViewGroup)?.removeView(wv)
        try {
            wv.stopLoading()
            wv.onPause()
            wv.webChromeClient = null
            wv.destroy()
        } catch (_: Exception) {
        }
    }

internal fun WebViewActivity.startBookmarkPrefetch() {
        if (!Session.isActive(this)) return
        mainHandler.removeCallbacks(prefetchRunnable)
        mainHandler.postDelayed(prefetchRunnable, 400)
    }

internal fun WebViewActivity.queueBookmarkPrefetch(urls: Collection<String>) {
        val seen = mutableSetOf<String>()
        urls.forEach { raw ->
            val url = raw.trim()
            if (url.isEmpty() || url == Destinations.HOME_URL) return@forEach
            val key = url.lowercase()
            if (!seen.add(key)) return@forEach
            if (tabs.any { !it.isHome && samePage(it.url, url) }) return@forEach
            if (prefetchViews.keys.any { samePage(it, url) }) return@forEach
            if (prefetchQueue.any { samePage(it, url) }) return@forEach
            prefetchQueue.addLast(url)
        }
        startBookmarkPrefetch()
    }

internal fun WebViewActivity.warmDetachedTab(tab: BrowserTab) {
        val wv = tab.webView ?: return
        val current = wv.url
        if (!current.isNullOrBlank() && current != "about:blank") return
        if (wv.parent == null) {
            wv.visibility = View.INVISIBLE
            webContainer.addView(
                wv,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        TrialIsolation.onNavigate(this, tab.url)
        wv.loadUrl(tab.url)
    }

internal fun WebViewActivity.cancelPrefetchForUrl(url: String) {
        prefetchQueue.removeAll { samePage(it, url) }
        val key = prefetchViews.keys.firstOrNull { samePage(it, url) } ?: return
        val wv = prefetchViews.remove(key)
        if (wv != null) destroyOrphanWebView(wv)
    }

internal fun WebViewActivity.pumpBookmarkPrefetch() {
        if (prefetchLoading) return
        if (!isConnectionOk()) return
        if (prefetchViews.size >= WebViewActivityConstants.MAX_PREFETCH) return
        val next = prefetchQueue.removeFirstOrNull()
            ?: TrialBookmarks.load(this).map { it.url }.firstOrNull { url ->
                tabs.none { !it.isHome && samePage(it.url, url) } &&
                    prefetchViews.keys.none { samePage(it, url) }
            }
            ?: return
        val wv = createWebView()
        prefetchViews[next] = wv
        prefetchLoading = true
        prefetchActiveView = wv
        wv.visibility = View.INVISIBLE
        webContainer.addView(
            wv,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        TrialIsolation.onNavigate(this, next)
        wv.loadUrl(next)
        mainHandler.postDelayed({
            if (prefetchActiveView === wv && prefetchLoading) {
                prefetchLoading = false
                prefetchActiveView = null
                try { wv.onPause() } catch (_: Exception) {}
                pumpBookmarkPrefetch()
            }
        }, 20_000L)
    }

internal fun WebViewActivity.onPrefetchFinished(view: WebView) {
        val inPrefetch = prefetchViews.values.any { it === view }
        if (!inPrefetch && prefetchActiveView !== view) return
        prefetchLoading = false
        if (prefetchActiveView === view) prefetchActiveView = null
        view.postDelayed({
            if (prefetchViews.values.none { it === view }) return@postDelayed
            try {
                view.onPause()
                view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
            } catch (_: Exception) {
            }
        }, 3500)
        pumpBookmarkPrefetch()
    }

internal fun WebViewActivity.bumpAuthCount(webView: WebView): Int {
        val map = authChallengeCounts.getOrPut(webView) { mutableMapOf() }
        val n = (map["session"] ?: 0) + 1
        map["session"] = n
        return n
    }

internal fun WebViewActivity.resetAuthCount(webView: WebView) {
        authChallengeCounts[webView]?.remove("session")
    }

internal fun WebViewActivity.bindTabStripItem(
        inflater: LayoutInflater,
        tab: BrowserTab,
        selected: Boolean,
        showClose: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ): View {
        val item = inflater.inflate(R.layout.item_browser_tab, tabStrip, false)
        val root = item.findViewById<View>(R.id.tabRoot)
        val content = item.findViewById<View>(R.id.tabContent)
        val title = item.findViewById<TextView>(R.id.tabTitle)
        val pin = item.findViewById<ImageView>(R.id.tabPin)
        val close = item.findViewById<ImageButton>(R.id.tabClose)
        val savedMark = item.findViewById<View>(R.id.tabSavedMark)

        title.text = tab.title
        title.setTextColor(
            ContextCompat.getColor(this, if (selected) R.color.accent else R.color.ink_soft)
        )
        pin.visibility = if (tab.pinned) View.VISIBLE else View.GONE
        pin.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.saved)
        )
        savedMark.visibility =
            if (!tab.isHome && TrialBookmarks.isSaved(this, tab.url)) View.VISIBLE else View.GONE
        root.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab
        )
        root.clipToOutline = true
        root.invalidateOutline()
        root.setOnClickListener { onClick() }
        root.setOnLongClickListener {
            onLongClick()
            true
        }
        close.visibility = if (showClose) View.VISIBLE else View.GONE
        val padEnd = ((if (showClose) 4 else 14) * resources.displayMetrics.density).toInt()
        content.setPaddingRelative(
            content.paddingStart,
            content.paddingTop,
            padEnd,
            content.paddingBottom
        )
        if (showClose) {
            close.setOnClickListener { closeTab(tab.id) }
        } else {
            close.setOnClickListener(null)
        }
        return item
    }

internal fun WebViewActivity.refreshTabStrip() {
        tabStrip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val source = if (chromeOffline) tabs.filter { it.pinned } else tabs
        val shown = source.sortedWith(
            compareBy<BrowserTab> { TrialPins.stripGroup(it.isHome, it.pinned) }
                .thenBy { tabs.indexOf(it) }
        )
        var titlesSynced = false
        var activeEntryIndex = -1
        shown.forEachIndexed { index, tab ->
            if (tab.id == activeTabId) activeEntryIndex = index
            val savedTitle = TrialBookmarks.findByUrl(this, tab.url)?.title?.trim().orEmpty()
            if (!tab.isHome && savedTitle.isNotEmpty() && tab.title != savedTitle) {
                tab.title = savedTitle
                titlesSynced = true
            }
            val showClose = tabs.size > 1 && !tab.pinned
            val item = bindTabStripItem(
                inflater = inflater,
                tab = tab,
                selected = tab.id == activeTabId,
                showClose = showClose,
                onClick = { selectTab(tab.id) },
                onLongClick = {
                    if (!chromeOffline) showTabActions(tab)
                }
            )
            tabStrip.addView(item)
        }
        if (titlesSynced) persistOpenTabsFromTabs()
        tabScroll.post {
            if (activeEntryIndex >= 0 && activeEntryIndex < tabStrip.childCount) {
                val child = tabStrip.getChildAt(activeEntryIndex)
                tabScroll.smoothScrollTo((child.left - 24).coerceAtLeast(0), 0)
            }
        }
    }

internal fun WebViewActivity.tabLabel(url: String): String {
        val saved = TrialBookmarks.findByUrl(this, url)?.title?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved
        return Destinations.tabTitle(url)
    }

internal fun WebViewActivity.applySavedTitleToTabs(url: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        tabs.filter { !it.isHome && samePage(it.url, url) }.forEach { tab ->
            tab.title = clean
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
    }

internal fun WebViewActivity.updateTabMeta(webView: WebView, url: String?) {
        val tab = tabs.find { it.webView === webView } ?: return
        var stripChanged = false
        if (!url.isNullOrBlank()) {
            tab.url = url
            val label = tabLabel(url)
            if (tab.title != label) {
                tab.title = label
                stripChanged = true
            }
            persistOpenTabsFromTabs()
        }
        if (stripChanged) refreshTabStrip()
    }

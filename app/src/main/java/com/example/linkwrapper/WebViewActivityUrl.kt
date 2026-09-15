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

internal fun WebViewActivity.explicitUrlFromIntent(intent: Intent?): String? {
        intent?.data?.toString()?.let { return it }
        intent?.getStringExtra(WebViewActivity.EXTRA_URL)?.let { return it }

        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            return shared.split(Regex("\\s+"))
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: shared.takeIf { it.isNotEmpty() }?.let { "https://$it" }
        }
        return null
    }

internal fun WebViewActivity.normalizeUrl(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        if (Uri.parse(text).host.isNullOrEmpty()) return null
        return text
    }

internal fun WebViewActivity.loadInActiveTab(url: String) {
        val tab = activeTab
        if (tab == null) {
            openInNewTab(url)
            return
        }
        dialogShown = false
        if (tab.pinned) {
            if (samePage(tab.url, url)) {
                selectTab(tab.id)
                return
            }
            openInNewTab(url)
            return
        }
        if (tab.isHome) {
            if (needsAppLogin(url)) {
                pendingStartUrl = url
                presentLogin()
                return
            }
            val webView = takePrefetch(url) ?: createWebView()
            tab.webView = webView
            tab.isHome = false
            tab.url = url
            tab.title = tabLabel(url)
            selectTab(tab.id)
            if (webView.url.isNullOrBlank() || webView.url == "about:blank") {
                TrialIsolation.onNavigate(this, url)
                webView.loadUrl(url)
            }
            return
        }
        tab.url = url
        tab.title = tabLabel(url)
        selectTab(tab.id)
        TrialIsolation.onNavigate(this, url)
        tab.webView?.loadUrl(url)
    }

internal fun WebViewActivity.showOpenUrlDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_open_url, null)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.urlLayout)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        val current = activeTab?.url?.takeUnless { it == Destinations.HOME_URL }
        urlInput.setText(current ?: WebViewActivity.DEFAULT_URL)
        urlInput.setSelection(urlInput.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Otevřít URL adresu")
            .setView(view)
            .setPositiveButton("Otevřít URL", null)
            .setNegativeButton("Zrušit", null)
            .create()
        urlDialog = dialog

        fun tryOpen() {
            val normalized = normalizeUrl(urlInput.text?.toString().orEmpty())
            if (normalized == null) {
                urlLayout.error = "Tohle nevypadá jako adresa"
                return
            }
            urlLayout.error = null
            hideKeyboard(urlInput)
            dialog.dismiss()
            dialogShown = false
            if (needsAppLogin(normalized)) {
                pendingStartUrl = normalized
                presentLogin()
            } else {
                enterBrowser()
                loadInActiveTab(normalized)
            }
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
        dialog.setOnDismissListener {
            hideKeyboard(urlInput)
            urlDialog = null
            dialogShown = false
            window.decorView.post { hideKeyboard() }
        }
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        dialog.show()
    }

internal fun WebViewActivity.handleWebViewLongClick(webView: WebView): Boolean {
        if (chromeOffline) return true
        val result = webView.hitTestResult
        when (result.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val fallback = result.extra
                val handler = Handler(Looper.getMainLooper()) { msg ->
                    val href = msg.data.getString("url")
                        ?: msg.data.getString("src")
                        ?: fallback
                    if (!href.isNullOrBlank()) {
                        val uri = runCatching { Uri.parse(href) }.getOrNull()
                        if (uri != null && isAllowedWebUri(uri)) {
                            showLinkOrTabActions(href, "Odkaz")
                        }
                    }
                    true
                }
                webView.requestFocusNodeHref(Message.obtain(handler))
            }
        }
        return true
    }

internal fun WebViewActivity.showLinkOrTabActions(url: String, heading: String) {
        if (url.isBlank() || url == Destinations.HOME_URL) {
            Toast.makeText(this, "Domů nelze otevřít na nové kartě", Toast.LENGTH_SHORT).show()
            return
        }
        val already = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        showActionSheet(heading, url, pageActionRows(url, already))
    }

internal fun WebViewActivity.showTabActions(tab: BrowserTab) {
        if (tab.isHome) {
            Toast.makeText(this, "Domů nelze otevřít na nové kartě", Toast.LENGTH_SHORT).show()
            return
        }
        showActionSheet(tab.title, tab.url, pageActionRows(tab.url, tab))
    }

internal fun WebViewActivity.pageActionRows(url: String, tab: BrowserTab?): List<ActionRow> {
        val rows = mutableListOf<ActionRow>()
        val openTab = tab
        rows.add(
            if (openTab != null && openTab.pinned) {
                ActionRow("Odepnout", R.drawable.ic_wifi_off) { setTabPinned(openTab, false) }
            } else {
                ActionRow("Připnout na lištu", R.drawable.ic_wifi_off) {
                    if (openTab != null) setTabPinned(openTab, true)
                    else togglePinForUrl(url)
                }
            }
        )
        rows.add(ActionRow("Otevřít na nové kartě", R.drawable.ic_add) { openOnNewTab(url) })
        if (TrialBookmarks.isSaved(this, url)) {
            rows.add(ActionRow("Přejmenovat", R.drawable.ic_edit) { renameSavedPage(url) })
            rows.add(ActionRow("Odebrat", R.drawable.ic_close) { removeSavedPage(url) })
        } else {
            rows.add(ActionRow("Uložit", R.drawable.ic_bookmark) { savePageThenRename(url) })
        }
        return rows
    }

internal fun WebViewActivity.showActionSheet(title: String, subtitle: String?, rows: List<ActionRow>) {
        dismissActionSheet()
        val view = layoutInflater.inflate(R.layout.popup_action_sheet, null)
        view.findViewById<TextView>(R.id.actionSheetTitle).text = title
        val sub = view.findViewById<TextView>(R.id.actionSheetSubtitle)
        if (subtitle.isNullOrBlank()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = subtitle
        }
        val list = view.findViewById<LinearLayout>(R.id.actionSheetList)
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        val inflater = LayoutInflater.from(this)
        rows.forEach { row ->
            val item = inflater.inflate(R.layout.item_action_row, list, false)
            item.findViewById<ImageView>(R.id.actionRowIcon).setImageResource(row.icon)
            item.findViewById<TextView>(R.id.actionRowTitle).text = row.title
            item.setOnClickListener {
                dialog.dismiss()
                row.run()
            }
            list.addView(item)
        }
        actionDialog = dialog
        dialog.setOnDismissListener {
            if (actionDialog === dialog) actionDialog = null
        }
        dialog.show()
        dialog.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (320 * resources.displayMetrics.density).toInt()
            win.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            val loc = IntArray(2)
            val chromeBottom = if (tabScrollReady) {
                tabScroll.getLocationOnScreen(loc)
                loc[1] + tabScroll.height
            } else {
                (72 * resources.displayMetrics.density).toInt()
            }
            win.decorView.getLocationOnScreen(loc)
            val gap = (8 * resources.displayMetrics.density).toInt()
            win.attributes = win.attributes.apply {
                y = (chromeBottom - loc[1] + gap).coerceAtLeast(gap)
            }
        }
    }

internal fun WebViewActivity.dismissActionSheet() {
        try {
            actionDialog?.dismiss()
        } catch (_: Exception) {
        }
        actionDialog = null
    }

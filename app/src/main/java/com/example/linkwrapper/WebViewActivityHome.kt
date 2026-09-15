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

internal fun WebViewActivity.bindHomeUi() {
        homeOverlay = findViewById(R.id.homeOverlay)
        homeAppList = findViewById(R.id.homeAppList)
        homeBookmarkList = findViewById(R.id.homeBookmarkList)
        homeVersion = findViewById(R.id.homeVersion)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        homeVersion.text = getString(R.string.version_label, version ?: "—")
        populateHomeApps()
    }

internal fun WebViewActivity.populateHomeApps() {
        homeAppList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val gap = (10 * resources.displayMetrics.density).toInt()
        Destinations.apps.forEachIndexed { index, app ->
            val item = inflater.inflate(R.layout.item_home_app, homeAppList, false)
            item.findViewById<TextView>(R.id.homeAppTitle).text = app.title
            item.isLongClickable = false
            item.setOnLongClickListener { true }
            item.findViewById<TextView>(R.id.homeAppTitle).setOnLongClickListener { true }
            val lp = if (Destinations.apps.size == 1) {
                val half = ((homeAppList.width.takeIf { it > 0 }
                    ?: (resources.displayMetrics.widthPixels
                        - (80 * resources.displayMetrics.density).toInt())) / 2)
                    .coerceAtLeast((240 * resources.displayMetrics.density).toInt())
                LinearLayout.LayoutParams(half, LinearLayout.LayoutParams.MATCH_PARENT)
            } else {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (index > 0) marginStart = gap
                }
            }
            item.layoutParams = lp
            val offline = chromeOffline
            item.alpha = if (offline) 0.45f else 1f
            item.isEnabled = !offline
            item.setOnClickListener {
                if (!ensureHomeNavigationAllowed()) return@setOnClickListener
                openDestination(app)
            }
            homeAppList.addView(item)
        }
        populateHomeBookmarks()
    }

internal fun WebViewActivity.populateHomeBookmarks() {
        if (!homeBookmarkListReady) return
        homeBookmarkList.removeAllViews()
        val items = TrialBookmarks.load(this)
        if (items.isEmpty()) {
            homeBookmarkList.visibility = View.GONE
            return
        }
        homeBookmarkList.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(this)
        val gap = (10 * resources.displayMetrics.density).toInt()
        val groups = TrialBookmarks.grouped(items, TrialBookmarks.loadFolders(this))
        renderBookmarkGroups(
            parent = homeBookmarkList,
            groups = groups,
            inflater = inflater,
            foldersFirst = false,
            hideCollapsedItems = false,
            onFolderHeader = { header, folder ->
                header.findViewById<TextView>(R.id.groupHeaderTitle).text = folder.title
                header.findViewById<View>(R.id.groupHeaderChevron).visibility = View.GONE
                header.isClickable = false
                header.background = null
            },
            onFolderClick = null,
            onFolderLongClick = null,
            onItems = { items -> addHomeBookmarkRows(homeBookmarkList, items, gap, inflater) }
        )
    }

internal fun WebViewActivity.addHomeBookmarkRows(
        parent: LinearLayout,
        items: List<TrialBookmark>,
        gap: Int,
        inflater: LayoutInflater
    ) {
        items.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            }
            rowItems.forEachIndexed { index, bookmark ->
                val card = inflater.inflate(R.layout.item_home_bookmark, row, false)
                card.findViewById<TextView>(R.id.homeBookmarkTitle).text = bookmark.title
                val lp = LinearLayout.LayoutParams(
                    0,
                    (72 * resources.displayMetrics.density).toInt(),
                    1f
                )
                if (index > 0) lp.marginStart = gap
                card.layoutParams = lp
                val offline = chromeOffline
                card.alpha = if (offline) 0.45f else 1f
                card.isEnabled = !offline
                card.setOnClickListener {
                    if (!ensureHomeNavigationAllowed()) return@setOnClickListener
                    openSavedUrl(bookmark.url)
                }
                card.findViewById<View>(R.id.homeBookmarkMore).setOnClickListener { more ->
                    showHomeBookmarkMenu(more, bookmark)
                }
                row.addView(card)
            }
            if (rowItems.size == 1) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = gap
                }
                row.addView(spacer)
            }
            parent.addView(row)
        }
    }

internal fun WebViewActivity.hideHomeOverlay() {
        if (!homeOverlayReady) return
        homeOverlay.visibility = View.GONE
    }

internal fun WebViewActivity.openDestination(app: Destinations.AppLink) {
        if (!ensureHomeNavigationAllowed()) return
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, app.url) }
        if (existing != null) {
            enterBrowser()
            selectTab(existing.id)
            return
        }
        if (app.requiresAppLogin && !Session.isActive(this)) {
            pendingStartUrl = app.url
            presentLogin()
            return
        }
        enterBrowser()
        loadInActiveTab(app.url)
    }

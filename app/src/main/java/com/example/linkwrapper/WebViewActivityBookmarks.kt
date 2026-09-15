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

internal fun WebViewActivity.openSavedUrl(url: String) {
        val existing = tabs.filter { !it.isHome }.find { samePage(it.url, url) }
        if (existing != null) {
            enterBrowser()
            selectTab(existing.id)
            return
        }
        if (needsAppLogin(url)) {
            pendingStartUrl = url
            presentLogin()
            return
        }
        enterBrowser()
        if (activeTab?.isHome == true) loadInActiveTab(url)
        else openInNewTab(url)
    }

internal fun WebViewActivity.showTrialBookmarkMenu() {
        dismissBookmarkPopup()
        val content = layoutInflater.inflate(R.layout.popup_trial_bookmarks, null)
        val density = resources.displayMetrics.density
        val width = minOf(
            (resources.displayMetrics.widthPixels - (24 * density).toInt()),
            (400 * density).toInt()
        )
        val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = 12f * density
        popup.setOnDismissListener { bookmarkPopup = null }
        bookmarkPopup = popup
        bindTrialBookmarkMenu(content, popup)
        val anchor = toolbar.findViewById<View>(R.id.action_folders) ?: toolbar
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

internal fun WebViewActivity.bindTrialBookmarkMenu(content: View, popup: PopupWindow) {
        val save = content.findViewById<TextView>(R.id.bookmarkSave)
        val divider = content.findViewById<View>(R.id.bookmarkDivider)
        val empty = content.findViewById<TextView>(R.id.bookmarkEmpty)
        val list = content.findViewById<LinearLayout>(R.id.bookmarkList)
        val scroll = content.findViewById<View>(R.id.bookmarkScroll)
        content.findViewById<View>(R.id.bookmarkAddFolder).setOnClickListener {
            promptFolderName("Nová skupina", "", "Přidat") { name ->
                val folder = TrialBookmarks.addFolder(this, name)
                if (folder == null) {
                    Toast.makeText(this, "Maximum je ${TrialBookmarks.MAX_FOLDERS} skupin", Toast.LENGTH_SHORT).show()
                    return@promptFolderName
                }
                populateHomeBookmarks()
                if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
            }
        }
        val saveUrl = currentSaveableUrl()?.takeIf { !TrialBookmarks.isSaved(this, it) }
        save.visibility = if (saveUrl != null) View.VISIBLE else View.GONE
        save.setOnClickListener {
            val url = currentSaveableUrl() ?: return@setOnClickListener
            popup.dismiss()
            savePageThenRename(url)
        }
        val items = TrialBookmarks.load(this)
        val folders = TrialBookmarks.loadFolders(this)
        val groups = TrialBookmarks.grouped(
            items,
            folders,
            includeEmptyFolders = true,
            foldersFirst = true
        )
        list.removeAllViews()
        empty.visibility = if (items.isEmpty() && folders.isEmpty()) View.VISIBLE else View.GONE
        divider.visibility = if (saveUrl != null && (items.isNotEmpty() || folders.isNotEmpty())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val density = resources.displayMetrics.density
        val maxH = minOf(
            (resources.displayMetrics.heightPixels * 0.55f).toInt(),
            (480 * density).toInt()
        )
        val longList = items.size + folders.size > 5
        scroll.layoutParams = scroll.layoutParams.apply {
            height = if (longList) maxH else ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val inflater = LayoutInflater.from(this)
        renderBookmarkGroups(
            parent = list,
            groups = groups,
            inflater = inflater,
            foldersFirst = true,
            onFolderHeader = { header, folder ->
                header.findViewById<TextView>(R.id.groupHeaderTitle).text = folder.title
                val chevron = header.findViewById<ImageView>(R.id.groupHeaderChevron)
                chevron.rotation = if (folder.collapsed) 0f else 180f
                bindFolderHeaderActions(header, folder, popup, content)
            },
            onFolderClick = { folder ->
                TrialBookmarks.setFolderCollapsed(this, folder.id, !folder.collapsed)
                if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
            },
            onFolderLongClick = { folder ->
                showFolderActions(folder, content, popup)
            },
            onItems = { items ->
                items.forEach { item ->
                    val row = inflater.inflate(R.layout.item_trial_bookmark, list, false)
                    row.findViewById<TextView>(R.id.bookmarkTitle).text = item.title
                    row.findViewById<TextView>(R.id.bookmarkUrl).text =
                        item.url.removePrefix("https://").removePrefix("http://")
                    row.setOnClickListener {
                        dismissBookmarkPopup()
                        openSavedUrl(item.url)
                    }
                    bindBookmarkActions(
                        pin = row.findViewById(R.id.bookmarkPin),
                        rename = row.findViewById(R.id.bookmarkRename),
                        delete = row.findViewById(R.id.bookmarkDelete),
                        item = item,
                        afterChange = {
                            if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                        }
                    )
                    list.addView(row)
                }
            }
        )
    }

internal fun WebViewActivity.renderBookmarkGroups(
        parent: LinearLayout,
        groups: List<BookmarkGroup>,
        inflater: LayoutInflater,
        foldersFirst: Boolean,
        hideCollapsedItems: Boolean = true,
        onFolderHeader: (View, TrialBookmarkFolder) -> Unit,
        onFolderClick: ((TrialBookmarkFolder) -> Unit)?,
        onFolderLongClick: ((TrialBookmarkFolder) -> Unit)?,
        onItems: (List<TrialBookmark>) -> Unit
    ) {
        val hasFoldered = groups.any { it.folder != null && it.items.isNotEmpty() }
        val hasUnfiled = groups.any { it.folder == null && it.items.isNotEmpty() }
        var sectionDividerShown = false
        groups.forEach { group ->
            val folder = group.folder
            if (!sectionDividerShown && hasFoldered && hasUnfiled) {
                val showDivider = if (foldersFirst) folder == null else folder != null
                if (showDivider) {
                    parent.addView(
                        inflater.inflate(R.layout.item_bookmark_section_divider, parent, false)
                    )
                    sectionDividerShown = true
                }
            }
            if (folder != null) {
                val header = inflater.inflate(R.layout.item_bookmark_group_header, parent, false)
                onFolderHeader(header, folder)
                onFolderClick?.let { click ->
                    header.setOnClickListener { click(folder) }
                }
                onFolderLongClick?.let { longClick ->
                    header.setOnLongClickListener {
                        longClick(folder)
                        true
                    }
                }
                parent.addView(header)
                if (hideCollapsedItems && folder.collapsed) return@forEach
            }
            if (group.items.isNotEmpty()) onItems(group.items)
        }
    }

internal fun WebViewActivity.folderItems(folderId: String): List<TrialBookmark> =
        TrialBookmarks.itemsInFolder(this, folderId)


internal fun WebViewActivity.bindFolderHeaderActions(
        header: View,
        folder: TrialBookmarkFolder,
        popup: PopupWindow? = null,
        popupContent: View? = null
    ) {
        val items = folderItems(folder.id)
        val openAll = header.findViewById<ImageButton>(R.id.groupHeaderOpenAll)
        val pinAll = header.findViewById<ImageButton>(R.id.groupHeaderPinAll)
        val tabStack = header.findViewById<ImageButton>(R.id.groupHeaderTabStack)
        val show = items.isNotEmpty()
        openAll.visibility = if (show) View.VISIBLE else View.GONE
        pinAll.visibility = if (show) View.VISIBLE else View.GONE
        tabStack.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        openAll.setOnClickListener {
            openFolderInTabs(folder)
            popup?.dismiss()
        }
        pinAll.setOnClickListener {
            pinFolderInTabs(folder)
            populateHomeBookmarks()
            if (popup?.isShowing == true && popupContent != null) {
                bindTrialBookmarkMenu(popupContent, popup)
            }
        }
        tabStack.setOnClickListener {
            toggleFolderTabGrouped(folder)
            if (popup?.isShowing == true && popupContent != null) {
                bindTrialBookmarkMenu(popupContent, popup)
            }
        }
        updateFolderPinGlyph(pinAll, folder)
        updateFolderStackGlyph(tabStack, folder)
    }

internal fun WebViewActivity.updateFolderPinGlyph(pin: ImageButton, folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        val allPinned = items.isNotEmpty() && items.all { isUrlPinned(it.url) }
        pin.contentDescription = getString(
            if (allPinned) R.string.folder_unpin_all else R.string.folder_pin_all
        )
        ImageViewCompat.setImageTintList(
            pin,
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (allPinned) R.color.saved else R.color.ink_soft)
            )
        )
    }

internal fun WebViewActivity.updateFolderStackGlyph(stack: ImageButton, folder: TrialBookmarkFolder) {
        stack.contentDescription = getString(
            if (folder.tabGrouped) R.string.folder_tab_stack_off else R.string.folder_tab_stack_on
        )
        ImageViewCompat.setImageTintList(
            stack,
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    this,
                    if (folder.tabGrouped) R.color.saved else R.color.ink_soft
                )
            )
        )
    }

internal fun WebViewActivity.toggleFolderTabGrouped(folder: TrialBookmarkFolder) {
        val grouped = !folder.tabGrouped
        if (grouped) expandedTabGroups.remove(folder.id)
        TrialBookmarks.setFolderTabGrouped(this, folder.id, grouped)
        refreshTabStrip()
        populateHomeBookmarks()
        Toast.makeText(
            this,
            getString(if (grouped) R.string.folder_tab_stacked else R.string.folder_tab_expanded),
            Toast.LENGTH_SHORT
        ).show()
    }

internal fun WebViewActivity.openFolderInTabs(folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.folder_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val needsLogin = items.firstOrNull { needsAppLogin(it.url) && !Session.isActive(this) }
        if (needsLogin != null) {
            pendingStartUrl = needsLogin.url
            presentLogin()
            return
        }
        dismissBookmarkPopup()
        var opened = 0
        var alreadyOpen = 0
        var focusId: Long? = null
        for (item in items) {
            val existing = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (existing != null) {
                alreadyOpen++
                if (focusId == null) focusId = existing.id
                continue
            }
            if (tabs.size >= WebViewActivityConstants.MAX_TABS) break
            val tab = addTabForUrl(item.url, select = false) ?: break
            opened++
            if (focusId == null) focusId = tab.id
        }
        if (focusId != null) {
            enterBrowser()
            selectTab(focusId)
        }
        val total = opened + alreadyOpen
        val message = when {
            opened == 0 && alreadyOpen > 0 ->
                getString(R.string.folder_tabs_already_open)
            total < items.size ->
                getString(R.string.folder_opened_tabs, total, items.size) +
                    " — maximum je $WebViewActivityConstants.MAX_TABS karet"
            opened > 0 ->
                getString(R.string.folder_opened_tabs, opened, items.size)
            else -> "Maximum je $WebViewActivityConstants.MAX_TABS karet"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

internal fun WebViewActivity.pinFolderInTabs(folder: TrialBookmarkFolder) {
        val items = folderItems(folder.id)
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.folder_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val allPinned = items.all { isUrlPinned(it.url) }
        if (allPinned) {
            unpinFolderInTabs(items)
            return
        }
        val needsLogin = items.firstOrNull { needsAppLogin(it.url) && !Session.isActive(this) }
        if (needsLogin != null) {
            pendingStartUrl = needsLogin.url
            presentLogin()
            return
        }
        dismissBookmarkPopup()
        var pinned = 0
        for (item in items) {
            if (isUrlPinned(item.url)) continue
            if (!pinUrl(item.url, quiet = true, select = false)) break
            pinned++
        }
        warmPinnedFolderTabs(items.map { it.url })
        enterBrowser()
        refreshTabStrip()
        populateHomeBookmarks()
        Toast.makeText(
            this,
            getString(R.string.folder_prefetch_started, items.size),
            Toast.LENGTH_SHORT
        ).show()
        val message = when {
            pinned > 0 -> getString(R.string.folder_pinned_tabs, pinned, items.size)
            else -> "Maximum je ${TrialPins.MAX_ITEMS} připnutých karet"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

internal fun WebViewActivity.unpinFolderInTabs(items: List<TrialBookmark>) {
        dismissBookmarkPopup()
        var unpinned = 0
        for (item in items) {
            val tab = tabs.filter { !it.isHome }.find { samePage(it.url, item.url) }
            if (tab != null && tab.pinned) {
                setTabPinned(tab, false, quiet = true)
                unpinned++
            }
        }
        refreshTabStrip()
        populateHomeBookmarks()
        Toast.makeText(
            this,
            getString(R.string.folder_unpinned_tabs, unpinned),
            Toast.LENGTH_SHORT
        ).show()
    }

internal fun WebViewActivity.showFolderActions(folder: TrialBookmarkFolder, content: View, popup: PopupWindow) {
        MaterialAlertDialogBuilder(this)
            .setTitle(folder.title)
            .setItems(
                arrayOf(
                    "Otevřít vše v kartách",
                    if (folderItems(folder.id).all { isUrlPinned(it.url) }) {
                        "Odepnout vše"
                    } else {
                        "Připnout a přednačíst vše"
                    },
                    if (folder.tabGrouped) "Rozbalit na liště" else "Smrštit na liště do jedné karty",
                    "Přejmenovat",
                    "Odebrat skupinu"
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        openFolderInTabs(folder)
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    1 -> {
                        pinFolderInTabs(folder)
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    2 -> {
                        toggleFolderTabGrouped(folder)
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    3 -> promptFolderName("Přejmenovat skupinu", folder.title, "Hotovo") { name ->
                        if (!TrialBookmarks.renameFolder(this, folder.id, name)) {
                            Toast.makeText(this, "Skupinu nelze přejmenovat", Toast.LENGTH_SHORT).show()
                            return@promptFolderName
                        }
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                    4 -> {
                        TrialBookmarks.removeFolder(this, folder.id)
                        populateHomeBookmarks()
                        if (popup.isShowing) bindTrialBookmarkMenu(content, popup)
                    }
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    @SuppressLint("RestrictedApi")
internal fun WebViewActivity.showHomeBookmarkMenu(anchor: View, item: TrialBookmark) {
        val popup = PopupMenu(this, anchor, Gravity.END)
        val pinTitle = if (isUrlPinned(item.url)) "Odepnout" else "Připnout na lištu"
        popup.menu.add(0, 1, 0, pinTitle).setIcon(R.drawable.ic_wifi_off)
        popup.menu.add(0, 2, 1, "Přejmenovat").setIcon(R.drawable.ic_edit)
        popup.menu.add(0, 3, 2, "Odebrat").setIcon(R.drawable.ic_close)
        (popup.menu as? MenuBuilder)?.setOptionalIconsVisible(true)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    togglePinForUrl(item.url)
                    populateHomeBookmarks()
                }
                2 -> promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
                    TrialBookmarks.rename(this, item.id, title, folderId)
                    populateHomeBookmarks()
                    applySavedTitleToTabs(item.url, title)
                }
                3 -> {
                    TrialBookmarks.remove(this, item.id)
                    populateHomeBookmarks()
                    forgetSavedTitleOnTabs(item.url)
                }
            }
            true
        }
        popup.show()
    }

internal fun WebViewActivity.bindBookmarkActions(
        pin: ImageButton,
        rename: View,
        delete: View,
        item: TrialBookmark,
        afterChange: () -> Unit = {}
    ) {
        bindBookmarkPin(pin, item.url, afterChange)
        rename.setOnClickListener {
            promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
                TrialBookmarks.rename(this, item.id, title, folderId)
                populateHomeBookmarks()
                applySavedTitleToTabs(item.url, title)
                afterChange()
            }
        }
        delete.setOnClickListener {
            TrialBookmarks.remove(this, item.id)
            populateHomeBookmarks()
            forgetSavedTitleOnTabs(item.url)
            afterChange()
        }
    }

internal fun WebViewActivity.bindBookmarkPin(pin: ImageButton, url: String, afterChange: () -> Unit = {}) {
        updatePinGlyph(pin, url)
        pin.setOnClickListener {
            togglePinForUrl(url)
            populateHomeBookmarks()
            afterChange()
        }
    }

internal fun WebViewActivity.updatePinGlyph(pin: ImageButton, url: String) {
        val pinned = isUrlPinned(url)
        pin.contentDescription = if (pinned) "Odepnout" else "Připnout na lištu"
        ImageViewCompat.setImageTintList(
            pin,
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (pinned) R.color.saved else R.color.ink_soft)
            )
        )
    }

internal fun WebViewActivity.isUrlPinned(url: String): Boolean =
        tabs.any { !it.isHome && it.pinned && samePage(it.url, url) }


internal fun WebViewActivity.savePageThenRename(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed == Destinations.HOME_URL) {
            Toast.makeText(this, "Tuhle stránku nelze uložit", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = TrialBookmarks.findByUrl(this, trimmed)
        if (existing != null) {
            renameSavedPage(trimmed)
            return
        }
        val suggested = tabs.filter { !it.isHome }.find { samePage(it.url, trimmed) }
            ?.title
            ?.takeIf { it.isNotBlank() }
            ?: tabLabel(trimmed)
        val added = TrialBookmarks.add(this, suggested, trimmed)
        if (added == null) {
            Toast.makeText(this, "Složka je plná", Toast.LENGTH_SHORT).show()
            return
        }
        populateHomeBookmarks()
        applySavedTitleToTabs(trimmed, added.title)
        promptBookmarkLabel("Přejmenovat", added.title, added.folderId, "Hotovo") { title, folderId ->
            TrialBookmarks.rename(this, added.id, title, folderId)
            populateHomeBookmarks()
            applySavedTitleToTabs(trimmed, title)
        }
    }

internal fun WebViewActivity.renameSavedPage(url: String) {
        val item = TrialBookmarks.findByUrl(this, url)
        if (item == null) {
            Toast.makeText(this, "Stránka není uložená", Toast.LENGTH_SHORT).show()
            return
        }
        promptBookmarkLabel("Přejmenovat", item.title, item.folderId, "Hotovo") { title, folderId ->
            TrialBookmarks.rename(this, item.id, title, folderId)
            populateHomeBookmarks()
            applySavedTitleToTabs(url, title)
        }
    }

internal fun WebViewActivity.removeSavedPage(url: String) {
        val item = TrialBookmarks.findByUrl(this, url) ?: return
        TrialBookmarks.remove(this, item.id)
        populateHomeBookmarks()
        forgetSavedTitleOnTabs(url)
    }

internal fun WebViewActivity.forgetSavedTitleOnTabs(url: String) {
        tabs.filter { !it.isHome && samePage(it.url, url) }.forEach { tab ->
            tab.title = Destinations.tabTitle(tab.url)
        }
        persistOpenTabsFromTabs()
        refreshTabStrip()
    }

internal fun WebViewActivity.promptBookmarkLabel(
        title: String,
        initial: String,
        currentFolderId: String?,
        confirmLabel: String = "Uložit",
        onSave: (String, String?) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_bookmark_label, null)
        val layout = view.findViewById<TextInputLayout>(R.id.bookmarkLabelLayout)
        val input = view.findViewById<TextInputEditText>(R.id.bookmarkLabelInput)
        val folderLayout = view.findViewById<TextInputLayout>(R.id.bookmarkFolderLayout)
        val folderInput = view.findViewById<AutoCompleteTextView>(R.id.bookmarkFolderInput)
        val newLayout = view.findViewById<TextInputLayout>(R.id.bookmarkFolderNewLayout)
        val newInput = view.findViewById<TextInputEditText>(R.id.bookmarkFolderNewInput)
        val confirm = view.findViewById<MaterialButton>(R.id.bookmarkDialogConfirm)
        val cancel = view.findViewById<View>(R.id.bookmarkDialogCancel)
        view.findViewById<TextView>(R.id.bookmarkDialogTitle).text = title
        confirm.text = confirmLabel
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        val folders = TrialBookmarks.loadFolders(this)
        val labels = mutableListOf(WebViewActivityConstants.FOLDER_NONE)
        labels.addAll(folders.map { it.title })
        labels.add(WebViewActivityConstants.FOLDER_NEW)
        folderInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels))
        folderInput.threshold = 0
        folderInput.keyListener = null
        val currentTitle = folders.find { it.id == currentFolderId }?.title ?: WebViewActivityConstants.FOLDER_NONE
        folderInput.setText(currentTitle, false)
        fun syncNewFolderField() {
            val selected = folderInput.text?.toString().orEmpty()
            newLayout.visibility = if (selected == WebViewActivityConstants.FOLDER_NEW) View.VISIBLE else View.GONE
            if (selected != WebViewActivityConstants.FOLDER_NEW) newLayout.error = null
        }
        syncNewFolderField()
        folderInput.setOnClickListener { folderInput.showDropDown() }
        folderInput.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) folderInput.showDropDown() }
        folderInput.setOnItemClickListener { _, _, _, _ ->
            syncNewFolderField()
            if (newLayout.visibility == View.VISIBLE) {
                view.post {
                    (view as? ScrollView)?.smoothScrollTo(0, newLayout.bottom)
                }
            }
        }
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        bookmarkLabelDialog?.dismiss()
        bookmarkLabelDialog = dialog
        cancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            val label = input.text?.toString().orEmpty().trim()
            if (label.isEmpty()) {
                layout.error = "Zadejte popisek"
                return@setOnClickListener
            }
            layout.error = null
            val selected = folderInput.text?.toString().orEmpty()
            val folderId = when (selected) {
                WebViewActivityConstants.FOLDER_NONE, "" -> null
                WebViewActivityConstants.FOLDER_NEW -> {
                    val name = newInput.text?.toString().orEmpty().trim()
                    if (name.isEmpty()) {
                        newLayout.error = "Zadejte název skupiny"
                        return@setOnClickListener
                    }
                    val folder = TrialBookmarks.addFolder(this, name)
                    if (folder == null) {
                        newLayout.error = "Maximum je ${TrialBookmarks.MAX_FOLDERS} skupin"
                        return@setOnClickListener
                    }
                    folder.id
                }
                else -> folders.find { it.title == selected }?.id
                    ?: TrialBookmarks.addFolder(this, selected)?.id
            }
            folderLayout.error = null
            newLayout.error = null
            dialog.dismiss()
            onSave(label, folderId)
        }
        dialog.setOnShowListener {
            bindDialogIme(dialog, view)
            polishBookmarkDialogWindow(dialog)
        }
        dialog.setOnDismissListener {
            if (bookmarkLabelDialog === dialog) bookmarkLabelDialog = null
        }
        bindDialogIme(dialog, view)
        dialog.show()
        polishBookmarkDialogWindow(dialog)
        input.requestFocus()
    }

internal fun WebViewActivity.promptFolderName(
        title: String,
        initial: String,
        confirmLabel: String,
        onSave: (String) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_bookmark_label, null)
        val layout = view.findViewById<TextInputLayout>(R.id.bookmarkLabelLayout)
        val input = view.findViewById<TextInputEditText>(R.id.bookmarkLabelInput)
        val confirm = view.findViewById<MaterialButton>(R.id.bookmarkDialogConfirm)
        view.findViewById<View>(R.id.bookmarkFolderLayout).visibility = View.GONE
        view.findViewById<View>(R.id.bookmarkFolderNewLayout).visibility = View.GONE
        view.findViewById<TextView>(R.id.bookmarkDialogTitle).text = title
        layout.hint = "Název skupiny"
        confirm.text = confirmLabel
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        val dialog = MaterialAlertDialogBuilder(this, R.style.RoundedDialog)
            .setView(view)
            .create()
        bookmarkLabelDialog?.dismiss()
        bookmarkLabelDialog = dialog
        view.findViewById<View>(R.id.bookmarkDialogCancel).setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            val name = input.text?.toString().orEmpty().trim()
            if (name.isEmpty()) {
                layout.error = "Zadejte název skupiny"
                return@setOnClickListener
            }
            layout.error = null
            dialog.dismiss()
            onSave(name)
        }
        dialog.setOnShowListener {
            bindDialogIme(dialog, view)
            polishBookmarkDialogWindow(dialog)
        }
        dialog.setOnDismissListener {
            if (bookmarkLabelDialog === dialog) bookmarkLabelDialog = null
        }
        bindDialogIme(dialog, view)
        dialog.show()
        polishBookmarkDialogWindow(dialog)
        input.requestFocus()
    }

internal fun WebViewActivity.polishBookmarkDialogWindow(dialog: AlertDialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val density = resources.displayMetrics.density
        val width = minOf(
            (400 * density).toInt(),
            resources.displayMetrics.widthPixels - (48 * density).toInt()
        )
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

internal fun WebViewActivity.bindDialogIme(dialog: AlertDialog, scroll: View) {
        val window = dialog.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        fun capScroll() {
            val ime = ViewCompat.getRootWindowInsets(window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val density = resources.displayMetrics.density
            val chrome = (200 * density).toInt()
            val cap = (resources.displayMetrics.heightPixels - ime - chrome)
                .coerceAtLeast((120 * density).toInt())
            val childH = (scroll as? ViewGroup)?.getChildAt(0)?.measuredHeight
                ?: scroll.measuredHeight
            val lp = scroll.layoutParams ?: return
            val next = if (ime > 0 && childH > cap) cap else ViewGroup.LayoutParams.WRAP_CONTENT
            if (lp.height != next) {
                lp.height = next
                scroll.layoutParams = lp
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            scroll.post { capScroll() }
            insets
        }
        scroll.post { capScroll() }
    }

internal fun WebViewActivity.dismissBookmarkPopup() {
        try {
            bookmarkPopup?.dismiss()
        } catch (_: Exception) {
        }
        bookmarkPopup = null
    }

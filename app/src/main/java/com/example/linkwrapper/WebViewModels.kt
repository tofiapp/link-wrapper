package com.example.linkwrapper

import android.webkit.WebView

internal class BrowserTab(
    val id: Long,
    var webView: WebView?,
    var title: String,
    var url: String,
    var isHome: Boolean = false,
    var pinned: Boolean = false
)

internal sealed class TabStripEntry {
    data class Single(val tab: BrowserTab) : TabStripEntry()
}

internal enum class Gate { BROWSER, HOME, LOGIN }

internal data class ActionRow(
    val title: String,
    val icon: Int,
    val run: () -> Unit
)

internal object WebViewActivityConstants {
    const val MAX_TABS = 8
    const val MAX_AUTH_ROUNDS = 16
    const val LOGIN_TIMEOUT_MS = 15_000L
    const val CHART_FIT_DELAY_MS = 300L
    const val MAX_PREFETCH = 8
    const val FOLDER_NONE = "Bez skupiny"
    const val FOLDER_NEW = "Nová skupina…"
}

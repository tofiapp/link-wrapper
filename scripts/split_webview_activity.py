#!/usr/bin/env python3
"""Split WebViewActivity.kt into logical extension files."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/example/linkwrapper"
MAIN = SRC / "WebViewActivity.kt"

# Functions kept in WebViewActivity.kt (lifecycle + menu + back)
KEEP_IN_MAIN = {
    "onCreate",
    "onStart",
    "onResume",
    "onConfigurationChanged",
    "onPause",
    "onStop",
    "onNewIntent",
    "onDestroy",
    "dispatchTouchEvent",
    "onCreateOptionsMenu",
    "onPrepareOptionsMenu",
    "onOptionsItemSelected",
    "onBackPressed",
}

# target file -> function names (order preserved within each file)
GROUPS: dict[str, list[str]] = {
    "WebViewActivityGate.kt": [
        "openUrlFromExternal",
        "samePage",
        "refreshGate",
        "needsAppLogin",
        "presentLogin",
        "presentHome",
        "enterBrowser",
        "presentBrowser",
    ],
    "WebViewActivityTabs.kt": [
        "restoreSavedTabsOrStart",
        "openHomeWindow",
        "convertActiveTabToHome",
        "openNewHomeTab",
        "openInNewTab",
        "addTabForUrl",
        "selectTab",
        "parkBackgroundWebView",
        "reviveBrowserWebView",
        "closeTab",
        "destroyWebView",
        "destroyTab",
        "destroyAllTabs",
        "takePrefetch",
        "destroyPrefetchViews",
        "destroyOrphanWebView",
        "startBookmarkPrefetch",
        "queueBookmarkPrefetch",
        "warmPinnedFolderTabs",
        "warmDetachedTab",
        "cancelPrefetchForUrl",
        "pumpBookmarkPrefetch",
        "onPrefetchFinished",
        "bumpAuthCount",
        "resetAuthCount",
        "tabFolderId",
        "buildTabStripEntries",
        "expandFolderGroup",
        "collapseFolderGroup",
        "showFolderTabActions",
        "bindTabStripItem",
        "bindTabStripCollapse",
        "refreshTabStrip",
        "tabLabel",
        "applySavedTitleToTabs",
        "updateTabMeta",
    ],
    "WebViewActivityWebView.kt": ["createWebView"],
    "WebViewActivityUrl.kt": [
        "explicitUrlFromIntent",
        "normalizeUrl",
        "loadInActiveTab",
        "showOpenUrlDialog",
        "handleWebViewLongClick",
        "showLinkOrTabActions",
        "showTabActions",
        "pageActionRows",
        "showActionSheet",
        "dismissActionSheet",
    ],
    "WebViewActivityPins.kt": [
        "setTabPinned",
        "syncOpenTabWebViews",
        "persistOpenTabsFromTabs",
        "restorePinnedTabs",
        "loadUnloadedTabsAfterLogin",
        "tabNeedsReconnectReload",
        "ensureTabLoaded",
        "markInterruptedTabLoads",
        "reloadTabsAfterReconnect",
        "isLoadedPinnedView",
        "shouldPromptLoginForTab",
        "maybePromptLoginForActiveUnpinnedTab",
        "openOnNewTab",
        "togglePinForUrl",
        "pinUrl",
        "loadUrlIntoTab",
    ],
    "WebViewActivityGeo.kt": [
        "handleGeolocationPrompt",
        "hasLocationPermission",
        "finishGeolocationRequest",
        "onGeoWatchDelta",
        "syncGeoUpdates",
        "startGeoUpdates",
        "stopGeoUpdates",
        "pushGeoToTabs",
    ],
    "WebViewActivityNetwork.kt": [
        "isAllowedWebUri",
        "setSensitiveScreen",
        "isVpnActive",
        "isConnectionOk",
        "refreshConnectionBanner",
        "applyOfflineChrome",
        "applyChromeMenu",
        "showConnectionBanner",
        "hideConnectionBanner",
        "raiseConnectionBanner",
        "registerVpnMonitor",
        "unregisterVpnMonitor",
        "scheduleVpnCheck",
    ],
    "WebViewActivityCharts.kt": [
        "shouldDeferChartFit",
        "installChartPerfBootstrap",
        "injectChartFit",
        "injectChartFitIntoAllTabs",
        "injectPsstDataLayout",
        "injectPsstDataLayoutIntoAllTabs",
        "scheduleChartFit",
        "injectChartPerfFallback",
        "pageZoomPercentFor",
        "pageZoomJs",
        "applyPageZoomToAllTabs",
    ],
    "WebViewActivityLogin.kt": [
        "bindLoginUi",
        "updateLoginButton",
        "hideLoginOverlay",
        "submitLogin",
        "succeedLogin",
        "failLogin",
        "setCertBannerVisible",
        "refreshCertBanner",
    ],
    "WebViewActivityHome.kt": [
        "bindHomeUi",
        "populateHomeApps",
        "populateHomeBookmarks",
        "addHomeBookmarkRows",
        "hideHomeOverlay",
        "openDestination",
    ],
    "WebViewActivityIme.kt": [
        "bindImeInsets",
        "attachImeLayoutListener",
        "applyVisibleFrameImePadding",
        "setImePadding",
        "hideKeyboard",
        "isImeVisible",
        "isTouchOnEditText",
    ],
    "WebViewActivitySession.kt": [
        "showCertWarning",
        "showNetworkWarning",
        "shouldSuppressPageErrorDialogs",
        "dismissWarningDialog",
        "confirmLogout",
        "cancelPendingAuth",
        "clearStoredData",
        "openLinkSettings",
        "consumeTrialIdleTimeout",
        "endTrialSessionKeepTabs",
        "currentSaveableUrl",
    ],
    "WebViewActivityBookmarks.kt": [
        "openSavedUrl",
        "showTrialBookmarkMenu",
        "bindTrialBookmarkMenu",
        "renderBookmarkGroups",
        "folderItems",
        "bindFolderHeaderActions",
        "updateFolderPinGlyph",
        "updateFolderStackGlyph",
        "toggleFolderTabGrouped",
        "openFolderInTabs",
        "pinFolderInTabs",
        "unpinFolderInTabs",
        "showFolderActions",
        "showHomeBookmarkMenu",
        "bindBookmarkActions",
        "bindBookmarkPin",
        "updatePinGlyph",
        "isUrlPinned",
        "savePageThenRename",
        "renameSavedPage",
        "removeSavedPage",
        "forgetSavedTitleOnTabs",
        "promptBookmarkLabel",
        "promptFolderName",
        "polishBookmarkDialogWindow",
        "bindDialogIme",
        "dismissBookmarkPopup",
    ],
}

MODELS_HEADER = """package com.example.linkwrapper

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
    data class FolderGroup(
        val folderId: String,
        val title: String,
        val tabs: List<BrowserTab>
    ) : TabStripEntry()
    data class FolderCollapse(val folderId: String) : TabStripEntry()
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
"""

GEO_BRIDGE = """
internal class GeoBridge(private val activity: WebViewActivity) {
    @JavascriptInterface
    fun watchStart() {
        activity.mainHandler.post { activity.onGeoWatchDelta(1) }
    }

    @JavascriptInterface
    fun watchStop() {
        activity.mainHandler.post { activity.onGeoWatchDelta(-1) }
    }
}
"""


def function_extent(class_body: str, start: int, header_end: int) -> tuple[str, int] | None:
    """Return ('block'|'expr', end_pos) for a function starting at start."""
    paren = 0
    saw_paren = False
    body_brace = -1
    expr_eq = -1
    k = header_end
    while k < min(len(class_body), header_end + 4000):
        ch = class_body[k]
        if ch == "(":
            paren += 1
            saw_paren = True
        elif ch == ")":
            paren -= 1
            if saw_paren and paren == 0:
                j = k + 1
                while j < len(class_body) and class_body[j] in " \t\n\r":
                    j += 1
                if j < len(class_body) and class_body[j] == ":":
                    j += 1
                    while j < len(class_body) and class_body[j] not in "{=":
                        j += 1
                while j < len(class_body) and class_body[j] in " \t\n\r":
                    j += 1
                if j < len(class_body) and class_body[j] == "=":
                    expr_eq = j
                elif j < len(class_body) and class_body[j] == "{":
                    body_brace = j
                break
        k += 1
    if expr_eq != -1:
        end = class_body.find("\n", expr_eq)
        if end == -1:
            return ("expr", len(class_body))
        end += 1
        while end < len(class_body):
            line_end = class_body.find("\n", end)
            if line_end == -1:
                line_end = len(class_body)
            line = class_body[end:line_end]
            if line.strip() == "":
                end = line_end + 1 if line_end < len(class_body) else line_end
                break
            if re.match(r"^    \S", line) and not line.startswith("        "):
                break
            end = line_end + 1
        return ("expr", end)
    if body_brace == -1:
        return None
    depth = 0
    j = body_brace
    while j < len(class_body):
        if class_body[j] == "{":
            depth += 1
        elif class_body[j] == "}":
            depth -= 1
            if depth == 0:
                return ("block", j + 1)
        j += 1
    return None


def extract_functions(text: str) -> dict[str, str]:
    """Extract top-level class member functions (not nested in other blocks at class level)."""
    lines = text.splitlines(keepends=True)
    class_start = text.index("class WebViewActivity")
    body = text[class_start:]
    # find opening brace of class
    idx = body.index("{")
    depth = 0
    i = idx
    while i < len(body):
        c = body[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                class_body = body[idx + 1 : i]
                break
        i += 1
    else:
        raise RuntimeError("Could not find class end")

    functions: dict[str, str] = {}
    pos = 0
    member_re = re.compile(
        r"^    ((?:@\w+(?:\([^)]*\))?\s*)*)"
        r"(override |private |internal |public )?"
        r"(?:inner )?"
        r"(?:data )?"
        r"(class|fun|enum class) ",
        re.MULTILINE,
    )

    while pos < len(class_body):
        m = member_re.search(class_body, pos)
        if not m:
            break
        start = m.start()
        kind_match = re.search(
            r"(?:override |private |internal |public )?"
            r"(?:inner )?"
            r"(?:data )?"
            r"(class|fun|enum class)\s+(\w+)",
            class_body[start : start + 400],
        )
        if not kind_match:
            pos = m.end()
            continue
        kind = kind_match.group(1)
        name = kind_match.group(2)
        if kind != "fun":
            header_end = kind_match.end() + start
            paren = 0
            decl_end = header_end
            saw_paren = False
            for k in range(header_end, min(len(class_body), header_end + 2000)):
                ch = class_body[k]
                if ch == "(":
                    paren += 1
                    saw_paren = True
                elif ch == ")":
                    paren -= 1
                    if saw_paren and paren == 0:
                        decl_end = k + 1
                        break
                elif ch == "{" and paren == 0:
                    decl_end = k
                    break
            if decl_end == header_end:
                line_end = class_body.find("\n", start)
                decl_end = line_end + 1 if line_end != -1 else len(class_body)
            elif class_body[decl_end - 1] != "{":
                line_end = class_body.find("\n", decl_end)
                decl_end = line_end + 1 if line_end != -1 else len(class_body)
            if class_body[decl_end - 1 : decl_end] == "{":
                depth = 0
                j = decl_end - 1
                while j < len(class_body):
                    if class_body[j] == "{":
                        depth += 1
                    elif class_body[j] == "}":
                        depth -= 1
                        if depth == 0:
                            pos = j + 1
                            break
                    j += 1
                else:
                    pos = decl_end
            else:
                pos = decl_end
            continue

        header_end = kind_match.end() + start
        extent = function_extent(class_body, start, header_end)
        if extent is None:
            pos = m.end()
            continue
        _, end = extent
        functions[name] = class_body[start:end]
        pos = end

    return functions


def to_extension(func_src: str, name: str) -> str:
    lines = func_src.splitlines()
    out = []
    converted = False
    for line in lines:
        if line.strip().startswith("@") and "fun " not in line:
            out.append(line)
            continue
        if not converted and re.match(r"^    (override )?private fun ", line):
            line = re.sub(r"^    private fun ", "internal fun ", line)
            line = re.sub(r"^    override fun ", "override fun ", line)
            if line.startswith("override fun "):
                line = line.replace("override fun ", "override fun WebViewActivity.", 1)
            else:
                line = line.replace("internal fun ", "internal fun WebViewActivity.", 1)
            converted = True
            out.append(line)
        else:
            out.append(line)
    return "\n".join(out)


def strip_extracted_functions(class_body: str, names: set[str]) -> str:
    pos = 0
    result = []
    member_re = re.compile(
        r"^    ((?:@\w+(?:\([^)]*\))?\s*)*)"
        r"(override |private |internal |public )?"
        r"(?:inner )?"
        r"(?:data )?"
        r"(class|fun|enum class) ",
        re.MULTILINE,
    )
    while pos < len(class_body):
        m = member_re.search(class_body, pos)
        if not m:
            result.append(class_body[pos:])
            break
        start = m.start()
        result.append(class_body[pos:start])
        kind_match = re.search(
            r"(?:override |private |internal |public )?"
            r"(?:inner )?"
            r"(?:data )?"
            r"(class|fun|enum class)\s+(\w+)",
            class_body[start : start + 200],
        )
        if not kind_match:
            pos = m.end()
            continue
        kind = kind_match.group(1)
        name = kind_match.group(2)
        if kind != "fun":
            header_end = kind_match.end() + start
            paren = 0
            decl_end = header_end
            saw_paren = False
            for k in range(header_end, min(len(class_body), header_end + 2000)):
                ch = class_body[k]
                if ch == "(":
                    paren += 1
                    saw_paren = True
                elif ch == ")":
                    paren -= 1
                    if saw_paren and paren == 0:
                        decl_end = k + 1
                        break
                elif ch == "{" and paren == 0:
                    decl_end = k
                    break
            if decl_end == header_end:
                line_end = class_body.find("\n", start)
                decl_end = line_end + 1 if line_end != -1 else len(class_body)
            elif class_body[decl_end - 1] != "{":
                line_end = class_body.find("\n", decl_end)
                decl_end = line_end + 1 if line_end != -1 else len(class_body)
            if name not in ("GeoBridge", "ActionRow", "Gate"):
                if class_body[decl_end - 1] == "{":
                    depth = 0
                    j = decl_end - 1
                    while j < len(class_body):
                        if class_body[j] == "{":
                            depth += 1
                        elif class_body[j] == "}":
                            depth -= 1
                            if depth == 0:
                                result.append(class_body[start : j + 1])
                                pos = j + 1
                                break
                        j += 1
                    else:
                        pos = decl_end
                else:
                    result.append(class_body[start:decl_end])
                    pos = decl_end
            else:
                if class_body[decl_end - 1] == "{":
                    depth = 0
                    j = decl_end - 1
                    while j < len(class_body):
                        if class_body[j] == "{":
                            depth += 1
                        elif class_body[j] == "}":
                            depth -= 1
                            if depth == 0:
                                pos = j + 1
                                break
                        j += 1
                    else:
                        pos = decl_end
                else:
                    pos = decl_end
            continue
        header_end = kind_match.end() + start
        extent = function_extent(class_body, start, header_end)
        if extent is None:
            pos = m.end()
            continue
        _, end = extent
        if name not in names:
            result.append(class_body[start:end])
        pos = end

    return re.sub(r"\n{3,}", "\n\n", "".join(result))


def main() -> None:
    text = MAIN.read_text(encoding="utf-8")
    functions = extract_functions(text)

    all_extract = set()
    for names in GROUPS.values():
        all_extract.update(names)

    missing = all_extract - set(functions.keys())
    extra = set(functions.keys()) - all_extract - KEEP_IN_MAIN
    if missing:
        raise SystemExit(f"Missing functions in mapping: {sorted(missing)}")
    extra = {name for name in extra if name != "onLocationCh"}
    if extra:
        raise SystemExit(f"Unmapped functions: {sorted(extra)}")

    # package + imports for extensions (broad import set from original)
    import_block = []
    for line in text.splitlines():
        if line.startswith("import "):
            import_block.append(line)
    imports = "\n".join(import_block)

    for filename, names in GROUPS.items():
        parts = []
        for n in names:
            if n not in functions:
                raise SystemExit(f"{n} not found for {filename}")
            parts.append(to_extension(functions[n], n))
        body = "\n\n".join(parts)
        extra_imports = ""
        if filename == "WebViewActivityGeo.kt":
            body += GEO_BRIDGE
            extra_imports = ""
        content = f"package com.example.linkwrapper\n\n{imports}\n{extra_imports}\n{body}\n"
        (SRC / filename).write_text(content, encoding="utf-8")

    (SRC / "WebViewModels.kt").write_text(MODELS_HEADER, encoding="utf-8")

    # Rewrite main file
    import_block = [line for line in text.splitlines() if line.startswith("import ")]
    header = "package com.example.linkwrapper\n\n" + "\n".join(import_block) + "\n\n"

    class_start = text.index("class WebViewActivity")
    body_start = text.index("{", class_start) + 1
    body_end = text.rindex("}")
    class_body = text[body_start:body_end]

    new_body = strip_extracted_functions(class_body, all_extract)
    # Remove inner GeoBridge class manually
    new_body = re.sub(
        r"\n    private inner class GeoBridge \{.*?\n    \}\n",
        "\n",
        new_body,
        flags=re.DOTALL,
    )
    new_body = re.sub(
        r"\n        @JavascriptInterface\n        fun watchStart\(\) \{.*?\n    \}\n",
        "\n",
        new_body,
        flags=re.DOTALL,
    )
    # Remove ActionRow data class
    new_body = re.sub(
        r"\n    private data class ActionRow\(\n.*?\n    \)\n",
        "\n",
        new_body,
        flags=re.DOTALL,
    )
    # Remove Gate enum
    new_body = re.sub(r"\n    private enum class Gate \{[^}]+\}\n", "\n", new_body)
    # Remove companion private constants (moved to WebViewActivityConstants)
    new_body = re.sub(
        r"\n        private const val MAX_TABS = 8\n"
        r"        private const val MAX_AUTH_ROUNDS = 16\n"
        r"        private const val LOGIN_TIMEOUT_MS = 15_000L\n"
        r"        private const val CHART_FIT_DELAY_MS = 300L\n"
        r"        private const val MAX_PREFETCH = 8\n"
        r"        private const val FOLDER_NONE = \"Bez skupiny\"\n"
        r"        private const val FOLDER_NEW = \"Nová skupina…\"\n",
        "\n",
        new_body,
    )

    # private -> internal for fields and remaining members
    new_body = re.sub(r"\n    private (lateinit |val |var )", r"\n    internal \1", new_body)
    new_body = re.sub(r"\n    private val ", r"\n    internal val ", new_body)
    new_body = re.sub(r"\n    private var ", r"\n    internal var ", new_body)
    new_body = re.sub(r"\n    private fun ", r"\n    internal fun ", new_body)

    # geoBridge init
    new_body = new_body.replace(
        "private val geoBridge = GeoBridge()",
        "internal val geoBridge = GeoBridge(this)",
    )
    new_body = new_body.replace(
        "internal val geoBridge = GeoBridge()",
        "internal val geoBridge = GeoBridge(this)",
    )

    # mainHandler must be internal for GeoBridge
    # Replace constant references
    for const in [
        "MAX_TABS",
        "MAX_AUTH_ROUNDS",
        "LOGIN_TIMEOUT_MS",
        "CHART_FIT_DELAY_MS",
        "MAX_PREFETCH",
        "FOLDER_NONE",
        "FOLDER_NEW",
    ]:
        new_body = re.sub(
            rf"(?<![.\w]){const}(?!\s*=)",
            f"WebViewActivityConstants.{const}",
            new_body,
        )

    for filename in GROUPS:
        content = (SRC / filename).read_text(encoding="utf-8")
        for const in [
            "MAX_TABS",
            "MAX_AUTH_ROUNDS",
            "LOGIN_TIMEOUT_MS",
            "CHART_FIT_DELAY_MS",
            "MAX_PREFETCH",
            "FOLDER_NONE",
            "FOLDER_NEW",
        ]:
            content = re.sub(
                rf"(?<![.\w]){const}(?!\s*=)",
                f"WebViewActivityConstants.{const}",
                content,
            )
        (SRC / filename).write_text(content, encoding="utf-8")

    ui_ready_props = """
    internal val loginButtonReady get() = ::loginButton.isInitialized
    internal val certBannerReady get() = ::certBanner.isInitialized
    internal val homeBookmarkListReady get() = ::homeBookmarkList.isInitialized
    internal val homeOverlayReady get() = ::homeOverlay.isInitialized
    internal val loginOverlayReady get() = ::loginOverlay.isInitialized
    internal val usernameInputReady get() = ::usernameInput.isInitialized
    internal val passwordInputReady get() = ::passwordInput.isInitialized
    internal val webContainerReady get() = ::webContainer.isInitialized
    internal val toolbarReady get() = ::toolbar.isInitialized
    internal val connectionBannerReady get() = ::connectionBanner.isInitialized
    internal val tabScrollReady get() = ::tabScroll.isInitialized

"""
    new_body = new_body.replace(
        "    internal val geoBridge = GeoBridge(this)",
        ui_ready_props + "    internal val geoBridge = GeoBridge(this)",
        1,
    )

    main_content = header + "class WebViewActivity : AppCompatActivity() {\n\n" + new_body + "\n}\n"
    MAIN.write_text(main_content, encoding="utf-8")

    lateinit_checks = {
        "::loginButton.isInitialized": "loginButtonReady",
        "::certBanner.isInitialized": "certBannerReady",
        "::homeBookmarkList.isInitialized": "homeBookmarkListReady",
        "::homeOverlay.isInitialized": "homeOverlayReady",
        "::loginOverlay.isInitialized": "loginOverlayReady",
        "::usernameInput.isInitialized": "usernameInputReady",
        "::passwordInput.isInitialized": "passwordInputReady",
        "::webContainer.isInitialized": "webContainerReady",
        "::toolbar.isInitialized": "toolbarReady",
        "::connectionBanner.isInitialized": "connectionBannerReady",
        "::tabScroll.isInitialized": "tabScrollReady",
    }
    for filename in GROUPS:
        path = SRC / filename
        content = path.read_text(encoding="utf-8")
        content = re.sub(r"(?<![.\w])EXTRA_URL\b", "WebViewActivity.EXTRA_URL", content)
        content = re.sub(r"(?<![.\w])DEFAULT_URL\b", "WebViewActivity.DEFAULT_URL", content)
        for old, new in lateinit_checks.items():
            content = content.replace(old, new)
        if filename == "WebViewActivityWebView.kt":
            content = content.replace(
                "internal fun WebViewActivity.createWebView(): WebView {\n",
                "internal fun WebViewActivity.createWebView(): WebView {\n"
                "        val self = this\n",
                1,
            )
            content = content.replace("this@WebViewActivity", "self")
        path.write_text(content, encoding="utf-8")
    print("Split complete.")
    print(f"Main file lines: {len(main_content.splitlines())}")
    for f in sorted(GROUPS):
        print(f"  {f}: {len((SRC/f).read_text().splitlines())} lines")


if __name__ == "__main__":
    main()

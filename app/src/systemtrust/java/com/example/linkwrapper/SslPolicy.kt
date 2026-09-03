package com.example.linkwrapper

import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity

/**
 * Žádné vlastní SSL. Platí jen CA, kterým důvěřuje tablet.
 * Chybu spojení appka nepřekračuje a nic o ní uživateli nevysvětluje.
 */
internal object SslPolicy {

    const val SHOW_CERT_MENU = false

    const val PROBE_SSL_FAILURE =
        "Stránku se nepodařilo načíst. Zkontrolujte VPN a zkuste to znovu."

    @Suppress("UNUSED_PARAMETER")
    fun handleSslError(
        context: Context,
        handler: SslErrorHandler?,
        error: SslError?,
        onRejected: () -> Unit
    ) {
        handler?.cancel()
        onRejected()
    }

    @Suppress("UNUSED_PARAMETER")
    fun describeChain(context: Context): String = ""

    fun loadError(): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun showSslRejected(
        activity: AppCompatActivity,
        error: SslError?,
        onDismiss: () -> Unit
    ) {
        onDismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun showInfo(activity: AppCompatActivity) {
    }
}

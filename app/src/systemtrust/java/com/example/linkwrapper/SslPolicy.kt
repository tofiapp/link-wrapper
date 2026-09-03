package com.example.linkwrapper

import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Žádné vlastní SSL. Platí jen CA, kterým důvěřuje tablet.
 */
internal object SslPolicy {

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

    fun probeFailure(error: SslError?): String = SslMessages.probeFailure(error)

    fun showSslRejected(
        activity: AppCompatActivity,
        error: SslError?,
        onDismiss: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Chybí certifikáty")
            .setMessage(SslMessages.probeFailure(error))
            .setPositiveButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { onDismiss() }
            .show()
    }

    @Suppress("UNUSED_PARAMETER")
    fun showInfo(activity: AppCompatActivity) {
    }
}

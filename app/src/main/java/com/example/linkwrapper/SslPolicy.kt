package com.example.linkwrapper

import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * HTTPS podle CA na tabletu (systém + uživatel / MDM).
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
}

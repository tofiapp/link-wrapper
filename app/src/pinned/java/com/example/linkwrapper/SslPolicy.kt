package com.example.linkwrapper

import android.content.Context
import android.net.http.SslError
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Firemní CA v APK. Tablety bez systémové CA by se jinak k webu nedostaly.
 */
internal object SslPolicy {

    fun handleSslError(
        context: Context,
        handler: SslErrorHandler?,
        error: SslError?,
        onRejected: () -> Unit
    ) {
        if (CertPinning.shouldProceed(context, error)) {
            handler?.proceed()
        } else {
            handler?.cancel()
            onRejected()
        }
    }

    fun probeFailure(error: SslError?): String = SslMessages.probeFailure(error)

    fun showSslRejected(activity: AppCompatActivity, error: SslError?, onDismiss: () -> Unit) {
        val missing = error == null || error.hasError(SslError.SSL_UNTRUSTED)
        val title = if (missing) "Chybí certifikáty" else "Spojení nebylo ověřeno"
        val message = if (missing) {
            SslMessages.MISSING_DEVICE_CERTS
        } else {
            when (error?.primaryError) {
                SslError.SSL_EXPIRED -> "Certifikát stránky vypršel."
                SslError.SSL_IDMISMATCH ->
                    "Certifikát patří jiné adrese, než na kterou se připojujete."
                SslError.SSL_NOTYETVALID -> "Certifikát zatím není platný."
                SslError.SSL_DATE_INVALID -> "Certifikát má neplatné datum."
                else -> SslMessages.MISSING_DEVICE_CERTS
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Zavřít", null)
            .setCancelable(true)
            .setOnDismissListener { onDismiss() }
            .show()
    }

    fun showInfo(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Ověřování certifikátů")
            .setMessage(CertPinning.describeChain(activity))
            .setPositiveButton("Zavřít", null)
            .show()
    }
}

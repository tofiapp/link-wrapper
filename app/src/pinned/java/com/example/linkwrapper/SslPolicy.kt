package com.example.linkwrapper

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Firemní CA v APK. Tablety bez systémové CA by se jinak k webu nedostaly.
 */
internal object SslPolicy {

    const val SHOW_CERT_MENU = true

    const val PROBE_SSL_FAILURE =
        "Spojení nebylo ověřeno. Zkontrolujte VPN a certifikát."

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

    fun describeChain(context: Context): String = CertPinning.describeChain(context)

    fun loadError(): String? = CertPinning.loadError()

    fun showSslRejected(activity: AppCompatActivity, error: SslError?, onDismiss: () -> Unit) {
        val detail = when (error?.primaryError) {
            SslError.SSL_UNTRUSTED ->
                "Certifikát stránky nevydala firemní certifikační autorita " +
                    "(SZT Root BAU ECC CA) ani jiná autorita, které zařízení důvěřuje."
            SslError.SSL_EXPIRED -> "Certifikát stránky vypršel."
            SslError.SSL_IDMISMATCH ->
                "Certifikát patří jiné adrese, než na kterou se připojujete."
            SslError.SSL_NOTYETVALID -> "Certifikát zatím není platný."
            SslError.SSL_DATE_INVALID -> "Certifikát má neplatné datum."
            else -> "Certifikát stránky se nepodařilo ověřit."
        }
        val loadIssue = CertPinning.loadError()
        val diag = buildString {
            append("\n\nDetail: kód ")
            append(error?.primaryError ?: -1)
            error?.url?.let { append(", ").append(Uri.parse(it).host ?: it) }
            if (loadIssue != null) append("\n").append(loadIssue)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Spojení nebylo ověřeno")
            .setMessage(
                detail +
                    "\n\nStránka nebyla načtena. Pokud je to očekávané (např. byla " +
                    "vyměněna firemní CA), obraťte se na IT — do aplikace je potřeba " +
                    "doplnit nový certifikát." +
                    "\n\nPokud jste tuto hlášku nečekali, nepokračujte a nezadávejte " +
                    "na této stránce žádné přihlašovací údaje." +
                    diag
            )
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

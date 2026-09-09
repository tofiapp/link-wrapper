package com.example.linkwrapper

import android.net.http.SslError

/** Texty k SSL. */
internal object SslMessages {

    const val MISSING_DEVICE_CERTS =
        "Váš tablet nemá nainstalované potřebné certifikáty. " +
            "Nainstalujte je a zkuste přihlášení znovu."

    fun probeFailure(error: SslError?): String {
        if (error == null || error.hasError(SslError.SSL_UNTRUSTED)) {
            return MISSING_DEVICE_CERTS
        }
        return "Stránku se nepodařilo načíst. Zkontrolujte VPN a zkuste to znovu."
    }

    fun isMissingDeviceCerts(message: String?): Boolean =
        message == MISSING_DEVICE_CERTS
}

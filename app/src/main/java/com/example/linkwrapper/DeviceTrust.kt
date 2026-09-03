package com.example.linkwrapper

import android.net.Uri
import java.net.URL
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Důvěra tabletu k HTTPS — bez firemní CA v APK.
 *
 * [HttpsURLConnection] používá systémové (a uživatelské) CA. Varianta
 * s přibaleným certifikátem se tak může přihlásit i když banner svítí:
 * WebView si řetěz doplní z APK, tablet ale pořád CA nemá.
 */
internal object DeviceTrust {

    enum class Result { Trusted, Untrusted, Unknown }

    fun probe(url: String): Result {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return Result.Unknown
        if (!"https".equals(parsed.scheme, ignoreCase = true)) return Result.Unknown
        if (!AuthHosts.allows(parsed.host)) return Result.Unknown

        var conn: HttpsURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            conn.connect()
            // 401 je v pořádku: TLS už prošlo, chybí jen jméno a heslo.
            conn.responseCode
            Result.Trusted
        } catch (error: Throwable) {
            if (isUntrustedSsl(error)) Result.Untrusted else Result.Unknown
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun isUntrustedSsl(error: Throwable): Boolean {
        var current: Throwable? = error
        val seen = HashSet<Throwable>()
        while (current != null && seen.add(current)) {
            when (current) {
                is SSLHandshakeException,
                is SSLPeerUnverifiedException,
                is CertPathValidatorException,
                is CertificateException -> return true
            }
            current = current.cause
        }
        return false
    }
}

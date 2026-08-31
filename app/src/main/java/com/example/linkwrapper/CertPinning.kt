package com.example.linkwrapper

import android.content.Context
import android.net.http.SslCertificate
import android.os.Build
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Ověřování firemního certifikátu, kterému zařízení systémově nedůvěřuje,
 * protože nemá nainstalovanou firemní kořenovou CA (SZT Root BAU ECC CA).
 *
 * PRINCIP:
 * Aplikace NEIGNORUJE certifikátové chyby plošně a neváže se na jeden
 * konkrétní certifikát stránky. Místo toho ověřuje, že certifikát serveru
 * byl vydán (podepsán) firemní kořenovou CA, jejíž certifikát je uložený
 * v res/raw/corporate_ca.pem.
 *
 * Díky tomu:
 *  - appka funguje pro všechny interní stránky za firemní SSL inspekcí,
 *  - přežije pravidelnou výměnu certifikátů jednotlivých stránek (leaf),
 *  - odmítne jakýkoliv certifikát, který tato CA nevydala (ochrana proti
 *    podvrženému spojení zůstává funkční).
 *
 * Kořenová CA je platná do 4. dubna 2039. Do té doby není potřeba nic měnit.
 * Až vyprší, stačí nahradit soubor res/raw/corporate_ca.pem novým a znovu
 * sestavit APK.
 */
object CertPinning {

    // Cache načtené CA, ať se nečte z disku při každém požadavku.
    @Volatile
    private var cachedCa: X509Certificate? = null

    // Kontrolní otisk CA (SHA-256). Pojistka: pokud by někdo soubor v projektu
    // vyměnil za jiný, načtení se odmítne. Bez mezer, malými písmeny.
    private const val EXPECTED_CA_SHA256 =
        "47c337130d4b15f9b9fbd2dc5afdc0229ec356c5d38a71b2b584ad485f3172ba"

    private fun loadCa(context: Context): X509Certificate? {
        cachedCa?.let { return it }
        return try {
            val factory = CertificateFactory.getInstance("X.509")
            context.resources.openRawResource(R.raw.corporate_ca).use { input ->
                val ca = factory.generateCertificate(input) as X509Certificate
                // Ověření, že vložená CA je opravdu ta očekávaná.
                val digest = MessageDigest.getInstance("SHA-256")
                val fp = digest.digest(ca.encoded).toHex()
                if (!constantTimeEquals(fp, EXPECTED_CA_SHA256)) {
                    return null
                }
                cachedCa = ca
                ca
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Vrátí true, pokud certifikát serveru byl vydán firemní kořenovou CA
     * a je (časově) platný. Při jakékoliv nejistotě vrací false.
     */
    fun isIssuedByCorporateCa(context: Context, sslCertificate: SslCertificate?): Boolean {
        val serverCert = extractX509(sslCertificate) ?: return false
        val ca = loadCa(context) ?: return false
        return try {
            // Ověří podpis serverového certifikátu veřejným klíčem CA.
            // Pokud certifikát nevydala tato CA, vyhodí výjimku -> false.
            serverCert.verify(ca.publicKey)

            // Ověří i časovou platnost serverového certifikátu.
            serverCert.checkValidity()

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun extractX509(sslCertificate: SslCertificate?): X509Certificate? {
        if (sslCertificate == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sslCertificate.x509Certificate
        } else {
            try {
                val bundle = SslCertificate.saveState(sslCertificate)
                val bytes = bundle.getByteArray("x509-certificate") ?: return null
                val factory = CertificateFactory.getInstance("X.509")
                factory.generateCertificate(bytes.inputStream()) as? X509Certificate
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}

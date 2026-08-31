package com.example.linkwrapper

import android.content.Context
import android.net.http.SslCertificate
import android.os.Build
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Ověřování HTTPS spojení proti firemní certifikační autoritě.
 *
 * Tablety nemají firemní CA v systémovém trust store, proto jim WebView
 * hlásí chybu certifikátu. Aplikace ověření provede sama.
 *
 * Řetězec certifikátů:
 *   SZT Root BAU ECC CA  ->  SZT Sub BAU ECC CA1  ->  psst.tudc.cz
 *
 * Certifikát serveru podepisuje mezilehlá CA, ne kořenová. Ověření proto
 * prochází celý řetězec:
 *   1. kořenová CA je self-signed a odpovídá očekávanému otisku
 *   2. mezilehlá CA je podepsaná kořenovou
 *   3. certifikát serveru je podepsaný mezilehlou
 *   4. všechny tři jsou časově platné a obě CA mají CA:TRUE
 *
 * Aplikace neignoruje chyby certifikátů plošně. Cokoliv, co tímto ověřením
 * neprojde, je odmítnuto.
 */
object CertPinning {

    /**
     * SHA-256 otisk kořenové CA. Pojistka proti záměně souboru v projektu.
     * Bez mezer, malými písmeny.
     */
    private const val EXPECTED_ROOT_SHA256 =
        "47c337130d4b15f9b9fbd2dc5afdc0229ec356c5d38a71b2b584ad485f3172ba"

    private data class Chain(val root: X509Certificate, val sub: X509Certificate)

    @Volatile
    private var cached: Chain? = null

    @Volatile
    private var lastLoadError: String? = null

    /** Popis poslední chyby při načítání CA, pro diagnostiku v dialogu. */
    fun loadError(): String? = lastLoadError

    private fun loadChain(context: Context): Chain? {
        cached?.let { return it }
        return try {
            val factory = CertificateFactory.getInstance("X.509")

            val root = context.resources.openRawResource(R.raw.corporate_ca).use {
                factory.generateCertificate(it) as X509Certificate
            }

            val sub = context.resources.openRawResource(R.raw.corporate_sub_ca).use {
                factory.generateCertificate(it) as X509Certificate
            }

            // Kořenová CA musí odpovídat očekávanému otisku.
            if (!constantTimeEquals(sha256Hex(root.encoded), EXPECTED_ROOT_SHA256)) {
                lastLoadError = "Kořenová CA v aplikaci neodpovídá očekávanému otisku."
                return null
            }

            // Kořenová CA musí být self-signed.
            root.verify(root.publicKey)

            // Mezilehlá CA musí být podepsaná kořenovou.
            // Tím je ověřená i bez vlastního napevno zadaného otisku.
            sub.verify(root.publicKey)

            // Obě musí být skutečné CA.
            if (root.basicConstraints < 0 || sub.basicConstraints < 0) {
                lastLoadError = "Vložený certifikát není certifikační autorita."
                return null
            }

            lastLoadError = null
            Chain(root, sub).also { cached = it }
        } catch (e: Exception) {
            lastLoadError = "CA se nepodařilo načíst: ${e.javaClass.simpleName}"
            null
        }
    }

    /**
     * Vrátí true, pokud certifikát serveru pochází z firemního řetězce
     * a všechny články jsou platné. Při jakékoliv nejistotě vrací false.
     */
    fun isIssuedByCorporateCa(context: Context, sslCertificate: SslCertificate?): Boolean {
        val server = extractX509(sslCertificate) ?: return false
        val chain = loadChain(context) ?: return false
        return try {
            // Certifikát serveru musí být podepsaný mezilehlou CA.
            server.verify(chain.sub.publicKey)

            // Časová platnost celého řetězce.
            server.checkValidity()
            chain.sub.checkValidity()
            chain.root.checkValidity()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Vytáhne X509 certifikát z WebView objektu.
     * Přímý getter je až od API 29, na starších verzích jde přes Bundle.
     */
    private fun extractX509(sslCertificate: SslCertificate?): X509Certificate? {
        if (sslCertificate == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return sslCertificate.x509Certificate
        }
        return try {
            val bytes = SslCertificate.saveState(sslCertificate)
                .getByteArray("x509-certificate") ?: return null
            CertificateFactory.getInstance("X.509")
                .generateCertificate(bytes.inputStream()) as? X509Certificate
        } catch (e: Exception) {
            null
        }
    }

    /** Čitelný přehled stavu certifikátů, pro obrazovku v aplikaci. */
    fun describeChain(context: Context): String {
        val chain = loadChain(context)
            ?: return "Certifikáty se nepodařilo načíst.\n\n" +
                (lastLoadError ?: "Neznámá chyba.")

        val fmt = java.text.SimpleDateFormat("d. M. yyyy", java.util.Locale.getDefault())

        return buildString {
            append("Aplikace ověřuje, že certifikát stránky vydala firemní ")
            append("certifikační autorita. Cokoliv jiného odmítne.\n\n")
            append("Kořenová autorita\n")
            append(cn(chain.root)).append("\nplatí do ")
            append(fmt.format(chain.root.notAfter)).append("\n\n")
            append("Mezilehlá autorita\n")
            append(cn(chain.sub)).append("\nplatí do ")
            append(fmt.format(chain.sub.notAfter)).append("\n\n")
            append("Certifikáty jednotlivých stránek se obnovují průběžně. ")
            append("V aplikaci se kvůli tomu nic měnit nemusí.")
        }
    }

    private fun cn(cert: X509Certificate): String {
        val dn = cert.subjectX500Principal.name
        return Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1) ?: dn
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}

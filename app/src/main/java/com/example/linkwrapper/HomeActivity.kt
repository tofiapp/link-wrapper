package com.example.linkwrapper

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class HomeActivity : AppCompatActivity() {

    private lateinit var urlLayout: TextInputLayout
    private lateinit var urlInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        urlLayout = findViewById(R.id.urlLayout)
        urlInput = findViewById(R.id.urlInput)

        findViewById<MaterialButton>(R.id.openButton).setOnClickListener { openTyped() }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { openTyped(); true } else false
        }

        urlInput.setOnFocusChangeListener { _, _ -> urlLayout.error = null }

        findViewById<MaterialButton>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.certButton).setOnClickListener {
            showCertInfo()
        }

        findViewById<android.view.View>(R.id.statusCard).setOnClickListener {
            openLinkSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    /**
     * Ukáže, jestli je aplikace nastavená jako výchozí pro odkazy.
     *
     * Od Androidu 12 systém nenabízí aplikace s neověřenou doménou
     * automaticky — uživatel je musí povolit v nastavení. Bez toho
     * Outlook odkaz pošle rovnou do prohlížeče.
     */
    private fun refreshStatus() {
        val icon = findViewById<ImageView>(R.id.statusIcon)
        val title = findViewById<TextView>(R.id.statusTitle)
        val body = findViewById<TextView>(R.id.statusBody)

        if (isDefaultHandler()) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.imageTintList = ContextCompat.getColorStateList(this, R.color.ok)
            title.text = "Připraveno"
            body.text = "Odkazy na psst.tudc.cz míří sem"
        } else {
            icon.setImageResource(R.drawable.ic_settings_alert)
            icon.imageTintList = ContextCompat.getColorStateList(this, R.color.warn)
            title.text = "Nenastaveno pro odkazy"
            body.text = "Klepnutím nastavíš otevírání odkazů v aplikaci"
        }
    }

    /** Je aplikace zvolená jako výchozí pro firemní doménu? */
    private fun isDefaultHandler(): Boolean {
        return try {
            val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://psst.tudc.cz/"))
            val resolved = packageManager.resolveActivity(
                probe, PackageManager.MATCH_DEFAULT_ONLY
            )
            resolved?.activityInfo?.packageName == packageName
        } catch (e: Exception) {
            false
        }
    }

    /** Otevře systémové nastavení, kde se aplikace povolí pro odkazy. */
    private fun openLinkSettings() {
        val steps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "1. Klepni na „Otevírání odkazů\"\n" +
            "2. Zapni „Otevírat podporované odkazy\"\n" +
            "3. V „Podporované webové adresy\" zaškrtni psst.tudc.cz"
        } else {
            "1. Klepni na „Otevírat ve výchozím nastavení\"\n" +
            "2. Zvol „Otevírat v této aplikaci\""
        }

        AlertDialog.Builder(this)
            .setTitle("Nastavit otevírání odkazů")
            .setMessage(
                "Android sám nenabídne aplikaci u odkazů, dokud ji nepovolíš " +
                "v nastavení.\n\n$steps\n\n" +
                "Pokud Outlook odkazy i tak otevírá sám, vypni jeho vestavěný " +
                "prohlížeč: Outlook → Nastavení → Obecné → Otevírat odkazy."
            )
            .setPositiveButton("Otevřít nastavení") { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("Zavřít", null)
            .show()
    }

    private fun showCertInfo() {
        val info = CertPinning.describeChain(this)
        AlertDialog.Builder(this)
            .setTitle("Ověřování certifikátů")
            .setMessage(info)
            .setPositiveButton("Zavřít", null)
            .show()
    }

    private fun openTyped() {
        var text = urlInput.text?.toString()?.trim().orEmpty()

        if (text.isEmpty()) {
            urlLayout.error = "Zadej adresu"
            return
        }
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        if (Uri.parse(text).host.isNullOrEmpty()) {
            urlLayout.error = "Tohle nevypadá jako adresa"
            return
        }

        urlLayout.error = null
        startActivity(
            Intent(this, WebViewActivity::class.java)
                .putExtra(WebViewActivity.EXTRA_URL, text)
        )
    }
}

package com.example.linkwrapper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val openButton = findViewById<Button>(R.id.openButton)
        val historyButton = findViewById<Button>(R.id.historyButton)

        openButton.setOnClickListener {
            var text = urlInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Vlož nejdřív nějakou URL adresu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!text.startsWith("http://") && !text.startsWith("https://")) {
                text = "https://$text"
            }
            val intent = Intent(this, WebViewActivity::class.java)
            intent.putExtra(WebViewActivity.EXTRA_URL, text)
            startActivity(intent)
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }
}

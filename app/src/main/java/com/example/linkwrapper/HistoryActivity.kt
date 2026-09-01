package com.example.linkwrapper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.historyList)
        val empty = findViewById<View>(R.id.emptyState)

        val entries = LinkHistory.getLastDay(this)

        if (entries.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        empty.visibility = View.GONE
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = HistoryAdapter(entries) { url ->
            startActivity(
                Intent(this, WebViewActivity::class.java)
                    .putExtra(WebViewActivity.EXTRA_URL, url)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
            )
            finish()
        }
    }
}

class HistoryAdapter(
    private val entries: List<HistoryEntry>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val host: TextView = view.findViewById(R.id.hostText)
        val path: TextView = view.findViewById(R.id.pathText)
        val time: TextView = view.findViewById(R.id.timeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val uri = runCatching { Uri.parse(entry.url) }.getOrNull()

        holder.host.text = uri?.host ?: entry.url

        val rest = buildString {
            uri?.path?.takeIf { it.isNotEmpty() && it != "/" }?.let { append(it) }
            uri?.query?.let { append("?").append(it) }
        }
        holder.path.text = rest.ifEmpty { entry.url }
        holder.path.visibility = if (rest.isEmpty() && uri?.host == null) View.GONE else View.VISIBLE

        holder.time.text = label(entry.timestamp)
        holder.itemView.setOnClickListener { onClick(entry.url) }
    }

    /** Dnešní časy jako "14:22", včerejší jako "včera". */
    private fun label(ts: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = ts }
        val now = Calendar.getInstance()
        val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) clock.format(Date(ts)) else "včera"
    }

    override fun getItemCount() = entries.size
}

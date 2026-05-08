package com.pi.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

class SessionPickerActivity : AppCompatActivity() {

    companion object {
        const val RESULT_SESSION_PATH = "session_path"
        const val RESULT_NEW_SESSION = "new_session"
    }

    private val sessions = mutableListOf<SessionInfo>()
    private lateinit var adapter: SessionAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView
    private var ws: WebSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_picker)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Sessions"
            setDisplayHomeAsUpEnabled(true)
        }

        recyclerView = findViewById(R.id.rvSessions)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = SessionAdapter(sessions) { session ->
            val result = Intent().apply {
                putExtra(RESULT_SESSION_PATH, session.path)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.btnNewSession).setOnClickListener {
            val result = Intent().apply {
                putExtra(RESULT_NEW_SESSION, true)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        loadSessions()
    }

    override fun onDestroy() {
        ws?.close(1000, null)
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSessions() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val url = Prefs.getServerUrl(this)
        val client = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"list_sessions","id":"ls-1"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = Gson().fromJson(text, JsonObject::class.java)
                    if (obj.get("type")?.asString == "response" &&
                        obj.get("command")?.asString == "list_sessions" &&
                        obj.get("success")?.asBoolean == true
                    ) {
                        val data = obj.getAsJsonObject("data")
                        val arr = data.getAsJsonArray("sessions")
                        val parsed = arr.map { el ->
                            val s = el.asJsonObject
                            SessionInfo(
                                id = s.get("id")?.asString ?: "",
                                path = s.get("path")?.asString ?: "",
                                timestamp = s.get("timestamp")?.asString ?: "",
                                lastActivity = s.get("lastActivity")?.asString ?: "",
                                cwd = s.get("cwd")?.asString ?: "",
                                name = s.get("name")?.let { if (it.isJsonNull) null else it.asString },
                                preview = s.get("preview")?.asString ?: "",
                                messageCount = s.get("messageCount")?.asInt ?: 0
                            )
                        }

                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            sessions.clear()
                            sessions.addAll(parsed)
                            adapter.notifyDataSetChanged()
                            tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                        }

                        webSocket.close(1000, null)
                    }
                } catch (e: Exception) {
                    // ignore non-list_sessions events
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvEmpty.text = "Connection failed: ${t.message}"
                    tvEmpty.visibility = View.VISIBLE
                }
            }
        })
    }
}

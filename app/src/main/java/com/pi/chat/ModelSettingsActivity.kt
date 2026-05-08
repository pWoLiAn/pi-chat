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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

class ModelSettingsActivity : AppCompatActivity() {

    companion object {
        const val RESULT_MODEL_CHANGED = "model_changed"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var chipGroup: ChipGroup
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private val models = mutableListOf<ModelInfo>()
    private var currentModelId: String? = null
    private var currentThinkingLevel: String = "off"
    private var ws: WebSocket? = null
    private var pendingResponses = 0

    private val thinkingLevels = listOf("off", "minimal", "low", "medium", "high")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Model & Thinking"
            setDisplayHomeAsUpEnabled(true)
        }

        recyclerView = findViewById(R.id.rvModels)
        chipGroup = findViewById(R.id.chipGroupThinking)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        recyclerView.layoutManager = LinearLayoutManager(this)

        setupThinkingChips()
        loadModels()
    }

    override fun onDestroy() {
        ws?.close(1000, null)
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupThinkingChips() {
        chipGroup.removeAllViews()
        for (level in thinkingLevels) {
            val chip = Chip(this).apply {
                text = level.replaceFirstChar { it.uppercase() }
                isCheckable = true
                tag = level
                setOnClickListener { onThinkingLevelSelected(level) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun highlightThinkingLevel(level: String) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            chip.isChecked = chip.tag == level
        }
    }

    private fun onThinkingLevelSelected(level: String) {
        if (level == currentThinkingLevel) return
        sendCommand("""{"type":"set_thinking_level","level":"$level","id":"think-1"}""")
        currentThinkingLevel = level
        highlightThinkingLevel(level)
    }

    private fun loadModels() {
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE

        val url = Prefs.getServerUrl(this)
        val client = OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                pendingResponses = 2
                webSocket.send("""{"type":"get_state","id":"state-1"}""")
                webSocket.send("""{"type":"get_available_models","id":"models-1"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = Gson().fromJson(text, JsonObject::class.java)
                    val type = obj.get("type")?.asString ?: return
                    if (type != "response") return

                    val command = obj.get("command")?.asString
                    val success = obj.get("success")?.asBoolean ?: false

                    when (command) {
                        "get_state" -> {
                            if (success) {
                                val data = obj.getAsJsonObject("data")
                                currentModelId = data?.getAsJsonObject("model")?.get("id")?.asString
                                currentThinkingLevel = data?.get("thinkingLevel")?.asString ?: "off"
                                runOnUiThread { highlightThinkingLevel(currentThinkingLevel) }
                            }
                            checkDone()
                        }
                        "get_available_models" -> {
                            if (success) {
                                val data = obj.getAsJsonObject("data")
                                val arr = data.getAsJsonArray("models")
                                val parsed = arr.map { el ->
                                    val m = el.asJsonObject
                                    ModelInfo(
                                        id = m.get("id")?.asString ?: "",
                                        name = m.get("name")?.asString ?: "",
                                        provider = m.get("provider")?.asString ?: "",
                                        reasoning = m.get("reasoning")?.asBoolean ?: false,
                                        contextWindow = m.get("contextWindow")?.asInt ?: 0
                                    )
                                }
                                runOnUiThread {
                                    models.clear()
                                    models.addAll(parsed)
                                }
                            }
                            checkDone()
                        }
                        "set_model" -> {
                            if (success) {
                                val data = obj.getAsJsonObject("data")
                                currentModelId = data?.get("id")?.asString
                                runOnUiThread { updateModelList() }
                                setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_MODEL_CHANGED, true))
                            }
                        }
                        "set_thinking_level" -> {
                            if (success) {
                                setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_MODEL_CHANGED, true))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvError.text = "Connection failed: ${t.message}"
                    tvError.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun checkDone() {
        pendingResponses--
        if (pendingResponses <= 0) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                updateModelList()
            }
        }
    }

    private fun updateModelList() {
        recyclerView.adapter = ModelAdapter(models, currentModelId) { model ->
            sendCommand("""{"type":"set_model","provider":"${model.provider}","modelId":"${model.id}","id":"setm-1"}""")
            currentModelId = model.id
            updateModelList()
        }
    }

    private fun sendCommand(json: String) {
        ws?.send(json)
    }
}

data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val reasoning: Boolean,
    val contextWindow: Int
) {
    val contextWindowDisplay: String
        get() = "${contextWindow / 1000}K"
}

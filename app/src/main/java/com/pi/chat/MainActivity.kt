package com.pi.chat

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pi.chat.databinding.ActivityMainBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

class MainActivity : AppCompatActivity(), PiWebSocketClient.PiEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var markwon: Markwon
    private lateinit var piClient: PiWebSocketClient

    private val messages = mutableListOf<ChatMessage>()
    private val textBuffer = StringBuilder()
    private var isStreaming = false

    private val sessionPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val sessionPath = data.getStringExtra(SessionPickerActivity.RESULT_SESSION_PATH)
            val newSession = data.getBooleanExtra(SessionPickerActivity.RESULT_NEW_SESSION, false)

            if (newSession) {
                messages.clear()
                refreshList()
                addSystemMessage("Starting new session...")
                piClient.sendRaw("""{ "type": "new_session" }""")
            } else if (sessionPath != null) {
                messages.clear()
                refreshList()
                addSystemMessage("Switching session...")
                piClient.sendRaw("""{ "type": "switch_session", "sessionPath": "$sessionPath" }""")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Pi Chat"

        markwon = Markwon.builder(this)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .build()

        adapter = ChatAdapter(messages, markwon)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = this@MainActivity.adapter
        }

        piClient = PiWebSocketClient(this)

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.btnAbort.setOnClickListener { piClient.abort() }

        binding.editMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        connectToServer()
    }

    override fun onDestroy() {
        piClient.disconnect()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_reconnect -> {
                connectToServer()
                true
            }
            R.id.action_sessions -> {
                sessionPickerLauncher.launch(Intent(this, SessionPickerActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!piClient.isConnected) {
            connectToServer()
        }
    }

    private fun connectToServer() {
        val url = Prefs.getServerUrl(this)
        addSystemMessage("Connecting to $url...")
        piClient.connect(url)
    }

    private fun sendMessage() {
        val text = binding.editMessage.text.toString().trim()
        if (text.isEmpty() || isStreaming) return

        binding.editMessage.text?.clear()

        messages.add(ChatMessage(ChatMessage.Role.USER, text))
        refreshList()

        piClient.sendPrompt(text)
    }

    // --- PiEventListener callbacks (called from OkHttp threads) ---

    override fun onConnected() = runOnUiThread {
        updateConnectionStatus(true)
        addSystemMessage("Connected ✓")
    }

    override fun onDisconnected(reason: String) = runOnUiThread {
        updateConnectionStatus(false)
        isStreaming = false
        updateStreamingUI()
        addSystemMessage("Disconnected: $reason")
    }

    override fun onStateReceived(modelName: String?, sessionId: String?) = runOnUiThread {
        supportActionBar?.subtitle = modelName ?: "No model"
    }

    override fun onAgentStart() = runOnUiThread {
        isStreaming = true
        textBuffer.clear()
        messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, "", isStreaming = true))
        refreshList()
        updateStreamingUI()
    }

    override fun onTextDelta(delta: String) = runOnUiThread {
        textBuffer.append(delta)
        updateLastAssistantMessage(textBuffer.toString(), isStreaming = true)
    }

    override fun onAgentEnd() = runOnUiThread {
        isStreaming = false
        updateLastAssistantMessage(textBuffer.toString(), isStreaming = false)
        textBuffer.clear()
        updateStreamingUI()
    }

    override fun onToolStart(toolName: String, args: String) = runOnUiThread {
        addSystemMessage("🔧 $toolName")
    }

    override fun onToolEnd(toolName: String, isError: Boolean) = runOnUiThread {
        val icon = if (isError) "❌" else "✅"
        addSystemMessage("$icon $toolName done")
    }

    override fun onError(error: String) = runOnUiThread {
        addSystemMessage("⚠️ $error")
    }

    // --- UI helpers ---

    private fun addSystemMessage(text: String) {
        messages.add(ChatMessage(ChatMessage.Role.SYSTEM, text))
        refreshList()
    }

    private fun updateLastAssistantMessage(text: String, isStreaming: Boolean) {
        val idx = messages.indexOfLast { it.role == ChatMessage.Role.ASSISTANT }
        if (idx >= 0) {
            messages[idx] = ChatMessage(ChatMessage.Role.ASSISTANT, text, isStreaming)
            adapter.notifyItemChanged(idx)
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun refreshList() {
        adapter.notifyDataSetChanged()
        if (messages.isNotEmpty()) {
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun updateStreamingUI() {
        binding.btnSend.visibility = if (isStreaming) View.GONE else View.VISIBLE
        binding.btnAbort.visibility = if (isStreaming) View.VISIBLE else View.GONE
        binding.editMessage.isEnabled = !isStreaming
    }

    private fun updateConnectionStatus(connected: Boolean) {
        binding.statusIndicator.setBackgroundResource(
            if (connected) R.drawable.status_connected else R.drawable.status_disconnected
        )
    }
}

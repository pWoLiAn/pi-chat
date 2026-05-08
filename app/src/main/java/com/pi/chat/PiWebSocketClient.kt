package com.pi.chat

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

class PiWebSocketClient(
    private val listener: PiEventListener
) {
    interface PiEventListener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onTextDelta(delta: String)
        fun onAgentStart()
        fun onAgentEnd()
        fun onToolStart(toolName: String, args: String)
        fun onToolEnd(toolName: String, isError: Boolean)
        fun onError(error: String)
        fun onStateReceived(modelName: String?, sessionId: String?)
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout for streaming
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    var isConnected = false
        private set

    fun connect(url: String) {
        disconnect()
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                listener.onConnected()
                // Request initial state
                send("""{"type":"get_state"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleEvent(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected = false
                listener.onDisconnected(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                listener.onError("Connection failed: ${t.message}")
                listener.onDisconnected(t.message ?: "Unknown error")
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "Client disconnect")
        ws = null
        isConnected = false
    }

    fun sendPrompt(message: String) {
        val cmd = JsonObject().apply {
            addProperty("type", "prompt")
            addProperty("message", message)
        }
        send(gson.toJson(cmd))
    }

    fun abort() {
        send("""{"type":"abort"}""")
    }

    fun sendRaw(json: String) {
        send(json)
    }

    fun requestState() {
        send("""{"type":"get_state"}""")
    }

    private fun send(json: String) {
        ws?.send(json)
    }

    private fun handleEvent(raw: String) {
        try {
            val obj = gson.fromJson(raw, JsonObject::class.java)
            val type = obj.get("type")?.asString ?: return

            when (type) {
                "response" -> handleResponse(obj)
                "agent_start" -> listener.onAgentStart()
                "agent_end" -> listener.onAgentEnd()
                "message_update" -> handleMessageUpdate(obj)
                "tool_execution_start" -> {
                    val name = obj.get("toolName")?.asString ?: "unknown"
                    val args = obj.get("args")?.toString() ?: ""
                    listener.onToolStart(name, args)
                }
                "tool_execution_end" -> {
                    val name = obj.get("toolName")?.asString ?: "unknown"
                    val isError = obj.get("isError")?.asBoolean ?: false
                    listener.onToolEnd(name, isError)
                }
                "extension_ui_request" -> {
                    // Auto-respond to extension UI dialogs (e.g., confirm dangerous commands)
                    handleExtensionUi(obj)
                }
            }
        } catch (e: Exception) {
            listener.onError("Parse error: ${e.message}")
        }
    }

    private fun handleResponse(obj: JsonObject) {
        val command = obj.get("command")?.asString
        val success = obj.get("success")?.asBoolean ?: false

        if (command == "get_state" && success) {
            val data = obj.getAsJsonObject("data")
            val model = data?.getAsJsonObject("model")
            val modelName = model?.get("name")?.asString
            val sessionId = data?.get("sessionId")?.asString
            listener.onStateReceived(modelName, sessionId)
        }

        if (!success) {
            val error = obj.get("error")?.asString ?: "Unknown error"
            listener.onError("$command failed: $error")
        }
    }

    private fun handleMessageUpdate(obj: JsonObject) {
        val event = obj.getAsJsonObject("assistantMessageEvent") ?: return
        val eventType = event.get("type")?.asString ?: return

        when (eventType) {
            "text_delta" -> {
                val delta = event.get("delta")?.asString ?: ""
                listener.onTextDelta(delta)
            }
        }
    }

    private fun handleExtensionUi(obj: JsonObject) {
        val id = obj.get("id")?.asString ?: return
        val method = obj.get("method")?.asString ?: return

        // Auto-respond to dialogs that block the agent
        val response = when (method) {
            "confirm" -> """{"type":"extension_ui_response","id":"$id","confirmed":true}"""
            "select" -> {
                val options = obj.getAsJsonArray("options")
                val first = options?.firstOrNull()?.asString ?: ""
                """{"type":"extension_ui_response","id":"$id","value":"$first"}"""
            }
            else -> null // fire-and-forget methods don't need a response
        }
        if (response != null) send(response)
    }
}

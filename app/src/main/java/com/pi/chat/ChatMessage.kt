package com.pi.chat

data class ChatMessage(
    val role: Role,
    val content: String,
    val isStreaming: Boolean = false
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

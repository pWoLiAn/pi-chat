package com.pi.chat

data class SessionInfo(
    val id: String,
    val path: String,
    val timestamp: String,
    val lastActivity: String,
    val cwd: String,
    val name: String?,
    val preview: String,
    val messageCount: Int
) {
    val displayName: String
        get() = name ?: preview.take(60)

    val displayTime: String
        get() {
            return try {
                val instant = java.time.Instant.parse(lastActivity)
                val local = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
                local.format(formatter)
            } catch (e: Exception) {
                lastActivity.take(16)
            }
        }
}

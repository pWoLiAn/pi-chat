package com.pi.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val markwon: Markwon
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.tvContent)
        val role: TextView = view.findViewById(R.id.tvRole)
        val card: View = view.findViewById(R.id.card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.item_message_user
            1 -> R.layout.item_message_assistant
            else -> R.layout.item_message_system
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]

        when (msg.role) {
            ChatMessage.Role.USER -> {
                holder.content.text = msg.content
                holder.role.text = "You"
            }
            ChatMessage.Role.ASSISTANT -> {
                markwon.setMarkdown(holder.content, msg.content)
                holder.role.text = if (msg.isStreaming) "Pi ●" else "Pi"
            }
            ChatMessage.Role.SYSTEM -> {
                holder.content.text = msg.content
                holder.role.text = "ℹ️"
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (messages[position].role) {
        ChatMessage.Role.USER -> 0
        ChatMessage.Role.ASSISTANT -> 1
        ChatMessage.Role.SYSTEM -> 2
    }

    override fun getItemCount() = messages.size
}

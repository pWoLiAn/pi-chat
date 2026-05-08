package com.pi.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private val sessions: List<SessionInfo>,
    private val onSessionClick: (SessionInfo) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSessionName)
        val tvPreview: TextView = view.findViewById(R.id.tvSessionPreview)
        val tvTime: TextView = view.findViewById(R.id.tvSessionTime)
        val tvMessages: TextView = view.findViewById(R.id.tvMessageCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvName.text = session.name ?: "Unnamed Session"
        holder.tvPreview.text = session.preview
        holder.tvTime.text = session.displayTime
        holder.tvMessages.text = "${session.messageCount} msgs"
        holder.itemView.setOnClickListener { onSessionClick(session) }
    }

    override fun getItemCount() = sessions.size
}

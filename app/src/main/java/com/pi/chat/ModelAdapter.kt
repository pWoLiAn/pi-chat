package com.pi.chat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ModelAdapter(
    private val models: List<ModelInfo>,
    private val currentModelId: String?,
    private val onModelClick: (ModelInfo) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardModel)
        val tvName: TextView = view.findViewById(R.id.tvModelName)
        val tvProvider: TextView = view.findViewById(R.id.tvProvider)
        val tvContext: TextView = view.findViewById(R.id.tvContextWindow)
        val tvReasoning: TextView = view.findViewById(R.id.tvReasoning)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        val isSelected = model.id == currentModelId

        holder.tvName.text = model.name
        holder.tvProvider.text = model.provider
        holder.tvContext.text = model.contextWindowDisplay
        holder.tvReasoning.visibility = if (model.reasoning) View.VISIBLE else View.GONE

        holder.card.strokeWidth = if (isSelected) 2 else 0
        holder.card.strokeColor = if (isSelected) Color.parseColor("#58A6FF") else 0
        holder.tvName.setTextColor(
            if (isSelected) Color.parseColor("#58A6FF") else Color.WHITE
        )

        holder.itemView.setOnClickListener { onModelClick(model) }
    }

    override fun getItemCount() = models.size
}

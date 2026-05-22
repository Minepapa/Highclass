package com.alimjangbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alimjangbot.data.LogEntry
import com.alimjangbot.databinding.ItemLogBinding

class LogAdapter(private var logs: List<LogEntry>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: LogEntry) {
            binding.tvLogTime.text    = entry.formattedTime()
            binding.tvLogStatus.text  = entry.statusEmoji()
            binding.tvLogMessage.text = entry.message
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}

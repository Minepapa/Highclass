package com.alimjangbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alimjangbot.data.LogEntry
import com.alimjangbot.databinding.ItemLogBinding

class LogAdapter(private var logs: List<LogEntry>) :
    RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    inner class LogViewHolder(private val b: ItemLogBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(entry: LogEntry) {
            b.tvLogTime.text    = entry.formattedTime()
            b.tvLogStatus.text  = entry.statusEmoji()
            b.tvLogMessage.text = entry.message
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LogViewHolder(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) =
        holder.bind(logs[position])

    override fun getItemCount() = logs.size

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}

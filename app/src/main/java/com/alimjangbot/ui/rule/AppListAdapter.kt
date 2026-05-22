package com.alimjangbot.ui.rule

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alimjangbot.databinding.ItemAppBinding

class AppListAdapter(
    private val onSelect: (AppPickerActivity.AppItem) -> Unit
) : ListAdapter<AppPickerActivity.AppItem, AppListAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppPickerActivity.AppItem>() {
            override fun areItemsTheSame(a: AppPickerActivity.AppItem, b: AppPickerActivity.AppItem) =
                a.packageName == b.packageName
            override fun areContentsTheSame(a: AppPickerActivity.AppItem, b: AppPickerActivity.AppItem) =
                a == b
        }
    }

    inner class ViewHolder(private val b: ItemAppBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(item: AppPickerActivity.AppItem) {
            b.tvAppName.text    = item.label
            b.tvPackageName.text = item.packageName
            b.ivAppIcon.setImageDrawable(item.icon)
            b.root.setOnClickListener { onSelect(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}

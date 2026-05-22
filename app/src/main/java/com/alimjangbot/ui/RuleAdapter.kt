package com.alimjangbot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alimjangbot.data.AutomationRule
import com.alimjangbot.databinding.ItemRuleBinding

class RuleAdapter(
    private val onToggle: (AutomationRule) -> Unit,
    private val onEdit:   (AutomationRule) -> Unit,
    private val onDelete: (AutomationRule) -> Unit
) : ListAdapter<AutomationRule, RuleAdapter.RuleViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AutomationRule>() {
            override fun areItemsTheSame(a: AutomationRule, b: AutomationRule) = a.id == b.id
            override fun areContentsTheSame(a: AutomationRule, b: AutomationRule) = a == b
        }
    }

    inner class RuleViewHolder(private val b: ItemRuleBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(rule: AutomationRule) {
            b.tvRuleName.text    = rule.name
            b.tvRuleApp.text     = if (rule.appLabel.isNotBlank()) rule.appLabel else rule.packageName.ifBlank { "모든 앱" }
            b.tvRuleKeyword.text = if (rule.keyword.isNotBlank()) "\"${rule.keyword}\"" else "모든 알림"
            b.tvRuleAction.text  = rule.actionType.label
            b.switchRule.isChecked = rule.enabled
            b.root.alpha = if (rule.enabled) 1f else 0.5f

            b.switchRule.setOnCheckedChangeListener(null)
            b.switchRule.setOnCheckedChangeListener { _, _ -> onToggle(rule) }
            b.btnEdit.setOnClickListener   { onEdit(rule)   }
            b.btnDelete.setOnClickListener { onDelete(rule) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        RuleViewHolder(
            ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) =
        holder.bind(getItem(position))
}

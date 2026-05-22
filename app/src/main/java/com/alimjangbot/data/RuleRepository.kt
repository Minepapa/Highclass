package com.alimjangbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

/**
 * 자동화 규칙 목록을 SharedPreferences에 JSON 배열로 저장/로드.
 */
class RuleRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("automation_rules", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var INSTANCE: RuleRepository? = null

        fun getInstance(context: Context): RuleRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleRepository(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_RULES = "rules"
    }

    fun getRules(): List<AutomationRule> {
        val json = prefs.getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { AutomationRule.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 활성화된 규칙만 반환 (NotificationWatcher용) */
    fun getEnabledRules(): List<AutomationRule> = getRules().filter { it.enabled }

    fun saveRule(rule: AutomationRule) {
        val rules = getRules().toMutableList()
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) rules[idx] = rule else rules.add(rule)
        saveAll(rules)
    }

    fun deleteRule(ruleId: String) {
        val rules = getRules().toMutableList()
        rules.removeAll { it.id == ruleId }
        saveAll(rules)
    }

    fun toggleRule(ruleId: String) {
        val rules = getRules().toMutableList()
        val idx = rules.indexOfFirst { it.id == ruleId }
        if (idx >= 0) {
            rules[idx] = rules[idx].copy(enabled = !rules[idx].enabled)
            saveAll(rules)
        }
    }

    private fun saveAll(rules: List<AutomationRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        prefs.edit { putString(KEY_RULES, arr.toString()) }
    }
}

package com.alimjangbot.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.alimjangbot.data.AppSettings
import com.alimjangbot.data.AutomationRule
import com.alimjangbot.data.LogEntry
import com.alimjangbot.data.RuleRepository
import com.alimjangbot.data.SendLogRepository
import org.json.JSONArray

class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, NotificationWatcher::class.java).flattenToString()
            return flat.split(":").any { it == cn }
        }
    }

    private lateinit var settings: AppSettings
    private lateinit var ruleRepo: RuleRepository
    private lateinit var logRepo:  SendLogRepository

    private val recentlyProcessed = LinkedHashMap<String, Long>(16, 0.75f, true)
    private val DEBOUNCE_MS = 5_000L

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.getInstance(this)
        ruleRepo = RuleRepository.getInstance(this)
        logRepo  = SendLogRepository.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!settings.serviceEnabled) return

        val pkg      = sbn.packageName ?: return
        val notifKey = sbn.key         ?: return

        // 중복 방지
        val now = System.currentTimeMillis()
        if ((recentlyProcessed[notifKey] ?: 0L) + DEBOUNCE_MS > now) return
        recentlyProcessed[notifKey] = now
        if (recentlyProcessed.size > 50) recentlyProcessed.entries.first().let { recentlyProcessed.remove(it.key) }

        // 알림 텍스트 추출
        val extras   = sbn.notification?.extras ?: return
        val title    = extras.getCharSequence("android.title")?.toString()   ?: ""
        val text     = extras.getCharSequence("android.text")?.toString()    ?: ""
        val bigText  = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title $text $bigText"

        // 매칭 규칙 찾기
        val rule = ruleRepo.getEnabledRules().firstOrNull { matchesRule(it, pkg, fullText) } ?: return

        Log.i(TAG, "[${rule.name}] 매칭: pkg=$pkg, title=$title")
        logRepo.addLog(LogEntry.Status.SUCCESS, "[${rule.name}] 알림 감지: $title")

        // 알림 클릭 → 해당 게시글 오픈
        val contentIntent = sbn.notification?.contentIntent
        if (contentIntent != null) {
            try { contentIntent.send() }
            catch (e: PendingIntent.CanceledException) { openApp(pkg) }
        } else {
            openApp(pkg)
        }

        // 파이프라인 실행 요청
        launchPipeline(rule, title, "$text $bigText".trim())
    }

    private fun matchesRule(rule: AutomationRule, pkg: String, fullText: String): Boolean {
        if (rule.packageName.isNotBlank() && pkg != rule.packageName) return false
        if (rule.keyword.isNotBlank() && !fullText.contains(rule.keyword)) return false
        return true
    }

    private fun launchPipeline(rule: AutomationRule, notifTitle: String, notifText: String) {
        if (rule.steps.isEmpty()) {
            logRepo.addLog(LogEntry.Status.SKIPPED, "[${rule.name}] 단계 없음")
            return
        }

        // steps → JSON 직렬화해서 서비스에 전달
        val stepsJson = JSONArray().also { arr ->
            rule.steps.forEach { arr.put(it.toJson()) }
        }.toString()

        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_RUN_PIPELINE
            putExtra(ScreenCaptureService.EXTRA_STEPS_JSON,  stepsJson)
            putExtra(ScreenCaptureService.EXTRA_NOTIF_TITLE, notifTitle)
            putExtra(ScreenCaptureService.EXTRA_NOTIF_TEXT,  notifText)
            putExtra(ScreenCaptureService.EXTRA_RULE_NAME,   rule.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun openApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }
}

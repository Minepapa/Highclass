package com.alimjangbot.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.alimjangbot.data.ActionConfig
import com.alimjangbot.data.ActionType
import com.alimjangbot.data.AppSettings
import com.alimjangbot.data.AutomationRule
import com.alimjangbot.data.LogEntry
import com.alimjangbot.data.RuleRepository
import com.alimjangbot.data.SendLogRepository

/**
 * 모든 앱의 알림을 수신하여 등록된 규칙과 매칭되면 지정 액션을 실행.
 */
class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, NotificationWatcher::class.java).flattenToString()
            return flat.split(":").any { it == cn }
        }
    }

    private lateinit var settings:  AppSettings
    private lateinit var ruleRepo:  RuleRepository
    private lateinit var logRepo:   SendLogRepository

    /** 중복 처리 방지 캐시 (알림 key → 처리 시각) */
    private val recentlyProcessed = LinkedHashMap<String, Long>(16, 0.75f, true)
    private val DEBOUNCE_MS = 5_000L   // 같은 알림 5초 내 재처리 방지

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.getInstance(this)
        ruleRepo = RuleRepository.getInstance(this)
        logRepo  = SendLogRepository.getInstance(this)
        Log.d(TAG, "NotificationWatcher 시작")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 전체 서비스 비활성화
        if (!settings.serviceEnabled) return

        val pkg      = sbn.packageName ?: return
        val notifKey = sbn.key         ?: return

        // 중복 처리 방지
        val now = System.currentTimeMillis()
        if ((recentlyProcessed[notifKey] ?: 0L) + DEBOUNCE_MS > now) return
        recentlyProcessed[notifKey] = now
        if (recentlyProcessed.size > 50) recentlyProcessed.entries.first().let {
            recentlyProcessed.remove(it.key)
        }

        // 알림 본문 추출
        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence("android.title")?.toString()   ?: ""
        val text    = extras.getCharSequence("android.text")?.toString()    ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title $text $bigText"

        // 활성 규칙 중 매칭되는 규칙 찾기
        val matchedRule = ruleRepo.getEnabledRules().firstOrNull { rule ->
            matchesRule(rule, pkg, fullText)
        } ?: return

        Log.i(TAG, "규칙 매칭: [${matchedRule.name}] pkg=$pkg, text=$title")
        logRepo.addLog(LogEntry.Status.SUCCESS, "[${matchedRule.name}] 알림 감지: $title")

        // 알림 클릭 → 해당 앱 게시글 오픈
        val contentIntent = sbn.notification?.contentIntent
        if (contentIntent != null) {
            try {
                contentIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "contentIntent 취소됨, 앱 직접 실행")
                openApp(pkg)
            }
        } else {
            openApp(pkg)
        }

        // 액션 실행
        executeAction(matchedRule, title)
    }

    /** 규칙과 알림이 매칭되는지 확인 */
    private fun matchesRule(rule: AutomationRule, pkg: String, fullText: String): Boolean {
        // 앱 필터: 패키지명이 설정된 경우 일치해야 함 (비어있으면 모든 앱)
        if (rule.packageName.isNotBlank() && pkg != rule.packageName) return false

        // 키워드 필터: 설정된 경우 포함되어야 함 (비어있으면 모든 알림)
        if (rule.keyword.isNotBlank() && !fullText.contains(rule.keyword)) return false

        return true
    }

    /** 규칙의 액션 실행 */
    private fun executeAction(rule: AutomationRule, notifTitle: String) {
        when (rule.actionType) {
            ActionType.CAPTURE_AND_MMS -> {
                val config    = ActionConfig.parseMmsConfig(rule.actionConfig)
                val recipient = config.recipientNumber

                if (recipient.isBlank()) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "[${rule.name}] 수신 번호 미설정")
                    return
                }

                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_CAPTURE_AND_SEND
                    putExtra(ScreenCaptureService.EXTRA_DELAY_SEC,      config.captureDelaySec)
                    putExtra(ScreenCaptureService.EXTRA_RECIPIENT,      recipient)
                    putExtra(ScreenCaptureService.EXTRA_TRIGGER_TITLE,  notifTitle)
                    putExtra(ScreenCaptureService.EXTRA_RULE_NAME,      rule.name)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            // 향후 액션 추가 시 when 분기 추가
        }
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Log.w(TAG, "앱 실행 실패: $packageName")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { /* no-op */ }
}

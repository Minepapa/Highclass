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
import com.alimjangbot.data.LogEntry
import com.alimjangbot.data.SendLogRepository

/**
 * 하이클래스 앱의 알림을 실시간으로 감지하는 서비스.
 *
 * 동작:
 * 1. 모든 알림을 수신
 * 2. 하이클래스 패키지 + "알림장" 키워드 필터링
 * 3. 알림의 contentIntent(PendingIntent)를 실행하여 게시글 자동 오픈
 * 4. ScreenCaptureService에 캡처 + 전송 요청
 *
 * 등록 방법:
 * 설정 > 앱 > 특별한 앱 접근 > 알림 접근 허용 에서 이 앱을 허용해야 함.
 */
class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"

        /** 서비스가 활성화되어 있는지 시스템에 확인 */
        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, NotificationWatcher::class.java).flattenToString()
            return flat.split(":").any { it == cn }
        }
    }

    private lateinit var settings: AppSettings
    private lateinit var logRepo: SendLogRepository

    /** 중복 처리 방지: 최근 처리한 알림 key 캐시 (최대 10개) */
    private val recentlyProcessed = ArrayDeque<String>(10)

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings.getInstance(this)
        logRepo  = SendLogRepository.getInstance(this)
        Log.d(TAG, "NotificationWatcher 시작")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 서비스 비활성화 상태면 무시
        if (!settings.serviceEnabled) return

        val pkg     = sbn.packageName ?: return
        val notifKey = sbn.key ?: return

        // 1. 하이클래스 패키지 확인
        if (pkg != settings.highclassPackage) return

        // 2. 알림 본문에서 키워드 확인
        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence("android.title")?.toString() ?: ""
        val text   = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = "$title $text $bigText"

        val keyword = settings.keyword
        if (!fullText.contains(keyword)) {
            Log.d(TAG, "키워드 '$keyword' 없음 → 무시: $fullText")
            return
        }

        // 3. 중복 처리 방지
        if (recentlyProcessed.contains(notifKey)) {
            Log.d(TAG, "이미 처리된 알림: $notifKey")
            return
        }
        addToProcessedCache(notifKey)

        Log.i(TAG, "✅ 알림장 감지! title=$title, text=$text")
        logRepo.addLog(LogEntry.Status.SUCCESS, "알림 감지: $title")

        // 4. 알림의 contentIntent 실행 → 하이클래스 앱 해당 게시글 오픈
        val contentIntent = sbn.notification?.contentIntent
        if (contentIntent != null) {
            try {
                contentIntent.send()
                Log.i(TAG, "contentIntent 실행 → 하이클래스 게시글 오픈")
            } catch (e: PendingIntent.CanceledException) {
                Log.e(TAG, "contentIntent 취소됨: ${e.message}")
                logRepo.addLog(LogEntry.Status.FAILURE, "게시글 오픈 실패: ${e.message}")
                return
            }
        } else {
            Log.w(TAG, "contentIntent 없음 → 앱 직접 실행")
            openHighclassApp()
        }

        // 5. ScreenCaptureService에 캡처 요청 (딜레이 후 실행)
        val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_CAPTURE_AND_SEND
            putExtra(ScreenCaptureService.EXTRA_DELAY_SEC, settings.captureDelaySec)
            putExtra(ScreenCaptureService.EXTRA_RECIPIENT, settings.recipientNumber)
            putExtra(ScreenCaptureService.EXTRA_TRIGGER_TITLE, title)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(captureIntent)
        } else {
            startService(captureIntent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 필요 시 처리
    }

    /** 하이클래스 앱의 메인 화면을 직접 실행 (contentIntent 없을 때 폴백) */
    private fun openHighclassApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(settings.highclassPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Log.e(TAG, "하이클래스 앱이 설치되지 않음: ${settings.highclassPackage}")
            logRepo.addLog(LogEntry.Status.FAILURE, "하이클래스 앱 실행 실패 (설치 확인 필요)")
        }
    }

    private fun addToProcessedCache(key: String) {
        if (recentlyProcessed.size >= 10) recentlyProcessed.removeFirst()
        recentlyProcessed.addLast(key)
    }
}

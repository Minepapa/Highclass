package com.alimjangbot.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.alimjangbot.data.AppSettings

class NotificationWatcher : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationWatcher"
        private const val TARGET_PKG = "com.iscreammedia.app.hiclass.android"
        private const val TARGET_KEYWORD = "알림장"

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, NotificationWatcher::class.java).flattenToString()
            return flat.split(":").any { it == cn }
        }
    }

    private var lastSentTime = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != TARGET_PKG) return

        val settings = AppSettings.getInstance(this)
        if (!settings.serviceEnabled) return

        val phone = settings.phoneNumber
        if (phone.isBlank()) {
            Log.w(TAG, "수신 번호가 설정되지 않음")
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title   = extras.getCharSequence("android.title")?.toString() ?: ""
        val text    = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val content  = bigText.ifBlank { text }
        val fullText = "$title $content"

        if (!fullText.contains(TARGET_KEYWORD)) return

        // 10초 디바운스 (중복 방지)
        val now = System.currentTimeMillis()
        if (now - lastSentTime < 10_000L) return
        lastSentTime = now

        Log.i(TAG, "알림장 감지: $title")

        val msg = buildString {
            append("[하이클래스 알림장]\n")
            if (title.isNotBlank()) append("$title\n\n")
            if (content.isNotBlank()) append(content)
        }.trim()

        Thread {
            val ok = SmsDispatcher.send(this, phone, msg)
            Log.i(TAG, if (ok) "SMS 전송 성공 → $phone" else "SMS 전송 실패")
        }.start()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { }
}

package com.alimjangbot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AlimjangApplication : Application() {

    companion object {
        const val CHANNEL_ID_FOREGROUND = "alimjang_foreground"
        const val CHANNEL_ID_STATUS     = "alimjang_status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // 포그라운드 서비스용 채널 (화면 캡처 중 표시)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_FOREGROUND,
                    "알림장봇 실행 중",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "화면 캡처 서비스 실행 중 표시됩니다."
                    setShowBadge(false)
                }
            )

            // 전송 결과 알림 채널
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_STATUS,
                    "전송 결과",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "알림장 MMS 전송 결과를 알려드립니다."
                }
            )
        }
    }
}

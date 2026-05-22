package com.alimjangbot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 부팅 완료 시 NotificationListenerService를 유지하기 위한 수신기.
 *
 * NotificationListenerService는 시스템이 자동으로 재시작하지만,
 * 앱 업데이트 후 재시작이 필요한 경우 이 Receiver가 트리거됨.
 *
 * 주의: Android에서 NotificationListenerService는 시스템이 관리하므로
 *       별도로 startService를 호출할 필요가 없음.
 *       이 Receiver는 부팅 로그 및 향후 확장을 위해 유지.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "부팅 완료 → NotificationListenerService 시스템이 자동 복구")
                // NotificationListenerService는 BIND_NOTIFICATION_LISTENER_SERVICE 권한으로
                // 시스템이 자동으로 관리하므로 별도 시작 불필요
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "앱 업데이트 감지 → 서비스 확인")
            }
        }
    }
}

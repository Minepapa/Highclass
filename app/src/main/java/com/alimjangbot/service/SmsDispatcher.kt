package com.alimjangbot.service

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

/**
 * 텍스트를 SMS(문자)로 전송하는 유틸리티.
 * 내용이 길면 자동으로 멀티파트 SMS로 분할 전송.
 */
object SmsDispatcher {

    private const val TAG = "SmsDispatcher"

    /**
     * @param context   컨텍스트
     * @param recipient 수신 전화번호
     * @param text      전송할 텍스트
     * @return 전송 요청 성공 여부
     */
    fun send(context: Context, recipient: String, text: String): Boolean {
        return try {
            val normalized = normalizePhoneNumber(recipient)
            if (normalized.isBlank()) {
                Log.e(TAG, "수신 번호 없음")
                return false
            }

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            if (parts.size == 1) {
                smsManager.sendTextMessage(normalized, null, text, null, null)
            } else {
                smsManager.sendMultipartTextMessage(normalized, null, parts, null, null)
            }

            Log.i(TAG, "SMS 전송 요청 완료 → $normalized (${parts.size}파트)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS 전송 실패: ${e.message}", e)
            false
        }
    }

    private fun normalizePhoneNumber(number: String): String =
        number.replace(Regex("[^0-9+]"), "")
}

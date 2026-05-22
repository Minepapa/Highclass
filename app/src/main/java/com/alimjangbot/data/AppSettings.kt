package com.alimjangbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 앱 설정을 SharedPreferences로 관리.
 * 싱글턴 패턴으로 앱 전역에서 사용.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("alimjang_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }

        // 하이클래스 앱의 패키지명 (기기에서 확인 후 변경 가능)
        // Play Store URL: https://play.google.com/store/apps/details?id=com.hischool.highclass
        const val DEFAULT_HIGHCLASS_PACKAGE = "com.hischool.highclass"

        // 알림 본문에서 감지할 키워드
        const val DEFAULT_KEYWORD = "알림장"

        // 게시글 로드 후 캡처까지 대기 시간 (초)
        const val DEFAULT_CAPTURE_DELAY_SEC = 3

        private const val KEY_RECIPIENT_NUMBER  = "recipient_number"
        private const val KEY_CAPTURE_DELAY     = "capture_delay_sec"
        private const val KEY_KEYWORD           = "keyword"
        private const val KEY_HIGHCLASS_PACKAGE = "highclass_package"
        private const val KEY_SERVICE_ENABLED   = "service_enabled"
        private const val KEY_PROJECTION_GRANTED = "projection_granted"
    }

    /** 딸의 전화번호 (MMS 수신자) */
    var recipientNumber: String
        get() = prefs.getString(KEY_RECIPIENT_NUMBER, "") ?: ""
        set(v) = prefs.edit { putString(KEY_RECIPIENT_NUMBER, v) }

    /** 알림 클릭 후 캡처까지 대기 시간 (초) */
    var captureDelaySec: Int
        get() = prefs.getInt(KEY_CAPTURE_DELAY, DEFAULT_CAPTURE_DELAY_SEC)
        set(v) = prefs.edit { putInt(KEY_CAPTURE_DELAY, v) }

    /** 감지할 키워드 */
    var keyword: String
        get() = prefs.getString(KEY_KEYWORD, DEFAULT_KEYWORD) ?: DEFAULT_KEYWORD
        set(v) = prefs.edit { putString(KEY_KEYWORD, v) }

    /** 하이클래스 앱 패키지명 */
    var highclassPackage: String
        get() = prefs.getString(KEY_HIGHCLASS_PACKAGE, DEFAULT_HIGHCLASS_PACKAGE) ?: DEFAULT_HIGHCLASS_PACKAGE
        set(v) = prefs.edit { putString(KEY_HIGHCLASS_PACKAGE, v) }

    /** 서비스 활성화 여부 */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(v) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, v) }

    /** MediaProjection 권한 획득 여부 (UI 표시용) */
    var projectionGranted: Boolean
        get() = prefs.getBoolean(KEY_PROJECTION_GRANTED, false)
        set(v) = prefs.edit { putBoolean(KEY_PROJECTION_GRANTED, v) }

    /** 설정이 완료된 상태인지 (최소 전화번호는 입력되어야 함) */
    fun isConfigured(): Boolean = recipientNumber.isNotBlank()
}

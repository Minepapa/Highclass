package com.alimjangbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 앱 전역 설정 (규칙별 설정이 아닌 시스템 레벨 설정).
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_SERVICE_ENABLED    = "service_enabled"
        private const val KEY_PROJECTION_GRANTED = "projection_granted"
    }

    /** 전체 서비스 활성화 여부 (false면 모든 규칙 비활성) */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(v) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, v) }

    /** MediaProjection 권한 획득 여부 */
    var projectionGranted: Boolean
        get() = prefs.getBoolean(KEY_PROJECTION_GRANTED, false)
        set(v) = prefs.edit { putBoolean(KEY_PROJECTION_GRANTED, v) }
}

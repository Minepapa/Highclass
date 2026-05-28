package com.alimjangbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("global_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
    }

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", true)
        set(v) = prefs.edit { putBoolean("service_enabled", v) }

    var phoneNumber: String
        get() = prefs.getString("phone_number", "") ?: ""
        set(v) = prefs.edit { putString("phone_number", v) }
}

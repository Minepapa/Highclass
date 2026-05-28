package com.alimjangbot.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alimjangbot.data.AppSettings
import com.alimjangbot.databinding.ActivityMainBinding
import com.alimjangbot.service.NotificationWatcher

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val settings = AppSettings.getInstance(this)

        binding.etPhoneNumber.setText(settings.phoneNumber)

        binding.btnSave.setOnClickListener {
            settings.phoneNumber = binding.etPhoneNumber.text.toString().trim()
            Toast.makeText(this, "저장됐습니다", Toast.LENGTH_SHORT).show()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        }

        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            settings.serviceEnabled = checked
            refreshStatus()
        }

        binding.btnNotifPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val settings = AppSettings.getInstance(this)
        val notifOk = NotificationWatcher.isEnabled(this)
        val smsOk = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        binding.switchEnabled.isChecked = settings.serviceEnabled

        binding.tvStatus.text = when {
            !settings.serviceEnabled -> "⏸️ 일시정지"
            !notifOk                 -> "⚠️ 알림 접근 권한 필요"
            !smsOk                   -> "⚠️ SMS 권한 필요"
            else                     -> "🟢 실행 중"
        }

        binding.btnNotifPerm.visibility = if (!notifOk) View.VISIBLE else View.GONE
    }
}

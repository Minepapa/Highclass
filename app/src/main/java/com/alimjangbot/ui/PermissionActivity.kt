package com.alimjangbot.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alimjangbot.data.AppSettings
import com.alimjangbot.databinding.ActivityPermissionBinding
import com.alimjangbot.service.NotificationWatcher

/**
 * 권한 관리 전용 화면.
 */
class PermissionActivity : AppCompatActivity() {

    private lateinit var binding:  ActivityPermissionBinding
    private lateinit var settings: AppSettings

    private val smsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val ok = perms.values.all { it }
        Toast.makeText(this, if (ok) "✅ SMS 권한 허용됨" else "SMS 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding  = ActivityPermissionBinding.inflate(layoutInflater)
        settings = AppSettings.getInstance(this)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "권한 설정"
        }

        binding.btnNotifPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnSmsPerm.setOnClickListener {
            smsLauncher.launch(arrayOf(android.Manifest.permission.SEND_SMS))
        }
        binding.btnBatteryPerm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                ))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshStatus() {
        val notifOk = NotificationWatcher.isEnabled(this)
        val smsOk   = checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val battOk  = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        else true

        binding.tvNotifStatus.text  = if (notifOk) "✅ 허용됨" else "❌ 미허용"
        binding.tvSmsStatus.text    = if (smsOk)   "✅ 허용됨" else "❌ 미허용"
        binding.tvBatteryStatus.text = if (battOk) "✅ 제외됨" else "❌ 최적화 중"

        binding.btnNotifPerm.isEnabled  = !notifOk
        binding.btnSmsPerm.isEnabled    = !smsOk
        binding.btnBatteryPerm.isEnabled = !battOk
    }
}

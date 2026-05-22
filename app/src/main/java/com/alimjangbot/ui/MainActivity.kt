package com.alimjangbot.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alimjangbot.R
import com.alimjangbot.data.AppSettings
import com.alimjangbot.data.SendLogRepository
import com.alimjangbot.databinding.ActivityMainBinding
import com.alimjangbot.service.NotificationWatcher
import com.alimjangbot.service.ScreenCaptureService
import com.alimjangbot.util.ImageUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: AppSettings
    private lateinit var logRepo: SendLogRepository
    private lateinit var logAdapter: LogAdapter

    /** MediaProjection 권한 요청 런처 */
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // 권한 획득 성공 → ScreenCaptureService에 토큰 전달
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STORE_PROJECTION
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            settings.projectionGranted = true
            Toast.makeText(this, "✅ 화면 캡처 권한 허용됨", Toast.LENGTH_SHORT).show()
            refreshPermissionStatus()
        } else {
            Toast.makeText(this, "화면 캡처 권한이 거부됐습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** SMS 권한 요청 런처 */
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "✅ SMS 권한 허용됨", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding  = ActivityMainBinding.inflate(layoutInflater)
        settings = AppSettings.getInstance(this)
        logRepo  = SendLogRepository.getInstance(this)
        setContentView(binding.root)

        setupUi()
        refreshPermissionStatus()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        loadLogs()
    }

    private fun setupUi() {
        // ─── 서비스 토글 ───────────────────────────────────────────
        binding.switchService.isChecked = settings.serviceEnabled
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            settings.serviceEnabled = isChecked
            updateServiceStatusText()
        }

        // ─── 전화번호 저장 ─────────────────────────────────────────
        binding.etRecipient.setText(settings.recipientNumber)
        binding.btnSaveNumber.setOnClickListener {
            val number = binding.etRecipient.text.toString().trim()
            if (number.isBlank()) {
                binding.etRecipient.error = "전화번호를 입력하세요"
                return@setOnClickListener
            }
            settings.recipientNumber = number
            Toast.makeText(this, "저장됐습니다.", Toast.LENGTH_SHORT).show()
        }

        // ─── 딜레이 SeekBar ────────────────────────────────────────
        binding.seekBarDelay.max      = 9          // 1 ~ 10초 (offset +1)
        binding.seekBarDelay.progress = settings.captureDelaySec - 1
        updateDelayLabel(settings.captureDelaySec)

        binding.seekBarDelay.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = progress + 1
                settings.captureDelaySec = sec
                updateDelayLabel(sec)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // ─── 권한 버튼들 ───────────────────────────────────────────
        binding.btnPermNotification.setOnClickListener {
            openNotificationAccessSettings()
        }
        binding.btnPermScreenCapture.setOnClickListener {
            requestProjectionPermission()
        }
        binding.btnPermSms.setOnClickListener {
            requestSmsPermissions()
        }
        binding.btnPermBattery.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        // ─── 테스트 버튼 ───────────────────────────────────────────
        binding.btnTest.setOnClickListener { onTestClicked() }

        // ─── 로그 RecyclerView ─────────────────────────────────────
        logAdapter = LogAdapter(emptyList())
        binding.rvLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }

        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("이력 삭제")
                .setMessage("전송 이력을 모두 삭제하시겠어요?")
                .setPositiveButton("삭제") { _, _ ->
                    logRepo.clearLogs()
                    loadLogs()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // ─── 고급 설정 (패키지명/키워드) ──────────────────────────
        binding.etHighclassPackage.setText(settings.highclassPackage)
        binding.etKeyword.setText(settings.keyword)

        binding.btnSaveAdvanced.setOnClickListener {
            settings.highclassPackage = binding.etHighclassPackage.text.toString().trim()
                .ifBlank { AppSettings.DEFAULT_HIGHCLASS_PACKAGE }
            settings.keyword = binding.etKeyword.text.toString().trim()
                .ifBlank { AppSettings.DEFAULT_KEYWORD }
            Toast.makeText(this, "고급 설정 저장됨", Toast.LENGTH_SHORT).show()
        }

        updateServiceStatusText()
    }

    // ─── 권한 요청 메서드들 ────────────────────────────────────────────

    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun requestProjectionPermission() {
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun requestSmsPermissions() {
        smsPermissionLauncher.launch(arrayOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS
        ))
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "이미 배터리 최적화 제외 상태입니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── 테스트 ────────────────────────────────────────────────────────

    private fun onTestClicked() {
        val recipient = settings.recipientNumber
        if (recipient.isBlank()) {
            Toast.makeText(this, "먼저 전화번호를 입력하고 저장하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!settings.projectionGranted) {
            Toast.makeText(this, "화면 캡처 권한을 먼저 허용하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "3초 후 현재 화면을 캡처하여 전송합니다.", Toast.LENGTH_LONG).show()

        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_CAPTURE_AND_SEND
            putExtra(ScreenCaptureService.EXTRA_DELAY_SEC, 3)
            putExtra(ScreenCaptureService.EXTRA_RECIPIENT, recipient)
            putExtra(ScreenCaptureService.EXTRA_TRIGGER_TITLE, "테스트 알림장")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ─── UI 업데이트 ───────────────────────────────────────────────────

    private fun refreshPermissionStatus() {
        // 알림 접근 권한
        val notifOk = NotificationWatcher.isEnabled(this)
        setPermissionIcon(binding.ivPermNotification, notifOk)
        binding.btnPermNotification.text = if (notifOk) "✅ 허용됨" else "설정하기"
        binding.btnPermNotification.isEnabled = !notifOk

        // 화면 캡처 권한
        val captureOk = settings.projectionGranted
        setPermissionIcon(binding.ivPermScreenCapture, captureOk)
        binding.btnPermScreenCapture.text = if (captureOk) "✅ 허용됨" else "권한 허용"

        // SMS 권한
        val smsOk = checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        setPermissionIcon(binding.ivPermSms, smsOk)
        binding.btnPermSms.text = if (smsOk) "✅ 허용됨" else "권한 허용"
        binding.btnPermSms.isEnabled = !smsOk

        // 배터리 최적화
        val batteryOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        } else true
        setPermissionIcon(binding.ivPermBattery, batteryOk)
        binding.btnPermBattery.text = if (batteryOk) "✅ 제외됨" else "제외 설정"
        binding.btnPermBattery.isEnabled = !batteryOk

        // 전체 준비 상태 표시
        val allReady = notifOk && captureOk && smsOk && settings.isConfigured()
        binding.tvReadyStatus.text = if (allReady) "🟢 자동화 준비 완료!" else "🔴 설정을 완료해주세요"
        binding.tvReadyStatus.setTextColor(
            if (allReady) getColor(R.color.green) else getColor(R.color.red)
        )
    }

    private fun setPermissionIcon(imageView: android.widget.ImageView, granted: Boolean) {
        imageView.setImageResource(
            if (granted) R.drawable.ic_check_circle else R.drawable.ic_error
        )
        imageView.setColorFilter(
            if (granted) getColor(R.color.green) else getColor(R.color.red)
        )
    }

    private fun updateServiceStatusText() {
        val enabled = settings.serviceEnabled
        binding.tvServiceStatus.text = if (enabled) "실행 중" else "중지됨"
        binding.tvServiceStatus.setTextColor(
            if (enabled) getColor(R.color.green) else getColor(R.color.red)
        )
    }

    private fun updateDelayLabel(sec: Int) {
        binding.tvDelayLabel.text = "${sec}초 후 캡처"
    }

    private fun loadLogs() {
        val logs = logRepo.getLogs()
        logAdapter.updateLogs(logs)
        binding.tvNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
    }
}

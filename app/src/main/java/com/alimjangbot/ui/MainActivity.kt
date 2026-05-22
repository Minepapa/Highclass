package com.alimjangbot.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alimjangbot.R
import com.alimjangbot.data.AppSettings
import com.alimjangbot.data.RuleRepository
import com.alimjangbot.data.SendLogRepository
import com.alimjangbot.databinding.ActivityMainBinding
import com.alimjangbot.service.NotificationWatcher
import com.alimjangbot.service.ScreenCaptureService
import com.alimjangbot.ui.rule.RuleEditActivity
import com.alimjangbot.ui.log.LogActivity

/**
 * 메인 화면: 자동화 규칙 목록.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding:    ActivityMainBinding
    private lateinit var settings:   AppSettings
    private lateinit var ruleRepo:   RuleRepository
    private lateinit var ruleAdapter: RuleAdapter

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STORE_PROJECTION
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
            settings.projectionGranted = true
            Toast.makeText(this, "✅ 화면 캡처 권한 허용됨", Toast.LENGTH_SHORT).show()
            refreshStatusBar()
        } else {
            Toast.makeText(this, "화면 캡처 권한이 거부됐습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding   = ActivityMainBinding.inflate(layoutInflater)
        settings  = AppSettings.getInstance(this)
        ruleRepo  = RuleRepository.getInstance(this)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRuleList()
        setupStatusCard()
        setupFab()
    }

    override fun onResume() {
        super.onResume()
        refreshRuleList()
        refreshStatusBar()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_logs     -> { startActivity(Intent(this, LogActivity::class.java)); true }
        R.id.action_settings -> { startActivity(Intent(this, PermissionActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ── 규칙 목록 ─────────────────────────────────────────────────────────

    private fun setupRuleList() {
        ruleAdapter = RuleAdapter(
            onToggle = { rule ->
                ruleRepo.toggleRule(rule.id)
                refreshRuleList()
            },
            onEdit = { rule ->
                val intent = Intent(this, RuleEditActivity::class.java)
                    .putExtra(RuleEditActivity.EXTRA_RULE_ID, rule.id)
                startActivity(intent)
            },
            onDelete = { rule ->
                ruleRepo.deleteRule(rule.id)
                refreshRuleList()
                Toast.makeText(this, "규칙 삭제됨: ${rule.name}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvRules.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ruleAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddRule.setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java))
        }
    }

    private fun refreshRuleList() {
        val rules = ruleRepo.getRules()
        ruleAdapter.submitList(rules)
        binding.tvEmptyRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── 상태 카드 (상단 권한/서비스 상태) ────────────────────────────────

    private fun setupStatusCard() {
        binding.switchService.setOnCheckedChangeListener { _, checked ->
            settings.serviceEnabled = checked
            refreshStatusBar()
        }
        binding.chipNotifPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.chipCapturePerm.setOnClickListener { requestProjection() }
    }

    private fun refreshStatusBar() {
        val notifOk   = NotificationWatcher.isEnabled(this)
        val captureOk = settings.projectionGranted
        val smsPerm   = checkSelfPermission(android.Manifest.permission.SEND_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val allOk     = notifOk && captureOk && smsPerm && settings.serviceEnabled

        binding.switchService.isChecked = settings.serviceEnabled

        // 알림 접근 chip
        binding.chipNotifPerm.text = if (notifOk) "✅ 알림 접근" else "⚠️ 알림 접근"
        binding.chipNotifPerm.isChecked = notifOk

        // 화면 캡처 chip
        binding.chipCapturePerm.text = if (captureOk) "✅ 화면 캡처" else "⚠️ 화면 캡처"
        binding.chipCapturePerm.isChecked = captureOk

        // 전체 상태
        binding.tvGlobalStatus.text = when {
            !settings.serviceEnabled -> "⏸️ 서비스 일시정지"
            allOk -> "🟢 자동화 실행 중"
            else  -> "🔴 권한 설정 필요"
        }
    }

    private fun requestProjection() {
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
}

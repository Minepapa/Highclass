package com.alimjangbot.ui.rule

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.alimjangbot.data.ActionConfig
import com.alimjangbot.data.ActionType
import com.alimjangbot.data.AutomationRule
import com.alimjangbot.data.RuleRepository
import com.alimjangbot.databinding.ActivityRuleEditBinding

/**
 * 자동화 규칙 추가/편집 화면.
 *
 * 입력 항목:
 *  1. 규칙 이름
 *  2. 감지 앱  (앱 선택 또는 직접 입력)
 *  3. 키워드   (비어있으면 모든 알림)
 *  4. 액션 선택
 *  5. 액션 세부 설정 (MMS의 경우 수신번호 + 딜레이)
 */
class RuleEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
    }

    private lateinit var binding:  ActivityRuleEditBinding
    private lateinit var ruleRepo: RuleRepository

    /** 편집 중인 규칙 (null이면 신규 추가) */
    private var editingRule: AutomationRule? = null

    /** 앱 선택 결과 수신 */
    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val pkg   = result.data?.getStringExtra(AppPickerActivity.RESULT_PACKAGE) ?: return@registerForActivityResult
            val label = result.data?.getStringExtra(AppPickerActivity.RESULT_LABEL)   ?: pkg
            binding.etPackageName.setText(pkg)
            binding.tvSelectedApp.text = label
            binding.tvSelectedApp.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding  = ActivityRuleEditBinding.inflate(layoutInflater)
        ruleRepo = RuleRepository.getInstance(this)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = if (intent.hasExtra(EXTRA_RULE_ID)) "규칙 편집" else "새 규칙 추가"
        }

        // 기존 규칙 편집 모드
        intent.getStringExtra(EXTRA_RULE_ID)?.let { id ->
            editingRule = ruleRepo.getRules().firstOrNull { it.id == id }
            editingRule?.let { populateFields(it) }
        }

        setupActionSelector()
        setupAppPicker()
        setupSaveButton()
        setupTestButton()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── 필드 초기화 (편집 모드) ──────────────────────────────────────────

    private fun populateFields(rule: AutomationRule) {
        binding.etRuleName.setText(rule.name)
        binding.etPackageName.setText(rule.packageName)
        binding.etKeyword.setText(rule.keyword)

        if (rule.appLabel.isNotBlank()) {
            binding.tvSelectedApp.text = rule.appLabel
            binding.tvSelectedApp.visibility = View.VISIBLE
        }

        // 액션 선택
        val actionIdx = ActionType.entries.indexOf(rule.actionType)
        binding.rgAction.check(getActionRadioId(actionIdx))
        showActionConfig(rule.actionType)

        // 액션 세부 설정 채우기
        when (rule.actionType) {
            ActionType.CAPTURE_AND_MMS -> {
                val cfg = ActionConfig.parseMmsConfig(rule.actionConfig)
                binding.etRecipient.setText(cfg.recipientNumber)
                binding.seekBarDelay.progress = cfg.captureDelaySec - 1
                updateDelayLabel(cfg.captureDelaySec)
            }
        }
    }

    // ── 액션 선택 라디오 버튼 ────────────────────────────────────────────

    private fun setupActionSelector() {
        // 현재 ActionType 목록으로 라디오 버튼 동적 생성
        ActionType.entries.forEachIndexed { idx, type ->
            val rb = android.widget.RadioButton(this).apply {
                id   = getActionRadioId(idx)
                text = type.label
                tag  = type
                setPadding(0, 12, 0, 12)
            }
            binding.rgAction.addView(rb)
        }

        // 기본 선택: 첫 번째
        binding.rgAction.check(getActionRadioId(0))
        showActionConfig(ActionType.entries[0])

        binding.rgAction.setOnCheckedChangeListener { _, checkedId ->
            val idx = checkedId - ACTION_RADIO_BASE_ID
            if (idx >= 0 && idx < ActionType.entries.size) {
                showActionConfig(ActionType.entries[idx])
            }
        }

        // 딜레이 SeekBar
        binding.seekBarDelay.max = 9
        binding.seekBarDelay.progress = 2   // 기본 3초
        updateDelayLabel(3)
        binding.seekBarDelay.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, u: Boolean) {
                updateDelayLabel(p + 1)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
    }

    /** 선택된 액션에 맞는 설정 폼 표시 */
    private fun showActionConfig(actionType: ActionType) {
        // 현재는 CAPTURE_AND_MMS만 있으므로 해당 폼 항상 표시
        binding.cardMmsConfig.visibility = when (actionType) {
            ActionType.CAPTURE_AND_MMS -> View.VISIBLE
        }
    }

    // ── 앱 선택 ──────────────────────────────────────────────────────────

    private fun setupAppPicker() {
        binding.btnPickApp.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        // 패키지명 직접 입력 시 tvSelectedApp 초기화
        binding.etPackageName.setOnFocusChangeListener { _, focused ->
            if (focused) binding.tvSelectedApp.visibility = View.GONE
        }
    }

    // ── 저장 ─────────────────────────────────────────────────────────────

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val rule = buildRuleFromInputs() ?: return@setOnClickListener
            ruleRepo.saveRule(rule)
            Toast.makeText(this, "규칙 저장됨: ${rule.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun buildRuleFromInputs(): AutomationRule? {
        val name = binding.etRuleName.text.toString().trim()
        if (name.isBlank()) {
            binding.etRuleName.error = "규칙 이름을 입력하세요"
            return null
        }

        val pkg     = binding.etPackageName.text.toString().trim()
        val appLabel = if (binding.tvSelectedApp.visibility == View.VISIBLE)
            binding.tvSelectedApp.text.toString() else ""
        val keyword = binding.etKeyword.text.toString().trim()

        // 선택된 액션 타입
        val checkedIdx = binding.rgAction.indexOfChild(
            binding.rgAction.findViewById(binding.rgAction.checkedRadioButtonId)
        )
        val actionType = if (checkedIdx >= 0) ActionType.entries[checkedIdx]
                         else ActionType.CAPTURE_AND_MMS

        // 액션 config 빌드
        val actionConfig = when (actionType) {
            ActionType.CAPTURE_AND_MMS -> {
                val recipient = binding.etRecipient.text.toString().trim()
                if (recipient.isBlank()) {
                    binding.etRecipient.error = "수신 번호를 입력하세요"
                    return null
                }
                val delay = binding.seekBarDelay.progress + 1
                ActionConfig.buildMmsConfig(recipient, delay)
            }
        }

        return (editingRule ?: AutomationRule(
            name         = name,
            packageName  = pkg,
            appLabel     = appLabel,
            keyword      = keyword,
            actionType   = actionType,
            actionConfig = actionConfig
        )).copy(
            name         = name,
            packageName  = pkg,
            appLabel     = appLabel,
            keyword      = keyword,
            actionType   = actionType,
            actionConfig = actionConfig
        )
    }

    // ── 테스트 ───────────────────────────────────────────────────────────

    private fun setupTestButton() {
        binding.btnTest.setOnClickListener {
            val rule = buildRuleFromInputs() ?: return@setOnClickListener
            when (rule.actionType) {
                ActionType.CAPTURE_AND_MMS -> {
                    val cfg = ActionConfig.parseMmsConfig(rule.actionConfig)
                    Toast.makeText(this, "3초 후 현재 화면 캡처 → ${cfg.recipientNumber}", Toast.LENGTH_LONG).show()
                    val intent = Intent(this,
                        com.alimjangbot.service.ScreenCaptureService::class.java).apply {
                        action = com.alimjangbot.service.ScreenCaptureService.ACTION_CAPTURE_AND_SEND
                        putExtra(com.alimjangbot.service.ScreenCaptureService.EXTRA_DELAY_SEC, 3)
                        putExtra(com.alimjangbot.service.ScreenCaptureService.EXTRA_RECIPIENT, cfg.recipientNumber)
                        putExtra(com.alimjangbot.service.ScreenCaptureService.EXTRA_TRIGGER_TITLE, "테스트")
                        putExtra(com.alimjangbot.service.ScreenCaptureService.EXTRA_RULE_NAME, rule.name)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        startForegroundService(intent) else startService(intent)
                }
            }
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    private fun updateDelayLabel(sec: Int) {
        binding.tvDelayLabel.text = "${sec}초 후 캡처"
    }

    companion object {
        private const val ACTION_RADIO_BASE_ID = 9000
        private fun getActionRadioId(idx: Int) = ACTION_RADIO_BASE_ID + idx
    }
}

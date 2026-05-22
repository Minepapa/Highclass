package com.alimjangbot.ui.rule

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alimjangbot.data.ActionStep
import com.alimjangbot.data.AutomationRule
import com.alimjangbot.data.RuleRepository
import com.alimjangbot.data.StepType
import com.alimjangbot.databinding.ActivityRuleEditBinding
import com.alimjangbot.service.ScreenCaptureService
import org.json.JSONArray

/**
 * 규칙 추가 / 편집 화면.
 *
 * 상단: 규칙 이름, 감지 앱, 키워드
 * 하단: 파이프라인 빌더 (단계 목록 + 추가 버튼)
 */
class RuleEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
    }

    private lateinit var binding:      ActivityRuleEditBinding
    private lateinit var ruleRepo:     RuleRepository
    private lateinit var stepAdapter:  StepAdapter
    private var editingRule: AutomationRule? = null

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val pkg   = result.data?.getStringExtra(AppPickerActivity.RESULT_PACKAGE) ?: return@registerForActivityResult
            val label = result.data?.getStringExtra(AppPickerActivity.RESULT_LABEL) ?: pkg
            binding.etPackageName.setText(pkg)
            binding.tvSelectedApp.text       = label
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

        // 기존 규칙 편집
        intent.getStringExtra(EXTRA_RULE_ID)?.let { id ->
            editingRule = ruleRepo.getRules().firstOrNull { it.id == id }
        }

        setupPipelineList()
        setupBasicFields()
        setupButtons()
        editingRule?.let { populateFields(it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── 파이프라인 빌더 ────────────────────────────────────────────────────

    private fun setupPipelineList() {
        val initialSteps = editingRule?.steps?.toMutableList() ?: mutableListOf()
        stepAdapter = StepAdapter(initialSteps) { refreshPipelineStatus() }

        binding.rvSteps.apply {
            layoutManager = LinearLayoutManager(this@RuleEditActivity)
            adapter = stepAdapter
            isNestedScrollingEnabled = false
        }

        binding.btnAddStep.setOnClickListener { showAddStepDialog() }
        refreshPipelineStatus()
    }

    /** 추가 가능한 단계 선택 다이얼로그 */
    private fun showAddStepDialog() {
        val types  = StepType.entries
        val labels = types.map { "${it.icon} ${it.label}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("단계 추가")
            .setItems(labels) { _, idx ->
                val newStep = ActionStep(type = types[idx])
                stepAdapter.addStep(newStep)
                // 방금 추가한 아이템으로 스크롤
                binding.rvSteps.smoothScrollToPosition(stepAdapter.itemCount - 1)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 파이프라인 상태 요약 텍스트 갱신 */
    private fun refreshPipelineStatus() {
        val steps = stepAdapter.getSteps()
        binding.tvPipelineSummary.text = if (steps.isEmpty()) {
            "단계를 추가하세요"
        } else {
            steps.joinToString(" → ") { "${it.type.icon} ${it.type.label}" }
        }
        binding.tvEmptySteps.visibility = if (steps.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── 기본 필드 설정 ─────────────────────────────────────────────────────

    private fun setupBasicFields() {
        binding.btnPickApp.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }
        binding.etPackageName.setOnFocusChangeListener { _, focused ->
            if (focused) binding.tvSelectedApp.visibility = View.GONE
        }
    }

    private fun populateFields(rule: AutomationRule) {
        binding.etRuleName.setText(rule.name)
        binding.etPackageName.setText(rule.packageName)
        binding.etKeyword.setText(rule.keyword)
        if (rule.appLabel.isNotBlank()) {
            binding.tvSelectedApp.text       = rule.appLabel
            binding.tvSelectedApp.visibility = View.VISIBLE
        }
    }

    // ── 저장 / 테스트 버튼 ────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            buildRule()?.let { rule ->
                ruleRepo.saveRule(rule)
                Toast.makeText(this, "규칙 저장: ${rule.name}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.btnTest.setOnClickListener {
            val steps = stepAdapter.getSteps()
            if (steps.isEmpty()) {
                Toast.makeText(this, "단계를 먼저 추가하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "파이프라인 테스트를 시작합니다.", Toast.LENGTH_SHORT).show()

            val stepsJson = JSONArray().also { arr ->
                steps.forEach { arr.put(it.toJson()) }
            }.toString()

            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_RUN_PIPELINE
                putExtra(ScreenCaptureService.EXTRA_STEPS_JSON,  stepsJson)
                putExtra(ScreenCaptureService.EXTRA_NOTIF_TITLE, "테스트 알림")
                putExtra(ScreenCaptureService.EXTRA_NOTIF_TEXT,  "알림 자동화 테스트 메시지입니다.")
                putExtra(ScreenCaptureService.EXTRA_RULE_NAME,   "테스트")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }

    private fun buildRule(): AutomationRule? {
        val name = binding.etRuleName.text.toString().trim()
        if (name.isBlank()) { binding.etRuleName.error = "규칙 이름 필요"; return null }

        val steps = stepAdapter.getSteps()
        if (steps.isEmpty()) {
            Toast.makeText(this, "단계를 하나 이상 추가하세요.", Toast.LENGTH_SHORT).show()
            return null
        }

        // 수신번호 필요한 단계에서 번호 검증
        steps.filter { it.type.hasRecipient }.forEach { step ->
            if (step.recipient.isBlank()) {
                Toast.makeText(this,
                    "${step.type.icon} ${step.type.label}: 수신 번호를 입력하세요.",
                    Toast.LENGTH_SHORT).show()
                return null
            }
        }

        val pkg      = binding.etPackageName.text.toString().trim()
        val appLabel = if (binding.tvSelectedApp.visibility == View.VISIBLE)
            binding.tvSelectedApp.text.toString() else ""
        val keyword  = binding.etKeyword.text.toString().trim()

        val base = editingRule ?: AutomationRule(
            name = name, packageName = pkg, appLabel = appLabel,
            keyword = keyword, steps = steps
        )
        return base.copy(
            name = name, packageName = pkg, appLabel = appLabel,
            keyword = keyword, steps = steps
        )
    }
}

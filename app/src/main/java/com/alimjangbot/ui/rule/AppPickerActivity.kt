package com.alimjangbot.ui.rule

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alimjangbot.databinding.ActivityAppPickerBinding

/**
 * 설치된 앱 목록에서 감지할 앱을 선택하는 화면.
 * 상단 검색창으로 앱 이름/패키지명 필터링 가능.
 */
class AppPickerActivity : AppCompatActivity() {

    companion object {
        const val RESULT_PACKAGE = "result_package"
        const val RESULT_LABEL   = "result_label"
    }

    data class AppItem(
        val label:       String,
        val packageName: String,
        val icon:        android.graphics.drawable.Drawable?
    )

    private lateinit var binding: ActivityAppPickerBinding
    private var allApps:      List<AppItem> = emptyList()
    private var filteredApps: List<AppItem> = emptyList()
    private lateinit var adapter: AppListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "앱 선택"
        }

        setupRecyclerView()
        setupSearch()
        setupManualInput()
        loadInstalledApps()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { setResult(RESULT_CANCELED); finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter { appItem ->
            setResult(RESULT_OK, Intent().apply {
                putExtra(RESULT_PACKAGE, appItem.packageName)
                putExtra(RESULT_LABEL,   appItem.label)
            })
            finish()
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterApps(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun setupManualInput() {
        binding.btnManualInput.setOnClickListener {
            val input = EditText(this).apply {
                hint = "예: com.iscreammedia.app.hiclass.android"
                inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(this)
                .setTitle("패키지명 직접 입력")
                .setMessage("앱의 패키지명을 입력하세요")
                .setView(input)
                .setPositiveButton("확인") { _, _ ->
                    val pkg = input.text.toString().trim()
                    if (pkg.isNotEmpty()) {
                        val pm = packageManager
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        }.getOrDefault(pkg)
                        setResult(RESULT_OK, Intent().apply {
                            putExtra(RESULT_PACKAGE, pkg)
                            putExtra(RESULT_LABEL, label)
                        })
                        finish()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun loadInstalledApps() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }  // 실행 가능한 앱만
                .map { info ->
                    AppItem(
                        label       = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        icon        = runCatching { pm.getApplicationIcon(info.packageName) }.getOrNull()
                    )
                }
                .sortedBy { it.label }

            runOnUiThread {
                allApps      = apps
                filteredApps = apps
                adapter.submitList(filteredApps)
                binding.progressBar.visibility = android.view.View.GONE
            }
        }.start()
    }

    private fun filterApps(query: String) {
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.lowercase()
            allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
        adapter.submitList(filteredApps)
    }
}

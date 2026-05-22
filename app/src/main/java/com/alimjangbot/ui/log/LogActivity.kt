package com.alimjangbot.ui.log

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.alimjangbot.data.SendLogRepository
import com.alimjangbot.databinding.ActivityLogBinding
import com.alimjangbot.ui.LogAdapter

class LogActivity : AppCompatActivity() {

    private lateinit var binding:  ActivityLogBinding
    private lateinit var logRepo:  SendLogRepository
    private lateinit var adapter:  LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding  = ActivityLogBinding.inflate(layoutInflater)
        logRepo  = SendLogRepository.getInstance(this)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "전송 이력"
        }

        adapter = LogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter

        binding.btnClearLogs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("이력 삭제")
                .setMessage("전송 이력을 모두 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    logRepo.clearLogs(); loadLogs()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        loadLogs()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun loadLogs() {
        val logs = logRepo.getLogs()
        adapter.updateLogs(logs)
        binding.tvNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
    }
}

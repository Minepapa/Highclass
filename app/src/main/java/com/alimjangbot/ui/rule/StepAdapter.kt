package com.alimjangbot.ui.rule

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.alimjangbot.data.ActionStep
import com.alimjangbot.data.StepType
import com.alimjangbot.databinding.ItemActionStepBinding

/**
 * 파이프라인 단계 목록 어댑터.
 * 각 카드에서 순서 변경(↑↓), 설정 편집, 삭제 가능.
 */
class StepAdapter(
    private val steps: MutableList<ActionStep>,
    private val onChange: () -> Unit          // 변경 시 부모에 알림
) : RecyclerView.Adapter<StepAdapter.StepViewHolder>() {

    inner class StepViewHolder(val b: ItemActionStepBinding) : RecyclerView.ViewHolder(b.root) {

        // TextWatcher 재사용 방지를 위해 참조 보관
        private var recipientWatcher: TextWatcher? = null

        fun bind(step: ActionStep, position: Int) {
            val total = itemCount

            // ── 헤더 ──────────────────────────────────────────
            b.tvStepNumber.text = "${position + 1}"
            b.tvStepIcon.text   = step.type.icon
            b.tvStepLabel.text  = step.type.label
            b.tvStepDesc.text   = step.type.description

            // ── 이동 버튼 ─────────────────────────────────────
            b.btnMoveUp.isEnabled   = position > 0
            b.btnMoveDown.isEnabled = position < total - 1
            b.btnMoveUp.alpha   = if (position > 0)          1f else 0.3f
            b.btnMoveDown.alpha = if (position < total - 1)  1f else 0.3f

            b.btnMoveUp.setOnClickListener {
                if (position > 0) { swap(position, position - 1); onChange() }
            }
            b.btnMoveDown.setOnClickListener {
                if (position < steps.size - 1) { swap(position, position + 1); onChange() }
            }

            // ── 삭제 버튼 ─────────────────────────────────────
            b.btnDeleteStep.setOnClickListener {
                steps.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, steps.size)
                onChange()
            }

            // ── 설정 폼 표시/숨김 ─────────────────────────────
            b.layoutRecipient.visibility    = if (step.type.hasRecipient)    View.VISIBLE else View.GONE
            b.layoutCaptureDelay.visibility = if (step.type.hasCaptureDelay) View.VISIBLE else View.GONE

            // ── 수신 번호 입력 ────────────────────────────────
            if (step.type.hasRecipient) {
                recipientWatcher?.let { b.etRecipient.removeTextChangedListener(it) }
                b.etRecipient.setText(step.recipient)
                recipientWatcher = object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        steps[position] = steps[position].copy(recipient = s?.toString()?.trim() ?: "")
                    }
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                }
                b.etRecipient.addTextChangedListener(recipientWatcher)
            }

            // ── 캡처 딜레이 SeekBar ───────────────────────────
            if (step.type.hasCaptureDelay) {
                b.seekBarDelay.max      = 9
                b.seekBarDelay.progress = step.captureDelaySec - 1
                updateDelayLabel(step.captureDelaySec)

                b.seekBarDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                        val sec = p + 1
                        steps[position] = steps[position].copy(captureDelaySec = sec)
                        updateDelayLabel(sec)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
        }

        private fun updateDelayLabel(sec: Int) {
            b.tvDelayLabel.text = "${sec}초 후 캡처"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        StepViewHolder(
            ItemActionStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) =
        holder.bind(steps[position], position)

    override fun getItemCount() = steps.size

    fun addStep(step: ActionStep) {
        steps.add(step)
        notifyItemInserted(steps.size - 1)
        onChange()
    }

    fun getSteps(): List<ActionStep> = steps.toList()

    private fun swap(from: Int, to: Int) {
        val tmp = steps[from]
        steps[from] = steps[to]
        steps[to] = tmp
        notifyItemMoved(from, to)
        // 번호 갱신
        notifyItemChanged(from)
        notifyItemChanged(to)
    }
}

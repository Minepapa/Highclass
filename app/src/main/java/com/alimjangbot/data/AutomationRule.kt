package com.alimjangbot.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ─────────────────────────────────────────────────────────────
// 단계(Step) 타입 정의
// ─────────────────────────────────────────────────────────────

enum class StepType(
    val icon:        String,
    val label:       String,
    val description: String,
    val hasRecipient:    Boolean = false,
    val hasCaptureDelay: Boolean = false
) {
    /** 알림 클릭 후 N초 후 화면을 캡처 → bitmap 생성 */
    CAPTURE_SCREEN(
        icon        = "📸",
        label       = "화면 캡처",
        description = "알림을 클릭한 후 지정 시간이 지나면 현재 화면을 캡처합니다",
        hasCaptureDelay = true
    ),

    /** 알림 본문 텍스트를 파이프라인에 주입 (별도 설정 없음) */
    EXTRACT_TEXT(
        icon        = "📋",
        label       = "알림 텍스트 가져오기",
        description = "알림의 제목·본문 텍스트를 다음 단계로 전달합니다"
    ),

    /** bitmap이 있으면 이미지 MMS, 없으면 텍스트를 이미지로 렌더링해서 MMS */
    SEND_MMS(
        icon        = "📱",
        label       = "MMS 전송 (이미지)",
        description = "캡처 이미지 또는 텍스트를 이미지로 변환하여 MMS 전송합니다",
        hasRecipient = true
    ),

    /** 텍스트를 SMS로 전송 */
    SEND_SMS(
        icon        = "💬",
        label       = "문자(SMS) 전송",
        description = "알림 본문 텍스트를 SMS 문자로 전송합니다",
        hasRecipient = true
    ),
}

// ─────────────────────────────────────────────────────────────
// 단계 데이터 모델
// ─────────────────────────────────────────────────────────────

data class ActionStep(
    val id:             String    = UUID.randomUUID().toString(),
    val type:           StepType,
    val recipient:      String    = "",   // SEND_MMS / SEND_SMS
    val captureDelaySec: Int      = 3,    // CAPTURE_SCREEN
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id",          id)
        put("type",        type.name)
        put("recipient",   recipient)
        put("delay_sec",   captureDelaySec)
    }

    companion object {
        fun fromJson(obj: JSONObject): ActionStep = ActionStep(
            id              = obj.optString("id", UUID.randomUUID().toString()),
            type            = StepType.valueOf(obj.getString("type")),
            recipient       = obj.optString("recipient", ""),
            captureDelaySec = obj.optInt("delay_sec", 3)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 규칙 데이터 모델
// ─────────────────────────────────────────────────────────────

data class AutomationRule(
    val id:          String = UUID.randomUUID().toString(),
    val name:        String,
    val packageName: String,
    val appLabel:    String,
    val keyword:     String,
    val steps:       List<ActionStep>,
    val enabled:     Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id",          id)
        put("name",        name)
        put("packageName", packageName)
        put("appLabel",    appLabel)
        put("keyword",     keyword)
        put("enabled",     enabled)
        put("steps", JSONArray().also { arr -> steps.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): AutomationRule {
            // ── 구버전 마이그레이션 (actionType + actionConfig → steps) ──
            val steps: List<ActionStep> = if (obj.has("steps")) {
                val arr = obj.getJSONArray("steps")
                (0 until arr.length()).mapNotNull {
                    runCatching { ActionStep.fromJson(arr.getJSONObject(it)) }.getOrNull()
                }
            } else {
                migrateOldFormat(obj)
            }

            return AutomationRule(
                id          = obj.getString("id"),
                name        = obj.getString("name"),
                packageName = obj.optString("packageName", ""),
                appLabel    = obj.optString("appLabel", ""),
                keyword     = obj.optString("keyword", ""),
                steps       = steps,
                enabled     = obj.optBoolean("enabled", true)
            )
        }

        /** 구버전 actionType+actionConfig → steps 변환 */
        private fun migrateOldFormat(obj: JSONObject): List<ActionStep> {
            val oldType   = obj.optString("actionType", "")
            val oldConfig = runCatching { JSONObject(obj.optString("actionConfig", "{}")) }
                .getOrElse { JSONObject() }
            return when (oldType) {
                "CAPTURE_AND_MMS" -> listOf(
                    ActionStep(
                        type            = StepType.CAPTURE_SCREEN,
                        captureDelaySec = oldConfig.optInt("delay_sec", 3)
                    ),
                    ActionStep(
                        type      = StepType.SEND_MMS,
                        recipient = oldConfig.optString("recipient", "")
                    )
                )
                else -> emptyList()
            }
        }
    }

    /** 파이프라인에 캡처 단계가 포함되어 있는지 (MediaProjection 권한 필요 여부) */
    fun requiresScreenCapture(): Boolean = steps.any { it.type == StepType.CAPTURE_SCREEN }
}

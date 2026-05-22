package com.alimjangbot.data

import org.json.JSONObject
import java.util.UUID

/**
 * 하나의 자동화 규칙.
 *
 * "어떤 앱"에서 "어떤 키워드"가 오면 "어떤 액션"을 한다.
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),

    /** 사용자 정의 규칙 이름 (예: "딸 알림장") */
    val name: String,

    /** 감지할 앱 패키지명 (비어있으면 모든 앱) */
    val packageName: String,

    /** UI 표시용 앱 이름 */
    val appLabel: String,

    /** 알림 본문에서 찾을 키워드 (비어있으면 해당 앱의 모든 알림) */
    val keyword: String,

    /** 실행할 액션 종류 */
    val actionType: ActionType,

    /** 액션 세부 설정 (JSON 문자열로 저장) */
    val actionConfig: String = "{}",

    /** 규칙 활성화 여부 */
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id",          id)
        put("name",        name)
        put("packageName", packageName)
        put("appLabel",    appLabel)
        put("keyword",     keyword)
        put("actionType",  actionType.name)
        put("actionConfig", actionConfig)
        put("enabled",     enabled)
    }

    companion object {
        fun fromJson(obj: JSONObject): AutomationRule = AutomationRule(
            id           = obj.getString("id"),
            name         = obj.getString("name"),
            packageName  = obj.optString("packageName", ""),
            appLabel     = obj.optString("appLabel", ""),
            keyword      = obj.optString("keyword", ""),
            actionType   = ActionType.valueOf(obj.getString("actionType")),
            actionConfig = obj.optString("actionConfig", "{}"),
            enabled      = obj.optBoolean("enabled", true)
        )
    }
}

/**
 * 알림 수신 시 실행할 액션 종류.
 * 향후 새 액션 추가 시 여기에 enum 값 추가 + ActionConfigHelper에 UI 추가.
 */
enum class ActionType(
    val label: String,
    val description: String
) {
    /** 화면을 캡처하여 MMS(문자)로 전송 */
    CAPTURE_AND_MMS(
        label       = "📸 캡처 → MMS 전송",
        description = "알림 클릭 후 화면을 캡처해서 지정 번호로 MMS 전송"
    ),

    // ── 향후 추가 가능한 액션들 ──────────────────────────────
    // FORWARD_TEXT(
    //     label       = "💬 알림 텍스트 → 문자 전달",
    //     description = "캡처 없이 알림 본문 텍스트만 문자로 전달"
    // ),
    // SAVE_SCREENSHOT(
    //     label       = "💾 캡처 → 갤러리 저장",
    //     description = "화면을 캡처하여 갤러리에 저장"
    // ),
    // WEBHOOK(
    //     label       = "🌐 웹훅 호출",
    //     description = "지정 URL로 HTTP POST 요청"
    // ),
}

/**
 * ActionType별 config JSON 파싱/생성 헬퍼.
 */
object ActionConfig {

    // ── CAPTURE_AND_MMS ──────────────────────────────────────

    data class MmsConfig(
        val recipientNumber: String,
        val captureDelaySec: Int = 3
    )

    fun buildMmsConfig(recipient: String, delaySec: Int): String =
        JSONObject().apply {
            put("recipient", recipient)
            put("delay_sec", delaySec)
        }.toString()

    fun parseMmsConfig(json: String): MmsConfig {
        val obj = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        return MmsConfig(
            recipientNumber = obj.optString("recipient", ""),
            captureDelaySec = obj.optInt("delay_sec", 3)
        )
    }
}

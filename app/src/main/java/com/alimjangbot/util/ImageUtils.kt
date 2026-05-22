package com.alimjangbot.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 이미지 처리 유틸리티.
 *
 * 스크린샷 캡처가 불가능한 경우(하이클래스 보안 정책 등)
 * 알림 텍스트 내용을 이미지로 렌더링하는 폴백 기능 제공.
 */
object ImageUtils {

    /**
     * 텍스트 내용을 이미지로 렌더링 (스크린샷 불가 시 폴백).
     *
     * @param title   알림 제목
     * @param content 알림 본문 텍스트
     * @return 렌더링된 Bitmap
     */
    fun renderTextAsBitmap(title: String, content: String): Bitmap {
        val width  = 1080
        val padding = 60f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = Color.parseColor("#1A1A2E")
            textSize  = 52f
            typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#333333")
            textSize = 42f
            isAntiAlias = true
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#888888")
            textSize = 34f
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#4A90D9")
        }

        // 텍스트 줄바꿈 처리
        val titleLines  = wrapText(title, titlePaint, width - padding * 2)
        val bodyLines   = wrapText(content, bodyPaint, width - padding * 2)

        // 전체 높이 계산
        val titleHeight = titleLines.size * 64f
        val bodyHeight  = bodyLines.size * 54f
        val totalHeight = (padding * 4 + 80f + titleHeight + 40f + bodyHeight + 60f).toInt()
            .coerceAtLeast(400)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 배경
        canvas.drawColor(Color.WHITE)

        // 상단 헤더 바
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, headerPaint)

        var y = padding + 12f

        // 헤더 텍스트
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color    = Color.parseColor("#4A90D9")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("📚 하이클래스 알림장", padding, y + 36f, headerTextPaint)
        y += 70f

        // 구분선
        val dividerPaint = Paint().apply {
            color       = Color.parseColor("#E0E0E0")
            strokeWidth = 2f
        }
        canvas.drawLine(padding, y, width - padding, y, dividerPaint)
        y += 30f

        // 제목
        titleLines.forEach { line ->
            canvas.drawText(line, padding, y + titlePaint.textSize, titlePaint)
            y += 64f
        }
        y += 20f

        // 본문
        bodyLines.forEach { line ->
            canvas.drawText(line, padding, y + bodyPaint.textSize, bodyPaint)
            y += 54f
        }

        y += 30f
        canvas.drawLine(padding, y, width - padding, y, dividerPaint)
        y += 20f

        // 날짜/시간
        val dateStr = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREA).format(Date())
        canvas.drawText("수신: $dateStr", padding, y + timePaint.textSize, timePaint)

        return bitmap
    }

    /** 텍스트를 주어진 너비에 맞게 줄바꿈 */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val words = text.replace("\n", " \n ").split(" ")
        var currentLine = StringBuilder()

        words.forEach { word ->
            if (word == "\n") {
                lines.add(currentLine.toString().trim())
                currentLine = StringBuilder()
                return@forEach
            }
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString().trim())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString().trim())
        return lines.ifEmpty { listOf("") }
    }

    /** MMS 전송 크기 제한에 맞게 Bitmap 압축 (일반적으로 300KB 이하) */
    fun compressForMms(bitmap: Bitmap, maxSizeKb: Int = 300): ByteArray {
        var quality = 85
        var result: ByteArray
        do {
            val baos = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            result = baos.toByteArray()
            quality -= 10
        } while (result.size > maxSizeKb * 1024 && quality > 10)
        return result
    }
}

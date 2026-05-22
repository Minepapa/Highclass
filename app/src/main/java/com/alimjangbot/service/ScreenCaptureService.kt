package com.alimjangbot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.alimjangbot.AlimjangApplication
import com.alimjangbot.R
import com.alimjangbot.data.ActionStep
import com.alimjangbot.data.LogEntry
import com.alimjangbot.data.SendLogRepository
import com.alimjangbot.data.StepType
import com.alimjangbot.ui.MainActivity
import com.alimjangbot.util.ImageUtils
import org.json.JSONArray

/**
 * 파이프라인 단계를 순서대로 실행하는 포그라운드 서비스.
 *
 * 단계 실행 흐름:
 *  CAPTURE_SCREEN  → bitmap 생성 (MediaProjection)
 *  EXTRACT_TEXT    → 알림 텍스트 context에 이미 있음 (no-op)
 *  SEND_MMS        → bitmap 있으면 이미지 MMS, 없으면 텍스트→이미지 렌더링 후 MMS
 *  SEND_SMS        → 텍스트 SMS 전송
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"

        const val ACTION_RUN_PIPELINE   = "com.alimjangbot.RUN_PIPELINE"
        const val ACTION_STORE_PROJECTION = "com.alimjangbot.STORE_PROJECTION"

        // RUN_PIPELINE extras
        const val EXTRA_STEPS_JSON    = "steps_json"
        const val EXTRA_NOTIF_TITLE   = "notif_title"
        const val EXTRA_NOTIF_TEXT    = "notif_text"
        const val EXTRA_RULE_NAME     = "rule_name"

        // STORE_PROJECTION extras
        const val EXTRA_RESULT_CODE   = "result_code"
        const val EXTRA_RESULT_DATA   = "result_data"

        private const val NOTIF_ID    = 1001

        @Volatile var projectionResultCode: Int     = 0
        @Volatile var projectionResultData: Intent? = null
    }

    private lateinit var logRepo: SendLogRepository

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay:  VirtualDisplay?  = null
    private var imageReader:     ImageReader?     = null

    override fun onCreate() {
        super.onCreate()
        logRepo = SendLogRepository.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildForegroundNotif("실행 중..."))

        when (intent?.action) {

            ACTION_STORE_PROJECTION -> {
                projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                projectionResultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                Log.d(TAG, "MediaProjection 토큰 저장 완료")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            ACTION_RUN_PIPELINE -> {
                val stepsJson  = intent.getStringExtra(EXTRA_STEPS_JSON) ?: "[]"
                val notifTitle = intent.getStringExtra(EXTRA_NOTIF_TITLE) ?: ""
                val notifText  = intent.getStringExtra(EXTRA_NOTIF_TEXT)  ?: ""
                val ruleName   = intent.getStringExtra(EXTRA_RULE_NAME)   ?: ""

                val steps = parseSteps(stepsJson)
                if (steps.isEmpty()) {
                    Log.w(TAG, "[$ruleName] 단계 없음")
                    stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId); return START_NOT_STICKY
                }

                // 배경 스레드에서 파이프라인 실행
                Thread {
                    runPipeline(steps, notifTitle, notifText, ruleName, startId)
                }.start()
            }

            else -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    // ── 파이프라인 실행 ───────────────────────────────────────────────────

    private fun runPipeline(
        steps:      List<ActionStep>,
        notifTitle: String,
        notifText:  String,
        ruleName:   String,
        startId:    Int
    ) {
        var capturedBitmap: Bitmap? = null
        var hasError = false

        for ((idx, step) in steps.withIndex()) {
            val stepLabel = "${idx + 1}. ${step.type.icon} ${step.type.label}"
            Log.i(TAG, "[$ruleName] 단계 실행: $stepLabel")

            val ok = try {
                executeStep(step, notifTitle, notifText, ruleName) { bmp ->
                    capturedBitmap = bmp   // CAPTURE_SCREEN이 bitmap을 callback으로 넘김
                }
                // SEND_MMS/SEND_SMS는 현재 capturedBitmap 참조
                executeSendStep(step, notifTitle, notifText, capturedBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "[$ruleName] 단계 오류: ${e.message}", e)
                logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] $stepLabel 오류: ${e.message}")
                false
            }

            if (!ok && step.type in listOf(StepType.SEND_MMS, StepType.SEND_SMS)) {
                hasError = true
            }
        }

        if (!hasError) {
            showResultNotif(true, "[$ruleName] 파이프라인 완료")
            logRepo.addLog(LogEntry.Status.SUCCESS, "[$ruleName] 모든 단계 완료")
        }

        stopAndClean(startId)
    }

    /**
     * 수집(INPUT) 단계 실행.
     * CAPTURE_SCREEN: 딜레이 후 캡처, callback으로 bitmap 전달
     * EXTRACT_TEXT: no-op (알림 텍스트는 이미 context에 있음)
     * SEND_*: 여기서는 처리하지 않음 (executeSendStep에서 처리)
     */
    private fun executeStep(
        step:       ActionStep,
        notifTitle: String,
        notifText:  String,
        ruleName:   String,
        onBitmap:   (Bitmap) -> Unit
    ): Boolean {
        return when (step.type) {
            StepType.CAPTURE_SCREEN -> {
                if (projectionResultData == null) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 화면 캡처 권한 없음")
                    showResultNotif(false, "화면 캡처 권한이 없습니다. 앱에서 설정하세요.")
                    return false
                }
                Thread.sleep(step.captureDelaySec * 1000L)
                val bmp = captureScreen()
                if (bmp != null) {
                    onBitmap(bmp)
                    logRepo.addLog(LogEntry.Status.SUCCESS, "[$ruleName] 화면 캡처 완료")
                    true
                } else {
                    logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 화면 캡처 실패")
                    false
                }
            }
            StepType.EXTRACT_TEXT -> {
                // 알림 텍스트는 이미 파라미터로 전달됨 — 별도 작업 없음
                Log.d(TAG, "[$ruleName] 텍스트 준비: $notifTitle / $notifText")
                true
            }
            StepType.SEND_MMS, StepType.SEND_SMS -> true  // executeSendStep에서 처리
        }
    }

    /**
     * 전송(OUTPUT) 단계 실행.
     */
    private fun executeSendStep(
        step:           ActionStep,
        notifTitle:     String,
        notifText:      String,
        capturedBitmap: Bitmap?
    ): Boolean {
        return when (step.type) {
            StepType.SEND_MMS -> {
                if (step.recipient.isBlank()) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "MMS 수신 번호 없음")
                    return false
                }
                // bitmap 없으면 텍스트를 이미지로 렌더링
                val bitmap = capturedBitmap
                    ?: ImageUtils.renderTextAsBitmap(notifTitle, notifText)
                val ok = MmsDispatcher.send(this, step.recipient, bitmap, notifTitle)
                if (ok) logRepo.addLog(LogEntry.Status.SUCCESS, "MMS 전송 완료 → ${step.recipient}")
                else    logRepo.addLog(LogEntry.Status.FAILURE, "MMS 전송 실패")
                if (!ok) showResultNotif(false, "MMS 전송 실패. 번호를 확인하세요.")
                ok
            }
            StepType.SEND_SMS -> {
                if (step.recipient.isBlank()) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "SMS 수신 번호 없음")
                    return false
                }
                val body = buildSmsBody(notifTitle, notifText)
                val ok = SmsDispatcher.send(this, step.recipient, body)
                if (ok) logRepo.addLog(LogEntry.Status.SUCCESS, "SMS 전송 완료 → ${step.recipient}")
                else    logRepo.addLog(LogEntry.Status.FAILURE, "SMS 전송 실패")
                ok
            }
            else -> true
        }
    }

    private fun buildSmsBody(title: String, text: String): String = buildString {
        if (title.isNotBlank()) { append(title); append("\n") }
        if (text.isNotBlank())  append(text)
    }.trim()

    // ── 화면 캡처 (MediaProjection) ───────────────────────────────────────

    private fun captureScreen(): Bitmap? {
        val metrics = getDisplayMetrics()
        val w   = metrics.widthPixels
        val h   = metrics.heightPixels
        val dpi = metrics.densityDpi

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(projectionResultCode, projectionResultData!!)
        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "Capture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        Thread.sleep(500)

        val image = imageReader!!.acquireLatestImage() ?: return null
        return try {
            val plane      = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * w
            val bmp = Bitmap.createBitmap(
                w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            Bitmap.createBitmap(bmp, 0, 0, w, h)
        } finally {
            image.close()
            virtualDisplay?.release()
            mediaProjection?.stop()
            virtualDisplay  = null
            mediaProjection = null
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                it.widthPixels  = b.width()
                it.heightPixels = b.height()
                it.densityDpi   = resources.displayMetrics.densityDpi
            } else {
                @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(it)
            }
        }
    }

    // ── 알림 ────────────────────────────────────────────────────────────

    private fun buildForegroundNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_FOREGROUND)
            .setContentTitle("알림 자동화").setContentText(text)
            .setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).build()
    }

    private fun showResultNotif(success: Boolean, msg: String) {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_STATUS)
            .setContentTitle(if (success) "✅ 완료" else "❌ 실패")
            .setContentText(msg).setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi).setAutoCancel(true).build()
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NOTIF_ID + 1, notif)
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    private fun parseSteps(json: String): List<ActionStep> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull {
            runCatching { ActionStep.fromJson(arr.getJSONObject(it)) }.getOrNull()
        }
    } catch (e: Exception) { emptyList() }

    private fun stopAndClean(startId: Int) {
        virtualDisplay?.release(); mediaProjection?.stop(); imageReader?.close()
        virtualDisplay = null; mediaProjection = null; imageReader = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        virtualDisplay?.release(); mediaProjection?.stop(); imageReader?.close()
        super.onDestroy()
    }
}

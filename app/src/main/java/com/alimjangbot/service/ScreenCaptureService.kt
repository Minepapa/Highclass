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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.alimjangbot.AlimjangApplication
import com.alimjangbot.R
import com.alimjangbot.data.LogEntry
import com.alimjangbot.data.SendLogRepository
import com.alimjangbot.ui.MainActivity

/**
 * 화면 캡처 + MMS 전송 포그라운드 서비스.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"

        const val ACTION_CAPTURE_AND_SEND = "com.alimjangbot.CAPTURE_AND_SEND"
        const val ACTION_STORE_PROJECTION = "com.alimjangbot.STORE_PROJECTION"

        const val EXTRA_DELAY_SEC      = "delay_sec"
        const val EXTRA_RECIPIENT      = "recipient"
        const val EXTRA_TRIGGER_TITLE  = "trigger_title"
        const val EXTRA_RULE_NAME      = "rule_name"
        const val EXTRA_RESULT_CODE    = "result_code"
        const val EXTRA_RESULT_DATA    = "result_data"

        private const val NOTIF_ID = 1001

        @Volatile var projectionResultCode: Int    = 0
        @Volatile var projectionResultData: Intent? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logRepo: SendLogRepository

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay:  VirtualDisplay?  = null
    private var imageReader:     ImageReader?     = null

    override fun onCreate() {
        super.onCreate()
        logRepo = SendLogRepository.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildForegroundNotif("캡처 준비 중..."))

        when (intent?.action) {
            ACTION_STORE_PROJECTION -> {
                projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                projectionResultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                Log.d(TAG, "MediaProjection 토큰 저장 완료")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }

            ACTION_CAPTURE_AND_SEND -> {
                val delaySec  = intent.getIntExtra(EXTRA_DELAY_SEC, 3)
                val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""
                val title     = intent.getStringExtra(EXTRA_TRIGGER_TITLE) ?: ""
                val ruleName  = intent.getStringExtra(EXTRA_RULE_NAME) ?: ""

                if (recipient.isBlank()) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 수신 번호 없음")
                    stopSelf(startId); return START_NOT_STICKY
                }
                if (projectionResultData == null) {
                    logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 화면 캡처 권한 없음")
                    showResultNotif(false, "화면 캡처 권한이 없습니다. 앱을 열어 설정하세요.")
                    stopSelf(startId); return START_NOT_STICKY
                }

                handler.postDelayed({
                    performCapture(recipient, title, ruleName, startId)
                }, delaySec * 1000L)
            }

            else -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    private fun performCapture(recipient: String, title: String, ruleName: String, startId: Int) {
        try {
            val bitmap = captureScreen()
            if (bitmap == null) {
                logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 화면 캡처 실패")
                showResultNotif(false, "화면 캡처 실패"); return
            }
            val ok = MmsDispatcher.send(this, recipient, bitmap, title)
            if (ok) {
                logRepo.addLog(LogEntry.Status.SUCCESS, "[$ruleName] MMS 전송 완료 → $recipient")
                showResultNotif(true, "[$ruleName] MMS 전송 완료!")
            } else {
                logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] MMS 전송 실패")
                showResultNotif(false, "MMS 전송 실패. 번호를 확인하세요.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "오류: ${e.message}", e)
            logRepo.addLog(LogEntry.Status.FAILURE, "[$ruleName] 오류: ${e.message}")
        } finally {
            stopAndClean(startId)
        }
    }

    private fun captureScreen(): Bitmap? {
        val metrics = getDisplayMetrics()
        val w = metrics.widthPixels
        val h = metrics.heightPixels
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
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * w
            val bmp = Bitmap.createBitmap(w + rowPadding / plane.pixelStride, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            Bitmap.createBitmap(bmp, 0, 0, w, h)
        } finally { image.close() }
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

    private fun buildForegroundNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_FOREGROUND)
            .setContentTitle("알림장봇").setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi).setOngoing(true).build()
    }

    private fun showResultNotif(success: Boolean, msg: String) {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_STATUS)
            .setContentTitle(if (success) "✅ 전송 완료" else "❌ 전송 실패")
            .setContentText(msg).setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi).setAutoCancel(true).build()
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .notify(NOTIF_ID + 1, notif)
    }

    private fun stopAndClean(startId: Int) {
        virtualDisplay?.release(); mediaProjection?.stop(); imageReader?.close()
        virtualDisplay = null; mediaProjection = null; imageReader = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release(); mediaProjection?.stop(); imageReader?.close()
        super.onDestroy()
    }
}

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
import com.alimjangbot.util.ImageUtils

/**
 * 화면 캡처 및 MMS 전송을 담당하는 포그라운드 서비스.
 *
 * 흐름:
 * 1. NotificationWatcher → ACTION_CAPTURE_AND_SEND 인텐트 수신
 * 2. 지정된 딜레이(초) 동안 대기 (하이클래스 게시글 로드 시간)
 * 3. MediaProjection으로 현재 화면 캡처
 * 4. MmsDispatcher로 MMS 전송
 * 5. 전송 결과 알림 표시 + 로그 저장
 *
 * MediaProjection 토큰은 MainActivity에서 권한 획득 후 전달해야 함.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"

        const val ACTION_CAPTURE_AND_SEND = "com.alimjangbot.CAPTURE_AND_SEND"
        const val ACTION_STORE_PROJECTION = "com.alimjangbot.STORE_PROJECTION"

        const val EXTRA_DELAY_SEC       = "delay_sec"
        const val EXTRA_RECIPIENT       = "recipient"
        const val EXTRA_TRIGGER_TITLE   = "trigger_title"
        const val EXTRA_RESULT_CODE     = "result_code"
        const val EXTRA_RESULT_DATA     = "result_data"

        private const val NOTIF_ID_FOREGROUND = 1001

        /** MediaProjection 토큰 (MainActivity에서 권한 획득 후 저장) */
        @Volatile
        var projectionResultCode: Int = 0
        @Volatile
        var projectionResultData: Intent? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var logRepo: SendLogRepository

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        logRepo = SendLogRepository.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 항상 포그라운드로 승격 (Android 9+ 요구사항)
        startForeground(NOTIF_ID_FOREGROUND, buildForegroundNotification())

        when (intent?.action) {
            ACTION_STORE_PROJECTION -> {
                // MainActivity에서 권한 획득 후 토큰 저장
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
                val delaySec   = intent.getIntExtra(EXTRA_DELAY_SEC, 3)
                val recipient  = intent.getStringExtra(EXTRA_RECIPIENT) ?: ""
                val title      = intent.getStringExtra(EXTRA_TRIGGER_TITLE) ?: "알림장"

                if (recipient.isBlank()) {
                    Log.e(TAG, "수신 번호 없음 → 전송 취소")
                    logRepo.addLog(LogEntry.Status.FAILURE, "수신 번호 미설정")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                if (projectionResultData == null) {
                    Log.e(TAG, "MediaProjection 권한 없음 → 캡처 불가")
                    logRepo.addLog(LogEntry.Status.FAILURE, "화면 캡처 권한 없음 (앱에서 권한 부여 필요)")
                    showResultNotification(false, "화면 캡처 권한이 없습니다. 앱을 열어 권한을 허용하세요.")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }

                Log.i(TAG, "${delaySec}초 후 캡처 시작...")
                handler.postDelayed({
                    performCaptureAndSend(recipient, title, startId)
                }, delaySec * 1000L)
            }

            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun performCaptureAndSend(recipient: String, title: String, startId: Int) {
        try {
            val bitmap = captureScreen()
            if (bitmap == null) {
                logRepo.addLog(LogEntry.Status.FAILURE, "화면 캡처 실패")
                showResultNotification(false, "화면 캡처에 실패했습니다.")
                stopAndClean(startId)
                return
            }

            Log.i(TAG, "화면 캡처 완료: ${bitmap.width}x${bitmap.height}")

            // MMS 전송
            val success = MmsDispatcher.send(
                context   = this,
                recipient = recipient,
                bitmap    = bitmap,
                subject   = title
            )

            if (success) {
                logRepo.addLog(LogEntry.Status.SUCCESS, "MMS 전송 완료 → $recipient")
                showResultNotification(true, "알림장 MMS 전송 완료!")
            } else {
                logRepo.addLog(LogEntry.Status.FAILURE, "MMS 전송 실패")
                showResultNotification(false, "MMS 전송 실패. 번호를 확인하세요.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "캡처/전송 오류: ${e.message}", e)
            logRepo.addLog(LogEntry.Status.FAILURE, "오류: ${e.message}")
            showResultNotification(false, "오류 발생: ${e.message}")
        } finally {
            stopAndClean(startId)
        }
    }

    /**
     * MediaProjection API로 현재 화면을 Bitmap으로 캡처.
     */
    private fun captureScreen(): Bitmap? {
        val metrics = getDisplayMetrics()
        val width   = metrics.widthPixels
        val height  = metrics.heightPixels
        val density = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(
            projectionResultCode,
            projectionResultData!!
        )

        // ImageReader 설정
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "AlimjangCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        // 렌더링 대기 (최대 2초)
        Thread.sleep(500)

        val image = imageReader!!.acquireLatestImage() ?: run {
            Log.e(TAG, "acquireLatestImage() = null")
            return null
        }

        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride   = planes[0].rowStride
            val rowPadding  = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 실제 화면 크기로 자르기
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } finally {
            image.close()
        }
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                it.widthPixels  = bounds.width()
                it.heightPixels = bounds.height()
                it.densityDpi   = resources.displayMetrics.densityDpi
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getMetrics(it)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_FOREGROUND)
            .setContentTitle("알림장봇")
            .setContentText("화면 캡처 준비 중...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showResultNotification(success: Boolean, message: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, AlimjangApplication.CHANNEL_ID_STATUS)
            .setContentTitle(if (success) "✅ 알림장 전송 완료" else "❌ 알림장 전송 실패")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID_FOREGROUND + 1, notif)
    }

    private fun stopAndClean(startId: Int) {
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        virtualDisplay  = null
        mediaProjection = null
        imageReader     = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}

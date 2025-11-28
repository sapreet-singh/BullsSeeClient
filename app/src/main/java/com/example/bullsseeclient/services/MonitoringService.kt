package com.example.bullsseeclient.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.bullsseeclient.DataCollectionWorker

class MonitoringService : Service() {
    private lateinit var handler: Handler
    private val intervalMs = 60_000L
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MonitoringService", "onCreate")
        startForeground()
        handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    val deviceName = android.os.Build.MODEL
                    val req = OneTimeWorkRequestBuilder<DataCollectionWorker>()
                        .setInputData(workDataOf("deviceName" to deviceName))
                        .build()
                    WorkManager.getInstance(this@MonitoringService).enqueue(req)
                    android.util.Log.d("MonitoringService", "enqueued DataCollectionWorker")
                } catch (e: Exception) {
                    Log.e("MonitoringService", "Enqueue error: ${e.message}")
                } finally {
                    handler.postDelayed(this, intervalMs)
                }
            }
        }, intervalMs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("MonitoringService", "onStartCommand")
        return START_STICKY
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("MONITOR", "Monitor", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val n: Notification = NotificationCompat.Builder(this, "MONITOR")
            .setContentTitle("Monitor")
            .setContentText("Scheduling uploads")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .build()
        startForeground(1002, n)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }
}

package com.example.bullssee

import android.content.Context
import android.provider.CallLog
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson

class CallLogWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val cursor = appContext.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null)
        val callLogs = mutableListOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                callLogs.add("Number: $number, Date: $date")
            }
        }
        cursor?.close()
        val json = Gson().toJson(callLogs)
        // Upload json to BullsSeeAPI (implement similar to LocationService)
        return Result.success()
    }
}
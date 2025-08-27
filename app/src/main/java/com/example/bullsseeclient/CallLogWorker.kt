package com.example.bullsseeclient

import android.content.Context
import android.provider.CallLog
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson

class CallLogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            null
        )

        val callLogs = mutableListOf<String>()
        cursor?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val date = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.DATE))
                callLogs.add("Number: $number, Date: $date")
            }
        }

        val json = Gson().toJson(callLogs)
        // TODO: Upload json to BullsSeeAPI
        return Result.success()
    }
}

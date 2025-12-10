package com.example.alltodo.services

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

object RemoteLogger {
    private const val TAG = "RemoteLogger"
    private val client = OkHttpClient()
    // Using the same external IP as iOS for field testing consistency
    // If testing on emulator, use 10.0.2.2. If physical device, use server local IP.
    // Using the same external IP as iOS for field testing consistency
    // If testing on emulator, use 10.0.2.2. If physical device, use server local IP.
    private const val LOG_URL = com.example.alltodo.BuildConfig.LOG_URL
    
    // Generate a session ID or retrieve persistent ID
    // For simplicity, we generate one per app launch or use Build.MODEL + UUID
    val deviceID: String by lazy {
        val model = Build.MODEL.filter { it.isLetterOrDigit() }
        val uuid = UUID.randomUUID().toString().take(8)
        "${model}_$uuid"
    }

    fun log(level: String, message: String) {
        val json = JSONObject()
        json.put("level", level)
        json.put("message", message)
        json.put("device", deviceID)
        json.put("timestamp", System.currentTimeMillis() / 1000.0)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(LOG_URL)
            .post(body)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send log: ${e.message}")
            }
        }
    }

    fun info(message: String) {
        log("INFO", message)
    }

    fun error(message: String) {
        log("ERROR", message)
    }
}

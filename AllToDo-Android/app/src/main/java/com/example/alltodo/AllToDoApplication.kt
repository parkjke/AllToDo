package com.example.alltodo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.kakao.vectormap.KakaoMapSdk

@HiltAndroidApp
class AllToDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // [FIX] Initialize Naver Map SDK globally
        try {
            com.naver.maps.map.NaverMapSdk.getInstance(this).client = 
                com.naver.maps.map.NaverMapSdk.NaverCloudPlatformClient("i7652syq10")
        } catch (e: Exception) {
            android.util.Log.e("AllToDo", "Naver SDK Init Failed", e)
        }
        
        // [DEBUG] Log SHA-1 for Console Registration Verification
        try {
            val packageName = packageName
            val pInfo = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            for (signature in pInfo.signatures) {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                md.update(signature.toByteArray())
                val hexString = StringBuilder()
                for (b in md.digest()) {
                    hexString.append(String.format("%02X:", b))
                }
                if (hexString.isNotEmpty()) hexString.setLength(hexString.length - 1)
                android.util.Log.e("AUTH_CHECK", "📦 Package: $packageName")
                android.util.Log.e("AUTH_CHECK", "🔑 SHA-1: $hexString")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // [NEW] Global Crash Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = android.util.Log.getStackTraceString(throwable)
            android.util.Log.e("CRASH_REPORT", "🔥 FATAL CRASH on thread ${thread.name}: $trace")
            
            // Try to write to file
            try {
                val file = java.io.File(filesDir, "crash_log.txt")
                file.appendText("\n[${java.util.Date()}] FATAL: $trace")
            } catch (e: Exception) {
                // Ignore file write error during crash
            }
            // kotlin.system.exitProcess(1) // DISABLED to prevent restart loop and allow system crash dialog
        }
    }
}

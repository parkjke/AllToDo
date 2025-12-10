package com.example.alltodo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.kakao.vectormap.KakaoMapSdk

@HiltAndroidApp
class AllToDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // [DEBUG] Moved SDK Init to MainScreen to prevent startup crash
        // try {
        //     val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
        //     val appKey = appInfo.metaData.getString("com.kakao.vectormap.APP_KEY")
        //     if (appKey != null) {
        //         KakaoMapSdk.init(this, appKey)
        //     }
        // } catch (e: Exception) {
        //     android.util.Log.e("AllToDo", "Failed to initialize KakaoMap SDK", e)
        // }
        
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
            
            // Re-throw or kill process to let system handle it (but we logged it first)
            kotlin.system.exitProcess(1)
        }
    }
}

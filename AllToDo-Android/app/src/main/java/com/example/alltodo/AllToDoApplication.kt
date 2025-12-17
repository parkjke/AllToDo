package com.example.alltodo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import com.kakao.vectormap.KakaoMapSdk

@HiltAndroidApp
class AllToDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        

        
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // [NEW] Global Crash Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            
            // Try to write to file
            try {
                val file = java.io.File(filesDir, "crash_log.txt")
                file.appendText("\n[${java.util.Date()}] FATAL: ${throwable.stackTraceToString()}")
            } catch (e: Exception) {
                // Ignore file write error during crash
            }
            // kotlin.system.exitProcess(1) // DISABLED to prevent restart loop and allow system crash dialog
        }
    }
}

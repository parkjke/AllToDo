package kr.alltodo

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.NaverMapSdk.NaverCloudPlatformClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AllToDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // [FORCE FIX] Fix [800] Client is unspecified by programmatically setting the ID
        // This bypasses the manifest reading issue that occurs in the main Hilt/Project structure.
        NaverMapSdk.getInstance(this).client = NaverCloudPlatformClient("i7652syq10")

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

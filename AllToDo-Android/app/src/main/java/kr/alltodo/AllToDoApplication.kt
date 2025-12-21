package kr.alltodo

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.NaverMapSdk.NaverCloudPlatformClient
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AllToDoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // [Item 0.1] Kakao Map Initialization
        com.kakao.vectormap.KakaoMapSdk.init(this, "73c078184e5277946f8078004f60bd51")

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

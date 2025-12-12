package com.example.alltodo.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Log Types
enum class LogType {
    MOTION_CHANGE,
    LOCATION_PAUSE,
    LOCATION_RESUME,
    BATTERY_LEVEL,
    ERROR
}

object OptimizationLogger {
    private const val FILE_NAME = "optimization_log.json"
    private const val TAG = "OptimizationLogger"

    suspend fun log(context: Context, type: LogType, value: String) {
        withContext(Dispatchers.IO) {
            try {
                // Determine file path: External Files Dir (easier to access via USB)
                // /Android/data/com.example.alltodo/files/Documents/optimization_log.json
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, FILE_NAME)
                
                // Get Battery Level
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)

                val json = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("datetime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    put("type", type.name)
                    put("value", value)
                    put("battery", "$batteryLevel%")
                }

                // Append line
                FileOutputStream(file, true).use { fos ->
                    fos.write((json.toString() + "\n").toByteArray())
                }
                
                Log.d(TAG, "Logged: $json")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to log", e)
            }
        }
    }
    
    // Helper to read logs (for verification)
    suspend fun readLogs(context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                 val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                 val file = File(dir, FILE_NAME)
                 if (file.exists()) file.readText() else "No logs found."
            } catch (e: Exception) {
                "Error reading logs: ${e.message}"
            }
        }
    }
}

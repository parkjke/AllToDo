package com.example.alltodo.wasm

import android.content.Context
import com.google.gson.Gson
import com.example.alltodo.services.RemoteLogger
import okhttp3.OkHttpClient
import okhttp3.Request

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class WasmManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val storage = WasmStorage(context)
    private val runtime: WasmRuntime = WebViewWasmRuntime(context)
    private val advancedUrl = "http://175.194.163.56:8003/wasm/advanced"
    private val versionUrl = "http://175.194.163.56:8003/wasm/version"
    
    private val TAG = "[WASM_STATUS]"
    
    data class VersionResponse(val version: String)
    
    // [NEW] Debugging Properties for UI Overlay
    var lastErrorMessage: String? = null
        private set
        
    var onStatusUpdate: ((String) -> Unit)? = null

    fun initialize(onReady: (Boolean) -> Unit) {
        android.util.Log.d(TAG, "🚀 Initializing WASM Manager...")
        
        // 1. Initial Load from storage if available
        storage.load()?.let { (_, blobJson) ->
            try {
                val bundle = gson.fromJson(blobJson, WasmBundle::class.java)
                val decrypted = WasmCrypto.decrypt(bundle)
                runtime.loadModule(decrypted)
                android.util.Log.d(TAG, "✅ Loaded stored WASM (Version: ${bundle.version})")
                RemoteLogger.info("WASM Init (Stored): Version ${bundle.version}")
                lastErrorMessage = null
                onStatusUpdate?.invoke("Ready (Stored v${bundle.version})")
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e(TAG, "❌ Failed to load stored WASM. Using Fallback.")
                RemoteLogger.error("Failed to load stored WASM: ${e.message}")
                loadFallback()
            }
        } ?: run {
            android.util.Log.d(TAG, "ℹ️ No stored WASM found. Using Fallback.")
            RemoteLogger.info("No stored WASM. Using Fallback.")
            loadFallback()
            onStatusUpdate?.invoke("Ready (Fallback)")
        }

        // 2. Check for Updates in Background
        Thread {
            checkForUpdate()
            onReady(true) // Notify valid state (either stored or fallback is ready)
        }.start()
    }
    
    // [NEW] Exposed method to replace Native Code
    fun compress(points: List<Int>): List<Int> {
        val start = System.currentTimeMillis()
        android.util.Log.d(TAG, "⚡️ Executing WASM 'compressTrajectory' with ${points.size/2} points...")
        
        val result = runtime.compressTrajectory(points, 3, 10)
        
        val duration = System.currentTimeMillis() - start
        val msg = "WASM Success: ${points.size/2} -> ${result.size/2} pts (${duration}ms)"
        android.util.Log.d(TAG, "✨ $msg")
        // RemoteLogger.info(msg) // Disabled to avoid spam. Handled by ViewModel.
        lastErrorMessage = null
        onStatusUpdate?.invoke("Comp: ${points.size/2}->${result.size/2} (${duration}ms)")
        return result
    }

    private fun checkForUpdate() {
        try {
            android.util.Log.d(TAG, "🔍 Checking for updates at: $versionUrl")
            // A. Check Server Version
            val request = Request.Builder().url(versionUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w(TAG, "⚠️ Update check failed: ${response.code}")
                return
            }
            
            val serverVersion = gson.fromJson(response.body?.string(), VersionResponse::class.java).version
            val (storedVersion, _) = storage.load() ?: ("0.0.0" to "")
            
            if (serverVersion != storedVersion) {
                android.util.Log.d(TAG, "🆕 New version found: $serverVersion (Current: $storedVersion). Downloading...")
                if (fetchAndLoadAdvanced()) {
                    android.util.Log.d(TAG, "🎉 Successfully upgraded to Version $serverVersion")
                    RemoteLogger.info("WASM Upgraded to Version $serverVersion")
                } else {
                    android.util.Log.e(TAG, "❌ Failed to download/load update.")
                }
            } else {
                android.util.Log.d(TAG, "✅ Up to date ($storedVersion)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Update check error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun fetchAndLoadAdvanced(): Boolean {
        return try {
            android.util.Log.d(TAG, "🔍 (1/3) Start Connection Check to $advancedUrl...")
            val request = Request.Builder().url(advancedUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.w(TAG, "⚠️ (2/3) Connection Reached Server but Failed. Code: ${response.code}")
                return false
            }
            
            android.util.Log.d(TAG, "✅ (2/3) Connection Successful! (Status: 200 OK)")
            
            val body = response.body?.string() ?: return false
            val bundle = gson.fromJson(body, WasmBundle::class.java)
            val decrypted = WasmCrypto.decrypt(bundle)
            
            runtime.loadModule(decrypted)
            storage.save(bundle.version, body)
            
            android.util.Log.d(TAG, "🎉 (3/3) Download & Load Complete. Version: ${bundle.version}")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Connection FAILED: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun loadFallback() {
        try {
            val input = context.assets.open("fallback.wasm")
            val bytes = input.readBytes()
            runtime.loadModule(bytes)
            android.util.Log.d(TAG, "📦 Loaded built-in Fallback WASM")
            RemoteLogger.info("Loaded Fallback WASM")
            lastErrorMessage = null
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e(TAG, "🔥 CRITICAL: Failed to load Fallback WASM!")
            RemoteLogger.error("CRITICAL: Failed to load Fallback WASM")
            lastErrorMessage = "Failed to load WASM"
        }
    }
}

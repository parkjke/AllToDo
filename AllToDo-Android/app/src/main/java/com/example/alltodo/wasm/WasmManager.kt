package com.example.alltodo.wasm

import android.content.Context
import com.google.gson.Gson
import com.example.alltodo.services.RemoteLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.*

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
        
        // 1. Initial Load from storage if available
        storage.load()?.let { (_, blobJson) ->
            try {
                val bundle = gson.fromJson(blobJson, WasmBundle::class.java)
                val decrypted = WasmCrypto.decrypt(bundle)
                runtime.loadModule(decrypted)
                RemoteLogger.info("WASM Init (Stored): Version ${bundle.version}")
                lastErrorMessage = null
                onStatusUpdate?.invoke("Ready (Stored v${bundle.version})")
            } catch (e: Exception) {
                e.printStackTrace()
                RemoteLogger.error("Failed to load stored WASM: ${e.message}")
                loadFallback()
            }
        } ?: run {
            RemoteLogger.info("No stored WASM. Using Fallback.")
            loadFallback()
            onStatusUpdate?.invoke("Ready (Fallback)")
        }

        // 2. Check for Updates in Background
        CoroutineScope(Dispatchers.IO).launch {
            checkForUpdate()
            onReady(true) // Notify valid state (either stored or fallback is ready)
        }
    }
    
    // [NEW] Exposed method to replace Native Code
    // [NEW] Exposed method to replace Native Code
    suspend fun compress(points: List<Int>): List<Int> {
        val start = System.currentTimeMillis()
        
        val result = runtime.compressTrajectory(points, 3, 10)
        
        val duration = System.currentTimeMillis() - start
        val msg = "WASM Success: ${points.size/2} -> ${result.size/2} pts (${duration}ms)"
        // RemoteLogger.info(msg)
        lastErrorMessage = null
        withContext(Dispatchers.Main) {
            onStatusUpdate?.invoke("Comp: ${points.size/2}->${result.size/2} (${duration}ms)")
        }
        return result
    }

    // [NEW] Clustering Support
    suspend fun cluster(points: List<Int>, cellSizeMeters: Int): List<Int> {
        val start = System.currentTimeMillis()
        
        val result = runtime.clusterPoints(points, cellSizeMeters)
        
        val duration = System.currentTimeMillis() - start
        lastErrorMessage = null
        // onStatusUpdate?.invoke("Cluster: ${duration}ms")
        return result
    }

    private suspend fun checkForUpdate() {
        try {
            // A. Check Server Version
            val request = Request.Builder().url(versionUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return
            }
            
            val serverVersion = gson.fromJson(response.body?.string(), VersionResponse::class.java).version
            val (storedVersion, _) = storage.load() ?: ("0.0.0" to "")
            
            if (serverVersion != storedVersion) {
                if (fetchAndLoadAdvanced()) {
                    RemoteLogger.info("WASM Upgraded to Version $serverVersion")
                } else {
                }
            } else {
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchAndLoadAdvanced(): Boolean {
        return try {
            val request = Request.Builder().url(advancedUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return false
            }
            
            
            val body = response.body?.string() ?: return false
            val bundle = gson.fromJson(body, WasmBundle::class.java)
            val decrypted = WasmCrypto.decrypt(bundle)
            
            runtime.loadModule(decrypted)
            storage.save(bundle.version, body)
            
            
            // [NEW] Self-Test Routine
            verifyWasm()
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun verifyWasm() {
        try {
            // 1. RDP Test Case
            // (0,0) -> (1,1) -> (2,2) with thresh=5m. Expected: (0,0) -> (2,2)
            val rdpPoints = listOf(0, 0, 100000, 100000, 200000, 200000) 
            val rdpResult = runtime.compressTrajectory(rdpPoints, 5, 5)
            
            val rdpPassed = if (rdpResult.size == 4) {
                 val p1 = "${rdpResult[0]},${rdpResult[1]}"
                 val p2 = "${rdpResult[2]},${rdpResult[3]}"
                 (p1 == "0,0" && p2 == "200000,200000")
            } else false
            
            // 2. Clustering Test Case
            // 2 points close to each other: (0,0) and (10,10) (approx 1.4m dist)
            // Cell Size: 100m. Expected: 1 Cluster with count 2.
            val clusterPoints = listOf(0, 0, 100, 100)
            val clusterResult = runtime.clusterPoints(clusterPoints, 100)
            
            val clusterPassed = if (clusterResult.size == 3) {
                val count = clusterResult[2]
                count == 2
            } else false

            if (rdpPassed && clusterPassed) {
                onStatusUpdate?.invoke("WASM Verified (RDP+Cluster)")
            } else {
            }
        } catch (e: Exception) {
        }
    }

    private fun loadFallback() {
        try {
            val input = context.assets.open("fallback.wasm")
            val bytes = input.readBytes()
            runtime.loadModule(bytes)
            RemoteLogger.info("Loaded Fallback WASM")
            lastErrorMessage = null
        } catch (e: Exception) {
            e.printStackTrace()
            RemoteLogger.error("CRITICAL: Failed to load Fallback WASM")
            lastErrorMessage = "Failed to load WASM"
        }
    }
}

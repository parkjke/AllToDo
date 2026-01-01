package kr.alltodo.wasm

import android.content.Context
import com.google.gson.Gson
import kr.alltodo.services.RemoteLogger
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
    private var runtime: WasmRuntime = WebViewWasmRuntime(context)
    private val advancedUrl = "http://175.194.163.56:8003/wasm/advanced"
    private val versionUrl = "http://175.194.163.56:8003/wasm/version"
    
    private val TAG = "[WASM_STATUS]"
    
    data class VersionResponse(val version: String)
    
    // [NEW] Debugging Properties for UI Overlay
    var lastErrorMessage: String? = null
        private set
        
    var onStatusUpdate: ((String) -> Unit)? = null

    private fun logStep(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        android.util.Log.e("WASM_LOG", "$time >>> WASM ($message)")
        System.out.println("$time >>> WASM ($message)")
    }

    fun initialize(onReady: (Boolean) -> Unit) {
        try {
            logStep(">>> WASM Loading Fallback...")
            val input = context.assets.open("fallback.wasm")
            val bytes = input.readBytes()
            runtime.loadModule(bytes)
            lastErrorMessage = null
            logStep(">>> WASM Fallback Loaded Success")
        } catch (e: Exception) {
            e.printStackTrace()
            lastErrorMessage = "Failed to load WASM"
            logStep(">>> WASM Fallback Load Failed")
        }
        // 비동기 방식으로 
        logStep(">>> WASM Server Connecting...")

        onReady(true)

        // 2. Check for Updates in Background
        CoroutineScope(Dispatchers.IO).launch {
            loadFromStorage()
            checkForUpdate()
        }
   }

    private suspend fun loadFromStorage() {
        storage.load()?.let { (_, blobJson) ->
            try {
                // A. Load into Candidate Runtime (Separate from Fallback)
                val candidateRuntime = WebViewWasmRuntime(context)
                
                val bundle = gson.fromJson(blobJson, WasmBundle::class.java)
                val decrypted = WasmCrypto.decrypt(bundle)
                
                withContext(Dispatchers.Main) {
                    candidateRuntime.loadModule(decrypted)
                }
                
                // B. Verify Candidate
                val isVerified = verifyWasm(candidateRuntime)
                
                // C. Hot-Swap if Verified
                if (isVerified) {
                    runtime = candidateRuntime
                    lastErrorMessage = null
                    onStatusUpdate?.invoke("Ready (Stored v${bundle.version})")
                    logStep(">>> WASM Loaded Stored v${bundle.version}")
                } else {
                    logStep(">>> WASM Stored Verification Failed")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } ?: run {
            logStep(">>> WASM No stored WASM")
        }
    }
    
    // [NEW] Exposed method to replace Native Code
    suspend fun compress(points: List<Int>): List<Int> {
        val start = System.currentTimeMillis()
        
        val result = runtime.compressTrajectory(points, 3, 10)
        
        val duration = System.currentTimeMillis() - start
        val msg = "WASM Success: ${points.size/2} -> ${result.size/2} pts (${duration}ms)"
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
        return result
    }

    private suspend fun checkForUpdate() {
        try {
            // A. Check Server Version
            logStep(">>> Checking Server Version...")
            val request = Request.Builder().url(versionUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                logStep(">>> Server Check Failed: ${response.code}")
                return
            }
            
            val serverVersion = gson.fromJson(response.body?.string(), VersionResponse::class.java).version
            val (storedVersion, _) = storage.load() ?: ("0.0.0" to "")
            
            if (serverVersion != storedVersion) {
                logStep(">>> New Version Found: $serverVersion (Current: $storedVersion)")
                logStep(">>> Downloading Advanced Module...")
                if (fetchAndLoadAdvanced()) {
                    RemoteLogger.info("WASM Upgraded to Version $serverVersion")
                    logStep(">>> Download & Load Success")
                } else {
                    logStep(">>> Download Failed")
                }
            } else {
                logStep(">>> Already Latest Version ($serverVersion)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logStep(">>> Server Check Error: ${e.message}")
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
            
            // [Safety] Load into CANDIDATE Runtime first
            val candidateRuntime = WebViewWasmRuntime(context)
            
            withContext(Dispatchers.Main) {
                candidateRuntime.loadModule(decrypted)
            }
            
            // [Safety] Verify before Swap
            if (verifyWasm(candidateRuntime)) {
                runtime = candidateRuntime
                storage.save(bundle.version, body)
                logStep(">>> WASM Hot-Swapped to Server Version ${bundle.version}")
                return true
            } else {
                logStep(">>> Server WASM Verification Failed. Discarding.")
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private suspend fun verifyWasm(targetRuntime: WasmRuntime): Boolean {
        return try {
            logStep("Running Self-Test...")
            // 1. RDP Test Case
            val rdpPoints = listOf(0, 0, 100000, 100000, 200000, 200000) 
            val rdpResult = targetRuntime.compressTrajectory(rdpPoints, 5, 5)
            
            val rdpPassed = if (rdpResult.size == 4) {
                 val p1 = "${rdpResult[0]},${rdpResult[1]}"
                 val p2 = "${rdpResult[2]},${rdpResult[3]}"
                 (p1 == "0,0" && p2 == "200000,200000")
            } else false
            
            // 2. Clustering Test Case
            val clusterPoints = listOf(0, 0, 100, 100)
            val clusterResult = targetRuntime.clusterPoints(clusterPoints, 100)
            
            val clusterPassed = if (clusterResult.size == 3) {
                val count = clusterResult[2]
                count == 2
            } else false

            if (rdpPassed && clusterPassed) {
                onStatusUpdate?.invoke("WASM Verified (RDP+Cluster)")
                logStep("Self-Test Passed")
                true
            } else {
                logStep("Self-Test Failed (RDP=$rdpPassed, Cluster=$clusterPassed)")
                false
            }
        } catch (e: Exception) {
            logStep("Self-Test Error: ${e.message}")
            false
        }
    }
}

package com.example.alltodo.wasm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch // Simple sync for now, or use Coroutines

class WebViewWasmRuntime(private val context: Context) : WasmRuntime {
    private var webView: WebView? = null
    private var isReady = false
    private val handler = Handler(Looper.getMainLooper())

    private var isPageLoaded = false

    init {
        handler.post {
            try {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.webViewClient = object : WebViewClient() {
                     override fun onPageFinished(view: WebView?, url: String?) {
                         super.onPageFinished(view, url)
                         isPageLoaded = true
                     }
                }
                
                // Load Glue Code
                var glueCode = try {
                    context.assets.open("wasm_glue.js").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                
                // Robust Patching (Match iOS)
                // 1. Remove strict 'let'
                glueCode = glueCode.replace(Regex("let\\s+wasm_bindgen\\s*;"), "// removed let")
                
                // Generate HTML with Glue + Init Logic
                val html = """
                <html>
                <head></head>
                <body>
                <script>
                // 0. Error Handler
                window.LAST_ERROR = null;
                window.onerror = function(message, source, lineno, colno, error) {
                    window.LAST_ERROR = message + " at " + lineno + ":" + colno;
                    console.error("WASM_ERROR: " + window.LAST_ERROR);
                };
                
                // 1. Declare global var explicitly
                var wasm_bindgen;
                </script>
                
                <script>
                // 2. Inject Glue
                $glueCode
                
                // 3. Explicitly attach to window
                window.wasm_bindgen = wasm_bindgen;
                </script>
                
                <script>
                // Bridge
                var androidBridge = {
                    log: function(msg) { console.log(msg); }
                };
                
                async function loadWasm(base64Data) {
                    if (window.LAST_ERROR) return "LOAD_ERROR: " + window.LAST_ERROR;
                    if (typeof wasm_bindgen === 'undefined' || typeof wasm_bindgen !== 'function') {
                        return "SETUP_ERROR: wasm_bindgen is missing (" + typeof wasm_bindgen + ")";
                    }
                    
                    try {
                        const binaryString = atob(base64Data);
                        const bytes = new Uint8Array(binaryString.length);
                        for (let i = 0; i < binaryString.length; i++) {
                            bytes[i] = binaryString.charCodeAt(i);
                        }
                        
                        await wasm_bindgen(bytes);
                        return "OK";
                    } catch (e) {
                        return "ERROR: " + e.toString();
                    }
                }
                
                function compress(pointsJson, minDist, angleThresh) {
                    try {
                        const points = JSON.parse(pointsJson);
                        const int32Array = new Int32Array(points);
                        const result = wasm_bindgen.compress_trajectory(int32Array, minDist, angleThresh);
                        return Array.from(result);
                    } catch (e) {
                        return null;
                    }
                }
                
                function cluster(pointsJson, cellSizeMeters) {
                    try {
                        const points = JSON.parse(pointsJson);
                        const int32Array = new Int32Array(points);
                        const result = wasm_bindgen.cluster_points(int32Array, cellSizeMeters);
                        return Array.from(result);
                    } catch (e) {
                         // Fallback or Error
                         console.error("Cluster Error: " + e);
                         return null;
                    }
                }
                </script>
                </body>
                </html>
                """
                
                // Use meaningful BaseURL to prevent Script Errors (about:blank issues)
                wv.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
                webView = wv
            } catch (e: Exception) {
                // If WebView creation fails (e.g. headless emulator or system issue), do not crash.
                webView = null
            }
        }
    }

    override fun loadModule(wasmBytes: ByteArray) {
        val base64 = Base64.encodeToString(wasmBytes, Base64.NO_WRAP)
        val js = "loadWasm('$base64')"
        
        // Retry loop using handler
        val retryRunnable = object : Runnable {
            var attempts = 0
            override fun run() {
                if (isPageLoaded && webView != null) {
                    webView?.evaluateJavascript(js) { result ->
                        if (result != null && !result.startsWith("\"ERROR") && !result.startsWith("\"LOAD_ERROR")) {
                             isReady = true
                        } else {
                        }
                    }
                } else {
                    attempts++
                    if (attempts < 20) {
                        handler.postDelayed(this, 100) // Retry every 100ms
                    } else {
                    }
                }
            }
        }
        handler.post(retryRunnable)
    }

    override fun callFunction(name: String, params: IntArray): Int {
        return 0
    }

    // Non-blocking Suspend Call using Coroutines
    override suspend fun compressTrajectory(points: List<Int>, minDist: Int, angleThresh: Int): List<Int> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        if (!isReady) {
            continuation.resumeWith(Result.success(points))
            return@suspendCancellableCoroutine
        }

        val pointsJson = points.toString()
        val js = "JSON.stringify(compress('$pointsJson', $minDist, $angleThresh))"
        
        handler.post {
            webView?.evaluateJavascript(js) { result ->
                if (result != null && result != "null") {
                   try {
                       var cleanResult = result
                       if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                           cleanResult = cleanResult.substring(1, cleanResult.length - 1)
                       }
                       cleanResult = cleanResult.replace("\\\"", "\"")
                       
                       val gson = com.google.gson.Gson()
                       val parsed = gson.fromJson(cleanResult, Array<Int>::class.java)
                       if (parsed != null) {
                           continuation.resumeWith(Result.success(parsed.toList()))
                           return@evaluateJavascript
                       }
                   } catch (e: Exception) {
                   }
                }
                // Fallback / Error
                continuation.resumeWith(Result.success(points))
            } ?: run {
                 // WebView is null?
                 continuation.resumeWith(Result.success(points))
            }
        }
    }


    override suspend fun clusterPoints(points: List<Int>, cellSizeMeters: Int): List<Int> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        if (!isReady) {
             continuation.resumeWith(Result.success(emptyList()))
             return@suspendCancellableCoroutine
        }

        val pointsJson = points.toString()
        val js = "JSON.stringify(cluster('$pointsJson', $cellSizeMeters))"
        
        handler.post {
            webView?.evaluateJavascript(js) { result ->
                if (result != null && result != "null") {
                   try {
                       var cleanResult = result
                       if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                           cleanResult = cleanResult.substring(1, cleanResult.length - 1)
                       }
                       cleanResult = cleanResult.replace("\\\"", "\"")
                       
                       val gson = com.google.gson.Gson()
                       val parsed = gson.fromJson(cleanResult, Array<Int>::class.java)
                       if (parsed != null) {
                           continuation.resumeWith(Result.success(parsed.toList()))
                           return@evaluateJavascript
                       }
                   } catch (e: Exception) {
                   }
                }
                // Fallback / Error
                continuation.resumeWith(Result.success(emptyList()))
            } ?: run {
                 continuation.resumeWith(Result.success(emptyList()))
            }
        }
    }
}

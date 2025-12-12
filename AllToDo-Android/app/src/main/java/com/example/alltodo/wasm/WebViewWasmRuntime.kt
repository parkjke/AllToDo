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
                         android.util.Log.d("WebViewWasm", "WebView Loaded")
                     }
                }
                
                // Load Glue Code
                var glueCode = try {
                    context.assets.open("wasm_glue.js").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    android.util.Log.e("WebViewWasm", "Failed to load glue", e)
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
                android.util.Log.e("WebViewWasm", "CRITICAL: WebView creation failed!", e)
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
                        android.util.Log.d("WebViewWasm", "Init Result: $result")
                        if (result != null && !result.startsWith("\"ERROR") && !result.startsWith("\"LOAD_ERROR")) {
                             isReady = true
                        } else {
                             android.util.Log.e("WebViewWasm", "Init Failed: $result")
                        }
                    }
                } else {
                    attempts++
                    if (attempts < 20) {
                        handler.postDelayed(this, 100) // Retry every 100ms
                    } else {
                        android.util.Log.e("WebViewWasm", "Timeout waiting for WebView")
                    }
                }
            }
        }
        handler.post(retryRunnable)
    }

    override fun callFunction(name: String, params: IntArray): Int {
        return 0
    }

    // Blocking Call for Simplicity (Ideally should be suspend function)
    override fun compressTrajectory(points: List<Int>, minDist: Int, angleThresh: Int): List<Int> {
        if (!isReady) return points // Return original if not ready

        var resultList: List<Int> = points
        val latch = CountDownLatch(1)
        
        val pointsJson = points.toString() // [1,2,3] format works in JS JSON.parse? Yes
        val js = "JSON.stringify(compress('$pointsJson', $minDist, $angleThresh))"
        
        handler.post {
            webView?.evaluateJavascript(js) { result ->
                // result is JSON string of array, e.g. "[1,2,3]"
                // If null or "null", failed.
                if (result != null && result != "null") {
                   try {
                       // Remove quotes if present from evaluateJavascript return
                       // It returns "\"str\"" for string, but for array it returns "[...]"
                       // evaluateJavascript returns the result as a JSON string.
                       // Since we used JSON.stringify in JS, the result in JS is a string like "[1,2]"
                       // evaluateJavascript wraps this in quotes: "\"[1,2]\""
                       
                       // 1. Unquote if wrapped in quotes
                       var cleanResult = result
                       if (cleanResult.startsWith("\"") && cleanResult.endsWith("\"")) {
                           cleanResult = cleanResult.substring(1, cleanResult.length - 1)
                       }
                       
                       // 2. Unescape quotes (JSON string content is escaped)
                       cleanResult = cleanResult.replace("\\\"", "\"")
                       
                       val gson = com.google.gson.Gson()
                       val parsed = gson.fromJson(cleanResult, Array<Int>::class.java)
                       if (parsed != null) {
                           resultList = parsed.toList()
                       }
                   } catch (e: Exception) {
                       android.util.Log.e("WebViewWasm", "Parse Error: $result", e)
                   }
                }
                latch.countDown()
            }
        }
        
        try {
            latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        
        return resultList
    }
        return resultList
    }

    override fun clusterPoints(points: List<Int>, cellSizeMeters: Int): List<Int> {
        if (!isReady) return emptyList()

        var resultList: List<Int> = emptyList()
        val latch = CountDownLatch(1)
        
        val pointsJson = points.toString()
        // Call JS wrapper `cluster_points`
        // Note: JS side expects `cluster_points(points_flat, cell_size_m)`
        // Glue code signature: `cluster_points(points_flat, cell_size_m)`
        // We need a JS bridge function similar to `compress` called `cluster`
        
        // Let's inject a new JS bridge function dynamically or rely on one if we added it?
        // Wait, we need to check if we added `cluster` function to HTML in init block.
        // Looking at previous valid file read, we only added `compress` function.
        // We need to modify `init` block to add `cluster` function too. 
        // BUT, since we can't edit `init` block easily without replacing huge chunk,
        // we can try to call `wasm_bindgen.cluster_points` directly via helper or 
        // assume we will update `init` block in next step. 
        
        // Let's assume we update `init` block to include `cluster` function wrapper.
        // Or we can define it one-off here? No, context is lost.
        
        // Actually, let's use `WasmManager` or `WebViewWasmRuntime`'s init to update the HTML template.
        // For now, I will assume the function name `cluster` exists in JS (I will add it in next step).
        
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
                           resultList = parsed.toList()
                       }
                   } catch (e: Exception) {
                       android.util.Log.e("WebViewWasm", "Cluster Parse Error: $result", e)
                   }
                }
                latch.countDown()
            }
        }
        
        try {
            latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        
        return resultList
    }
}

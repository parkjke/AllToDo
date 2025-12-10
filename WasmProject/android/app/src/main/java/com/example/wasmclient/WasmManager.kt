package com.example.wasmclient

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request

class WasmManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val storage = WasmStorage(context)
    private val runtime: WasmRuntime = DummyWasmRuntime()
    private val advancedUrl = "http://10.0.2.2:8000/wasm/advanced"

    fun initialize(onReady: (Boolean) -> Unit) {
        // 1. 저장된 WASM 먼저 시도
        storage.load()?.let { (_, blobJson) ->
            try {
                val bundle = gson.fromJson(blobJson, WasmBundle::class.java)
                val decrypted = WasmCrypto.decrypt(bundle)
                runtime.loadModule(decrypted)
                onReady(true)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. 서버에서 새로 받기 시도 (에뮬레이터 기준 10.0.2.2)
        Thread {
            val success = fetchAndLoadAdvanced()
            if (!success) {
                // 3. 실패 시 fallback.wasm 사용
                loadFallback()
            }
            onReady(success)
        }.start()
    }

    private fun fetchAndLoadAdvanced(): Boolean {
        return try {
            val request = Request.Builder().url(advancedUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return false
            
            val body = response.body?.string() ?: return false
            val bundle = gson.fromJson(body, WasmBundle::class.java)
            val decrypted = WasmCrypto.decrypt(bundle)
            
            runtime.loadModule(decrypted)
            storage.save(bundle.version, body)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadFallback() {
        try {
            val input = context.assets.open("fallback.wasm")
            val bytes = input.readBytes()
            runtime.loadModule(bytes)
            println("Loaded fallback WASM")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

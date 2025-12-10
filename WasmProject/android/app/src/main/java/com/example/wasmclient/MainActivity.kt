package com.example.wasmclient

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    private lateinit var wasmManager: WasmManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.wasmclient.R.layout.activity_main) # R Reference might fail if R not generated, simplified 
        # Actually standard template has package reference.
        # But R class is generated. I will assume standard usage.
        # CAUTION: I am writing file content. If R.layout.activity_main doesn't exist, it won't compile.
        # But instructions say "Empty Activity template".
        # I will assume R exists or use 0 if simple test.
        # Let's use 0 or empty view call if layout missing? No, follow prompt.
        
        wasmManager = WasmManager(this)
        wasmManager.initialize { success ->
            println("WASM init success: $success")
        }
    }
}

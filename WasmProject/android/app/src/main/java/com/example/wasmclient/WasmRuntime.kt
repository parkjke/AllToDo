package com.example.wasmclient

interface WasmRuntime {
    @Throws(Exception::class)
    fun loadModule(wasmBytes: ByteArray)
    
    @Throws(Exception::class)
    fun callFunction(name: String, params: IntArray): Int
}

class DummyWasmRuntime : WasmRuntime {
    override fun loadModule(wasmBytes: ByteArray) {
        println("Loaded WASM module size=${wasmBytes.size}")
    }

    override fun callFunction(name: String, params: IntArray): Int {
        println("Call wasm function $name with params=${params.toList()}")
        return 0
    }
}

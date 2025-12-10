package com.example.alltodo.wasm

interface WasmRuntime {
    @Throws(Exception::class)
    fun loadModule(wasmBytes: ByteArray)
    
    @Throws(Exception::class)
    fun callFunction(name: String, params: IntArray): Int
    
    // [NEW] Direct support for trajectory compression (Interface for WASM calls)
    // In a real WASM runtime, this might serialize data to WASM memory and call a function.
    fun compressTrajectory(points: List<Int>, minDist: Int, angleThresh: Int): List<Int>
}

class DummyWasmRuntime : WasmRuntime {
    override fun loadModule(wasmBytes: ByteArray) {
        println("Loaded WASM module size=${wasmBytes.size}")
    }

    override fun callFunction(name: String, params: IntArray): Int {
        println("Call wasm function $name with params=${params.toList()}")
        return 0
    }
    
    // Simulating WASM Logic in "Dummy" Runtime so app works without Native File
    override fun compressTrajectory(points: List<Int>, minDist: Int, angleThresh: Int): List<Int> {
        // Mock Implementation: Just return points for now, or simple filter to prove "WASM" called
        // Since user deleted native code, we must provide SOME logic if we want app to behave similar.
        // For simulation, let's just return every 2nd point to show "compression" happened via "WASM".
        println("WASM (Dummy) compressTrajectory called with ${points.size} points")
        return points.filterIndexed { index, _ -> index % 2 == 0 } 
    }
}

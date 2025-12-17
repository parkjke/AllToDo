package com.example.alltodo.utils

import android.os.Debug
import kotlinx.coroutines.*

object PerformanceMonitor {
    private var job: Job? = null
    private val TAG = "PerfMonitor"

    fun start() {
        if (job?.isActive == true) return
        
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                logMemoryAndThreads()
                delay(2000) // Log every 2 seconds
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun logMemoryAndThreads() {
        // Heap Memory
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxMemInMB = runtime.maxMemory() / 1048576L
        
        // Native Heap (Bitmap usage resides here mostly for older Androids, but pixel data is native since 8.0)
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / 1048576L
        
        val threadCount = Thread.activeCount()
        
    }
}

package com.example.alltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alltodo.data.TodoItem
import com.example.alltodo.data.TodoRepository
import com.example.alltodo.data.LocationRepository
import com.example.alltodo.data.LocationEntity
import com.example.alltodo.data.IntCoordinate // [NEW]
// import com.example.alltodo.data.TrajectoryCompressor // [DELETED] Native Logic Removed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val locationRepository: LocationRepository,
    private val userLogDao: com.example.alltodo.data.UserLogDao,
    private val wasmManager: com.example.alltodo.wasm.WasmManager // [FIX] Hilt Injected
) : ViewModel() {

    // [REMOVED] Manual Wasm Manager Instance
    // private val wasmManager = com.example.alltodo.wasm.WasmManager(context)

    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems.asStateFlow()

    private val _todayLocations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val todayLocations: StateFlow<List<LocationEntity>> = _todayLocations.asStateFlow()
    
    // Session Management
    private val _userLogs = MutableStateFlow<List<com.example.alltodo.data.UserLog>>(emptyList())
    val userLogs: StateFlow<List<com.example.alltodo.data.UserLog>> = _userLogs.asStateFlow()
    
    // [NEW] Debug Status for Overlay
    private val _debugStatus = MutableStateFlow("Initializing...")
    val debugStatus: StateFlow<String> = _debugStatus.asStateFlow()

    // [NEW] UI Settings
    private val _maxPopupItems = MutableStateFlow(5)
    val maxPopupItems: StateFlow<Int> = _maxPopupItems.asStateFlow()

    private val _popupFontSize = MutableStateFlow(1) // 0: Small, 1: Medium, 2: Large
    val popupFontSize: StateFlow<Int> = _popupFontSize.asStateFlow()

    fun updateMaxPopupItems(count: Int) { _maxPopupItems.value = count }
    fun updatePopupFontSize(size: Int) { _popupFontSize.value = size }

    // [NEW] Live Session Visualization (Moved here to avoid Init NPE)
    private val _liveSessionPoints = MutableStateFlow<List<LocationEntity>>(emptyList())
    val liveSessionPoints: StateFlow<List<LocationEntity>> = _liveSessionPoints.asStateFlow()

    
    // [NEW] Streaming RDP buffers
    private val processedSessionPoints = mutableListOf<LocationEntity>() // Permanently stored in memory
    private val pendingBuffer = mutableListOf<LocationEntity>() // Temporary buffer
    
    private val sessionPoints = mutableListOf<LocationEntity>()
    private var sessionStartTime: Long = 0
    private var lastRecordedTime: Long = 0

    init {
        // Init WASM
        wasmManager.initialize { success -> 
            println("WASM initialized in ViewModel: $success")
            if (!success) _debugStatus.value = "WASM Init Failed"
        }
        
        // Listen to WASM Status
        wasmManager.onStatusUpdate = { msg ->
            _debugStatus.value = msg
        }
        
        loadTodos()
        loadTodayLocations()
        loadUserLogs()
        startSession() // Start session on init
    }
    

    
    // Lifecycle hooks called from MainScreen/MainActivity
    fun startSession() {
        processedSessionPoints.clear()
        pendingBuffer.clear()
        _liveSessionPoints.value = emptyList()
        sessionStartTime = System.currentTimeMillis()
        lastRecordedTime = 0
        _debugStatus.value = "Recording... (0 pts)"
    }
    
    fun endSession() {
        val start = sessionStartTime
        if (start == 0L || (processedSessionPoints.isEmpty() && pendingBuffer.isEmpty())) return
        
        val now = System.currentTimeMillis()
        val endTime = now
        
        // Use GlobalScope or NonCancellable to ensure save completes even if UI is destroyed
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            // Flush remaining
            if (pendingBuffer.isNotEmpty()) {
                _debugStatus.value = "Flushing buffer..."
                processBuffer(force = true)
            }
            
            val totalCount = processedSessionPoints.size
            if (totalCount == 0) return@launch
            
            _debugStatus.value = "Saving $totalCount pts..."
            
            // 1. Calculate Midpoint
            var sumLat: Double = 0.0
            var sumLng: Double = 0.0
            for (p in processedSessionPoints) {
                sumLat += p.latitude
                sumLng += p.longitude
            }
            val avgLat = sumLat / totalCount
            val avgLon = sumLng / totalCount
            
            // 2. Encode
            val pathJson = StringBuilder("[")
            for (i in processedSessionPoints.indices) {
                if (i > 0) pathJson.append(",")
                val p = processedSessionPoints[i]
                pathJson.append("{\"lat\":${p.latitude},\"lon\":${p.longitude}}")
            }
            pathJson.append("]")
            
            val log = com.example.alltodo.data.UserLog(
                latitude = avgLat,
                longitude = avgLon,
                startTime = start,
                endTime = endTime,
                pathData = pathJson.toString()
            )
            
            userLogDao.insertLog(log)
            
            val msg = "Session Saved: $totalCount pts"
            com.example.alltodo.services.RemoteLogger.info(msg)
            // [FIX] Send Data to Server
            com.example.alltodo.services.RemoteLogger.log("PATH_DATA", pathJson.toString())
            _debugStatus.value = "Done. $totalCount pts saved."
            
            // 3. Clear (on Main Thread if needed, but this is new scope)
            withContext(Dispatchers.Main) {
                processedSessionPoints.clear()
                pendingBuffer.clear()
                _liveSessionPoints.value = emptyList()
                sessionStartTime = 0
                loadUserLogs()
            }
        }
    }
    
    private suspend fun processBuffer(force: Boolean = false) {
        if (pendingBuffer.isEmpty()) return
        
        // Snapshot
        val pointsToProcess = ArrayList(pendingBuffer)
        pendingBuffer.clear()
        
        // Run in background to avoid blocking UI with WASM
        withContext(Dispatchers.Default) {
             val flatPoints = pointsToProcess.flatMap { 
                 listOf((it.latitude * 100_000).toInt(), (it.longitude * 100_000).toInt()) 
             }
             
             // WASM Compress
             val compressedFlat = wasmManager.compress(flatPoints) // This logs internally too? Check WasmManager.
             // WasmManager logs "WASM Success...". We might want to add batch info here.
             
             // Reconstruct
             val newProcessed = mutableListOf<LocationEntity>()
             for (i in 0 until compressedFlat.size step 2) {
                 val lat = compressedFlat[i] / 100_000.0
                 val lng = (compressedFlat.getOrNull(i+1) ?: 0) / 100_000.0
                 newProcessed.add(LocationEntity(latitude = lat, longitude = lng, timestamp = System.currentTimeMillis()))
             }
             
             // Add to main list (Thread safe access needed? ViewModel is Main Thread usually, but we are in Default. 
             // Switch back to Main for modifying state variables if needed, but ArrayList is not thread safe.
             // Best to switch to Main to append.)
             withContext(Dispatchers.Main) {
                 processedSessionPoints.addAll(newProcessed)
                 _liveSessionPoints.value = ArrayList(processedSessionPoints) // Trigger UI update (StateFlow needs new ref or list content change)
                 val logMsg = "Batch RDP: ${pointsToProcess.size} -> ${newProcessed.size} pts"
                 com.example.alltodo.services.RemoteLogger.info(logMsg)
                 _debugStatus.value = "Rec: ${processedSessionPoints.size} pts"
             }
        }
    }

    private fun loadTodos() {
        viewModelScope.launch {
            todoRepository.allTodos.collect { items: List<TodoItem> ->
                _todoItems.value = items
            }
        }
    }

    private fun loadTodayLocations() {
        viewModelScope.launch {
            locationRepository.getTodayLocations().collect { locations: List<LocationEntity> ->
                _todayLocations.value = locations
            }
        }
    }
    
    private fun loadUserLogs() {
        viewModelScope.launch {
            userLogDao.getAllLogs().collect { logs ->
                _userLogs.value = logs
            }
        }
    }

    fun addTodo(text: String) {
        viewModelScope.launch {
            todoRepository.insert(TodoItem(text = text, source = "local"))
        }
    }

    fun addTodo(text: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            todoRepository.insert(TodoItem(
                text = text, 
                source = "local",
                latitude = latitude,
                longitude = longitude,
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    fun toggleTodo(item: TodoItem) {
        viewModelScope.launch {
            todoRepository.update(item.copy(completed = !item.completed))
        }
    }

    fun deleteTodo(item: TodoItem) {
        viewModelScope.launch {
            todoRepository.delete(item)
        }
    }
    
    fun deleteUserLog(log: com.example.alltodo.data.UserLog) {
        viewModelScope.launch {
            userLogDao.deleteLog(log)
            loadUserLogs()
        }
    }

    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            locationRepository.delete(location)
            loadTodayLocations()
        }
    }

    fun convertLocationToTodo(location: LocationEntity) {
        viewModelScope.launch {
            val newTodo = TodoItem(
                text = "위치 할 일",
                completed = false,
                source = "local",
                latitude = location.latitude,
                longitude = location.longitude,
                createdAt = System.currentTimeMillis()
            )
            todoRepository.insert(newTodo)
            locationRepository.delete(location)
            loadTodos()
            loadTodayLocations()
        }
    }

    fun saveLocation(latitude: Double, longitude: Double) {
        // Buffering Logic: Only record every 1 second (Optimized)
        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 1000) {
            return
        }
        
        lastRecordedTime = now
        val entity = LocationEntity(
            latitude = latitude,
            longitude = longitude,
            timestamp = now
        )
        
        // Add to Buffer
        pendingBuffer.add(entity)
        
        // Update Status
        _debugStatus.value = "Rec: ${processedSessionPoints.size} + ${pendingBuffer.size} buf"
        
        // Check Batch Size (5)
        if (pendingBuffer.size >= 5) {
            viewModelScope.launch {
                processBuffer()
            }
        }
    }
}

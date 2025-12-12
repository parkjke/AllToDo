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
import kotlinx.coroutines.NonCancellable // [FIX] Import
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

    // [NEW] Unified Data & Clustering
    private val _displayItems = MutableStateFlow<List<com.example.alltodo.ui.UnifiedItem>>(emptyList())
    val displayItems: StateFlow<List<com.example.alltodo.ui.UnifiedItem>> = _displayItems.asStateFlow()

    data class PinClusterItem(
        val latitude: Double,
        val longitude: Double,
        val count: Int,
        val items: List<com.example.alltodo.ui.UnifiedItem>
    )

    private val _clusteredItems = MutableStateFlow<List<PinClusterItem>>(emptyList())
    val clusteredItems: StateFlow<List<PinClusterItem>> = _clusteredItems.asStateFlow()

    private val _currentZoom = MutableStateFlow(15f)
    private val _showHistoryMode = MutableStateFlow(false)
    val showHistoryMode: StateFlow<Boolean> = _showHistoryMode.asStateFlow()

    fun updateZoom(zoom: Float) {
        if (_currentZoom.value != zoom) {
             _currentZoom.value = zoom
             recalculateClusters()
        }
    }
    
    fun toggleHistoryMode() {
        _showHistoryMode.value = !_showHistoryMode.value
        updateFilteredItems()
    }
    
    private fun updateFilteredItems() {
        viewModelScope.launch(Dispatchers.Default) {
             val todos = _todoItems.value
             val logs = _userLogs.value
             val isHistory = _showHistoryMode.value
             
             val now = System.currentTimeMillis()
             val oneDay = 24 * 60 * 60 * 1000L
             // If History Mode: Show items from Yesterday
             // If Normal Mode: Show items from Today
             // (Logic copied from MainScreen)
             val targetTime = if (isHistory) now - oneDay else now
             val minTime = targetTime - oneDay
             val maxTime = targetTime + oneDay

             val filteredLogItems = logs.filter { it.startTime in minTime..maxTime }.map { com.example.alltodo.ui.UnifiedItem.History(it) }
             val filteredTodoItems = if (isHistory) {
                 emptyList()
             } else {
                 todos.filter {
                     val t = it.createdAt
                     t in minTime..maxTime
                 }.map { com.example.alltodo.ui.UnifiedItem.Todo(it) }
             }
             
             val combined = filteredLogItems + filteredTodoItems
             withContext(Dispatchers.Main) {
                 _displayItems.value = combined
                 recalculateClusters()
             }
        }
    }

    private fun recalculateClusters() {
        val items = _displayItems.value
        val zoom = _currentZoom.value
        if (items.isEmpty()) {
            _clusteredItems.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
             // 1. Prepare Points for WASM
             // [lat, lng, lat, lng...] * 100000
             val flatPoints = items.flatMap { item ->
                 val (lat, lng) = when(item) {
                     is com.example.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                     is com.example.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                     else -> null to null
                 }
                 if (lat != null && lng != null && lat != 0.0) {
                     listOf((lat * 100_000).toInt(), (lng * 100_000).toInt())
                 } else {
                     emptyList()
                 }
             }
             
             if (flatPoints.isEmpty()) {
                 _clusteredItems.value = emptyList()
                 return@launch
             }

             // 2. Calculate Cell Size based on Zoom
             // Resolution (m/px) ~= 156543 * cos(lat) / 2^zoom
             // Let's assume lat=37, cos(37) ~= 0.798
             // Res ~= 125000 / 2^zoom
             // Cell Size (100px) ~= 12,500,000 / 2^zoom
             val resolution = 12_500_000.0 / Math.pow(2.0, zoom.toDouble())
             val cellSizeMeters = resolution.toInt().coerceAtLeast(10) // Min 10m

             // 3. Call WASM
             // Returns [lat, lng, count, lat, lng, count...]
             val clustersFlat = wasmManager.cluster(flatPoints, cellSizeMeters)
             
             // 4. Map Clusters to Items (Nearest Neighbor Assignment)
             val newClusters = mutableListOf<PinClusterItem>()
             
             // Parse Clusters
             for (i in 0 until clustersFlat.size step 3) {
                 val cLat = clustersFlat[i] / 100_000.0
                 val cLng = clustersFlat[i+1] / 100_000.0
                 val count = clustersFlat[i+2]
                 
                 // Find items belonging to this cluster
                 // Since WASM only returns counts, we need to re-assign.
                 // Ideally WASM should return Indices, but per current spec it returns Count.
                 // We will simply assign items to the closest cluster center.
                 
                 // NOTE: This logic is imperfect efficiently (O(N*K)), but for <1000 items it's fine.
                 newClusters.add(PinClusterItem(cLat, cLng, count, mutableListOf()))
             }
             
             // Assign Items
             // Optimization: Prepare cluster centers list first?
             if (newClusters.isNotEmpty()) {
                 items.forEach { item ->
                     val (lat, lng) = when(item) {
                         is com.example.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                         is com.example.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                         else -> null to null
                     }
                     if (lat != null && lng != null) {
                         // Find nearest cluster
                         var minDist = Double.MAX_VALUE
                         var bestCluster: PinClusterItem? = null
                         
                         for (cluster in newClusters) {
                             val dLat = cluster.latitude - lat
                             val dLng = cluster.longitude - lng
                             val distSq = dLat*dLat + dLng*dLng
                             if (distSq < minDist) {
                                 minDist = distSq
                                 bestCluster = cluster
                             }
                         }
                         
                         // Cast to MutableList to add (dirty but works if we recreated PinClusterItem or held generic list)
                         // Actually PinClusterItem items is List val. We need a helper DTO or modify list.
                         // Let's assume we can somehow mutate or we rebuild.
                         // Rebuilding map is cleaner.
                         (bestCluster?.items as? MutableList)?.add(item)
                     }
                 }
             }

             withContext(Dispatchers.Main) {
                 _clusteredItems.value = newClusters
             }
        }
    }

    
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

        // [FIX] Snapshot data immediately to prevent Race Condition with clear()
        val finalPoints = ArrayList(processedSessionPoints)
        if (pendingBuffer.isNotEmpty()) {
            finalPoints.addAll(pendingBuffer)
        }

        // Cleanup immediately (UI Thread)
        processedSessionPoints.clear()
        pendingBuffer.clear()
        _liveSessionPoints.value = emptyList()
        sessionStartTime = 0

        if (finalPoints.isEmpty()) return

        // [FIX] Use NonCancellable to ensure save completes in background without blocking Main Thread (No ANR)
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val totalCount = finalPoints.size
                    
                    // 1. Calculate Midpoint
                    var sumLat: Double = 0.0
                    var sumLng: Double = 0.0
                    for (p in finalPoints) {
                        sumLat += p.latitude
                        sumLng += p.longitude
                    }
                    val avgLat = sumLat / totalCount
                    val avgLon = sumLng / totalCount
                    
                    // 2. Encode
                    val pathJson = StringBuilder("[")
                    for (i in finalPoints.indices) {
                        if (i > 0) pathJson.append(",")
                        val p = finalPoints[i]
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
                    
                    // Save to DB (Suspend safe)
                    userLogDao.insertLog(log)
                    
                    // Remote Log
                    com.example.alltodo.services.RemoteLogger.info("Session Saved (Async): $totalCount pts")
                    com.example.alltodo.services.RemoteLogger.log("PATH_DATA", pathJson.toString())

                    // Refresh logs on UI
                    loadUserLogs()

                } catch (e: Exception) {
                    e.printStackTrace()
                    com.example.alltodo.services.RemoteLogger.error("Failed to save session: ${e.message}")
                }
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
                updateFilteredItems()
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
                updateFilteredItems()
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
        // Buffering Logic: Only record every 0.9 second (Optimized)
        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 900) {
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

package kr.alltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.alltodo.data.TodoItem
import kr.alltodo.data.TodoRepository
import kr.alltodo.data.LocationRepository
import kr.alltodo.data.LocationEntity
import kr.alltodo.data.IntCoordinate // [NEW]
// import kr.alltodo.data.TrajectoryCompressor // [DELETED] Native Logic Removed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job // [FIX] Added
import kotlinx.coroutines.delay // [FIX] Added
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable // [FIX] Import
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val locationRepository: LocationRepository,
    private val userLogDao: kr.alltodo.data.UserLogDao,
    private val wasmManager: kr.alltodo.wasm.WasmManager // [FIX] Hilt Injected
) : ViewModel() {

    // [REMOVED] Manual Wasm Manager Instance
    // private val wasmManager = kr.alltodo.wasm.WasmManager(context)

    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems.asStateFlow()

    private val _todayLocations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val todayLocations: StateFlow<List<LocationEntity>> = _todayLocations.asStateFlow()
    
    // Session Management
    private val _userLogs = MutableStateFlow<List<kr.alltodo.data.UserLog>>(emptyList())
    val userLogs: StateFlow<List<kr.alltodo.data.UserLog>> = _userLogs.asStateFlow()
    
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
    private val _displayItems = MutableStateFlow<List<kr.alltodo.ui.UnifiedItem>>(emptyList())
    val displayItems: StateFlow<List<kr.alltodo.ui.UnifiedItem>> = _displayItems.asStateFlow()

    data class PinClusterItem(
        val latitude: Double,
        val longitude: Double,
        val count: Int,
        val items: List<kr.alltodo.ui.UnifiedItem>
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
    
    private val _currentLocation = MutableStateFlow<UnifiedItem.CurrentLocation?>(null)
    private var filterJob: Job? = null

    fun updateCurrentLocation(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        val newLoc = UnifiedItem.CurrentLocation(lat, lon)
        // Only update if changed significantly or first time (Optimization can be added here)
        _currentLocation.value = newLoc
        // Trigger filtering/clustering immediately but protected by debounce in updateFilteredItems
        updateFilteredItems()
    }

    private fun updateFilteredItems() {
        // [FIX] Debounce logic to prevent UI freeze on high-frequency location updates (0.9s)
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
             delay(500) // Wait 500ms for settlement
             
             val todos = _todoItems.value
             val logs = _userLogs.value
             val isHistory = _showHistoryMode.value
             val currentLoc = _currentLocation.value

             
             val now = System.currentTimeMillis()
             val oneDay = 24 * 60 * 60 * 1000L
             // If History Mode: Show items from Yesterday
             // If Normal Mode: Show items from Today
             val targetTime = if (isHistory) now - oneDay else now
             val minTime = targetTime - oneDay
             val maxTime = targetTime + oneDay

             // [DEBUG] Log Raw Counts

             val filteredLogItems = logs.filter { it.startTime in minTime..maxTime }.map { kr.alltodo.ui.UnifiedItem.History(it) }
             
             // [FIX] Temporarily Disable Time Filter for Todos to show ALL pins
             val filteredTodoItems = if (isHistory) {
                 emptyList()
             } else {
                 // Show ALL Todos regardless of time to debug "Missing Pins"
                 todos.map { kr.alltodo.ui.UnifiedItem.Todo(it) }
             }
             
             var combined = filteredLogItems + filteredTodoItems
             
             // [FIX] Add Current Location to display items
             if (currentLoc != null) {
                 combined = combined + currentLoc
             }
             

             withContext(Dispatchers.Main) {
                 _displayItems.value = combined
                 recalculateClusters()
             }
        }
    }

    // [NEW] Delayed Clustering State
    private val _isClusteringEnabled = MutableStateFlow(false)
    val isClusteringEnabled: StateFlow<Boolean> = _isClusteringEnabled.asStateFlow()

    fun enableClustering() {
        if (!_isClusteringEnabled.value) {
            _isClusteringEnabled.value = true
            recalculateClusters()
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
                     is kr.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                     is kr.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                     is kr.alltodo.ui.UnifiedItem.CurrentLocation -> item.lat to item.lon
                 }
                 // [DEBUG] Check individual item coordinates

                 if (lat != null && lng != null && lat != 0.0) {
                     listOf((lat * 100_000).toInt(), (lng * 100_000).toInt())
                 } else {
                     emptyList()
                 }
             }
             
             // [DEBUG] Log valid points count to identify if items are lost here

             if (flatPoints.isEmpty()) {
                 _clusteredItems.value = emptyList()
                 return@launch
             }

             // [FIX] Delayed Clustering Check
             // If disabled (Initial Launch), skip WASM and show raw items
             if (!_isClusteringEnabled.value) {
                 val rawClusters = mutableListOf<PinClusterItem>()
                 items.forEach { item ->
                     val (lat, lng) = when(item) {
                         is kr.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                         is kr.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                         is kr.alltodo.ui.UnifiedItem.CurrentLocation -> item.lat to item.lon
                     }
                     if (lat != null && lng != null && lng != 0.0) {
                         val list = ArrayList<UnifiedItem>()
                         list.add(item)
                         rawClusters.add(PinClusterItem(lat, lng, 1, list))
                     }
                 }
                 withContext(Dispatchers.Main) {
                     _clusteredItems.value = rawClusters
                 }
                 return@launch
             }

             // 2. Calculate Cell Size based on Zoom
             // [FIX] Dynamic Clustering Radius to ensure unclustering at high zoom
             // Constant (12M) / 2^zoom approx equals 75px visual radius on screen.
             // Zoom 15: ~366 units (~400m)
             // Zoom 20: ~11 units (~12m) -> Allows separation of close pins
             val resolution = 12_000_000.0 / Math.pow(2.0, zoom.toDouble())
             val cellSizeMeters = resolution.toInt().coerceAtLeast(2) 

             // 3. Call WASM
             // Returns [lat, lng, count, lat, lng, count...]
             // [FIX] Now suspend function, non-blocking UI
             var clustersFlat = try {
                 wasmManager.cluster(flatPoints, cellSizeMeters)
             } catch (e: Exception) {
                 emptyList<Int>()
             }
             
             
             // [FIX] Fallback for WASM Failure (or Self-Test Failure) or Empty Result despite valid inputs
             if (clustersFlat.isEmpty() && flatPoints.isNotEmpty()) {
                 // Fallback: Create 1 item per valid point
                 val fallbackClusters = mutableListOf<PinClusterItem>()
                 items.forEach { item ->
                     val (lat, lng) = when(item) {
                         is kr.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                         is kr.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                         is kr.alltodo.ui.UnifiedItem.CurrentLocation -> item.lat to item.lon
                     }
                     if (lat != null && lng != null && lng != 0.0) { // Should match validation logic
                         val list = ArrayList<UnifiedItem>()
                         list.add(item)
                         fallbackClusters.add(PinClusterItem(lat, lng, 1, list))
                     }
                 }
                 withContext(Dispatchers.Main) {
                     _clusteredItems.value = fallbackClusters
                 }
                 System.out.println(">>> WASM [TodoViewModel] Fallback Triggered (Empty Result from WASM)")
                 return@launch
             }
             
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
                         is kr.alltodo.ui.UnifiedItem.Todo -> item.item.latitude to item.item.longitude
                         is kr.alltodo.ui.UnifiedItem.History -> item.log.latitude to item.log.longitude
                         is kr.alltodo.ui.UnifiedItem.CurrentLocation -> item.lat to item.lon
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
            // [FIX] Trigger Recalculation now that WASM is ready (if clustering is enabled)
            recalculateClusters()
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
        try {
            val start = sessionStartTime
            // [FIX] Guard: If session not started or empty, skip
            if (start == 0L || (processedSessionPoints.isEmpty() && pendingBuffer.isEmpty())) {
                _debugStatus.value = "Session Empty/Inactive"
                return
            }
            
            val now = System.currentTimeMillis()
            val endTime = now

            // [ROBUST] Snapshot data immediately
            val finalPoints = ArrayList(processedSessionPoints)
            if (pendingBuffer.isNotEmpty()) {
                finalPoints.addAll(pendingBuffer)
            }
            
            // [ROBUST] Reset State immediately to prevent Double-Save
            processedSessionPoints.clear()
            pendingBuffer.clear()
            _liveSessionPoints.value = emptyList()
            sessionStartTime = 0
            
            if (finalPoints.isEmpty()) return

            val pointCount = finalPoints.size
            kr.alltodo.services.RemoteLogger.info(">>> Ending Session. Saving $pointCount points (Start: $start)")

            // [ROBUST] Launch with NonCancellable to survive ViewModel clearing during save
            // Using IO Dispatcher for DB operations
            viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                try {
                    // 1. Calculate Midpoint
                    var sumLat: Double = 0.0
                    var sumLng: Double = 0.0
                    for (p in finalPoints) {
                        sumLat += p.latitude
                        sumLng += p.longitude
                    }
                    val avgLat = sumLat / pointCount
                    val avgLon = sumLng / pointCount
                    
                    // 2. Encode Path
                    val pathJson = StringBuilder("[")
                    for (i in finalPoints.indices) {
                        if (i > 0) pathJson.append(",")
                        val p = finalPoints[i]
                        pathJson.append("{\"lat\":${p.latitude},\"lon\":${p.longitude}}")
                    }
                    pathJson.append("]")
                    
                    val log = kr.alltodo.data.UserLog(
                        latitude = avgLat,
                        longitude = avgLon,
                        startTime = start,
                        endTime = endTime,
                        pathData = pathJson.toString()
                    )
                    
                    // 3. Save to DB
                    userLogDao.insertLog(log)
                    
                    kr.alltodo.services.RemoteLogger.info(">>> Session Saved Successfully to DB.")
                    
                    // Refresh logs on Main Thread
                    withContext(Dispatchers.Main) {
                        loadUserLogs()
                        _debugStatus.value = "Saved $pointCount pts"
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    kr.alltodo.services.RemoteLogger.error(">>> CRITICAL: Failed to save session: ${e.message}")
                    // Note: Data is in finalPoints snapshot. We could try to recover here if critical.
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Sync Error
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
             // [FIX] WebView methods must be called on Main Thread
             val compressedFlat = withContext(Dispatchers.Main) {
                 wasmManager.compress(flatPoints)
             }
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
                 kr.alltodo.services.RemoteLogger.info(logMsg)
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
    
    fun deleteUserLog(log: kr.alltodo.data.UserLog) {
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
        // [FIX] Validate Coordinates: Ignore (0,0) to prevent corrupting history/path data
        if (latitude == 0.0 && longitude == 0.0) {
             return
        }

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

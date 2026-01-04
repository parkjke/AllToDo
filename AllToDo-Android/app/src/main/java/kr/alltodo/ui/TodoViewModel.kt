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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job // [FIX] Added
import kotlinx.coroutines.delay // [FIX] Added
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable // [FIX] Import
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val locationRepository: LocationRepository,
    private val wasmManager: kr.alltodo.wasm.WasmManager // [FIX] Hilt Injected
) : ViewModel() {

    // [REMOVED] Manual Wasm Manager Instance
    // private val wasmManager = kr.alltodo.wasm.WasmManager(context)

    private val _todoItems = MutableStateFlow<List<TodoItem>>(emptyList())
    val todoItems: StateFlow<List<TodoItem>> = _todoItems.asStateFlow()

    private val _todayLocations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val todayLocations: StateFlow<List<LocationEntity>> = _todayLocations.asStateFlow()
    
    // [NEW] Path Viewer State (Now using TodoItem)
    private val _selectedHistoryPath = MutableStateFlow<List<kr.alltodo.data.PathItem>>(emptyList())
    val selectedHistoryPath: StateFlow<List<kr.alltodo.data.PathItem>> = _selectedHistoryPath.asStateFlow()
    
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

    private val _farItemCount = MutableStateFlow(0)
    val farItemCount: StateFlow<Int> = _farItemCount.asStateFlow()

    private val _clusteredItems = MutableStateFlow<List<PinClusterItem>>(emptyList())
    val clusteredItems: StateFlow<List<PinClusterItem>> = _clusteredItems.asStateFlow()

    private val _recentNames = MutableStateFlow<List<String>>(emptyList())
    val recentNames: StateFlow<List<String>> = _recentNames.asStateFlow()

    private val _recentMemos = MutableStateFlow<List<String>>(emptyList())
    val recentMemos: StateFlow<List<String>> = _recentMemos.asStateFlow()

    private val _currentZoom = MutableStateFlow(15f)
    private val _showHistoryMode = MutableStateFlow(false)
    val showHistoryMode: StateFlow<Boolean> = _showHistoryMode.asStateFlow()

    // [NEW] Todo List Layer States
    private val _isListLayerVisible = MutableStateFlow(false)
    val isListLayerVisible: StateFlow<Boolean> = _isListLayerVisible.asStateFlow()

    private val _sortByTime = MutableStateFlow(true) // true: Time, false: Color (Blue-Green-Red)
    val sortByTime: StateFlow<Boolean> = _sortByTime.asStateFlow()

    private val _filterServer = MutableStateFlow(true)
    val filterServer: StateFlow<Boolean> = _filterServer.asStateFlow()

    private val _filterTodo = MutableStateFlow(true)
    val filterTodo: StateFlow<Boolean> = _filterTodo.asStateFlow()

    private val _filterHistory = MutableStateFlow(true)
    val filterHistory: StateFlow<Boolean> = _filterHistory.asStateFlow()

    fun toggleListLayer() { _isListLayerVisible.value = !_isListLayerVisible.value }
    fun setSortByTime(value: Boolean) { _sortByTime.value = value; updateFilteredItems(immediate = true) }
    fun toggleFilterServer() { _filterServer.value = !_filterServer.value; updateFilteredItems(immediate = true) }
    fun toggleFilterTodo() { _filterTodo.value = !_filterTodo.value; updateFilteredItems(immediate = true) }
    fun toggleFilterHistory() { _filterHistory.value = !_filterHistory.value; updateFilteredItems(immediate = true) }

    fun updateZoom(zoom: Float) {
        if (_currentZoom.value != zoom) {
             _currentZoom.value = zoom
             recalculateClusters()
        }
    }
    
    fun toggleHistoryMode() {
        _showHistoryMode.value = !_showHistoryMode.value
        updateFilteredItems(immediate = true)
    }

    private var lastFilteredTodoCount = -1
    private var lastFilteredLogCount = -1
    private var lastTotalTodoCount = -1
    
    private val _currentLocation = MutableStateFlow<UnifiedItem.CurrentLocation?>(null)
    private var filterJob: Job? = null

    fun updateCurrentLocation(lat: Double, lon: Double) {
        if (lat == 0.0 && lon == 0.0) return
        val newLoc = UnifiedItem.CurrentLocation(lat, lon)
        _currentLocation.value = newLoc
        
        // [NEW] Update 0-th record in DB for persistent "Last Known Position"
        viewModelScope.launch(Dispatchers.IO) {
            todoRepository.updateCurrentLocation(lat, lon)
        }
        
        updateFilteredItems()
    }

    fun updateFilteredItems(immediate: Boolean = false) {
        // [FIX] Debounce logic for location updates, but bypass for manual actions
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
             if (!immediate) {
                 delay(200)
             }
             
             val todos = _todoItems.value
             val historyItems = todos.filter { it.type == "00" }
             val normalTodos = todos.filter { it.type == "10" }

             val isHistory = _showHistoryMode.value
             val currentLoc = _currentLocation.value

             val now = System.currentTimeMillis()
             val oneDay = 24 * 60 * 60 * 1000L
             // If History Mode: Show items from Yesterday
             // If Normal Mode: Show items from Today
             val targetTime = if (isHistory) now - oneDay else now
             val minTime = targetTime - oneDay
             val maxTime = Long.MAX_VALUE // [FIX] Future tasks are now always visible

             // [NEW] Apply Multi-Category Filter
             val filteredLogItems = if (_filterHistory.value) {
                 historyItems.filter { (it.int_lat != null) && (it.begin_time ?: it.created_at) in minTime..maxTime }
                     .map { kr.alltodo.ui.UnifiedItem.History(it) }
             } else emptyList()

             val filteredTodoItems = normalTodos.filter { (it.int_lat != null) && it.created_at in minTime..maxTime }
                 .filter { item ->
                     val isServer = item.source != "local"
                     if (isServer) _filterServer.value else _filterTodo.value
                 }
                 .map { kr.alltodo.ui.UnifiedItem.Todo(it) }

             // [NEW] Combine & Sort
             val combinedRaw = filteredLogItems + filteredTodoItems
             val sorted = if (_sortByTime.value) {
                 combinedRaw.sortedByDescending { it.createdAt }
             } else {
                 // Color Sort: Blue (Server) -> Green (Local) -> Red (History)
                 combinedRaw.sortedWith(compareBy<kr.alltodo.ui.UnifiedItem> {
                     when (it) {
                         is kr.alltodo.ui.UnifiedItem.Todo -> if (it.item.source != "local") 0 else 1
                         is kr.alltodo.ui.UnifiedItem.History -> 2
                         else -> 3
                     }
                 }.thenByDescending { it.createdAt })
             }

             // [DEBUG LOGGING] Log only if counts changed or immediate
             val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
             val currentFilteredTodoCount = filteredTodoItems.size
             val currentFilteredLogCount = filteredLogItems.size
             val rawTodoCount = normalTodos.size
             val rawLogCount = historyItems.size

             if (immediate || currentFilteredTodoCount != lastFilteredTodoCount || currentFilteredLogCount != lastFilteredLogCount || rawTodoCount != lastTotalTodoCount) {
                 println("$timeStr >>> [Map Display] Raw DB Counts: Todos=$rawTodoCount, Logs=$rawLogCount")
                 println("$timeStr >>> [Map Display] Filter Window: $minTime ~ $maxTime")
                 println("$timeStr >>> [Map Display] Filtered Counts: Todos=$currentFilteredTodoCount, History=$currentFilteredLogCount")
                 
                 if (rawTodoCount > 0 && currentFilteredTodoCount == 0) {
                     println("$timeStr >>> [ALERT] $rawTodoCount Todos exist in DB but 0 passed the 24h filter window!")
                 }
                 
                 lastFilteredTodoCount = currentFilteredTodoCount
                 lastFilteredLogCount = currentFilteredLogCount
                 lastTotalTodoCount = rawTodoCount
             }

             val combined = sorted

             // [NEW] 500km Filter Logic
             val farThreshold = 500_000f // 500km
             val visibleItems = mutableListOf<kr.alltodo.ui.UnifiedItem>()
             var farCount = 0

             if (currentLoc != null) {
                 // Always include current location
                 visibleItems.add(currentLoc)

                 combined.forEach { item ->
                     if (item is kr.alltodo.ui.UnifiedItem.CurrentLocation) return@forEach // already added
                     
                     val results = FloatArray(1)
                     android.location.Location.distanceBetween(
                         currentLoc.lat, currentLoc.lon,
                         item.latitude, item.longitude,
                         results
                     )
                     if (results[0] <= farThreshold) {
                         visibleItems.add(item)
                     } else {
                         farCount++
                     }
                 }
             } else {
                 // If no current loc yet, show all (or wait)
                 visibleItems.addAll(combined)
             }

             withContext(Dispatchers.Main) {
                  _farItemCount.value = farCount
                  _displayItems.value = visibleItems
                  recalculateClusters()
             }
        }
    }

    // [NEW] Delayed Clustering State
    private val _isClusteringEnabled = MutableStateFlow(false)
    val isClusteringEnabled: StateFlow<Boolean> = _isClusteringEnabled.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    fun toggleTracking() {
        if (_isTracking.value) {
            endSession()
            _isTracking.value = false
        } else {
            startSession()
            _isTracking.value = true
        }
    }

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
             val flatPoints = items.flatMap { item ->
                 if (item.intLat != 0) {
                     listOf(item.intLat, item.intLng)
                 } else {
                     emptyList()
                 }
             }
             
             // [DEBUG] Log valid points count to identify if items are lost here

             if (flatPoints.isEmpty()) {
                 _clusteredItems.value = emptyList()
                 return@launch
             }

             // 2. Clustering Check
             if (!_isClusteringEnabled.value) {
                 _clusteredItems.value = items.map { item ->
                      PinClusterItem(item.intLat, item.intLng, 1, listOf(item))
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
             
             
             // Fallback
             if (clustersFlat.isEmpty()) {
                  _clusteredItems.value = items.map { item ->
                      PinClusterItem(item.intLat, item.intLng, 1, listOf(item))
                 }
                 return@launch
             }
             
             // 4. Map Clusters to Items (Nearest Neighbor Assignment)
             val newClusters = mutableListOf<PinClusterItem>()
             
             // Parse Clusters
             for (i in 0 until clustersFlat.size step 3) {
                 val cLat = clustersFlat[i]
                 val cLng = clustersFlat[i+1]
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
                     val lat = item.intLat
                     val lng = item.intLng
                     // Null check removed (non-nullable Integer-First)
                     if (true) {
                         // Find nearest cluster
                         var minDist = Double.MAX_VALUE
                         var bestCluster: PinClusterItem? = null
                         
                         for (cluster in newClusters) {
                             val dLat = cluster.intLat - lat
                             val dLng = cluster.intLng - lng
                             val distSq = dLat.toLong()*dLat + dLng.toLong()*dLng
                             if (distSq < minDist) {
                                 minDist = distSq.toDouble()
                                 bestCluster = cluster
                             }
                         }
                                                  (bestCluster?.items as? MutableList<UnifiedItem>)?.add(item)
                      }
                  }
              }

              // [FIX] Cluster Anchoring: If user is in cluster, force cluster to user position
              val finalClusters = newClusters.map { cluster ->
                  val userLoc = cluster.items.find { it is UnifiedItem.CurrentLocation }
                  if (userLoc != null) {
                      cluster.copy(intLat = userLoc.intLat, intLng = userLoc.intLng)
                  } else {
                      cluster
                  }
              }

             withContext(Dispatchers.Main) {
                 _clusteredItems.value = finalClusters
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
        
        // [NEW] Ensure CURRENT_LOCATION record exists
        viewModelScope.launch {
            todoRepository.ensureCurrentLocationExists()
        }
        
        toggleTracking() // [FIX] Use toggleTracking to ensure _isTracking flag is also set
    }
    

    
    // Lifecycle hooks called from MainScreen/MainActivity
    fun startSession() {
        processedSessionPoints.clear()
        pendingBuffer.clear()
        _liveSessionPoints.value = emptyList()
        sessionStartTime = System.currentTimeMillis()
        lastRecordedTime = 0
        currentSessionId = java.util.UUID.randomUUID().toString() // [NEW] Track session ID
        _debugStatus.value = "Recording... (0 pts)"
    }
    
    // [NEW] Track session ID for multi-part history
    private var currentSessionId: String? = null
    
    fun endSession(scope: kotlinx.coroutines.CoroutineScope = viewModelScope) {
        try {
            val start = sessionStartTime
            // [FIX] Guard: If session not started or empty, skip
            if (start == 0L || (processedSessionPoints.isEmpty() && pendingBuffer.isEmpty())) {
                _debugStatus.value = "Session Empty/Inactive"
                return
            }
            
            val now = System.currentTimeMillis()
            val endTime = now

            val pointCount = processedSessionPoints.size + pendingBuffer.size
            kr.alltodo.services.RemoteLogger.info(">>> Ending Session. Saving $pointCount points (Start: $start)")

            val snapshotPoints = ArrayList(processedSessionPoints)
            val snapshotBuffer = ArrayList(pendingBuffer)
            val oldSessionId = currentSessionId
            
            // [ROBUST] Reset State immediately to prevent Double-Save
            processedSessionPoints.clear()
            pendingBuffer.clear()
            _liveSessionPoints.value = emptyList()
            sessionStartTime = 0
            currentSessionId = null
            
            if (pointCount == 0) return

            // [ROBUST] Launch with NonCancellable to survive ViewModel clearing during save
            scope.launch(Dispatchers.IO + NonCancellable) {
                finalizeAndSaveSession(oldSessionId, start, now, snapshotPoints, snapshotBuffer)
                // Refresh data on Main Thread
                withContext(Dispatchers.Main) {
                    loadTodos()
                    _debugStatus.value = "Saved $pointCount pts"
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
                 val latInt = compressedFlat[i]
                 val lngInt = (compressedFlat.getOrNull(i+1) ?: 0)
                 newProcessed.add(LocationEntity(int_lat = latInt, int_long = lngInt, timestamp = System.currentTimeMillis()))
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
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
                println("$timeStr >>> [App Start] Total DB Todos: ${items.size}")
                _todoItems.value = items
                _recentNames.value = items.map { it.todo_name }.distinct().take(3)
                _recentMemos.value = items.mapNotNull { it.memo }.distinct().take(3)

                // [NEW] Use immediate refresh when todos change to feel 'instant'
                updateFilteredItems(immediate = true)
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
    
    fun fetchPathForHistory(todo: TodoItem) {
        _selectedHistoryPath.value = emptyList() // Clear old path
        viewModelScope.launch {
            try {
                // Use first() to get the current snapshot and terminate the flow
                val paths = todoRepository.getPathsForTodo(todo.todo_id).first()
                _selectedHistoryPath.value = paths
                val firstPts = paths.take(3).map { "(${it.int_lat},${it.int_long})" }
                println(">>> fetchPathForHistory: Loaded ${paths.size} points for ${todo.todo_id}. Sample: $firstPts")
            } catch (e: Exception) {
                e.printStackTrace()
                _selectedHistoryPath.value = emptyList()
            }
        }
    }

    fun addTodo(text: String, latitude: Double, longitude: Double, person: String? = null, date: String? = null, time: String? = null, memo: String? = null) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        println("$timeStr >>> [Add Todo] Intent: $text at ($latitude, $longitude)")
        
        viewModelScope.launch {
            val todo = TodoItem(
                todo_name = text, 
                source = "local",
                int_lat = (latitude * 100_000).toInt(),
                int_long = (longitude * 100_000).toInt(),
                person = person,
                date_time = if (date != null && time != null) "$date $time" else date ?: time,
                memo = memo,
                no_of_path = 1, // [FIX] Set to 1 so it passes the "no_of_path > 0" filter
                created_at = System.currentTimeMillis()
            )
            todoRepository.insert(todo)
            val finishTimeStr = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
            println("$finishTimeStr >>> [Add Todo] Saved OK: id=${todo.todo_id}")
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
    
    fun deleteLocation(location: LocationEntity) {
        viewModelScope.launch {
            locationRepository.delete(location)
            loadTodayLocations()
        }
    }

    fun convertLocationToTodo(location: LocationEntity) {
        viewModelScope.launch {
            val newTodo = TodoItem(
                todo_name = "위치 할 일",
                completed = false,
                source = "local",
                int_lat = location.int_lat,
                int_long = location.int_long,
                created_at = System.currentTimeMillis(),
                no_of_path = 1 // [FIX] Set to 1 so it passes the filter
            )
            todoRepository.insert(newTodo)
            locationRepository.delete(location)
            loadTodos()
            loadTodayLocations()
        }
    }

    fun saveLocation(latitude: Double, longitude: Double) {
        if (!_isTracking.value) return // [FIX] Only store when tracking is ON
        
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
            int_lat = (latitude * 100_000).toInt(),
            int_long = (longitude * 100_000).toInt(),
            timestamp = now
        )
        
        // [FIX] Proactively save to DB to survive crashes/termination
        viewModelScope.launch(Dispatchers.IO) {
            locationRepository.saveLocation(latitude, longitude)
        }

        // [NEW] Automatic Session Splitting (30 Minutes)
        val sessionDuration = now - sessionStartTime
        if (sessionDuration > 30 * 60 * 1000L) {
             kr.alltodo.services.RemoteLogger.info(">>> 30 mins passed. Splitting session...")
             val snapshotPoints = ArrayList(processedSessionPoints)
             val snapshotBuffer = ArrayList(pendingBuffer)
             val oldStartTime = sessionStartTime
             val oldSessionId = currentSessionId
             
             // Reset for NEW session
             sessionStartTime = now
             currentSessionId = java.util.UUID.randomUUID().toString()
             processedSessionPoints.clear()
             pendingBuffer.clear()
             
             // Finalize OLD session in background
             viewModelScope.launch(Dispatchers.IO + NonCancellable) {
                 finalizeAndSaveSession(oldSessionId, oldStartTime, now, snapshotPoints, snapshotBuffer)
             }
        }

        // Add to Buffer for live visualization/WASM compression
        pendingBuffer.add(entity)
        
        // [FIX] Instant Feedback: Update Live Points immediately
        // Containment Logic: Circular Buffer Max 1000 (DISABLED FOR STABILITY CHECK)
        // If total > 1000, remove from HEAD (Oldest)
        
        // 1. Check Total Size
        // var currentTotal = processedSessionPoints.size + pendingBuffer.size
        // val MAX_HISTORY = 1000
        //
        // val startTime = System.nanoTime() // [DEBUG] Perf check
        // var loopCount = 0
        //
        // while (currentTotal > MAX_HISTORY) {
        //     loopCount++
        //     if (processedSessionPoints.isNotEmpty()) {
        //         processedSessionPoints.removeAt(0)
        //     } else if (pendingBuffer.isNotEmpty()) {
        //         pendingBuffer.removeAt(0)
        //     }
        //     currentTotal--
        // }
        // val duration = (System.nanoTime() - startTime) / 1_000_000.0 // ms
        // if (duration > 1.0) {
        //     println(">>> [PerformanceWarning] Circular Buffer Trim: $duration ms (Removed $loopCount pts)")
        // }

        // Combine processed (RDP) + pending (Raw) for visualization
        val combined = ArrayList(processedSessionPoints)
        combined.addAll(pendingBuffer)
        _liveSessionPoints.value = combined
        
        // Update Status
        _debugStatus.value = "Rec: ${processedSessionPoints.size} + ${pendingBuffer.size} buf"
        
        // Check Batch Size (5)
        if (pendingBuffer.size >= 5) {
            viewModelScope.launch {
                processBuffer()
            }
        }
    }

    private suspend fun finalizeAndSaveSession(
        sessionId: String?,
        startTime: Long,
        endTime: Long,
        processedPoints: List<LocationEntity>,
        bufferPoints: List<LocationEntity>
    ) {
        try {
            val finalPoints = ArrayList(processedPoints)
            finalPoints.addAll(bufferPoints)
            if (finalPoints.isEmpty()) return

            val pointCount = finalPoints.size
            val todoId = sessionId ?: java.util.UUID.randomUUID().toString()

            // 1. Calculate Midpoint & Bounding Box (Standardized Integer Math)
            val intPoints = finalPoints.map { it.int_lat to it.int_long }
            val centroid = kr.alltodo.utils.GeomUtils.calculateCentroid(intPoints)

            // 2. Check for Short Path (under ~30m)
            val isShort = kr.alltodo.utils.GeomUtils.isShortPath(intPoints)
            val finalNoOfPath = if (isShort) 1 else pointCount

            // 3. Create/Insert TodoItem (Type: 00 - History)
            val historyTodo = TodoItem(
                todo_id = todoId,
                todo_name = "갔던 곳",
                type = "00", 
                no_of_path = finalNoOfPath,
                int_lat = centroid.first,
                int_long = centroid.second,
                begin_time = startTime,
                end_time = endTime,
                created_at = startTime
            )
            todoRepository.insert(historyTodo)

            // 4. Insert Paths
            if (!isShort) {
                val pathItems = finalPoints.map { p ->
                    kr.alltodo.data.PathItem(
                        todo_id = todoId,
                        int_long = p.int_long,
                        int_lat = p.int_lat,
                        time = p.timestamp
                    )
                }
                todoRepository.insertPaths(pathItems)
                kr.alltodo.services.RemoteLogger.info(">>> History Saved: $todoId ($pointCount pts)")
            } else {
                kr.alltodo.services.RemoteLogger.info(">>> Short Path: $todoId (Saved as point)")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kr.alltodo.services.RemoteLogger.error(">>> Failed to finalize session: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // [ROBUST] When app is terminated/swiped away, end current session and save path to History
        // Using a new scope because viewModelScope is already cancelled here
        val deathScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        endSession(deathScope)
    }
}

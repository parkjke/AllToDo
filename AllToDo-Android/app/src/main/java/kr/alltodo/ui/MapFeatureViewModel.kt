package kr.alltodo.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.alltodo.data.TodoItem
import kr.alltodo.utils.MapLogicHelper
import java.util.Date
import javax.inject.Inject

enum class MapAction {
    NONE,
    ZOOM_IN,
    ZOOM_OUT,
    CURRENT_LOCATION,
    ROTATE_NORTH,
    ZOOM_TO_FIT,
    MOVE_TO_LOCATION // [NEW]
}

@HiltViewModel
class MapFeatureViewModel @Inject constructor(
    private val wasmManager: kr.alltodo.wasm.WasmManager // [NEW] Injected for clustering
) : ViewModel() {

    // MARK: - Map Control State
    private val _mapAction = MutableStateFlow(MapAction.NONE)
    val mapAction: StateFlow<MapAction> = _mapAction.asStateFlow()
    
    // Reset action after consumption
    fun consumeMapAction() {
        _mapAction.value = MapAction.NONE
    }

    private val _compassRotation = MutableStateFlow(0f)
    val compassRotation: StateFlow<Float> = _compassRotation.asStateFlow()
    
    fun updateCompassRotation(rotation: Float) {
        _compassRotation.value = rotation
    }

    // [NEW] Destination Coordinate for MOVE_TO_LOCATION
    private val _targetLocation = MutableStateFlow<android.location.Location?>(null)
    val targetLocation: StateFlow<android.location.Location?> = _targetLocation.asStateFlow()

    fun moveToLocation(lat: Double, lng: Double) {
        val loc = android.location.Location("search").apply {
            latitude = lat
            longitude = lng
        }
        _targetLocation.value = loc
        _mapAction.value = MapAction.MOVE_TO_LOCATION
    }

    // MARK: - Item Selection & Interaction
    private val _selectedCluster = MutableStateFlow<List<UnifiedItem>?>(null)
    val selectedCluster: StateFlow<List<UnifiedItem>?> = _selectedCluster.asStateFlow()
    
    private val _tapPosition = MutableStateFlow<androidx.compose.ui.geometry.Offset?>(null)
    val tapPosition: StateFlow<androidx.compose.ui.geometry.Offset?> = _tapPosition.asStateFlow()
    
    // MARK: - Navigation & Sheet State (Consolidated Central Control)
    private val _showAllTodoSheet = MutableStateFlow(false)
    val showAllTodoSheet: StateFlow<Boolean> = _showAllTodoSheet.asStateFlow()

    private val _mainSheetTab = MutableStateFlow(0) // 0: List, 1: Calendar
    val mainSheetTab: StateFlow<Int> = _mainSheetTab.asStateFlow()

    private val _viewingPathTodo = MutableStateFlow<TodoItem?>(null)
    val viewingPathTodo: StateFlow<TodoItem?> = _viewingPathTodo.asStateFlow()

    private val _selectedItem = MutableStateFlow<TodoItem?>(null)
    val selectedItem: StateFlow<TodoItem?> = _selectedItem.asStateFlow()

    fun setShowAllTodoSheet(show: Boolean) {
        _showAllTodoSheet.value = show
    }

    fun setMainSheetTab(tab: Int) {
        _mainSheetTab.value = tab
    }

    fun setViewingPathTodo(item: TodoItem?) {
        _viewingPathTodo.value = item
    }

    fun setSelectedItem(item: TodoItem?) {
        _selectedItem.value = item
    }

    // Navigation Recovery State (Internal)
    var shouldRestoreList = false
    var shouldRestoreCalendar = false
    
    // MARK: - UI Flags
    private val _showHistoryMode = MutableStateFlow(false)
    val showHistoryMode: StateFlow<Boolean> = _showHistoryMode.asStateFlow()
    
    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()

    // MARK: - Dynamic Clustering & Filtering
    private val _cachedMapItems = MutableStateFlow<List<UnifiedItem>>(emptyList())
    // Display items (filtered by Korea rule if needed)
    private val _displayItems = MutableStateFlow<List<UnifiedItem>>(emptyList())
    val displayItems: StateFlow<List<UnifiedItem>> = _displayItems.asStateFlow()

    // [NEW] Clustered Items (Source of Truth for Map)
    private val _clusteredItems = MutableStateFlow<List<PinClusterItem>>(emptyList())
    val clusteredItems: StateFlow<List<PinClusterItem>> = _clusteredItems.asStateFlow()

    private val _currentZoom = MutableStateFlow(15f)
    val currentZoom: StateFlow<Float> = _currentZoom.asStateFlow()

    private val _currentProvider = MutableStateFlow(MapProvider.Naver)
    val currentProvider: StateFlow<MapProvider> = _currentProvider.asStateFlow()

    private val _isClusteringEnabled = MutableStateFlow(false)

    // [NEW] Standardized metersPerPixel injected from UI layer (Projection sampling)
    private val _metersPerPixel = MutableStateFlow(0.0)
    val metersPerPixel: StateFlow<Double> = _metersPerPixel.asStateFlow()

    fun updateMetersPerPixel(value: Double) {
        _metersPerPixel.value = value
    }

    private var currentLatitude = 37.5759 // Default Seoul

    fun updateZoom(zoom: Float, lat: Double? = null, provider: MapProvider? = null) {
        var changed = false
        if (Math.abs(_currentZoom.value - zoom) > 0.01f) {
            _currentZoom.value = zoom
            changed = true
        }
        if (lat != null && currentLatitude != lat) {
            currentLatitude = lat
            changed = true
        }
        if (provider != null && _currentProvider.value != provider) {
            _currentProvider.value = provider
            changed = true
        }
        
        if (changed) {
            // [FIX] Do NOT cluster here. Rely on handleCameraIdle.
            // recalculateClusters()
        }
    }

    fun enableClustering() {
        if (!_isClusteringEnabled.value) {
            _isClusteringEnabled.value = true
            // If already clustered, no need to recalc immediately unless force
        }
    }
    
    // [NEW] Clustering State
    private var lastClusteredScreenWidthMeters: Double = -1.0
    private var isInitialLaunch: Boolean = true

    // [NEW] Camera Idle Handler (Main Trigger)
    // Wm = Screen Width in Meters
    fun handleCameraIdle(screenWidthMeters: Double, zoom: Float, lat: Double, provider: MapProvider) {
        val wm1 = screenWidthMeters
        val wm0 = lastClusteredScreenWidthMeters
        
        // 1. Initial Launch Guard (Raw Phase)
        if (isInitialLaunch) return

        // 2. Threshold Check (1.5x Rule)
        var shouldRecluster = false
        
        if (wm0 < 0) {
            shouldRecluster = true
        } else {
            val zoomInTriggered  = 3.0 * wm1 <= 2.0 * wm0
            val zoomOutTriggered = 2.0 * wm1 >= 3.0 * wm0
            if (zoomInTriggered || zoomOutTriggered) {
                shouldRecluster = true
            }
        }
        
        if (shouldRecluster || _currentProvider.value != provider) {
            _currentProvider.value = provider
            currentLatitude = lat
            _currentZoom.value = zoom
            lastClusteredScreenWidthMeters = wm1
            
            // Re-trigger clustering logic with latest MPP (MPP is updated by MainScreen call)
            viewModelScope.launch {
                performClusteringLogic()
            }
        }
    }
    
    private val _farItemsCount = MutableStateFlow(0)
    val farItemsCount: StateFlow<Int> = _farItemsCount.asStateFlow()
    
    private val _showFarNotification = MutableStateFlow(false)
    val showFarNotification: StateFlow<Boolean> = _showFarNotification.asStateFlow()
    
    // Ignore filter flag (User override)
    private val _ignoreDistanceFilter = MutableStateFlow(false)
    
    // MARK: - Actions
    fun handleZoomIn() { _mapAction.value = MapAction.ZOOM_IN }
    fun handleZoomOut() { _mapAction.value = MapAction.ZOOM_OUT }
    fun handleLocationClick() { _mapAction.value = MapAction.CURRENT_LOCATION }
    fun handleCompassClick() { _mapAction.value = MapAction.ROTATE_NORTH }
    
    fun handleHistoryClick() {
        if (!_showHistoryMode.value) {
            _showHistoryMode.value = true
            _selectedDate.value = Date()
            _mapAction.value = MapAction.ZOOM_TO_FIT
        } else {
            // Show Calendar logic (View responsibility or another state)
        }
    }
    
    fun showFarItems() {
        _ignoreDistanceFilter.value = true
        // Re-trigger update logic
    }
    
    // MARK: - Data Update Logic
    
    // This function mimics the iOS 'updateMapItems'.
    // In Android/Compose, this might be triggered by collecting Flows from TodoViewModel.
    fun updateMapItems(
        allItems: List<TodoItem>,
        currentLocation: Location?,
        anchorDate: Date = Date(), // Default now
        mapProvider: MapProvider
    ) {
        viewModelScope.launch {
            // 1. Functional Core: Time & Path Filtering
            val transformed = MapLogicHelper.filterAndTransformItems(
                allItems = allItems,
                currentLocation = currentLocation,
                showHistoryMode = _showHistoryMode.value,
                anchorDate = anchorDate,
                selectedDate = _selectedDate.value
            )
            _cachedMapItems.value = transformed
            
            // 2. South Korea Partitioning
            val isLocalMap = (mapProvider == MapProvider.Kakao || mapProvider == MapProvider.Naver)
            
            if (isLocalMap && !_ignoreDistanceFilter.value) {
                // Background thread if huge data? (LogicHelper is fast enough for now)
                val partition = MapLogicHelper.partitionItemsByKorea(transformed)
                _displayItems.value = partition.nearItems
                
                if (partition.farCount != _farItemsCount.value) {
                    _farItemsCount.value = partition.farCount
                    _showFarNotification.value = (partition.farCount > 0)
                }
            } else {
                // Show All (Apple/Google or Override)
                _displayItems.value = transformed
                _showFarNotification.value = false
                _farItemsCount.value = 0
                _displayItems.value = transformed
                _showFarNotification.value = false
                _farItemsCount.value = 0
            }
            // 3. Trigger Clustering
            recalculateClusters()
        }
    }

    private fun recalculateClusters() {
        viewModelScope.launch {
            performClusteringLogic()
        }
    }


    private suspend fun performClusteringLogic() {
        val items = _displayItems.value
        val zoom = _currentZoom.value
        
        // 1. Validation
        if (items.isEmpty()) {
            _clusteredItems.value = emptyList()
            return
        }

        // 2. Prepare Data
        val flatPoints = convertToFlatPoints(items)
        if (flatPoints.isEmpty()) {
             _clusteredItems.value = emptyList()
             return
        }
        
        // 3. Early Exit: Clustering Disabled
        if (!_isClusteringEnabled.value) {
            _clusteredItems.value = createOneToOneClusters(items)
            return
        }

        // 4. Calculate Resolution
        val cellSizeMeters = calculateCellSizeMeters(zoom)

        // 5. Execute WASM Logic
        val clustersFlat = try {
             wasmManager.cluster(flatPoints, cellSizeMeters)
        } catch (e: Exception) {
             emptyList<Int>()
        }
        
        // 6. Process Results
        _clusteredItems.value = if (clustersFlat.isEmpty()) {
            createOneToOneClusters(items)
        } else {
            mapClustersToItems(clustersFlat, items)
        }
    }

    // MARK: - Clustering Helpers

    private fun convertToFlatPoints(items: List<UnifiedItem>): List<Int> {
        return items.flatMap { item ->
            if (item.intLat != 0) listOf(item.intLat, item.intLng) else emptyList()
        }
    }

    private fun createOneToOneClusters(items: List<UnifiedItem>): List<PinClusterItem> {
        return items.filter { it.intLat != 0 }.map { item ->
            PinClusterItem(item.intLat, item.intLng, 1, listOf(item))
        }.distinctBy { "${it.intLat}_${it.intLng}_${it.count}" }
    }

    private fun calculateCellSizeMeters(zoom: Float): Int {
        val mpp = _metersPerPixel.value
        val sensitivity = 30.0
        val providerWeight = when(_currentProvider.value) {
            MapProvider.Google -> 1.0
            MapProvider.Naver, MapProvider.Kakao -> 0.85
            else -> 1.0
        }
        
        val resolution = if (mpp > 0) {
            mpp * sensitivity * providerWeight
        } else {
            // Fallback to legacy math if MPP not injected yet
            val cosLat = Math.cos(Math.toRadians(currentLatitude))
            (156543.03392 * cosLat * sensitivity * providerWeight) / Math.pow(2.0, zoom.toDouble())
        }
        
        return resolution.toInt().coerceAtLeast(2) 
    }

    private fun mapClustersToItems(clustersFlat: List<Int>, items: List<UnifiedItem>): List<PinClusterItem> {
        // Safety Check for WASM output
        if (clustersFlat.size % 3 != 0) {
             // CRITICAL: WASM returned invalid size
        }
        
        val newClusters = mutableListOf<PinClusterItem>()
        val safeLimit = (clustersFlat.size / 3) * 3
        
        for (i in 0 until safeLimit step 3) {
            newClusters.add(PinClusterItem(clustersFlat[i], clustersFlat[i+1], clustersFlat[i+2], mutableListOf()))
        }

        if (newClusters.isNotEmpty()) {
            items.forEach { item ->
                val lat = item.intLat.toLong()
                val lng = item.intLng.toLong()
                var minDist = Long.MAX_VALUE // Use Long for distance squared
                var bestCluster: PinClusterItem? = null
                
                for (cluster in newClusters) {
                    val dLat = cluster.intLat - lat
                    val dLng = cluster.intLng - lng
                    val distSq = dLat*dLat + dLng*dLng
                    if (distSq < minDist) {
                        minDist = distSq
                        bestCluster = cluster
                    }
                }
                (bestCluster?.items as? MutableList)?.add(item)
            }
        }
        
        // Final Anchor & Dedupe
        return newClusters.map { cluster ->
              val userLoc = cluster.items.find { it is UnifiedItem.CurrentLocation }
              if (userLoc != null) {
                  cluster.copy(intLat = userLoc.intLat, intLng = userLoc.intLng)
              } else {
                  cluster
              }
        }.distinctBy { "${it.intLat}_${it.intLng}_${it.count}" }
    }


    
    // [NEW] Launch Sequence (Raw -> 3s -> Cluster)
    private fun launchMapSequence() {
        viewModelScope.launch {
            // 1. Initial State: Raw Pins (Clustering Disabled by default logic in init)
            
            // 2. Wait 3 seconds
            kotlinx.coroutines.delay(3000)

            // 3. Enable Clustering
            isInitialLaunch = false
            _isClusteringEnabled.value = true

            // 4. Trigger Zoom to Current Location
            _mapAction.value = MapAction.CURRENT_LOCATION
            
            // Force Recalculate
            lastClusteredScreenWidthMeters = -1.0
            recalculateClusters()
            
            kotlinx.coroutines.delay(500) // Give UI time to react to Zoom
            _mapAction.value = MapAction.NONE
        }
    }
    
    private fun handleCurrentLocationZoom18() {
        // Deprecated by launchMapSequence step 4, but kept for button clicks
        viewModelScope.launch {
            _mapAction.value = MapAction.CURRENT_LOCATION
        }
    }

    // MARK: - Create Todo Flow
    // Using Kakao LatLng as generic or maintain double/double
    private val _isCreatingTodo = MutableStateFlow(false)
    val isCreatingTodo: StateFlow<Boolean> = _isCreatingTodo.asStateFlow()
    
    private val _creatingTodoLocation = MutableStateFlow<UnifiedItem.CurrentLocation?>(null) // Using UnifiedItem.CurrentLocation as a data holder for now, or just Pair
    // Better to use a simple data class or just lat/lon fields? 
    // Let's use a specific state object or just expose properties.
    private val _creatingLocation = MutableStateFlow<UnifiedItem.CurrentLocation?>(null)
    val creatingLocation: StateFlow<UnifiedItem.CurrentLocation?> = _creatingLocation.asStateFlow()
    
    private val _initialTodoName = MutableStateFlow("")
    val initialTodoName: StateFlow<String> = _initialTodoName.asStateFlow()
    
    private val _initialTodoTitle = MutableStateFlow("할 일 만들기")
    val initialTodoTitle: StateFlow<String> = _initialTodoTitle.asStateFlow()
    
    fun startCreatingTodo(lat: Double, lon: Double, title: String = "할 일 만들기", name: String = "") {
        _creatingLocation.value = UnifiedItem.CurrentLocation(lat, lon)
        _initialTodoTitle.value = title
        _initialTodoName.value = name
        _isCreatingTodo.value = true
    }
    
    fun cancelCreatingTodo() {
        _isCreatingTodo.value = false
        _creatingLocation.value = null
        _initialTodoName.value = ""
    }
    
    // MARK: - My Info State
    private val _showMyInfo = MutableStateFlow(false)
    val showMyInfo: StateFlow<Boolean> = _showMyInfo.asStateFlow()
    
    fun toggleMyInfo(show: Boolean) {
        _showMyInfo.value = show
    }
    
    // MARK: - Selection Logic
    fun selectCluster(items: List<UnifiedItem>, x: Float, y: Float) {
        _selectedCluster.value = items
        _tapPosition.value = androidx.compose.ui.geometry.Offset(x, y)
    }
    
    fun clearSelection() {
        _selectedCluster.value = null
        _tapPosition.value = null
    }
    
    // MARK: - Smart Diffing Logic
    // Same as iOS: Always true, let LogicHelper/View handle diffing
    fun shouldUpdateMapItems(newLocation: Location): Boolean {
        return true
    }

    init {
        // [Fire & Forget] Start WASM Init (Background)
        wasmManager.initialize { success ->
            if (success) {
                // WASM Ready
            }
        }
        
        // [FIX] Independent Helper Logic: Start Sequence Immediately
        // Matches iOS behavior: Map starts regardless of WASM status.
        launchMapSequence()
    }
}

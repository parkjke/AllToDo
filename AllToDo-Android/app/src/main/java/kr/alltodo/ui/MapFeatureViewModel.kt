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
    ZOOM_TO_FIT
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

    // MARK: - Item Selection & Interaction
    private val _selectedCluster = MutableStateFlow<List<UnifiedItem>?>(null)
    val selectedCluster: StateFlow<List<UnifiedItem>?> = _selectedCluster.asStateFlow()
    
    private val _tapPosition = MutableStateFlow<androidx.compose.ui.geometry.Offset?>(null)
    val tapPosition: StateFlow<androidx.compose.ui.geometry.Offset?> = _tapPosition.asStateFlow()
    
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

    private val _isClusteringEnabled = MutableStateFlow(false)

    fun updateZoom(zoom: Float) {
        if (_currentZoom.value != zoom) {
            _currentZoom.value = zoom
            recalculateClusters()
        }
    }

    fun enableClustering() {
        if (!_isClusteringEnabled.value) {
            _isClusteringEnabled.value = true
            recalculateClusters()
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
        // Debounce or launch?
        viewModelScope.launch {
            val items = _displayItems.value
            val zoom = _currentZoom.value

            if (items.isEmpty()) {
                _clusteredItems.value = emptyList()
                return@launch
            }

            // 1. Prepare Points for WASM
            val flatPoints = items.flatMap { item ->
                val lat = item.latitude
                val lng = item.longitude
                if (lat != 0.0) {
                    listOf((lat * 100_000).toInt(), (lng * 100_000).toInt())
                } else {
                    emptyList()
                }
            }

            if (flatPoints.isEmpty()) {
                _clusteredItems.value = emptyList()
                return@launch
            }

            // 2. Clustering Check
            if (!_isClusteringEnabled.value) {
                // Return 1-to-1 clusters
                _clusteredItems.value = items.map { item ->
                     PinClusterItem(item.latitude, item.longitude, 1, listOf(item))
                }
                return@launch
            }

            // 3. WASM Clustering
            val resolution = 12_000_000.0 / Math.pow(2.0, zoom.toDouble())
            val cellSizeMeters = resolution.toInt().coerceAtLeast(2) 

            val clustersFlat = try {
                 wasmManager.cluster(flatPoints, cellSizeMeters)
            } catch (e: Exception) {
                 emptyList<Int>()
            }
            
            // Fallback
            if (clustersFlat.isEmpty()) {
                 _clusteredItems.value = items.map { item ->
                     PinClusterItem(item.latitude, item.longitude, 1, listOf(item))
                }
                return@launch
            }

            // 4. Map Clusters
            val newClusters = mutableListOf<PinClusterItem>()
            for (i in 0 until clustersFlat.size step 3) {
                val cLat = clustersFlat[i] / 100_000.0
                val cLng = clustersFlat[i+1] / 100_000.0
                val count = clustersFlat[i+2]
                newClusters.add(PinClusterItem(cLat, cLng, count, mutableListOf()))
            }

            // Assign Items (Nearest Neighbor)
            if (newClusters.isNotEmpty()) {
                items.forEach { item ->
                    val lat = item.latitude
                    val lng = item.longitude
                    
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
                    (bestCluster?.items as? MutableList)?.add(item)
                }
            }

            // [FIX] Cluster Anchoring (Keep User Pin Position Exact if inside)
            val finalClusters = newClusters.map { cluster ->
                  val userLoc = cluster.items.find { it is UnifiedItem.CurrentLocation }
                  if (userLoc != null) {
                      cluster.copy(latitude = userLoc.latitude, longitude = userLoc.longitude)
                  } else {
                      cluster
                  }
            }

            _clusteredItems.value = finalClusters
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
        wasmManager.initialize { success ->
            if (success) {
                // Trigger initial calculation if items exist
                recalculateClusters()
            }
        }
    }
}

package kr.alltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.alltodo.data.GpsAuthPoint
import kr.alltodo.data.GpsAuthTrack
import kr.alltodo.data.GpsAuthDao
import kr.alltodo.data.GpsAuthTrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kr.alltodo.wasm.WasmManager
import javax.inject.Inject
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class GpsAuthViewModel @Inject constructor(
    private val gpsAuthDao: GpsAuthDao,
    val wasmManager: WasmManager
) : ViewModel() {
    private val _points = MutableStateFlow<List<GpsAuthPoint>>(emptyList())
    val points = _points.asStateFlow()

    private val _pointSize = MutableStateFlow(1) // 0: Small, 1: Medium, 2: Large
    val pointSize = _pointSize.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible = _isOverlayVisible.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _savedTracks = MutableStateFlow<List<GpsAuthTrack>>(emptyList())
    val savedTracks = _savedTracks.asStateFlow()

    private val _showActivePath = MutableStateFlow(false)
    val showActivePath = _showActivePath.asStateFlow()

    private val MAX_POINTS = 10000
    private var currentTrackStartTime: Long = 0

    init {
        viewModelScope.launch {
            gpsAuthDao.getAllTracks().collectLatest { entities ->
                _savedTracks.value = entities.map { it.toDomain() }
            }
        }
    }

    fun setOverlayVisible(visible: Boolean) {
        _isOverlayVisible.value = visible
    }

    fun setShowActivePath(visible: Boolean) {
        _showActivePath.value = visible
    }

    fun addLocation(lat: Double, lon: Double, timestamp: Long) {
        if (!_isTracking.value) return
        
        val currentList = _points.value
        val lastPoint = currentList.lastOrNull()
        val status = classifyLocation(lastPoint, lat, lon, timestamp)
        
        val newPoint = GpsAuthPoint((lat * 100_000).toInt(), (lon * 100_000).toInt(), timestamp, status)
        val newList = currentList.toMutableList()
        
        if (newList.size >= MAX_POINTS) {
            newList.removeAt(0)
        }
        newList.add(newPoint)
        _points.value = newList
    }

    fun startTracking() {
        _isTracking.value = true
        _points.value = emptyList()
        _showActivePath.value = true
        currentTrackStartTime = System.currentTimeMillis()
    }

    fun stopTrackingAndSave(scope: kotlinx.coroutines.CoroutineScope = viewModelScope) {
        if (!_isTracking.value) return
        
        val recordedPoints = _points.value
        if (recordedPoints.isNotEmpty()) {
            val track = GpsAuthTrack(
                startTime = currentTrackStartTime,
                endTime = System.currentTimeMillis(),
                points = recordedPoints
            )

            scope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    gpsAuthDao.insertTrack(GpsAuthTrackEntity.fromDomain(track))
                }
            }
        }
        
        _isTracking.value = false
        _points.value = emptyList()
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch {
            gpsAuthDao.deleteTrack(trackId)
        }
    }

    private fun classifyLocation(last: GpsAuthPoint?, lat: Double, lon: Double, time: Long): Int {
        if (last == null) return 0
        val dist = calculateDistance(last.latitude, last.longitude, lat, lon)
        val timeDelta = (time - last.timestamp) / 1000.0
        if (timeDelta <= 0.1) return 0
        val speed = dist / timeDelta
        return when {
            speed > 300.0 -> 2
            speed > 50.0 -> 1
            else -> 0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) + cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun setPointSize(size: Int) { _pointSize.value = size }

    override fun onCleared() {
        super.onCleared()
        if (_isTracking.value) {
            val deathScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
            stopTrackingAndSave(deathScope)
        }
    }
}

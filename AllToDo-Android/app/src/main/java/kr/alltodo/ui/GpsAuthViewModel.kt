
package kr.alltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.alltodo.data.GpsAuthPoint
import kr.alltodo.data.GpsAuthTrack
import kr.alltodo.data.GpsAuthDao
import kr.alltodo.data.GpsAuthTrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kr.alltodo.wasm.WasmManager
import javax.inject.Inject
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

@HiltViewModel
class GpsAuthViewModel @Inject constructor(
    private val gpsAuthDao: GpsAuthDao,
    private val wasmManager: WasmManager
) : ViewModel() {
    private val _points = MutableStateFlow<List<GpsAuthPoint>>(emptyList())
    val points = _points.asStateFlow()

    private val _simplifiedPoints = MutableStateFlow<List<GpsAuthPoint>>(emptyList())
    val simplifiedPoints = _simplifiedPoints.asStateFlow()

    private val _isSimplifying = MutableStateFlow(false)
    val isSimplifying = _isSimplifying.asStateFlow()

    private val _pointSize = MutableStateFlow(1) // 0: Small, 1: Medium, 2: Large
    val pointSize = _pointSize.asStateFlow()

    private val _isTimeMachinePlaying = MutableStateFlow(false)
    val isTimeMachinePlaying = _isTimeMachinePlaying.asStateFlow()

    private val _timeMachineSpeed = MutableStateFlow(1) // 1, 2, 3
    val timeMachineSpeed = _timeMachineSpeed.asStateFlow()

    private val _timeMachineIndex = MutableStateFlow(-1)
    val timeMachineIndex = _timeMachineIndex.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible = _isOverlayVisible.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _savedTracks = MutableStateFlow<List<GpsAuthTrack>>(emptyList())
    val savedTracks = _savedTracks.asStateFlow()

    private val _selectedTrack = MutableStateFlow<GpsAuthTrack?>(null)
    val selectedTrack = _selectedTrack.asStateFlow()

    private val _showActivePath = MutableStateFlow(false) // [FIX] Default to OFF as requested
    val showActivePath = _showActivePath.asStateFlow()


    private val MAX_POINTS = 10000
    private var timeMachineJob: Job? = null
    private var currentTrackStartTime: Long = 0

    init {
        // Observe tracks from Database
        viewModelScope.launch {
            gpsAuthDao.getAllTracks().collectLatest { entities ->
                val domainTracks = entities.map { it.toDomain() }
                _savedTracks.value = domainTracks
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
        if (!_isTracking.value) return // Only record when tracking is ON
        
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
        _selectedTrack.value = null
        _showActivePath.value = true // [FIX] Automatically show trail when tracking starts
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

            // Save to DB
            scope.launch {
                withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
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
            if (_selectedTrack.value?.id == trackId) {
                selectTrack(null)
            }
        }
    }

    fun selectTrack(track: GpsAuthTrack?) {
        _selectedTrack.value = track
        _simplifiedPoints.value = emptyList() // Clear previous simplified path
        if (track != null) {
            _points.value = track.points
            _timeMachineIndex.value = 0
            _isTimeMachinePlaying.value = true
            runTimeMachine()
        } else {
            stopTimeMachine()
            _points.value = emptyList()
        }
    }

    fun simplifyCurrentTrack() {
        val currentPoints = _points.value
        if (currentPoints.size < 3) return
        
        viewModelScope.launch {
            _isSimplifying.value = true
            try {
                // Convert to List<Int> for WASM (Lat*100k, Lon*100k)
                val input = currentPoints.flatMap { 
                    listOf(it.intLat, it.intLng) 
                }
                
                // Call WASM RDP
                val simplified = wasmManager.compress(input)
                
                // Convert back to GpsAuthPoint
                val result = mutableListOf<GpsAuthPoint>()
                for (i in 0 until simplified.size step 2) {
                    val latInt = simplified[i]
                    val lonInt = simplified[i+1]
                    // Find original point to preserve status and timestamp (approximate)
                    val original = currentPoints.find { 
                        it.intLat == latInt && it.intLng == lonInt
                    } ?: GpsAuthPoint(latInt, lonInt, 0, 0)
                    result.add(original)
                }
                
                _simplifiedPoints.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSimplifying.value = false
            }
        }
    }

    private fun classifyLocation(last: GpsAuthPoint?, lat: Double, lon: Double, time: Long): Int {
        if (last == null) return 0
        
        val dist = calculateDistance(last.latitude, last.longitude, lat, lon)
        val timeDelta = (time - last.timestamp) / 1000.0 // seconds
        
        if (timeDelta <= 0.1) return 0 // Too frequent, ignore for speed calc
        
        val speed = dist / timeDelta // m/s
        
        // speed > 300m/s ~= 1080km/h (Impossible for human/car)
        // speed > 50m/s ~= 180km/h (Bounced/Racing)
        return when {
            speed > 300.0 -> 2
            speed > 50.0 -> 1
            else -> 0
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) + 
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * 
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    fun setPointSize(size: Int) { _pointSize.value = size }
    
    fun toggleTimeMachine() {
        if (_isTimeMachinePlaying.value) {
            stopTimeMachine()
        } else {
            if (_points.value.isNotEmpty()) {
                _isTimeMachinePlaying.value = true
                _timeMachineIndex.value = 0
                runTimeMachine()
            }
        }
    }

    private fun stopTimeMachine() {
        _isTimeMachinePlaying.value = false
        _timeMachineIndex.value = -1
        timeMachineJob?.cancel()
    }

    private fun runTimeMachine() {
        timeMachineJob?.cancel()
        timeMachineJob = viewModelScope.launch {
            while (_isTimeMachinePlaying.value) {
                val nextIdx = _timeMachineIndex.value + 1
                if (nextIdx < _points.value.size) {
                    _timeMachineIndex.value = nextIdx
                    val delayMs = when (_timeMachineSpeed.value) {
                        1 -> 500L
                        2 -> 150L
                        3 -> 50L
                        else -> 500L
                    }
                    delay(delayMs)
                } else {
                    stopTimeMachine()
                }
            }
        }
    }

    fun setTimeMachineSpeed(speed: Int) { _timeMachineSpeed.value = speed }

    fun toggleActivePath() {
        _showActivePath.value = !_showActivePath.value
    }

    override fun onCleared() {
        super.onCleared()
        if (_isTracking.value) {
            val deathScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            stopTrackingAndSave(deathScope)
        }
    }
}

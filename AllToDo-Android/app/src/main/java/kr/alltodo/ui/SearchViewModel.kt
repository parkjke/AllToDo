package kr.alltodo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kr.alltodo.services.KakaoSearchService
import kr.alltodo.services.SearchResult
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchService: KakaoSearchService
) : ViewModel() {

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showRipple = MutableStateFlow(false)
    val showRipple: StateFlow<Boolean> = _showRipple.asStateFlow()

    fun triggerRipple() {
        viewModelScope.launch {
            _showRipple.value = true
            kotlinx.coroutines.delay(3000)
            _showRipple.value = false
        }
    }

    fun toggleOverlay() {
        _isOverlayVisible.value = !_isOverlayVisible.value
        if (!_isOverlayVisible.value) {
            clearSearch()
        }
    }

    fun onQueryChange(newQuery: String, latitude: Double? = null, longitude: Double? = null) {
        println(">>> [SearchViewModel] onQueryChange: query='$newQuery', lat=$latitude, lon=$longitude")
        _searchQuery.value = newQuery
        if (newQuery.trim().length >= 2) {
            performSearch(latitude, longitude)
        } else if (newQuery.isEmpty()) {
            _searchResults.value = emptyList()
        }
    }

    fun performSearch(latitude: Double? = null, longitude: Double? = null) {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        _isSearching.value = true
        _errorMessage.value = null
        
        searchService.searchKeyword(query, latitude, longitude) { poiResults, errorCode ->
            viewModelScope.launch {
                // 검증: 비동기 결과가 도착했을 때의 쿼리가 현재 쿼리와 같은지 확인
                if (_searchQuery.value.trim() != query) return@launch

                val finalResults = mutableListOf<SearchResult>()
                poiResults?.let { finalResults.addAll(it) }

                // POI 결과가 부족한 경우(0~1건) 주소 검색 병행
                if (finalResults.size <= 1) {
                    searchService.searchAddress(query) { addrResults, _ ->
                        viewModelScope.launch {
                            if (_searchQuery.value.trim() != query) return@launch
                            
                            addrResults?.forEach { addr ->
                                // 중복 좌표 제거 (이미 POI로 검색된 위치는 제외)
                                if (finalResults.none { it.latitude == addr.latitude && it.longitude == addr.longitude }) {
                                    finalResults.add(addr)
                                }
                            }
                            finalizeSearch(finalResults, errorCode)
                        }
                    }
                } else {
                    finalizeSearch(finalResults, errorCode)
                }
            }
        }
    }

    private fun finalizeSearch(results: List<SearchResult>, errorCode: Int?) {
        _isSearching.value = false
        _searchResults.value = results
        if (results.isEmpty()) {
            _errorMessage.value = if (errorCode != null && errorCode != 200 && errorCode != -1) {
                "검색 실패 (HTTP $errorCode)"
            } else {
                "검색 결과가 없습니다."
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun onVoiceResult(result: String) {
        _searchQuery.value = result
        // Optionally trigger search immediately
        performSearch()
    }
}

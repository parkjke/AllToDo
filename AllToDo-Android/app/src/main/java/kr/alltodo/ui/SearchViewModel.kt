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

        println(">>> [SearchViewModel] performSearch: query='$query'")
        _isSearching.value = true
        _errorMessage.value = null // Clear previous error
        
        // Assuming searchService.searchKeyword now provides an errorCode
        searchService.searchKeyword(query, latitude, longitude) { results, errorCode ->
            viewModelScope.launch {
                if (results == null) {
                    println(">>> [SearchViewModel] performSearch: FAILED with code $errorCode")
                    _errorMessage.value = "검색 실패 (HTTP $errorCode)"
                    _searchResults.value = emptyList()
                } else {
                    println(">>> [SearchViewModel] performSearch: resultsCount=${results.size}")
                    _searchResults.value = results
                    if (results.isEmpty()) {
                        _errorMessage.value = "검색 결과가 없습니다."
                    }
                }
                _isSearching.value = false
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

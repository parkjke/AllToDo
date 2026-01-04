import Foundation
import SwiftUI
import Combine

class SearchViewModel: ObservableObject {
    @Published var query: String = ""
    @Published var results: [SearchResult] = []
    @Published var isSearching: Bool = false
    @Published var errorMessage: String? = nil
    @Published var isOverlayVisible: Bool = false
    
    private let searchService = KakaoSearchService()
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        // Debounced search logic
        $query
            .debounce(for: .milliseconds(500), scheduler: RunLoop.main)
            .removeDuplicates()
            .sink { [weak self] newQuery in
                guard let self = self else { return }
                if newQuery.count >= 2 {
                    // Start search if we have lat/lng (will be passed from view)
                    // For now, we just track the query. 
                    // Real search will be triggered by the view's coordination.
                } else if newQuery.isEmpty {
                    self.results = []
                    self.errorMessage = nil
                }
            }
            .store(in: &cancellables)
    }
    
    func toggleOverlay() {
        withAnimation {
            isOverlayVisible.toggle()
        }
        if !isOverlayVisible {
            query = ""
            results = []
            errorMessage = nil
        }
    }
    
    func performSearch(latitude: Double?, longitude: Double?) {
        guard query.count >= 2 else { return }
        
        isSearching = true
        errorMessage = nil
        
        searchService.searchKeyword(query: query, latitude: latitude, longitude: longitude) { [weak self] results, errorCode in
            DispatchQueue.main.async {
                self?.isSearching = false
                if let results = results {
                    self?.results = results
                    if results.isEmpty {
                        self?.errorMessage = "검색 결과가 없습니다."
                    }
                } else {
                    self?.errorMessage = "검색 실패 (HTTP \(errorCode))"
                    self?.results = []
                }
            }
        }
    }
}

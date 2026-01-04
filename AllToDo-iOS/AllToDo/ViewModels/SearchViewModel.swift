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
        let currentQuery = query.trimmingCharacters(in: .whitespaces)
        guard currentQuery.count >= 2 else { return }
        
        isSearching = true
        errorMessage = nil
        
        searchService.searchKeyword(query: currentQuery, latitude: latitude, longitude: longitude) { [weak self] poiResults, statusCode in
            guard let self = self else { return }
            
            // 검증: 결과가 도착했을 때의 쿼리가 현재 쿼리와 같은지 확인
            guard self.query.trimmingCharacters(in: .whitespaces) == currentQuery else { return }
            
            var finalResults = poiResults ?? []
            
            // POI 결과가 부족한 경우(0~1건) 주소 검색 병행
            if finalResults.count <= 1 {
                self.searchService.searchAddress(query: currentQuery) { addrResults, _ in
                    guard self.query.trimmingCharacters(in: .whitespaces) == currentQuery else { return }
                    
                    if let addrResults = addrResults {
                        for addr in addrResults {
                            // 중복 좌표 제거
                            if !finalResults.contains(where: { $0.latitude == addr.latitude && $0.longitude == addr.longitude }) {
                                finalResults.append(addr)
                            }
                        }
                    }
                    self.finalizeSearch(results: finalResults, statusCode: statusCode)
                }
            } else {
                self.finalizeSearch(results: finalResults, statusCode: statusCode)
            }
        }
    }
    
    private func finalizeSearch(results: [SearchResult], statusCode: Int) {
        DispatchQueue.main.async {
            self.isSearching = false
            self.results = results
            if results.isEmpty {
                self.errorMessage = statusCode == 200 ? "검색 결과가 없습니다." : "검색 실패 (HTTP \(statusCode))"
            }
        }
    }
}

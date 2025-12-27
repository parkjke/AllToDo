import Foundation

class APIService {
    static let shared = APIService()
    
    private let serverURL = "https://api.alltodo.mock/v1" // Mock URL
    
    func registerUser(uuid: String) async throws {
        // Mock API call to register UUID
        print("Simulating Server Registration for UUID: \(uuid)")
        try await Task.sleep(nanoseconds: 500_000_000) // 0.5s delay
        print("Server Registration Successful")
    }
    
    func syncUsageStats(uuid: String, action: String) {
        // Fire and forget usage tracking
        print("Tracking usage for \(uuid): \(action)")
    }
}

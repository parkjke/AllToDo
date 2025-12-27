import Foundation

// MARK: - Models
struct UserCreate: Encodable {
    let uuid: String
    let latitude: Double
    let longitude: Double
    let nickname: String?
}

struct UserResponse: Decodable {
    let uuid: String
    let message: String
}

struct RecoverRequest: Encodable {
    let nickname: String
    let password: String
}

struct UserInfoResponse: Decodable {
    let user_uuid: String
    let name: String?
    let nickname: String?
    let phone_number: String?
    // Add other fields as needed
}

struct UserInfoUpdate: Encodable {
    let user_uuid: String
    let name: String?
    let password: String?
    let nickname: String?
    // Add other fields as needed
}

struct UsageLogRequest: Encodable {
    let uuid: String
    let latitude: Double
    let longitude: Double
}

// MARK: - API Manager
class APIManager {
    static let shared = APIManager()
    
    // Using the detected local IP
    private let baseURL = "http://192.168.1.109:8000" 
    
    private init() {}
    
    // Common Request Function
    private func request<T: Decodable>(endpoint: String, method: String, body: Encodable? = nil) async throws -> T {
        guard let url = URL(string: "\(baseURL)\(endpoint)") else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        if let body = body {
            request.httpBody = try JSONEncoder().encode(body)
        }
        
        // Timeout configuration
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        let session = URLSession(configuration: config)
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            print("API Error: \(httpResponse.statusCode)")
            if let errorText = String(data: data, encoding: .utf8) {
                print("Error Body: \(errorText)")
            }
            throw URLError(.badServerResponse)
        }
        
        return try JSONDecoder().decode(T.self, from: data)
    }
    
    // 1. Check User / Create User
    func checkUser(uuid: String, lat: Double, long: Double, nickname: String?) async throws -> UserResponse {
        let body = UserCreate(uuid: uuid, latitude: lat, longitude: long, nickname: nickname)
        return try await request(endpoint: "/check-user", method: "POST", body: body)
    }
    
    // 2. Log Usage
    func logUsage(uuid: String, lat: Double, long: Double) async throws -> [String: String] {
        let body = UsageLogRequest(uuid: uuid, latitude: lat, longitude: long)
        return try await request(endpoint: "/log-usage", method: "POST", body: body)
    }
    
    // 3. Recover UUID
    func recoverUUID(nickname: String, password: String) async throws -> UserResponse {
        let body = RecoverRequest(nickname: nickname, password: password)
        return try await request(endpoint: "/recover-uuid", method: "POST", body: body)
    }
    
    // 4. Get User Info
    func getUserInfo(uuid: String) async throws -> UserInfoResponse {
        return try await request(endpoint: "/user-info?uuid=\(uuid)", method: "GET")
    }
    
    // 5. Update User Info
    func updateUserInfo(uuid: String, name: String?, password: String?) async throws {
        let body = UserInfoUpdate(user_uuid: uuid, name: name, password: password, nickname: nil)
        let _: [String: String] = try await request(endpoint: "/update-info", method: "POST", body: body)
    }
}

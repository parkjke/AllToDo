import UIKit // Needed for UIDevice

class RemoteLogger {
    static let shared = RemoteLogger()
    private let session = URLSession(configuration: .default)
    
    // Server URL (Moved to Dev Endpoint)
    // Server URL (Moved to AppConfig)
    private let logURL = AppConfig.logUrl
    
    // Public Device ID for UI
    public let deviceID: String
    
    private init() {
        self.deviceID = UIDevice.current.identifierForVendor?.uuidString ?? "Unknown_Device"
        print("[RemoteLogger] Device ID: \(self.deviceID)")
    }
    
    func log(level: String, message: String) {
#if DEBUG
        // Prepare Payload
        let payload: [String: Any] = [
            "level": level,
            "message": message,
            "device": self.deviceID,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: payload) else { return }
        
        var request = URLRequest(url: logURL)
        request.httpMethod = "POST"
        request.httpBody = jsonData
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Fire and Forget
        Task {
            do {
                let (_, response) = try await session.data(for: request)
                if let httpRes = response as? HTTPURLResponse, httpRes.statusCode != 200 {
                    // print("[RemoteLogger] Failed to send log: \(httpRes.statusCode)")
                }
            } catch {
                // print("[RemoteLogger] Network Error: \(error)")
            }
        }
#endif
    }
    
    static func info(_ message: String) {
        shared.log(level: "INFO", message: message)
    }
    
    static func error(_ message: String) {
        shared.log(level: "ERROR", message: message)
    }
}

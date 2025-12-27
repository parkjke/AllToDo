import SwiftUI
import SwiftData

@Observable
class AppViewModel {
    var userUUID: String
    var userName: String = "User" // Placeholder
    
    init() {
        // Load or Create UUID
        if let savedUUID = UserDefaults.standard.string(forKey: "user_uuid") {
            self.userUUID = savedUUID
        } else {
            let newUUID = UUID().uuidString
            UserDefaults.standard.set(newUUID, forKey: "user_uuid")
            self.userUUID = newUUID
        }
        
        // Check/Register with server
        Task {
            do {
                // Sending 0.0, 0.0 initially. Ideally, we should wait for location.
                let response = try await APIManager.shared.checkUser(uuid: self.userUUID, lat: 0.0, long: 0.0, nickname: nil)
                print("User Checked: \(response.message)")
            } catch {
                print("Failed to check user: \(error)")
            }
        }
    }
    
    func predictNextTask() -> String {
        // Simple prediction logic based on time of day
        let hour = Calendar.current.component(.hour, from: Date())
        
        switch hour {
        case 6..<9:
            return "Morning Routine: Check today's schedule"
        case 9..<12:
            return "Work Focus: Complete high priority tasks"
        case 12..<14:
            return "Lunch Break: Check nearby restaurants"
        case 18..<22:
            return "Evening: Review completed tasks"
        default:
            return "Rest & Recharge"
        }
    }
}

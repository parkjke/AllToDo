import Foundation
import BackgroundTasks
import SwiftData
import CoreLocation

class BackgroundService {
    static let shared = BackgroundService()
    static let backgroundTaskIdentifier = "com.alltodo.dailyLog"
    
    func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.backgroundTaskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }
    
    func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: Self.backgroundTaskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60) // 24 hours
        
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule app refresh: \(error)")
        }
    }
    
    func handleAppRefresh(task: BGAppRefreshTask) {
        scheduleAppRefresh() // Schedule next one
        
        task.expirationHandler = {
            // Clean up if running out of time
        }
        
        // Perform the logging
        let context = ModelContext(AllToDoApp.sharedModelContainer)
        let locationManager = LocationManager()
        
        if let loc = locationManager.location {
            let log = UserLog(
                startTime: Date(),
                endTime: Date(), // Single point
                latitude: loc.coordinate.latitude,
                longitude: loc.coordinate.longitude,
                pathData: nil
            )
            context.insert(log)
            try? context.save()
            
            // Log to Server
            if let uuid = UserDefaults.standard.string(forKey: "user_uuid") {
                Task {
                    do {
                        _ = try await APIManager.shared.logUsage(uuid: uuid, lat: loc.coordinate.latitude, long: loc.coordinate.longitude)
                        print("Background Log Sent")
                    } catch {
                        print("Background Log Failed: \(error)")
                    }
                }
            }
        }
        
        task.setTaskCompleted(success: true)
    }
}

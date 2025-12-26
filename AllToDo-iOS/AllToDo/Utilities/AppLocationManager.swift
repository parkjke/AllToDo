import Foundation
import CoreLocation
import CoreMotion
import Combine
import SwiftData

struct PathPoint: Codable {
    var latitude: Double
    var longitude: Double
    var timestamp: Date
}

class AppLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    
    @Published var currentLocation: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    
    // [NEW] Request Permission Explicitly
    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }
    
    // [NEW] Recording State
    @Published var isRecording = true // [RESTORED] Auto-record on by default
    @Published var debugStatus: String = "Auto-Recording..."
    @Published var processedSessionPoints: [PathPoint] = []
    @Published var showActivePath = false // [DEBUG] Visualization toggle
    
    // [NEW] Continuous Persistence State
    private var currentTripID: UUID?
    
    // [NEW] Buffer for Batch Processing
    var pendingBuffer: [PathPoint] = []
    
    // [NEW] Logging Flag
    private var hasLoggedInitialLocation = false

    // [NEW] Smart Tracking State
    var currentSpan: Double = 0.005 // Default Zoom ~17
    private var lastIntLocation: SmartLocationManager.IntLocation?
    
    @MainActor
    private func processBuffer() async {
        guard !pendingBuffer.isEmpty else { return }
        
        let rawPoints = pendingBuffer
        pendingBuffer.removeAll()
        
        // 1. Convert to Int32 for WASM (scaled by 1e5)
        var intPoints: [Int32] = []
        for p in rawPoints {
            intPoints.append(Int32(p.latitude * 100_000))
            intPoints.append(Int32(p.longitude * 100_000))
        }
        
        // 2. Call WASM Compression
        let compressedInts = await WasmManager.shared.compress(points: intPoints)
        
        // 3. Convert back to PathPoint
        var newPoints: [PathPoint] = []
        if compressedInts.count % 2 == 0 {
            for i in stride(from: 0, to: compressedInts.count, by: 2) {
                let lat = Double(compressedInts[i]) / 100_000.0
                let lon = Double(compressedInts[i+1]) / 100_000.0
                let originalTimestamp = rawPoints.first?.timestamp ?? Date() 
                newPoints.append(PathPoint(latitude: lat, longitude: lon, timestamp: originalTimestamp))
            }
        }
        
        // 4. Update UI trail
        self.processedSessionPoints.append(contentsOf: newPoints)
        
        // 5. [NEW] Continuous Persistence: Save to DB immediately
        savePointsToDatabase(newPoints)
    }
    
    private func savePointsToDatabase(_ points: [PathPoint]) {
        guard !points.isEmpty else { return }
        
        // Use shared container to create context for real-time saving
        let context = ModelContext(AllToDoApp.sharedModelContainer)
        
        // A. Ensure Trip (ToDoItem) exists
        if currentTripID == nil {
            let startLoc = points.first!
            let newTrip = ToDoItem(
                todo_name: "자동 기록 경로 (\(Date().formatted(.dateTime.hour().minute())))",
                is_exist_location_path: true
            )
            newTrip.type = "00"
            newTrip.latitude = startLoc.latitude
            newTrip.longitude = startLoc.longitude

            context.insert(newTrip)
            currentTripID = newTrip.todo_id
            print(">>> Continuous Persistence: New Trip Created (\(newTrip.todo_id))")
        }
        
        guard let tripID = currentTripID else { return }
        
        // B. Insert PathItems
        for p in points {
            let item = PathItem(todo_id: tripID, latitude: p.latitude, longitude: p.longitude, timestamp: p.timestamp)
            context.insert(item)
        }
        
        // C. Commit
        do {
            try context.save()
            // print(">>> Continuous Persistence: Saved \(points.count) pts to DB")
        } catch {
            print(">>> Continuous Persistence Error: \(error)")
        }
    }
    
    // [NEW] Motion Manager
    private let motionActivityManager = CMMotionActivityManager()
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.activityType = .fitness
        
        locationManager.pausesLocationUpdatesAutomatically = false 
        locationManager.allowsBackgroundLocationUpdates = true 
        
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        
        startMotionUpdates()
    }
    
    private func startMotionUpdates() {
        if CMMotionActivityManager.isActivityAvailable() {
            motionActivityManager.startActivityUpdates(to: OperationQueue.main) { [weak self] activity in
                guard let self = self, let activity = activity else { return }
                if activity.stationary && activity.confidence == .high {
                    if self.currentLocation != nil {
                        self.locationManager.stopUpdatingLocation()
                    }
                } else {
                    self.locationManager.startUpdatingLocation()
                }
            }
        }
    }
    
    private func getActivityString(_ activity: CMMotionActivity) -> String {
        var modes: [String] = []
        if activity.stationary { modes.append("Stationary") }
        if activity.walking { modes.append("Walking") }
        if activity.running { modes.append("Running") }
        if activity.automotive { modes.append("Automotive") }
        if activity.cycling { modes.append("Cycling") }
        if activity.unknown { modes.append("Unknown") }
        return modes.joined(separator: ", ")
    }
    
    // [LEGACY] Session Management (Maintained for internal consistency but auto-triggered)
    func startSession() {
        isRecording = true
        currentTripID = nil // Start fresh trip
        processedSessionPoints.removeAll()
        pendingBuffer.removeAll()
        debugStatus = "Recording..."
    }
    
    func endSession() async -> (start: Date, end: Date, midLat: Double, midLon: Double, points: [PathPoint])? {
        // [FIX] In "Continuous" mode, we don't strictly "end" but we can close the current trip
        guard isRecording else { return nil }
        debugStatus = "Stopping..."
        
        await processBuffer()
        
        if processedSessionPoints.isEmpty, let current = currentLocation {
            let point = PathPoint(latitude: current.coordinate.latitude, longitude: current.coordinate.longitude, timestamp: Date())
            processedSessionPoints.append(point)
            savePointsToDatabase([point])
        }
        
        guard !processedSessionPoints.isEmpty else { return nil }
        
        let start = processedSessionPoints.first?.timestamp ?? Date()
        let end = processedSessionPoints.last?.timestamp ?? Date()
        let latSum = processedSessionPoints.reduce(0.0) { $0 + $1.latitude }
        let lonSum = processedSessionPoints.reduce(0.0) { $0 + $1.longitude }
        let count = Double(processedSessionPoints.count)
        
        let result = (start: start, end: end, midLat: latSum / count, midLon: lonSum / count, points: processedSessionPoints)
        
        // Close session for UI trail
        isRecording = true // Keep it on for next points but reset UI trail
        processedSessionPoints.removeAll()
        currentTripID = nil 
        
        return result
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        if SmartLocationManager.shared.shouldUpdate(lastLoc: lastIntLocation, newLoc: location, currentSpan: currentSpan) {
            lastIntLocation = SmartLocationManager.shared.toIntLocation(location)
            DispatchQueue.main.async {
                self.currentLocation = location
                print(">>> start map: Location received from OS: \(location.coordinate.latitude), \(location.coordinate.longitude)")
            }

        }
        
        if !hasLoggedInitialLocation {
            hasLoggedInitialLocation = true
            OptimizationLogger.shared.logLaunchStep(step: "set current location", data: [
                "latitude": location.coordinate.latitude,
                "longitude": location.coordinate.longitude,
                "accuracy": location.horizontalAccuracy,
                "timestamp": ISO8601DateFormatter().string(from: location.timestamp)
            ])
        }
        
        if isRecording {
            let data = PathPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, timestamp: location.timestamp)
            pendingBuffer.append(data)
            
            if pendingBuffer.count >= 5 {
                Task {
                    await processBuffer()
                }
            }
        }
    }
}

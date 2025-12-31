import Foundation
import CoreLocation
import CoreMotion
import Combine
import SwiftData

struct PathPoint: Codable {
    var latitude: Int32
    var longitude: Int32
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
    @Published var debugStatus: String = "Recording..."
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
        
        // 1. Convert to Int32 for WASM (Already Int32)
        var intPoints: [Int32] = []
        for p in rawPoints {
            intPoints.append(p.latitude)
            intPoints.append(p.longitude)
        }
        
        // 2. Call WASM Compression
        let compressedInts = await WasmManager.shared.compress(points: intPoints)
        
        // 3. Convert back to PathPoint
        var newPoints: [PathPoint] = []
        if compressedInts.count % 2 == 0 {
            for i in stride(from: 0, to: compressedInts.count, by: 2) {
                let lat = compressedInts[i]
                let lon = compressedInts[i+1]
                let originalTimestamp = rawPoints.first?.timestamp ?? Date() 
                newPoints.append(PathPoint(latitude: lat, longitude: lon, timestamp: originalTimestamp))
            }
        }
        
        // 4. Update UI trail
        self.processedSessionPoints.append(contentsOf: newPoints)
        
        // 5. [FIX] Removed Continuous Persistence (User req: Save only on background)
        // savePointsToDatabase(newPoints)
    }
    
    private func savePointsToDatabase(_ points: [PathPoint]) {
        guard !points.isEmpty else { return }
        

        
        // [OPTIMIZATION] 30m Path Optimization
        // Use Integer-based GeomUtils
        let isShortPath = GeomUtils.isShortPath(points: points, thresholdUnits: 30)
        
        // Use shared container
        let context = ModelContext(AllToDoApp.sharedModelContainer)
        
        // A. Ensure Trip (ToDoItem) exists
        if currentTripID == nil {
            let startLoc = points.first!
            // Use Integer Initializer
            let newTrip = ToDoItem(
                todo_name: "자동 기록 경로 (\(Date().formatted(.dateTime.hour().minute())))",
                no_of_path: points.count,
                type: "00",
                int_lat: Int(startLoc.latitude),
                int_long: Int(startLoc.longitude)
            )
            if isShortPath {
                newTrip.no_of_path = 1
            }
            context.insert(newTrip)
            currentTripID = newTrip.todo_id
            print(">>> Continuous Persistence: New Trip Created (\(newTrip.todo_id))")
        }
        
        guard let tripID = currentTripID else { return }
        
        if isShortPath {
            print(">>> Continuous Persistence: Path too short, skipping points. (isShortPath=true)") // [DEBUG LOG]
            do { try context.save() } catch { print("Error saving short path trip: \(error)") }
            return
        }
        
        // B. Insert PathItems
        for p in points {
            // Use Integer Initializer
            let item = PathItem(todo_id: tripID, int_lat: Int(p.latitude), int_long: Int(p.longitude), time: p.timestamp)
            context.insert(item)
        }
        
        // C. Commit
        do {
            try context.save()
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
        
        // [FIX] Initial Location Fallback Strategy
        // 1. Try Last Saved Location
        let lastLat = UserDefaults.standard.double(forKey: "last_latitude")
        let lastLon = UserDefaults.standard.double(forKey: "last_longitude")
        
        if lastLat != 0.0 && lastLon != 0.0 {
            self.currentLocation = CLLocation(
                coordinate: CLLocationCoordinate2D(latitude: lastLat, longitude: lastLon),
                altitude: 0, horizontalAccuracy: 0, verticalAccuracy: 0, timestamp: Date()
            )
            print(">>> AppLocationManager: Initialized with Last Saved Location")
        } else {
            // 2. Fallback to Gwanghwamun
            self.currentLocation = CLLocation(
                coordinate: CLLocationCoordinate2D(latitude: 37.5760222, longitude: 126.9769000),
                altitude: 0, horizontalAccuracy: 0, verticalAccuracy: 0, timestamp: Date()
            )
            print(">>> AppLocationManager: Initialized with Gwanghwamun Default")
        }
        
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
    
    func endSession() async -> (start: Date, end: Date, midLat: Int, midLon: Int, points: [PathPoint])? {
        // [FIX] In "Continuous" mode, we don't strictly "end" but we can close the current trip
        guard isRecording else { return nil }
        debugStatus = "Stopping..."
        
        await processBuffer()
        
        if processedSessionPoints.isEmpty, let current = currentLocation {
            let latInt = Int32(current.coordinate.latitude * 100_000)
            let lonInt = Int32(current.coordinate.longitude * 100_000)
            let point = PathPoint(latitude: latInt, longitude: lonInt, timestamp: Date())
            processedSessionPoints.append(point)
        }
        
        // [FIX] Save ENTIRE session to DB now (Background/Exit)
        savePointsToDatabase(processedSessionPoints)
        
        guard !processedSessionPoints.isEmpty else { return nil }
        
        let start = processedSessionPoints.first?.timestamp ?? Date()
        let end = processedSessionPoints.last?.timestamp ?? Date()

        
        // [FIX] Use shared GeomUtils for centroid calculation
        let (intMidLat, intMidLon) = GeomUtils.calculateCentroid(from: processedSessionPoints)
        
        // [NEW] Cache Midpoint in DB
        if let tripID = currentTripID {
            let context = ModelContext(AllToDoApp.sharedModelContainer)
            let tripIDCopy = tripID
            let descriptor = FetchDescriptor<ToDoItem>()
            if let trips = try? context.fetch(descriptor),
               let trip = trips.first(where: { $0.todo_id == tripIDCopy }) {
                // [FIX] Assign scaled average directly to int storage
                trip.int_lat = intMidLat
                trip.int_long = intMidLon
                try? context.save()
                print(">>> endSession: Cached midpoint for trip \(tripIDCopy)")
            }
        }
        
        let result = (start: start, end: end, midLat: intMidLat, midLon: intMidLon, points: processedSessionPoints)
        
        // Close session for UI trail
        isRecording = true // Keep it on for next points but reset UI trail
        processedSessionPoints.removeAll()
        currentTripID = nil 
        
        return result
    }
    
    /// [NEW] Force reset session state without saving (used for DB reset)
    func resetSession() {
        currentTripID = nil
        processedSessionPoints.removeAll()
        pendingBuffer.removeAll()
        print(">>> AppLocationManager: Session reset completed.")
    }

    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        // [FIX] Always update current location for UI and persistence, even if stationary
        DispatchQueue.main.async {
            self.currentLocation = location
            self.saveLastLocation(location)
        }
        
        if SmartLocationManager.shared.shouldUpdate(lastLoc: lastIntLocation, newLoc: location, currentSpan: currentSpan) {
            lastIntLocation = SmartLocationManager.shared.toIntLocation(location)
            print(">>> start map: Location received from OS: \(location.coordinate.latitude), \(location.coordinate.longitude)")
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
            let latInt = Int32(location.coordinate.latitude * 100_000)
            let lonInt = Int32(location.coordinate.longitude * 100_000)
            let data = PathPoint(latitude: latInt, longitude: lonInt, timestamp: location.timestamp)
            pendingBuffer.append(data)
            
            if pendingBuffer.count >= 5 {
                Task {
                    await processBuffer()
                }
            }
        }
    }
    
    private func saveLastLocation(_ loc: CLLocation) {
        UserDefaults.standard.set(loc.coordinate.latitude, forKey: "last_latitude")
        UserDefaults.standard.set(loc.coordinate.longitude, forKey: "last_longitude")
        UserDefaults.standard.set(true, forKey: "has_saved_location")
    }
}

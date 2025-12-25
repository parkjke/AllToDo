import Foundation
import CoreLocation
import CoreMotion
import Combine
// LocationData is defined in TaskModel.swift which is in the same module.

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
    @Published var isRecording = false
    @Published var debugStatus: String = "Ready"
    @Published var processedSessionPoints: [PathPoint] = []
    @Published var showActivePath = true
    
    // [NEW] Buffer for Batch Processing
    var pendingBuffer: [PathPoint] = []
    
    // [NEW] Logging Flag
    private var hasLoggedInitialLocation = false

    // [NEW] Smart Tracking State
    var currentSpan: Double = 0.005 // Default Zoom ~17
    private var lastIntLocation: SmartLocationManager.IntLocation?
    
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
                
                // Find original timestamp if possible (approximate)
                // Use the timestamp from the corresponding point in rawPoints if possible, 
                // but since WASM returns a subset, we'll just use the one from rawPoints that matches or interpolation.
                // Simple approach: Take from rawPoints at matching index or just use current.
                let originalTimestamp = rawPoints.first?.timestamp ?? Date() 
                
                newPoints.append(PathPoint(latitude: lat, longitude: lon, timestamp: originalTimestamp))
            }
        }
        
        DispatchQueue.main.async {
            self.processedSessionPoints.append(contentsOf: newPoints)
        }
    }
    
    // [NEW] Motion Manager
    private let motionActivityManager = CMMotionActivityManager()
    
    override init() {
        super.init()
        locationManager.delegate = self
        // [MODIFIED] High accuracy and no distance filter to ensure frequent updates (approximating 1s stream)
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.activityType = .fitness // Keeps GPS active even for small movements
        
        // [NEW] Allow auto-pause to save battery, but we control it via motion
        locationManager.pausesLocationUpdatesAutomatically = false 
        locationManager.allowsBackgroundLocationUpdates = true // [FIX] Ensure recording continues in background 
        
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
        
        startMotionUpdates()
    }
    
    private func startMotionUpdates() {
        if CMMotionActivityManager.isActivityAvailable() {
            motionActivityManager.startActivityUpdates(to: OperationQueue.main) { [weak self] activity in
                guard let self = self, let activity = activity else { return }
                
                // Logging
                let type = self.getActivityString(activity)
                // OptimizationLogger.shared.log(type: .motionChange, value: type)
                
                // Logic: High confidence stationary -> Stop Location
                // [FIX] Only stop if we actually have a location!
                if activity.stationary && activity.confidence == .high {
                    if self.currentLocation != nil {
                        self.locationManager.stopUpdatingLocation()
                        // OptimizationLogger.shared.log(type: .locationPause, value: "Stationary High")
                    } else {
                        // print("DEBUG: Stationary detected but waiting for first location...")
                    }
                } else {
                    // Moving or unknown -> Ensure Location Running
                    self.locationManager.startUpdatingLocation()
                    // OptimizationLogger.shared.log(type: .locationResume, value: "Moving")
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
    
    // [NEW] Session Management
    func startSession() {
        isRecording = true
        processedSessionPoints.removeAll()
        pendingBuffer.removeAll()
        debugStatus = "Recording..."
    }
    
    func endSession() async -> (start: Date, end: Date, midLat: Double, midLon: Double, points: [PathPoint])? {
        guard isRecording else { return nil }
        isRecording = false
        debugStatus = "Stopping..."
        
        // Process remaining buffer
        await processBuffer()
        
        // [FIX] Fallback for Stationary Sessions (Single Point)
        if processedSessionPoints.isEmpty, let current = currentLocation {
            print("AppLocationManager: Session empty, using current location as single point.")
            let point = PathPoint(latitude: current.coordinate.latitude, longitude: current.coordinate.longitude, timestamp: Date())
            processedSessionPoints.append(point)
        }
        
        guard !processedSessionPoints.isEmpty else { return nil }
        
        let start = processedSessionPoints.first?.timestamp ?? Date()
        let end = processedSessionPoints.last?.timestamp ?? Date()
        
        // Calculate approx midpoint
        let latSum = processedSessionPoints.reduce(0.0) { $0 + $1.latitude }
        let lonSum = processedSessionPoints.reduce(0.0) { $0 + $1.longitude }
        let count = Double(processedSessionPoints.count)
        
        return (start: start, end: end, midLat: latSum / count, midLon: lonSum / count, points: processedSessionPoints)
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
    
    // [FIX] Missing Delegate Method
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        // 1. Update Publisher (Smart Throttled)
        // [NEW] Smart Tracking Check
        if SmartLocationManager.shared.shouldUpdate(lastLoc: lastIntLocation, newLoc: location, currentSpan: currentSpan) {
            lastIntLocation = SmartLocationManager.shared.toIntLocation(location)
            DispatchQueue.main.async {
                self.currentLocation = location
            }
        }
        
        // [LOG] Step 3: set current location
        if !hasLoggedInitialLocation {
            hasLoggedInitialLocation = true
            OptimizationLogger.shared.logLaunchStep(step: "set current location", data: [
                "latitude": location.coordinate.latitude,
                "longitude": location.coordinate.longitude,
                "accuracy": location.horizontalAccuracy,
                "timestamp": ISO8601DateFormatter().string(from: location.timestamp)
            ])
        }
        
        // 2. Buffer for Path Recording (if active)
        if isRecording {
            // Filter: 1s or 5m delta? Logic is in main stream usually, but here we just buffer raw.
            // Or use distanceFilter from manager.
            let data = PathPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, timestamp: location.timestamp)
            pendingBuffer.append(data)
            
            // Trigger WASM compression if buffer full?
            if pendingBuffer.count >= 5 {
                Task {
                    await processBuffer()
                }
            }
        }
    }
}

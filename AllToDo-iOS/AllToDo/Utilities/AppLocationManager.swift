import Foundation
import CoreLocation
import Combine
// LocationData is defined in TaskModel.swift which is in the same module.

struct ClusterItem: Identifiable {
    let id = UUID()
    let coordinate: CLLocationCoordinate2D
    let count: Int
    var items: [UnifiedMapItem] // Items belonging to this cluster
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
    @Published var processedSessionPoints: [LocationData] = []
    
    // [NEW] Buffer for Batch Processing
    var pendingBuffer: [LocationData] = []
    
    // [NEW] Process Buffer with WASM
    func processBuffer() async {
        guard !pendingBuffer.isEmpty else { return }
        
        let batch = pendingBuffer // Capture current batch
        pendingBuffer.removeAll() // Clear buffer immediately
        
        // Convert to Int32 array [lat, lon, lat, lon...]
        var rawPoints: [Int32] = []
        for p in batch {
            rawPoints.append(Int32(p.latitude * 1_000_000)) // Micro-degrees
            rawPoints.append(Int32(p.longitude * 1_000_000))
        }
        
        // Call WASM
        let compressed = await WasmManager.shared.compress(points: rawPoints)
        
        // Convert back to LocationData
        var resultBatch: [LocationData] = []
        for i in stride(from: 0, to: compressed.count, by: 2) {
            let lat = Double(compressed[i]) / 1_000_000.0
            let lon = Double(compressed[i+1]) / 1_000_000.0
            // We use approximate timestamp of the batch for simplicity or interpolate
            // Use last point's time? Or just 'now'
            resultBatch.append(LocationData(latitude: lat, longitude: lon, name: nil, timestamp: Date()))
        }
        
        DispatchQueue.main.async {
            self.processedSessionPoints.append(contentsOf: resultBatch)
            self.debugStatus = "Saved: \(self.processedSessionPoints.count) pts"
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
        locationManager.pausesLocationUpdatesAutomatically = true 
        
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
                OptimizationLogger.shared.log(type: .motionChange, value: type)
                
                // Logic: High confidence stationary -> Stop Location
                if activity.stationary && activity.confidence == .high {
                    self.locationManager.stopUpdatingLocation() // Type-o fixed
                    // Using paused flag or just stop? Stop is safer for battery, but resume needs trigger.
                    // Actually, if we stop, how do we resume? 
                    // CoreMotion continues updates even if GPS stops.
                    OptimizationLogger.shared.log(type: .locationPause, value: "Stationary High")
                    // Note: In real world, we might want to keep minimal tracking or significantly lower accuracy
                    self.locationManager.stopUpdatingLocation()
                } else {
                    // Moving or unknown -> Ensure Location Running
                    // We can check if it's already running by authorized state or flag, but startUpdatingLocation is idempotent usually
                    self.locationManager.startUpdatingLocation()
                    OptimizationLogger.shared.log(type: .locationResume, value: "Moving")
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
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
}

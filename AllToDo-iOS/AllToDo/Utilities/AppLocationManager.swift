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
    
    override init() {
        super.init()
        locationManager.delegate = self
        // [MODIFIED] High accuracy and no distance filter to ensure frequent updates (approximating 1s stream)
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.activityType = .fitness // Keeps GPS active even for small movements
        
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }
    
    // ... (omitted) ...

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        currentLocation = location
        
        if isRecording {
            let now = Date()
            var shouldRecord = false
            
            if let lastTime = lastRecordedTime {
                let timeDelta = now.timeIntervalSince(lastTime)
                
                // [MODIFIED] 0.9s Interval as requested
                if timeDelta >= 0.9 {
                    shouldRecord = true
                }
            } else {
                shouldRecord = true
            }
            
            if shouldRecord {
                let data = LocationData(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude, name: nil, timestamp: now)
                
                // Append to Buffer
                pendingBuffer.append(data)
                
                lastRecordedTime = now
                lastRecordedLocation = location
                
                // Update Debug UI
                debugStatus = "Rec: \(processedSessionPoints.count) + \(pendingBuffer.count) buf"
                
                // Check Batch Size (5)
                if pendingBuffer.count >= 5 {
                    Task {
                        await processBuffer()
                    }
                }
            }
        }
    }
    
    private var sessionStartTime: Date?
    private var lastRecordedTime: Date?
    private var lastRecordedLocation: CLLocation?
    
    // [NEW] Start Recording
    func startSession() {
        debugStatus = "Starting..."
        isRecording = true
        sessionStartTime = Date()
        processedSessionPoints = []
        pendingBuffer = []
        lastRecordedTime = nil
    }
    
    // [NEW] End Recording
    func endSession() async -> (start: Date, end: Date, midLat: Double, midLon: Double, pathData: Data?)? {
        isRecording = false
        debugStatus = "Stopping..."
        
        // Process remaining buffer
        if !pendingBuffer.isEmpty {
            await processBuffer()
        }
        
        guard let start = sessionStartTime, !processedSessionPoints.isEmpty else {
            return nil
        }
        
        let end = Date()
        
        // Calculate Midpoint
        let totalLat = processedSessionPoints.reduce(0.0) { $0 + $1.latitude }
        let totalLon = processedSessionPoints.reduce(0.0) { $0 + $1.longitude }
        let count = Double(processedSessionPoints.count)
        let midLat = totalLat / count
        let midLon = totalLon / count
        
        // Encode Path
        let pathData = try? JSONEncoder().encode(processedSessionPoints)
        
        return (start, end, midLat, midLon, pathData)
    }
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
}

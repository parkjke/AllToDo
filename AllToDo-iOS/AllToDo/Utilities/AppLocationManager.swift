import Foundation
import CoreLocation
import Combine
// LocationData is defined in TaskModel.swift which is in the same module.

class AppLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    
    @Published var currentLocation: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    
    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.requestWhenInUseAuthorization()
        locationManager.startUpdatingLocation()
    }
    
    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }
    
    // MARK: - Session Logic
    // [NEW] Streaming RDP buffers
    private var processedSessionPoints: [LocationData] = [] // Permanently stored (in memory) until save
    private var pendingBuffer: [LocationData] = [] // Temporary buffer for batching
    
    private var sessionStartTime: Date?
    
    @Published var isRecording = false
    
    @Published var debugStatus: String = "Ready"
    @Published var lastResult: String = "No previous session"

    func startSession() {
        processedSessionPoints.removeAll()
        pendingBuffer.removeAll()
        sessionStartTime = Date()
        isRecording = true
        debugStatus = "Recording... (0 pts)"
        print("AppLocationManager: Session Started")
        RemoteLogger.info("Session Started")
    }
    
    // Updated to handle streaming storage
    func endSession() async -> (start: Date, end: Date, midLat: Double, midLon: Double, pathData: Data?)? {
        isRecording = false
        guard let start = sessionStartTime, (!processedSessionPoints.isEmpty || !pendingBuffer.isEmpty) else {
            debugStatus = "Session Ended (Empty)"
            return nil
        }
        
        let end = Date()
        
        // Flush remaining buffer
        if !pendingBuffer.isEmpty {
            debugStatus = "Flushing buffer (\(pendingBuffer.count))..."
            await processBuffer(force: true)
        }
        
        let totalCount = processedSessionPoints.count
        debugStatus = "Saving \(totalCount) points..."
        
        // 1. Calculate Midpoint
        let midLat = processedSessionPoints.reduce(0) { $0 + $1.latitude } / Double(max(1, totalCount))
        let midLon = processedSessionPoints.reduce(0) { $0 + $1.longitude } / Double(max(1, totalCount))
        
        // 2. Encode
        let pathData = try? JSONEncoder().encode(processedSessionPoints)
        
        let msg = "Session Saved: \(totalCount) pts"
        lastResult = msg
        debugStatus = "Done. \(totalCount) pts saved."
        RemoteLogger.info(msg)
        
        // 3. Clear
        processedSessionPoints.removeAll()
        pendingBuffer.removeAll()
        sessionStartTime = nil
        
        return (start, end, midLat, midLon, pathData)
    }
    
    // [NEW] Batch Processing
    // Processes points in pendingBuffer and moves them to processedSessionPoints
    private func processBuffer(force: Bool = false) async {
        guard !pendingBuffer.isEmpty else { return }
        
        // Take snapshot to process
        let pointsToProcess = pendingBuffer
        pendingBuffer.removeAll() // Clear immediately to allow new incoming points
        
        // Optimization: Keep the last point of this batch as the start of the next batch? 
        // User requested "delete processed", implying simple segmentation.
        // However, standard RDP on chunks might cause disconnected vertices if not careful.
        // But since we just append points, they are connected lines.
        // The issue is simply simplification quality at the boundary.
        // For simplicity and to match request exactly: Just process and append.
        
        // Convert to Flat Int
        let flatPoints: [Int32] = pointsToProcess.flatMap {
            [Int32($0.latitude * 100_000), Int32($0.longitude * 100_000)]
        }
        
        let startTime = Date()
        // Call WASM
        let compressedFlat = await WasmManager.shared.compress(points: flatPoints)
        let duration = Date().timeIntervalSince(startTime) * 1000
        
        // Reconstruct
        var newProcessed: [LocationData] = []
        var i = 0
        while i < compressedFlat.count - 1 {
            let lat = Double(compressedFlat[i]) / 100_000.0
            let lng = Double(compressedFlat[i+1]) / 100_000.0
            // We lose individual timestamps. Use current time or interpolate?
            // User just wants visual path.
            newProcessed.append(LocationData(latitude: lat, longitude: lng, name: nil, timestamp: Date()))
            i += 2
        }
        
        // Append to main storage (MainActor since this is async)
        await MainActor.run {
            self.processedSessionPoints.append(contentsOf: newProcessed)
            let logMsg = "Batch RDP: \(pointsToProcess.count) -> \(newProcessed.count) pts (\(String(format: "%.1f", duration))ms)"
            print(logMsg)
            RemoteLogger.info(logMsg)
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        currentLocation = location
        
        if isRecording {
            let now = Date()
            var shouldRecord = false
            
            if let lastTime = lastRecordedTime {
                let timeDelta = now.timeIntervalSince(lastTime)
                
                // [CHANGED] 1s Interval as requested
                if timeDelta >= 1.0 {
                    shouldRecord = true
                }
                // Distance/Heading checks can remain as secondary triggers, 
                // but 1s is aggressive enough to catch corners usually.
                else if let lastLoc = lastRecordedLocation {
                    if location.distance(from: lastLoc) > 10.0 { shouldRecord = true }
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
    
    private var lastRecordedTime: Date?
    private var lastRecordedLocation: CLLocation?
    
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        authorizationStatus = status
        if status == .authorizedWhenInUse || status == .authorizedAlways {
            locationManager.startUpdatingLocation()
        }
    }
}

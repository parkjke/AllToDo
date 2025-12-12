import Foundation

enum LogType: String {
    case motionChange = "MOTION_CHANGE"
    case locationPause = "LOCATION_PAUSE"
    case locationResume = "LOCATION_RESUME"
    case batteryLevel = "BATTERY_LEVEL"
    case error = "ERROR"
}

class OptimizationLogger {
    static let shared = OptimizationLogger()
    private let fileName = "optimization_log.json"
    private var fileURL: URL? {
        guard let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
        return documentsDirectory.appendingPathComponent(fileName)
    }

    private init() {}

    func log(type: LogType, value: String) {
        guard let url = fileURL else { return }

        // Get Battery Level
        UIDevice.current.isBatteryMonitoringEnabled = true
        let batteryLevel = Int(UIDevice.current.batteryLevel * 100)

        let logEntry: [String: Any] = [
            "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            "datetime": ISO8601DateFormatter().string(from: Date()),
            "type": type.rawValue,
            "value": value,
            "battery": "\(batteryLevel)%"
        ]

        do {
            let data = try JSONSerialization.data(withJSONObject: logEntry, options: [])
            if let jsonString = String(data: data, encoding: .utf8) {
                let line = jsonString + "\n"
                
                if FileManager.default.fileExists(atPath: url.path) {
                    if let fileHandle = try? FileHandle(forWritingTo: url) {
                        fileHandle.seekToEndOfFile()
                        if let dataToWrite = line.data(using: .utf8) {
                            fileHandle.write(dataToWrite)
                        }
                        fileHandle.closeFile()
                    }
                } else {
                    try line.write(to: url, atomically: true, encoding: .utf8)
                }
                print("OptimizationLogger: \(jsonString)")
            }
        } catch {
            print("OptimizationLogger Error: \(error)")
        }
    }
    }
    
    func readLogs() -> String? {
        guard let url = fileURL else { return nil }
        do {
            return try String(contentsOf: url, encoding: .utf8)
        } catch {
            print("OptimizationLogger Read Error: \(error)")
            return nil
        }
    }
}

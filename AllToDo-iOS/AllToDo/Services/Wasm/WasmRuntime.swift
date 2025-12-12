import Foundation

protocol WasmRuntime {
    func loadModule(_ data: Data) async throws
    func callFunction(_ name: String, params: [Int32]) async throws -> Int32
    // Added for specific logic
    func compressTrajectory(_ points: [Int32], minDistMeters: Double, angleThreshDeg: Double) async throws -> [Int32]
    // [NEW] Clustering
    func clusterPoints(_ points: [Int32], cellSizeMeters: Double) async throws -> [Int32]
}

final class DummyWasmRuntime: WasmRuntime {
    func loadModule(_ data: Data) async throws {
        print("Loaded WASM module size = \(data.count)")
    }
    
    func callFunction(_ name: String, params: [Int32]) async throws -> Int32 {
        print("Call wasm function \(name) with params \(params)")
        return 0
    }
    
    func compressTrajectory(_ points: [Int32], minDistMeters: Double, angleThreshDeg: Double) async throws -> [Int32] {
        print("Dummy Wasm Runtime: compressing \(points.count) points")
        // Just return as is for dummy
        return points
    }
    
    func clusterPoints(_ points: [Int32], cellSizeMeters: Double) async throws -> [Int32] {
        print("Dummy Wasm Runtime: clustering \(points.count) points")
        // Return dummy clusters
        var result: [Int32] = []
        for i in stride(from: 0, to: points.count, by: 2) {
            result.append(points[i])
            if i+1 < points.count { result.append(points[i+1]) }
            result.append(1) // count
        }
        return result
    }
}

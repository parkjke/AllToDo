import Foundation

protocol WasmRuntime {
    func loadModule(_ data: Data) async throws
    func callFunction(_ name: String, params: [Int32]) async throws -> Int32
    // Added for specific logic
    func compressTrajectory(_ points: [Int32], minDistMeters: Double, angleThreshDeg: Double) async throws -> [Int32]
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
}

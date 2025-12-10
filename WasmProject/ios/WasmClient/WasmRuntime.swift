import Foundation

protocol WasmRuntime {
    func loadModule(_ data: Data) throws
    func callFunction(_ name: String, params: [Int32]) throws -> Int32
}

final class DummyWasmRuntime: WasmRuntime {
    func loadModule(_ data: Data) throws {
        print("Loaded WASM module size = \(data.count)")
    }
    
    func callFunction(_ name: String, params: [Int32]) throws -> Int32 {
        print("Call wasm function \(name) with params \(params)")
        return 0
    }
}

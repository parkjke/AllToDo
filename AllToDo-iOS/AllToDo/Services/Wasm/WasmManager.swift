import Foundation

final class WasmManager {
    static let shared = WasmManager()
    
    // Switch to Real Runtime
    private let runtime: WasmRuntime = WebViewWasmRuntime()
    private let session = URLSession(configuration: .default)
    
    // Updated IP
    private let advancedURL = URL(string: "http://175.194.163.56:8003/wasm/advanced")!
    
    private init() {}
    
    func initialize(completion: @escaping (Bool) -> Void) {
        print("[WASM_STATUS] 🚀 Initializing WASM Manager...")
        
        Task {
            // 1. Storage
            if let (_, blobJson) = WasmStorage.shared.load() {
                if let bundle = try? JSONDecoder().decode(WasmBundle.self, from: blobJson),
                   let decrypted = try? WasmCrypto.shared.decrypt(bundle: bundle) {
                    do {
                        try await runtime.loadModule(decrypted)
                        print("[WASM_STATUS] ✅ Loaded stored WASM (Version: \(bundle.version))")
                        completion(true)
                        return
                    } catch {
                        print("[WASM_STATUS] ❌ Failed to load stored WASM: \(error)")
                    }
                }
            }
            
            print("[WASM_STATUS] ℹ️ No valied stored WASM found. Checking server...")
            
            // 2. Network Check
            let success = await fetchAdvancedWasm()
            if success {
                print("[WASM_STATUS] 🎉 Successfully loaded WASM from Server")
                completion(true)
            } else {
                print("[WASM_STATUS] ⚠️ Server download failed. Using Fallback.")
                await loadFallback()
                completion(false)
            }
        }
    }
    
    private func fetchAdvancedWasm() async -> Bool {
        print("[WASM_STATUS] 🔍 (1/3) Start Connection Check to \(advancedURL)...")
        do {
            var request = URLRequest(url: advancedURL)
            request.timeoutInterval = 5 // Short timeout for check
            
            let (data, response) = try await session.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 200 {
                    print("[WASM_STATUS] ✅ (2/3) Connection Successful! (Status: 200 OK)")
                } else {
                    print("[WASM_STATUS] ⚠️ (2/3) Connection Reached Server but Failed. Status: \(httpResponse.statusCode)")
                    return false
                }
            }
            
            let bundle = try JSONDecoder().decode(WasmBundle.self, from: data)
            let decrypted = try WasmCrypto.shared.decrypt(bundle: bundle)
            
            try await runtime.loadModule(decrypted)
            WasmStorage.shared.save(version: bundle.version, blobJson: data)
            
            print("[WASM_STATUS] 🎉 (3/3) Download & Load Complete. Version: \(bundle.version)")
            return true
        } catch {
            print("[WASM_STATUS] ❌ Connection FAILED: \(error.localizedDescription)")
            // Detailed error for easier debugging
            print("[WASM_STATUS] 🛑 Error Details: \(error)")
            return false
        }
    }
    
    private func loadFallback() async {
        guard
            let url = Bundle.main.url(forResource: "fallback", withExtension: "wasm"),
            let data = try? Data(contentsOf: url)
        else {
            print("[WASM_STATUS] 🔥 CRITICAL: fallback.wasm not found in Resources!")
            return
        }
        
        do {
            try await runtime.loadModule(data)
            print("[WASM_STATUS] 📦 Loaded built-in Fallback WASM")
            RemoteLogger.info("Unavailable Server. Loaded Fallback WASM.")
        } catch {
            print("[WASM_STATUS] 🔥 Failed to load fallback WASM: \(error)")
            RemoteLogger.error("Failed to load Fallback WASM: \(error.localizedDescription)")
        }
    }
    
    public private(set) var lastErrorMessage: String? = nil

    func compress(points: [Int32]) async -> [Int32] {
        let start = Date()
        print("[WASM_STATUS] ⚡️ Executing WASM 'compressTrajectory' with \(points.count/2) points...")
        
        do {
            let result = try await runtime.compressTrajectory(points, minDistMeters: 3.0, angleThreshDeg: 10.0)
            
            let duration = Date().timeIntervalSince(start) * 1000
            let msg = "WASM Success: \(points.count/2) -> \(result.count/2) pts (\(String(format: "%.1f", duration))ms)"
            print("[WASM_STATUS] ✨ \(msg)")
            
            // RemoteLogger.info(msg) // Disabled to avoid spam with batch streaming. Parent handles it.
            lastErrorMessage = nil
            return result
        } catch {
            print("[WASM_STATUS] ❌ Execution Failed: \(error)")
            lastErrorMessage = error.localizedDescription
            RemoteLogger.error("WASM Exec Failed: \(error.localizedDescription)")
            return points // Return original on error
        }
    }
}

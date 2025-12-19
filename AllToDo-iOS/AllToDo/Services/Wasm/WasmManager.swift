import Foundation

final class WasmManager {
    static let shared = WasmManager()
    
    // Switch to Real Runtime
    private let runtime: WasmRuntime = WebViewWasmRuntime()
    private let session = URLSession(configuration: .default)
    
    // Updated IP
    private let advancedURL = URL(string: "http://175.194.163.56:8003/wasm/advanced")!
    private let versionURL = URL(string: "http://175.194.163.56:8003/wasm/version")!
    
    private init() {}
    
    struct VersionResponse: Codable {
        let version: String
    }
    
    func initialize(completion: @escaping (Bool) -> Void) {
        print("[WASM_STATUS] 🚀 Initializing WASM Manager...")
        
        Task {
            // 1. Storage Load
            var isLoaded = false
            if let (_, blobJson) = WasmStorage.shared.load() {
                if let bundle = try? JSONDecoder().decode(WasmBundle.self, from: blobJson),
                   let decrypted = try? WasmCrypto.shared.decrypt(bundle: bundle) {
                    do {
                        try await runtime.loadModule(decrypted)
                        print("[WASM_STATUS] ✅ Loaded stored WASM (Version: \(bundle.version))")
                        isLoaded = true
                    } catch {
                        print("[WASM_STATUS] ❌ Failed to load stored WASM: \(error)")
                    }
                }
            }
            
            if !isLoaded {
                 print("[WASM_STATUS] ℹ️ No valid stored WASM found. Loading Fallback & Checking Server...")
                 await loadFallback()
            }
            
            // Notify App is ready (using Stored or Fallback)
            completion(true)
            
            // 2. Check for Updates in Background (Unified Logic with Android)
            await checkForUpdate()
        }
    }
    
    private func checkForUpdate() async {
        print("[WASM_STATUS] 🔍 Checking for updates at: \(versionURL)...")
        do {
            let (data, _) = try await session.data(from: versionURL)
            let serverVer = try JSONDecoder().decode(VersionResponse.self, from: data).version
            
            let (storedVer, _) = WasmStorage.shared.load() ?? ("0.0.0", Data())
            
            if serverVer != storedVer {
                print("[WASM_STATUS] 🆕 New version found: \(serverVer) (Current: \(storedVer)). Downloading...")
                _ = await fetchAdvancedWasm()
            } else {
                print("[WASM_STATUS] ✅ Up to date (\(storedVer))")
            }
        } catch {
            print("[WASM_STATUS] ❌ Update check error: \(error.localizedDescription)")
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
            
            // [NEW] Self-Test
            await verifyWasm()
            
            return true
        } catch {
            print("[WASM_STATUS] ❌ Connection FAILED: \(error.localizedDescription)")
            // Detailed error for easier debugging
            print("[WASM_STATUS] 🛑 Error Details: \(error)")
            return false
        }
    }

    private func verifyWasm() async {
        print("[WASM_STATUS] 🧪 Starting WASM Self-Test (RDP + Clustering)...")
        do {
            // 1. RDP Test
            let rdpPoints: [Int32] = [0, 0, 100000, 100000, 200000, 200000]
            let rdpResult = try await runtime.compressTrajectory(rdpPoints, minDistMeters: 5.0, angleThreshDeg: 5.0)
            
            var rdpPassed = false
            if rdpResult.count == 4 {
                if rdpResult[0] == 0 && rdpResult[2] == 200000 {
                    rdpPassed = true
                }
            }
            
            // 2. Clustering Test
            let clusterPoints: [Int32] = [0, 0, 100, 100] // Dist ~ 141 units
            // Radius 200m -> ~180 units > 141 units -> Should Cluster
            let clusterResult = await cluster(points: clusterPoints, cellSize: 200.0)
            
            var clusterPassed = false
            if clusterResult.count == 3 {
                 let count = clusterResult[2]
                 if count == 2 { clusterPassed = true }
            }
            
            if rdpPassed && clusterPassed {
                 print("[WASM_STATUS] ✅ WASM Self-Test PASSED (RDP + Clustering)")
            } else {
                 print("[WASM_STATUS] ❌ WASM Self-Test FAILED: RDP=\(rdpPassed), Cluster=\(clusterPassed)")
            }
        } catch {
             print("[WASM_STATUS] ❌ WASM Self-Test Error: \(error)")
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
    
    // [NEW] Expose Clustering
    func cluster(points: [Int32], cellSize: Double) async -> [Int32] {
        let start = Date()
        // print("[WASM_STATUS] ⚡️ Executing WASM 'clusterPoints' with \(points.count/2) points...")
        
        // Integer Conversion (Meters -> Scaled 1e5 Units)
        // 1 degree ≈ 111,320 meters
        // 1 scaled unit (1e-5 deg) ≈ 1.1132 meters
        let radiusInt = Int32(cellSize / 1.1132)
        
        do {
            let result = try await runtime.clusterPoints(points, radiusInt: radiusInt)
            
            let duration = Date().timeIntervalSince(start) * 1000
            // print("[WASM_STATUS] ✨ WASM Cluster Success: \(result.count/3) clusters found (\(String(format: "%.1f", duration))ms)")
            
            lastErrorMessage = nil
            return result
        } catch {
            print("[WASM_STATUS] ❌ Cluster Execution Failed: \(error)")
            lastErrorMessage = error.localizedDescription
            return []
        }
    }
}

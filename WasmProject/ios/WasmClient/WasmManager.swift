import Foundation

final class WasmManager {
    static let shared = WasmManager()
    private let runtime: WasmRuntime = DummyWasmRuntime()
    private let session = URLSession(configuration: .default)
    private let advancedURL = URL(string: "http://127.0.0.1:8000/wasm/advanced")!
    
    private init() {}
    
    func initialize(completion: @escaping (Bool) -> Void) {
        // 1. 저장된 WASM 먼저 사용 시도
        if let (version, blobJson) = WasmStorage.shared.load() {
            // Note: In storage usage, version is unused in this snippet but available.
            if let bundle = try? JSONDecoder().decode(WasmBundle.self, from: blobJson),
               let decrypted = try? WasmCrypto.shared.decrypt(bundle: bundle) {
                do {
                    try runtime.loadModule(decrypted)
                    completion(true)
                    return
                } catch {
                    print("Failed to load stored WASM: \(error)")
                }
            }
        }
        
        // 2. 서버에서 새로 다운로드 시도
        fetchAdvancedWasm { success in
            if success {
                completion(true)
            } else {
                // 3. 완전히 실패하면 fallback.wasm 사용
                self.loadFallback()
                completion(false)
            }
        }
    }
    
    private func fetchAdvancedWasm(completion: @escaping (Bool) -> Void) {
        session.dataTask(with: advancedURL) { data, _, error in
            guard let data = data, error == nil else {
                completion(false)
                return
            }
            
            do {
                let bundle = try JSONDecoder().decode(WasmBundle.self, from: data)
                let decrypted = try WasmCrypto.shared.decrypt(bundle: bundle)
                try self.runtime.loadModule(decrypted)
                
                // Save original JSON blob, not decrypted binary, as storage expects to decode Bundle again?
                // The snippet `WasmStorage.shared.save(version: bundle.version, blobJson: data)` saves the JSON response.
                // The load logic `JSONDecoder().decode...` confirms this.
                WasmStorage.shared.save(version: bundle.version, blobJson: data)
                
                completion(true)
            } catch {
                print("Failed to download/load advanced wasm: \(error)")
                completion(false)
            }
        }.resume()
    }
    
    private func loadFallback() {
        guard
            let url = Bundle.main.url(forResource: "fallback", withExtension: "wasm"),
            let data = try? Data(contentsOf: url)
        else {
            print("fallback.wasm not found")
            return
        }
        
        do {
            try runtime.loadModule(data)
            print("Loaded fallback WASM")
        } catch {
            print("Failed to load fallback WASM: \(error)")
        }
    }
}

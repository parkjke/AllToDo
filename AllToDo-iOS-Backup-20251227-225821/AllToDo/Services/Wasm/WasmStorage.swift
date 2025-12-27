import Foundation

final class WasmStorage {
    static let shared = WasmStorage()
    private init() {}
    
    private let versionKey = "advanced_wasm_version"
    private let blobKey = "advanced_wasm_blob"
    
    func save(version: String, blobJson: Data) {
        UserDefaults.standard.set(version, forKey: versionKey)
        UserDefaults.standard.set(blobJson, forKey: blobKey)
    }
    
    func load() -> (version: String, blobJson: Data)? {
        guard
            let version = UserDefaults.standard.string(forKey: versionKey),
            let blob = UserDefaults.standard.data(forKey: blobKey)
        else {
            return nil
        }
        return (version, blob)
    }
    
    func clear() {
        UserDefaults.standard.removeObject(forKey: versionKey)
        UserDefaults.standard.removeObject(forKey: blobKey)
    }
}

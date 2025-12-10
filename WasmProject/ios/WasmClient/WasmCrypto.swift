import Foundation
import CryptoKit

enum WasmCryptoError: Error {
    case invalidData
    case decryptFailed
}

final class WasmCrypto {
    static let shared = WasmCrypto()
    private init() {}
    
    private let keyData = "0123456789abcdef0123456789abcdef".data(using: .utf8)!
    
    func decrypt(bundle: WasmBundle) throws -> Data {
        guard
            let iv = Data(base64Encoded: bundle.ivB64),
            let ciphertext = Data(base64Encoded: bundle.ciphertextB64),
            let tag = Data(base64Encoded: bundle.tagB64)
        else {
            throw WasmCryptoError.invalidData
        }
        
        var combined = Data()
        combined.append(ciphertext)
        combined.append(tag)
        
        let key = SymmetricKey(data: keyData)
        let sealedBox = try AES.GCM.SealedBox(combined: combined)
        let decrypted = try AES.GCM.open(sealedBox, using: key, authenticating: iv)
        
        return decrypted
    }
}

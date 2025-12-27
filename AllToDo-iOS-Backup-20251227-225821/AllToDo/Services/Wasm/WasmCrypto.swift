import Foundation
import CryptoKit

enum WasmCryptoError: Error {
    case invalidData
    case decryptFailed
}

final class WasmCrypto {
    static let shared = WasmCrypto()
    private init() {}
    
    // Key from Backend .env (Standard Base64: h5eDj7nnM4A17/L1IrsbMMHsbA8YFdFL3L5ONYNkzNA=)
    private let keyData = Data(base64Encoded: "h5eDj7nnM4A17/L1IrsbMMHsbA8YFdFL3L5ONYNkzNA=")!
    
    func decrypt(bundle: WasmBundle) throws -> Data {
        guard
            let ivData = Data(base64Encoded: bundle.ivB64),
            let ciphertext = Data(base64Encoded: bundle.ciphertextB64),
            let tag = Data(base64Encoded: bundle.tagB64)
        else {
            throw WasmCryptoError.invalidData
        }
        
        let key = SymmetricKey(data: keyData)
        let nonce = try AES.GCM.Nonce(data: ivData)
        let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
        
        // Open (Decrypt)
        let decrypted = try AES.GCM.open(sealedBox, using: key)
        
        return decrypted
    }
}

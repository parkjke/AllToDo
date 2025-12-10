import Foundation

struct WasmBundle: Codable {
    let version: String
    let ciphertextB64: String
    let ivB64: String
    let tagB64: String
    
    enum CodingKeys: String, CodingKey {
        case version
        case ciphertextB64 = "ciphertext_b64"
        case ivB64 = "iv_b64"
        case tagB64 = "tag_b64"
    }
}

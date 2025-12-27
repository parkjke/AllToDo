import Foundation

struct KoreanUtils {
    static let choseong = ["ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]
    
    static func getChoseong(_ text: String) -> String {
        var result = ""
        for char in text {
            guard let scalar = char.unicodeScalars.first else { continue }
            let code = scalar.value
            
            // Hangul Syllables: 0xAC00 ~ 0xD7A3
            if code >= 0xAC00 && code <= 0xD7A3 {
                let index = Int((code - 0xAC00) / 28 / 21)
                result += choseong[index]
            } else {
                result += String(char)
            }
        }
        return result
    }
    
    static func matchesChoseong(query: String, target: String) -> Bool {
        let queryChoseong = getChoseong(query)
        let targetChoseong = getChoseong(target)
        return targetChoseong.contains(queryChoseong) || target.contains(query)
    }
}

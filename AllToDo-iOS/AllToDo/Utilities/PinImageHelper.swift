import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // [Global Flag] - Deprecated (Static Assets Used)
    // static var isMapPinCreating: Bool = false
    
    // [사용자 원천기술] 비트맵 캐시 시스템 (성능 최적화용)
    private static var imageCache: [String: UIImage] = [:]
    private static let HEADER_SIGNATURE = "ALLTODO_V7"
    
    // ... (loadFromDisk, saveToDisk omitted for brevity, logic remains same for fallback) ...

    /// 베이스 핀(Shield)과 마크(Mark)를 합성하여 반환합니다. (정적 에셋 우선 사용)
    func fetchCompositePin(shieldName: String, markName: String, size: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        let cacheKey = "composite-\(shieldName)-\(markName)-\(Int(size.width))x\(Int(size.height))"
        
        // 1. 메모리 캐시 확인
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        // [New] 2. 정적 에셋 확인 (Static Assets)
        // pin_mark_XX -> map_pin_XX
        let pattern = "pin_mark_([0-9]{2})"
        if let regex = try? NSRegularExpression(pattern: pattern),
           let match = regex.firstMatch(in: markName, range: NSRange(markName.startIndex..., in: markName)),
           let range = Range(match.range(at: 1), in: markName) {
            
            let suffix = String(markName[range])
            let directName = "map_pin_\(suffix)"
            
            if let staticImage = UIImage(named: directName) {
                // Resize to target size if needed, though they are likely correct 3x assets.
                // If static assets are Universal SVG, UIImage(named:) returns a scalable image.
                // We cache it to avoid lookup cost.
                 PinImageHelper.imageCache[cacheKey] = staticImage
                 return staticImage
            }
        }
        
        // 3. 디스크 캐시 확인 (Legacy or Fallback)
        if let diskImage = loadFromDisk(key: cacheKey) {
            PinImageHelper.imageCache[cacheKey] = diskImage
            return diskImage
        }
        
        // 4. 새로 생성 (합성 - Fallback for unknown combinations)
        guard let shieldImage = getAssetImage(named: shieldName),
              let markImage = getAssetImage(named: markName) else {
            return nil
        }
        
        // 캔버스 생성 및 합성 (기기 해상도 고려)
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        
        let finalImage = renderer.image { context in
            shieldImage.draw(in: CGRect(origin: .zero, size: size))
            
            let markTargetHeight = size.height * 0.52
            let markScale = markTargetHeight / markImage.size.height
            let markTargetWidth = markImage.size.width * markScale
            
            let markX = (size.width - markTargetWidth) / 2
            let markY = (size.height * 0.40) - (markTargetHeight / 2)
            
            markImage.draw(in: CGRect(x: markX, y: markY, width: markTargetWidth, height: markTargetHeight))
        }
        
        // 캐싱
        PinImageHelper.imageCache[cacheKey] = finalImage
        saveToDisk(key: cacheKey, image: finalImage)
        
        return finalImage
    }
    
    // 디스크 캐시 경로 (Application Support/pins/)
    private var pinsDirectory: URL {
        let paths = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
        let pinsUrl = paths[0].appendingPathComponent("pins")
        if !FileManager.default.fileExists(atPath: pinsUrl.path) {
            try? FileManager.default.createDirectory(at: pinsUrl, withIntermediateDirectories: true)
        }
        return pinsUrl
    }
    
    /// 디스크에서 비트맵을 로드하고 시그니처를 확인합니다.
    private func loadFromDisk(key: String) -> UIImage? {
        let fileUrl = pinsDirectory.appendingPathComponent("\(key).png")
        guard let data = try? Data(contentsOf: fileUrl) else { return nil }
        
        // 1. 헤더 체크
        let signatureData = PinImageHelper.HEADER_SIGNATURE.data(using: .utf8)!
        if data.count < signatureData.count { return nil }
        
        let header = data.subdata(in: 0..<signatureData.count)
        if header != signatureData { return nil }
        
        // 2. 실제 이미지 데이터 추출 및 생성
        // 파일에서 읽어올 때 기기 스케일(3.0x 등) 정보를 수동으로 주입하여 올바른 포인트 크기 복원
        let imageData = data.subdata(in: signatureData.count..<data.count)
        return UIImage(data: imageData, scale: UIScreen.main.scale)
    }
    
    /// 비트맵을 시그니처와 함께 디스크에 저장합니다.
    private func saveToDisk(key: String, image: UIImage) {
        let fileUrl = pinsDirectory.appendingPathComponent("\(key).png")
        guard let imageData = image.pngData() else { return }
        
        var finalData = PinImageHelper.HEADER_SIGNATURE.data(using: .utf8)!
        finalData.append(imageData)
        
        try? finalData.write(to: fileUrl)
    }
    
    /// 베이스 핀(Shield)과 마크(Mark)를 합성하여 반환합니다. (동적 생성 및 캐싱)
    func fetchCompositePin(shieldName: String, markName: String, size: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        let cacheKey = "composite-\(shieldName)-\(markName)-\(Int(size.width))x\(Int(size.height))"
        
        // 1. 메모리 캐시 확인
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        // 2. 디스크 캐시 확인
        if let diskImage = loadFromDisk(key: cacheKey) {
            PinImageHelper.imageCache[cacheKey] = diskImage
            return diskImage
        }
        
        // 3. 새로 생성 (합성)
        guard let shieldImage = getAssetImage(named: shieldName),
              let markImage = getAssetImage(named: markName) else {
            return nil
        }
        
        // 캔버스 생성 및 합성 (기기 해상도 고려)
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        
        let finalImage = renderer.image { context in
            // A. Shield 그리기 (배경)
            shieldImage.draw(in: CGRect(origin: .zero, size: size))
            
            // B. Mark 그리기 (전경) - 쉴드 상단 정렬 (Visual Center)
            // Bubble Area is top ~40pt. Center is ~20pt (40% of 50)
            
            let markTargetHeight = size.height * 0.52 // Reduced from 0.58 for padding
            let markScale = markTargetHeight / markImage.size.height
            let markTargetWidth = markImage.size.width * markScale
            
            // Center X: 50%
            // Center Y: 40% (20pt from top, visual center of bubble)
            let markX = (size.width - markTargetWidth) / 2
            let markY = (size.height * 0.40) - (markTargetHeight / 2)
            
            markImage.draw(in: CGRect(x: markX, y: markY, width: markTargetWidth, height: markTargetHeight))
        }
        
        // 캐싱
        PinImageHelper.imageCache[cacheKey] = finalImage
        saveToDisk(key: cacheKey, image: finalImage)
        
        return finalImage
    }
    
    // Legacy support (Deprecated ideally, but kept for build safety)
    func fetchBasePin(named imageName: String, size: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        return fetchCompositePin(shieldName: imageName, markName: "PinMark_00", size: size) // Fallback
    }
    
    /// 베이스 이미지 위에 클러스터 숫자를 나타내는 뱃지를 합성합니다.
    /// - Parameters:
    ///   - badgeSize: 뱃지의 지름 (애플 20pt, 네이버 18pt, 카카오 14pt 권장)
    func applyBadge(to baseImage: UIImage, count: Int, badgeColor: UIColor = .red, badgeSize: CGFloat = 20) -> UIImage {
        let baseSize = baseImage.size
        let badgeOverhang: CGFloat = 10 
        
        // 뱃지 크기에 맞춘 캔버스 사이즈 정의
        let size = CGSize(width: baseSize.width + badgeOverhang, height: baseSize.height + badgeOverhang)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale // 고해상도 유지
        
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            // 1. 베이스 핀 그리기 (아래쪽에 배치)
            baseImage.draw(in: CGRect(x: 0, y: badgeOverhang, width: baseSize.width, height: baseSize.height))
            
            // 2. 뱃지 그리기 (우측 상단)
            // [FIX] 위치를 3pt 내림 (badgeOverhang - 2.0 -> badgeOverhang + 1.0)
            let badgeCenter = CGPoint(x: baseSize.width - 2.0, y: badgeOverhang + 1.0)
            let badgeRect = CGRect(x: badgeCenter.x - badgeSize/2, y: badgeCenter.y - badgeSize/2, width: badgeSize, height: badgeSize)
            
            // 배경원
            UIColor.white.setFill()
            UIBezierPath(ovalIn: badgeRect).fill()
            
            // 테두리
            badgeColor.setStroke()
            let strokePath = UIBezierPath(ovalIn: badgeRect)
            strokePath.lineWidth = 1.5
            strokePath.stroke()
            
            // 숫자 텍스트
            let countText = count > 99 ? "99+" : "\(count)"
            let fontSize: CGFloat = badgeSize > 15 ? 11 : 9 // 크기에 따른 폰트 조절
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
                .foregroundColor: badgeColor
            ]
            let string = NSString(string: countText)
            let textSize = string.size(withAttributes: attributes)
            string.draw(at: CGPoint(x: badgeCenter.x - textSize.width/2, y: badgeCenter.y - textSize.height/2), withAttributes: attributes)
        }
    }
    
    // 메인 인터페이스 (동적 합성 적용)
    func createShieldPin(shieldName: String, markName: String, color: UIColor, count: Int? = nil, badgeSize: CGFloat = 20) -> UIImage? {
        let badgeCount = count ?? 1
        // Cache mapping: Shield + Mark + Badge
        let cacheKey = "pin-\(shieldName)-\(markName)-\(color.accessibilityName)-\(badgeCount)-\(Int(badgeSize))"
        
        // 1. 메모리 캐시
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        // 2. 디스크 캐시
        if let diskImage = loadFromDisk(key: cacheKey) {
            PinImageHelper.imageCache[cacheKey] = diskImage
            return diskImage
        }
        
        // 3. 합성 및 저장
        // Step A: Base (Shield + Mark)
        guard let baseImage = fetchCompositePin(shieldName: shieldName, markName: markName) else {
            return nil
        }
        
        // Step B: Badge overlay
        let finalImage: UIImage
        if badgeCount > 1 {
            finalImage = applyBadge(to: baseImage, count: badgeCount, badgeColor: color, badgeSize: badgeSize)
        } else {
            finalImage = baseImage
        }
        
        PinImageHelper.imageCache[cacheKey] = finalImage
        saveToDisk(key: cacheKey, image: finalImage)
        return finalImage
    }
    
    // Clear Cache Helper
    func clearCache() {
        PinImageHelper.imageCache.removeAll()
        try? FileManager.default.removeItem(at: pinsDirectory)
        try? FileManager.default.createDirectory(at: pinsDirectory, withIntermediateDirectories: true)
    }

    // Safe Image Loading Helper (Public for generic use)
    func getAssetImage(named name: String) -> UIImage? {
        // 1. Try Flat Name
        if let img = UIImage(named: name) { return img }
        
        // 2. Try with Parent Folders (Common Namespace patterns)
        let prefixes = ["Shields/", "Marks/", "Components/Shields/", "Components/Marks/"]
        for prefix in prefixes {
            if let img = UIImage(named: prefix + name) {
                // print("✅ Loaded with prefix: \(prefix + name)")
                return img
            }
        }
        
        return nil
    }


}

// MARK: - UIImage Extensions
extension UIImage {
    func resized(to size: CGSize) -> UIImage? {
        // 기기 해상도(0.0)를 사용하여 선명한 Retina 이미지 생성
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    func rasterized() -> UIImage? {
        // [FIX] PNG 데이터로 변환 후 시스템 스케일을 명시하여 재생성
        guard let data = self.pngData() else { return nil }
        return UIImage(data: data, scale: UIScreen.main.scale)
    }
}



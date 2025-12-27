import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // [사용자 원천기술] 비트맵 캐시 시스템 (성능 최적화용)
    private static var imageCache: [String: UIImage] = [:]
    private static let HEADER_SIGNATURE = "ALLTODO_V6"
    
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
    
    /// 베이스 핀(Shield)을 에셋에서 가져오거나 캐시에서 반환합니다. (40x50 표준화)
    func fetchBasePin(named imageName: String, size: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        let cacheKey = "\(imageName)-\(Int(size.width))x\(Int(size.height))"
        
        // 1. 메모리 캐시 확인
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        // 2. 디스크 캐시 확인
        if let diskImage = loadFromDisk(key: cacheKey) {
            PinImageHelper.imageCache[cacheKey] = diskImage
            return diskImage
        }
        
        // 3. 새로 생성
        guard let assetImage = UIImage(named: imageName) else {
            return nil
        }
        
        let resizedImage = assetImage.resized(to: size)
        let finalImage = resizedImage?.rasterized()
        
        if let finalImage = finalImage {
            PinImageHelper.imageCache[cacheKey] = finalImage
            saveToDisk(key: cacheKey, image: finalImage)
        }
        return finalImage
    }
    
    /// 베이스 이미지 위에 클러스터 숫자를 나타내는 뱃지를 합성합니다.
    func applyBadge(to baseImage: UIImage, count: Int, badgeColor: UIColor = .red) -> UIImage {
        let baseSize = baseImage.size
        let badgeSize: CGFloat = 20 
        let badgeOverhang: CGFloat = 10 
        
        // 뱃지가 튀어나오는 공간까지 고려한 컨텍스트 정의 (표준 40x50 -> 50x60)
        let size = CGSize(width: baseSize.width + badgeOverhang, height: baseSize.height + badgeOverhang)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale // 기기 해상도 대응
        
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            // 1. 베이스 핀 그리기 (아래쪽에 배치)
            baseImage.draw(in: CGRect(x: 0, y: badgeOverhang, width: baseSize.width, height: baseSize.height))
            
            // 2. 뱃지 그리기 (우측 상단)
            let badgeCenter = CGPoint(x: baseSize.width - 2.0, y: badgeOverhang - 2.0)
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
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 11, weight: .bold),
                .foregroundColor: badgeColor
            ]
            let string = NSString(string: countText)
            let textSize = string.size(withAttributes: attributes)
            string.draw(at: CGPoint(x: badgeCenter.x - textSize.width/2, y: badgeCenter.y - textSize.height/2), withAttributes: attributes)
        }
    }
    
    // 하위 호환성을 위해 유지되는 메인 인터페이스
    func createShieldPin(imageName: String, color: UIColor, count: Int? = nil) -> UIImage? {
        let badgeCount = count ?? 1
        let cacheKey = "shield-\(imageName)-\(color.accessibilityName)-\(badgeCount)"
        
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
        guard let baseImage = fetchBasePin(named: imageName) else {
            return nil
        }
        
        let finalImage: UIImage
        if badgeCount > 1 {
            finalImage = applyBadge(to: baseImage, count: badgeCount, badgeColor: color)
        } else {
            finalImage = baseImage
        }
        
        PinImageHelper.imageCache[cacheKey] = finalImage
        saveToDisk(key: cacheKey, image: finalImage)
        return finalImage
    }
}

// MARK: - UIImage Extensions
extension UIImage {
    func resized(to size: CGSize) -> UIImage? {
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    func rasterized() -> UIImage? {
        // [FIX] PNG 데이터로 변환 후 재상성하여 Kakao SDK 등의 "unsupported image format" 방지
        guard let data = self.pngData() else { return nil }
        return UIImage(data: data, scale: 0.0)
    }
}


import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // [사용자 원천기술] 비트맵 캐시 시스템 (성능 최적화용 - 메모리 전용)
    private static var imageCache: [String: UIImage] = [:]
    
    /// 핀 타입 문자열("00", "01", "10", "20" 등)을 받아 정적 에셋(map_pin_XX)을 반환합니다.
    func fetchPin(type: String) -> UIImage? {
        let cacheKey = "pin-type-\(type)"
        
        // 1. 메모리 캐시 확인
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        // 2. 정적 에셋 확인 (Direct Mapping)
        // type "00" -> map_pin_00
        // type "10" -> map_pin_10
        let assetName = "map_pin_\(type)"
        
        if let staticImage = UIImage(named: assetName) {
            PinImageHelper.imageCache[cacheKey] = staticImage
            return staticImage
        }
        
        // 3. Fallback: Return nil if asset missing
        return nil
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
            // Note: 뱃지가 있는 경우 동적 합성이 불가피하므로 유지하되, BaseImage는 이미 정적 에셋일 가능성이 높음
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



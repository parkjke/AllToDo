import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // [사용자 원천기술] 비트맵 캐시 시스템 (성능 최적화용)
    private static var imageCache: [String: UIImage] = [:]
    private static var cacheVersion: Int = 1 
    
    /// 베이스 핀(Shield)을 에셋에서 가져오거나 캐시에서 반환합니다. (40x50 표준화)
    /// - Parameters:
    ///   - name: 에셋 카탈로그의 이미지 명칭
    ///   - size: 최종 타겟 사이즈 (Apple Map Standard: 40x50)
    func fetchBasePin(named imageName: String, size: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        let cacheKey = "\(imageName)-\(size.width)x\(size.height)-v\(PinImageHelper.cacheVersion)"
        if let cachedImage = PinImageHelper.imageCache[cacheKey] {
            return cachedImage
        }
        
        guard let assetImage = UIImage(named: imageName) else {
            return nil
        }
        
        // 래스터화(평면화) 및 리사이징 적용
        let resizedImage = assetImage.resized(to: size)
        let finalImage = resizedImage?.rasterized()
        
        if let finalImage = finalImage {
            PinImageHelper.imageCache[cacheKey] = finalImage
        }
        return finalImage
    }
    
    /// 베이스 이미지 위에 클러스터 숫자를 나타내는 뱃지를 합성합니다.
    /// - Parameters:
    ///   - baseImage: 배경이 될 래스터화된 핀 이미지
    ///   - count: 표시할 숫자
    ///   - badgeColor: 뱃지 색상
    func applyBadge(to baseImage: UIImage, count: Int, badgeColor: UIColor = .red) -> UIImage {
        let baseSize = baseImage.size
        let badgeSize: CGFloat = 20 
        let badgeOverhang: CGFloat = 10 
        
        // 뱃지가 튀어나오는 공간까지 고려한 컨텍스트 정의 (표준 40x50 -> 50x60)
        let size = CGSize(width: baseSize.width + badgeOverhang, height: baseSize.height + badgeOverhang)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = 0.0 // 기기 해상도(Retina) 자동 대응
        
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
        guard let baseImage = fetchBasePin(named: imageName) else {
            return nil
        }
        
        if let count = count, count > 1 {
            return applyBadge(to: baseImage, count: count, badgeColor: color)
        } else {
            return baseImage
        }
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


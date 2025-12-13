import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // Shield Shape Generator
    func createShieldPin(color: UIColor, text: String? = nil, iconName: String? = nil, count: Int? = nil, baseImage: UIImage? = nil) -> UIImage {
        // [FIX] Increase size to accommodate Badge Overhang
        let shieldWidth: CGFloat = baseImage?.size.width ?? 40
        let shieldHeight: CGFloat = baseImage?.size.height ?? 50
        
        // [FIX] Add Transparent Padding for Touch Target
        let touchPadding: CGFloat = 20
        let badgeSize: CGFloat = 20
        let badgeOverhang: CGFloat = badgeSize / 2
        
        let contextWidth = shieldWidth + badgeOverhang + (touchPadding * 2)
        let contextHeight = shieldHeight + badgeOverhang + (touchPadding * 2)
        
        let size = CGSize(width: contextWidth, height: contextHeight)
        
        return UIGraphicsImageRenderer(size: size).image { context in
            // 1. Draw Base Image (User Asset) with Padding
            let imageRect = CGRect(x: touchPadding, y: badgeOverhang + touchPadding, width: shieldWidth, height: shieldHeight)
            
            if let baseImage = baseImage {
                baseImage.draw(in: imageRect)
            }
            
            // 2. Draw Badge (Red Circle + Count)
            if let count = count {
                let badgeCenter = CGPoint(x: shieldWidth + touchPadding, y: badgeOverhang + touchPadding)
                let badgeRect = CGRect(x: badgeCenter.x - badgeSize/2, y: badgeCenter.y - badgeSize/2, width: badgeSize, height: badgeSize)
                
                // Red Circle
                let path = UIBezierPath(ovalIn: badgeRect)
                UIColor.red.setFill()
                path.fill()
                
                // Border
                UIColor.white.setStroke()
                path.lineWidth = 1.5
                path.stroke()
                
                // Text
                let countText = count > 9 ? "9+" : "\(count)"
                let fontSize: CGFloat = 11
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
                    .foregroundColor: UIColor.white
                ]
                let string = NSString(string: countText)
                let textSize = string.size(withAttributes: attributes)
                string.draw(at: CGPoint(x: badgeCenter.x - textSize.width/2, y: badgeCenter.y - textSize.height/2), withAttributes: attributes)
            }
        }

    }
}

// MARK: - UIImage Extensions
extension UIImage {
    func resized(to size: CGSize) -> UIImage? {
        // [FIX] Use scale 1.0 to prevent double-sizing on Retina screens in KakaoMap
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    func rasterized() -> UIImage? {
        // [FIX] Flatten to PNG data to resolve "unsupported image format" in Kakao SDK
        guard let data = self.pngData() else { return nil }
        return UIImage(data: data, scale: 1.0)
    }
}

import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // Shield Shape Generator
    func createShieldPin(color: UIColor, text: String? = nil, iconName: String? = nil, count: Int? = nil, baseImage: UIImage? = nil) -> UIImage {
        // [FIX] Use Base Image Size if available, otherwise default to 32x40
        let shieldWidth: CGFloat = baseImage?.size.width ?? 32
        let shieldHeight: CGFloat = baseImage?.size.height ?? 40
        
        let touchPadding: CGFloat = 0
        let badgeSize: CGFloat = 16
        let badgeOverhang: CGFloat = badgeSize / 2
        
        let contextWidth = shieldWidth + badgeOverhang
        let contextHeight = shieldHeight + badgeOverhang
        
        let size = CGSize(width: contextWidth, height: contextHeight)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = 0.0 // Device Scale
        
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cgContext = context.cgContext
            
            // 1. Draw Shield Base (Manual Bezier Path as Fallback/Base)
            // Even if baseImage is nil, we MUST see the pin shape.
            let shieldRect = CGRect(x: 0, y: badgeOverhang, width: shieldWidth, height: shieldHeight)
            let path = UIBezierPath()
            let r = shieldWidth / 2
            let tailHeight = shieldHeight - shieldWidth
            
            path.move(to: CGPoint(x: shieldWidth/2, y: badgeOverhang + shieldHeight)) // Tip
            path.addCurve(to: CGPoint(x: 0, y: badgeOverhang + r),
                         controlPoint1: CGPoint(x: shieldWidth/2 - 5, y: badgeOverhang + shieldHeight - 5),
                         controlPoint2: CGPoint(x: 0, y: badgeOverhang + shieldHeight - 10))
            path.addArc(withCenter: CGPoint(x: r, y: badgeOverhang + r), radius: r, startAngle: .pi, endAngle: 0, clockwise: true)
            path.addCurve(to: CGPoint(x: shieldWidth/2, y: badgeOverhang + shieldHeight),
                         controlPoint1: CGPoint(x: shieldWidth, y: badgeOverhang + shieldHeight - 10),
                         controlPoint2: CGPoint(x: shieldWidth/2 + 5, y: badgeOverhang + shieldHeight - 5))
            path.close()
            
            color.setFill()
            path.fill()
            
            // 2. Draw Base Image if exists (Asset Override)
            if let baseImage = baseImage {
                baseImage.draw(in: shieldRect)
            }
            
            // 3. Draw Badge (Top-Right Corner)
            if let count = count {
                let badgeCenter = CGPoint(x: shieldWidth - 1.0, y: badgeOverhang + 1.0)
                let badgeRect = CGRect(x: badgeCenter.x - badgeSize/2, y: badgeCenter.y - badgeSize/2, width: badgeSize, height: badgeSize)
                
                let bPath = UIBezierPath(ovalIn: badgeRect)
                UIColor.white.setFill()
                bPath.fill()
                UIColor.red.setStroke()
                bPath.lineWidth = 1.5
                bPath.stroke()
                
                let countText = count > 9 ? "9+" : "\(count)"
                let fontSize: CGFloat = 11
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: fontSize, weight: .bold),
                    .foregroundColor: UIColor.red
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
        // [FIX] Use scale 0.0 for Retina Display Support
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    func rasterized() -> UIImage? {
        // [FIX] Flatten to PNG data to resolve "unsupported image format" in Kakao SDK
        guard let data = self.pngData() else { return nil }
        return UIImage(data: data, scale: 0.0) // Match device scale
    }
}

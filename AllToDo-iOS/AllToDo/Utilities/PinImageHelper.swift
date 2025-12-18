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
            // 1. Draw Base Image (Shifted down due to badge overhang possibility? No, badge overhang adds to width/height)
            // Wait, badgeOverhang is added to context dimension.
            // If badge is top-right, it extends beyond shieldWidth/0.
            
            // Logic:
            // Shield is at (0, badgeOverhang). badgeOverhang (8pt) pushes it down.
            // Original logic: `y: badgeOverhang` (Line 26).
            // This means visual top of pin is at y=8.
            // Badge center is at (width, 8).
            // Badge is 16x16, so it goes from y=0 to y=16.
            // If we keep this logic, it works for any size.
            
            let imageRect = CGRect(x: 0, y: badgeOverhang, width: shieldWidth, height: shieldHeight)
            
            if let baseImage = baseImage {
                baseImage.draw(in: imageRect)
            }
            
            // 2. Draw Badge (Top-Right Corner of Pin)
            if let count = count {
                let badgeCenter = CGPoint(x: shieldWidth, y: badgeOverhang)
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

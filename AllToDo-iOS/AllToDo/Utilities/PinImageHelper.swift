import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // Shield Shape Generator
    func createShieldPin(color: UIColor, text: String? = nil, iconName: String? = nil, count: Int? = nil, baseImage: UIImage? = nil) -> UIImage {
        // [FIX] Increase size to accommodate Badge Overhang
        let shieldWidth: CGFloat = baseImage?.size.width ?? 40
        let shieldHeight: CGFloat = baseImage?.size.height ?? 50
        
        let badgeSize: CGFloat = 20
        let badgeOverhang: CGFloat = badgeSize / 2
        
        let contextWidth = shieldWidth + badgeOverhang
        let contextHeight = shieldHeight + badgeOverhang
        
        let size = CGSize(width: contextWidth, height: contextHeight)
        
        return UIGraphicsImageRenderer(size: size).image { context in
            // 1. Draw Base Image (User Asset)
            let imageRect = CGRect(x: 0, y: badgeOverhang, width: shieldWidth, height: shieldHeight)
            
            if let baseImage = baseImage {
                baseImage.draw(in: imageRect)
            }
            
            // 2. Draw Badge (Red Circle + Count)
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
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }
    
    func rasterized() -> UIImage? {
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        draw(in: CGRect(origin: .zero, size: size))
        let image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image
    }
}

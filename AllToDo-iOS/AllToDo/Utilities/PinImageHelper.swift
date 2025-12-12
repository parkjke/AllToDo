import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // Shield Shape Generator
    func createShieldPin(color: UIColor, text: String? = nil, iconName: String? = nil, count: Int? = nil) -> UIImage {
        let width: CGFloat = 40
        let height: CGFloat = 50
        let size = CGSize(width: width, height: height)
        
        return UIGraphicsImageRenderer(size: size).image { context in
            // 1. Draw Shield Path
            let path = UIBezierPath()
            let cornerRadius: CGFloat = 4
            
            // Start Top Left
            path.move(to: CGPoint(x: 0, y: 0))
            // Top Edge (Slight curve or flat? Logo is flat top with rounded corners)
            path.addLine(to: CGPoint(x: width, y: 0))
            
            // Right Side down to ~60%
            path.addLine(to: CGPoint(x: width, y: height * 0.55))
            
            // Curve to Bottom Center Tip
            path.addQuadCurve(to: CGPoint(x: width / 2, y: height), controlPoint: CGPoint(x: width, y: height * 0.9))
            
            // Curve to Left Side
            path.addQuadCurve(to: CGPoint(x: 0, y: height * 0.55), controlPoint: CGPoint(x: 0, y: height * 0.9))
            
            path.close()
            
            color.setFill()
            path.fill()
            
            // Border so it stands out
            UIColor.white.setStroke()
            path.lineWidth = 2
            path.stroke()
            
            // 2. Content (Center Top Area)
            let contentCenter = CGPoint(x: width / 2, y: height * 0.4)
            
            if let count = count {
                // ... same count logic ...
                let countText = count > 9 ? "9+" : "\(count)"
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 14, weight: .bold),
                    .foregroundColor: UIColor.white
                ]
                let string = NSString(string: countText)
                let textSize = string.size(withAttributes: attributes)
                string.draw(at: CGPoint(x: contentCenter.x - textSize.width/2, y: contentCenter.y - textSize.height/2), withAttributes: attributes)
                
            } else if let iconName = iconName {
                let config = UIImage.SymbolConfiguration(pointSize: 18, weight: .bold)
                if let icon = UIImage(systemName: iconName, withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                    icon.draw(at: CGPoint(x: contentCenter.x - icon.size.width/2, y: contentCenter.y - icon.size.height/2))
                }
            } else if let text = text {
                 let attributes: [NSAttributedString.Key: Any] = [
                     .font: UIFont.systemFont(ofSize: 12, weight: .bold),
                     .foregroundColor: UIColor.white
                 ]
                 let string = NSString(string: text)
                 let textSize = string.size(withAttributes: attributes)
                 string.draw(at: CGPoint(x: contentCenter.x - textSize.width/2, y: contentCenter.y - textSize.height/2), withAttributes: attributes)
            }
        }
    }
}

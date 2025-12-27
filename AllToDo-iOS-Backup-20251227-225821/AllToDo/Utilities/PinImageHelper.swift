import UIKit

class PinImageHelper {
    static let shared = PinImageHelper()
    
    // [NEW] Bitmap Cache & Versioning
    private var bitmapCache: [String: UIImage] = [:]
    private let PIN_VERSION = "1.0.0"
    
    // Clear cache if version changes (Future use)
    func clearCache() {
        bitmapCache.removeAll()
    }
    
    // [NEW] Preload all pin bitmaps at app startup (Android-style)
    func preloadAllPins() {
        let pinNames = [
            "PinCurrent",
            "PinHistory", 
            "PinTodoReady",
            "PinTodoDone",
            "PinReceiveReady",
            "PinReceiveDone",
            "PinReceiveReject",
            "PinTodoCancel",
            "PinTodoFail"
        ]
        
        // Preload for different target sizes
        let sizes = [
            CGSize(width: 40, height: 50),  // Apple/Google
            CGSize(width: 36, height: 45),  // Naver (0.9 scale)
            CGSize(width: 28, height: 35)   // Kakao (0.7 scale)
        ]
        
        for name in pinNames {
            for size in sizes {
                _ = fetchBasePin(named: name, targetSize: size)
            }
        }
        
        print("[PinImageHelper] Preloaded \(pinNames.count) pins × \(sizes.count) sizes = \(bitmapCache.count) bitmaps")
    }
    
    // [NEW] Fetch or Create Base Pin (Cached & Rasterized)
    func fetchBasePin(named name: String, targetSize: CGSize = CGSize(width: 40, height: 50)) -> UIImage? {
        let cacheKey = "base_\(name)_\(targetSize.width)x\(targetSize.height)_\(PIN_VERSION)"
        
        if let cached = bitmapCache[cacheKey] {
            return cached
        }
        
        // Rasterize SVG asset to fixed size bitmap
        if let baseImage = UIImage(named: name) {
            let rasterized = baseImage.resized(to: targetSize)?.rasterized()
            bitmapCache[cacheKey] = rasterized
            return rasterized
        }
        
        return nil
    }
    
    // [NEW] Apply Badge to an existing base Pin (Dynamic merging)
    func applyBadge(to baseImage: UIImage, count: Int) -> UIImage {
        let badgeSize: CGFloat = 16
        let badgeOverhang: CGFloat = badgeSize / 2
        
        let shieldWidth = baseImage.size.width
        let shieldHeight = baseImage.size.height
        
        let size = CGSize(width: shieldWidth + badgeOverhang, height: shieldHeight + badgeOverhang)
        
        let format = UIGraphicsImageRendererFormat()
        format.scale = baseImage.scale
        
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            // 1. Draw Base 
            baseImage.draw(in: CGRect(x: 0, y: badgeOverhang, width: shieldWidth, height: shieldHeight))
            
            // 2. Draw Badge (Top-Right)
            let badgeCenter = CGPoint(x: shieldWidth - 1.0, y: badgeOverhang + 1.0)
            let badgeRect = CGRect(x: badgeCenter.x - badgeSize/2, y: badgeCenter.y - badgeSize/2, width: badgeSize, height: badgeSize)
            
            let bPath = UIBezierPath(ovalIn: badgeRect)
            UIColor.white.setFill()
            bPath.fill()
            UIColor.red.setStroke()
            bPath.lineWidth = 1.5
            bPath.stroke()
            
            let countText = count > 9 ? "9+" : "\(count)"
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 11, weight: .bold),
                .foregroundColor: UIColor.red
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
        // [FIX] Use UIScreen.main.scale explicitly since 0.0 doesn't work for UIImage(data:scale:)
        return UIImage(data: data, scale: UIScreen.main.scale) 
    }
}

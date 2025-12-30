#if DEBUG
import SwiftUI
import MapKit
import UIKit

// Map Provider Simulation
struct MapProviderPinCell: View {
    enum Provider: String, CaseIterable {
        case apple = "Apple"
        case google = "Google"
        case naver = "Naver"
        case kakao = "Kakao"
    }
    
    let provider: Provider
    let type: String
    let count: Int
    
    var body: some View {
        VStack {
            ZStack(alignment: .topLeading) {
                // 1. Reference Box
                Rectangle()
                    .fill(Color.gray.opacity(0.1))
                    .frame(width: 80, height: 80)
                    .overlay(Rectangle().stroke(Color.gray.opacity(0.2), lineWidth: 1))
                
                // 2. Crosshair (Map Coordinate)
                Path { path in
                    path.move(to: CGPoint(x: 40, y: 0))
                    path.addLine(to: CGPoint(x: 40, y: 80))
                    path.move(to: CGPoint(x: 0, y: 40))
                    path.addLine(to: CGPoint(x: 80, y: 40))
                }
                .stroke(Color.blue.opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: [4]))
                
                // 3. Red Dot (Coordinate)
                Circle()
                    .fill(Color.red)
                    .frame(width: 4, height: 4)
                    .position(x: 40, y: 40)
                    .zIndex(100)
                
                // 4. Pin Image
                if let params = getPinExample() {
                    let img = params.image
                    let anchor = params.anchor
                    
                    let w = img.size.width
                    let h = img.size.height
                    
                    // Logic:
                    // Center of Image should be placed such that the Anchor Point aligns with (40, 40).
                    // Anchor (0,0) -> Image TopLeft at (40,40)
                    // Anchor (0.5, 0.5) -> Image Center at (40,40)
                    // Anchor (1.0, 1.0) -> Image BottomRight at (40,40)
                    
                    // Image Center relative to TopLeft (w/2, h/2)
                    // Anchor Point in pixels relative to TopLeft (anchor.x * w, anchor.y * h)
                    // Shift needed: (40, 40) - (AnchorX_px, AnchorY_px)
                    // TargetTopLeft = (40 - anchor.x*w, 40 - anchor.y*h)
                    // TargetCenter = TargetTopLeft + (w/2, h/2)
                    
                    let targetCx = 40 - (anchor.x * w) + (w/2)
                    let targetCy = 40 - (anchor.y * h) + (h/2)
                    
                    Image(uiImage: img)
                        .position(x: targetCx, y: targetCy)
                }
            }
            .frame(width: 80, height: 80)
            
            Text(provider.rawValue)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
    }
    
    func getPinExample() -> (image: UIImage, anchor: CGPoint)? {
        guard let base = PinImageHelper.shared.fetchPin(type: type) else { return nil }
        
        let badgeColor: UIColor = (type == "10") ? .allToDoGreen : .systemBlue
        let isBadged = count > 1
        
        switch provider {
        case .naver:
            // Naver Logic (Recently Fixed)
            let targetSize = CGSize(width: 36, height: 45)
            guard let resized = base.resized(to: targetSize) else { return nil }
            
            if isBadged {
                let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: badgeColor, badgeSize: 18)
                return (badged, CGPoint(x: 18.0/46.0, y: 1.0))
            } else {
                return (resized, CGPoint(x: 0.5, y: 1.0))
            }
            
        case .kakao:
            // Kakao Logic (Recently Fixed)
            // Scale 0.7x -> 28x35
            let targetSize = CGSize(width: 28, height: 35)
            guard let resized = base.resized(to: targetSize) else { return nil }
            
            if isBadged {
                // Badged: Base(28) + 10 = 38. Tip at 14. Anchor = 14/38.
                let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: badgeColor, badgeSize: 14) // Kakao badge 14
                return (badged, CGPoint(x: 14.0/38.0, y: 1.0))
            } else {
                return (resized, CGPoint(x: 0.5, y: 1.0))
            }
            
        case .google:
            // Google Logic (View: GoogleMapView.swift)
            // Raw: uses base image. Anchor (0.5, 1.0)
            // Badged: uses applyBadge(badgeSize: 20). Anchor (0.4, 1.0)
            if isBadged {
                let badged = PinImageHelper.shared.applyBadge(to: base, count: count, badgeColor: badgeColor, badgeSize: 20)
                return (badged, CGPoint(x: 0.4, y: 1.0))
            } else {
                return (base, CGPoint(x: 0.5, y: 1.0))
            }
            
        case .apple:
            // Apple Logic (View: AppleMapView.swift)
            // Uses centerOffset. 
            // Frame is set to image size.
            // centerOffset (0, -25) -> Shift Up 25 -> Bottom Center is Anchor?
            // If H=50, H/2=25. (0, -25) aligns bottom.
            // Anchor equivalent: (0.5, 1.0)
            
            // Badged: centerOffset (-5, 30) in code.
            // Let's visualize EXACTLY what (-5, 30) does relative to Center.
            // Anchor X: -5 offset means shift Left 5px from center. 
            // If Anchor is normalized position: CenterX + offsetX = AnchorX_px
            // AnchorX = 0.5 + (-5/W) ? No. 
            // centerOffset is "Offset of the center point of the view relative to the annotation's coordinate".
            // If I want the VIEW's center to be at (CoordX - 5, CoordY + 30).
            // It means the VIEW is shifted Left 5 and Down 30.
            // So the Coordinate is at (ViewCenterX + 5, ViewCenterY - 30).
            // Anchor Logic: Where is the Coordinate relative to the Image?
            // AnchorX = 0.5 + (5/W)
            // AnchorY = 0.5 - (30/H)
            
            if isBadged {
                let badged = PinImageHelper.shared.applyBadge(to: base, count: count, badgeColor: badgeColor, badgeSize: 20)
                // Assuming L799 centerOffset is correct, let's reverse engineer anchor
                // offset = (-5, 30)
                let w = badged.size.width
                let h = badged.size.height
                
                // Coord is at ViewCenter - offset? No.
                // Apple Doc: "Positive values move the view down and right."
                // So View Center is moved (-5, 30) from Coord.
                // Coord is at (ViewCenter.x - (-5), ViewCenter.y - 30) 
                // Coord = (ViewCenter.x + 5, ViewCenter.y - 30)
                
                // Normalized Anchor:
                // Ax = (W/2 + 5) / W
                // Ay = (H/2 - 30) / H
                
                let ax = (w/2.0 + 5.0) / w
                let ay = (h/2.0 - 30.0) / h
                
                return (badged, CGPoint(x: ax, y: ay))
            } else {
                // centerOffset (0, -25)
                // View Center moved (0, -25) from Coord.
                // Coord = (ViewCenter.x - 0, ViewCenter.y - (-25)) = (ViewCenter.x, ViewCenter.y + 25)
                // If H=50 (approx base), 25 is H/2.
                // Ay = (H/2 + 25) / H = 50/50 = 1.0. Correct.
                
                let w = base.size.width
                let h = base.size.height
                
                // Using 25 hardcoded from AppleMapView defaults (assuming height 50)
                // Actually AppleMapView sets `view.centerOffset = CGPoint(x: 0, y: -25)`.
                
                let ax = 0.5
                let ay = (h/2.0 + 25.0) / h 
                
                return (base, CGPoint(x: ax, y: ay))
            }
        }
    }
}
#endif

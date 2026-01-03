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
    var onDetailsRequested: ((PinDetail) -> Void)? = nil
    
    var body: some View {
        VStack {
            Button(action: {
                if let params = getPinExample() {
                    let detail = PinDetail(provider: provider, type: type, count: count, image: params.image, anchor: params.anchor)
                    onDetailsRequested?(detail)
                }
            }) {
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
                    path.move(to: CGPoint(x: 0, y: 60))
                    path.addLine(to: CGPoint(x: 80, y: 60))
                }
                .stroke(Color.blue.opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: [4]))
                
                // 3. Red Dot (Coordinate)
                Circle()
                    .fill(Color.red)
                    .frame(width: 4, height: 4)
                    .position(x: 40, y: 60)
                    .zIndex(100)
                
                // 4. Pin Image
                if let params = getPinExample() {
                    let img = params.image
                    let anchor = params.anchor
                    
                    let w = img.size.width
                    let h = img.size.height
                    
                    // Logic:
                    // Center of Image should be placed such that the Anchor Point aligns with (40, 60).
                    // TargetTopLeft = (40 - anchor.x*w, 60 - anchor.y*h)
                    
                    let targetCx = 40 - (anchor.x * w) + (w/2)
                    let targetCy = 60 - (anchor.y * h) + (h/2)
                    
                    Image(uiImage: img)
                        .position(x: targetCx, y: targetCy)
                }
            }
            .frame(width: 80, height: 80)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
            
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
            // Naver Logic: Scale 0.9x (36x45)
            let targetSize = CGSize(width: 36, height: 45)
            guard let resized = base.resized(to: targetSize) else { return nil }
            
            if isBadged {
                let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: badgeColor, badgeSize: 18)
                return (badged, CGPoint(x: 18.0/46.0, y: 1.0))
            } else {
                return (resized, CGPoint(x: 0.5, y: 1.0))
            }
            
        case .kakao:
            // Kakao Logic: Scale 0.7x -> 28x35
            let targetSize = CGSize(width: 28, height: 35)
            guard let resized = base.resized(to: targetSize) else { return nil }
            
            if isBadged {
                let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: badgeColor, badgeSize: 14) 
                return (badged, CGPoint(x: 14.0/38.0, y: 1.0))
            } else {
                return (resized, CGPoint(x: 0.5, y: 1.0))
            }
            
        case .google:
            // Google Logic
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
                let w = badged.size.width
                let h = badged.size.height
                
                // Original Apple Badged
                let ax = (w/2.0 - 5.0) / w
                let ay = (h/2.0 + 30.0) / h
                
                return (badged, CGPoint(x: ax, y: ay))
            } else {
                let w = base.size.width
                let h = base.size.height
                
                let ax = 0.5
                let ay = (h/2.0 + 25.0) / h 
                
                return (base, CGPoint(x: ax, y: ay))
            }
        }
    }
}
// MARK: - Inspector View (Precision Tool)
struct PinInspectorView: View {
    let detail: PinDetail
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                VStack(alignment: .leading) {
                    Text("\(detail.provider.rawValue) Map - Type \(detail.type)")
                        .font(.title2.bold())
                    Text(detail.count > 1 ? "Cluster (\(detail.count) items)" : "Single Item")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button("Done") { dismiss() }
                    .font(.headline)
            }
            .padding()
            .background(Color(.systemBackground))
            
            Divider()
            
            // Inspection Area (The Grid)
            GeometryReader { geo in
                // [FIX] Move center point down by 1/4 to prevent top clipping
                let center = CGPoint(x: geo.size.width / 2, y: geo.size.height * 0.75)
                let scale: CGFloat = 8.0 // 8x Zoom for pixel-perfect check
                
                ZStack {
                    // 1. Grid Background
                    GridBackground()
                        .stroke(Color.gray.opacity(0.1), lineWidth: 0.5)
                    
                    // 2. Crosshair (Map Target)
                    Path { path in
                        path.move(to: CGPoint(x: center.x, y: 0))
                        path.addLine(to: CGPoint(x: center.x, y: geo.size.height))
                        path.move(to: CGPoint(x: 0, y: center.y))
                        path.addLine(to: CGPoint(x: geo.size.width, y: center.y))
                    }
                    .stroke(Color.blue.opacity(0.5), lineWidth: 1.5)
                    
                    // 3. Pin Image (Mirrored Logic)
                    let img = detail.image
                    let anchor = detail.anchor
                    let w = img.size.width * scale
                    let h = img.size.height * scale
                    
                    // Calculation: 
                    // To place the Anchor point at the Center (crosshair),
                    // The TopLeft of the image should be: Center - (Anchor * DisplaySize)
                    let topLeftX = center.x - (anchor.x * w)
                    let topLeftY = center.y - (anchor.y * h)
                    
                    Image(uiImage: img)
                        .resizable()
                        .interpolation(.none) // CRITICAL: Maintain pixels
                        .frame(width: w, height: h)
                        .position(x: topLeftX + (w/2), y: topLeftY + (h/2))
                    
                    // 4. Focal Dot (The exact anchor point on image)
                    Circle()
                        .fill(Color.red)
                        .frame(width: 6, height: 6)
                        .position(center)
                        .zIndex(200)
                }
                .clipped()
            }
            .background(Color(.secondarySystemBackground))
            
            // Footer Info
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label("Logic Mirroring", systemImage: "cpu")
                        .font(.caption.bold())
                    Spacer()
                }
                
                Group {
                    if detail.provider == .apple {
                        Text("Policy: centerOffset (UIScreen coords)")
                        let offsetX = (detail.anchor.x * detail.image.size.width) - (detail.image.size.width/2.0)
                        let offsetY = (detail.anchor.y * detail.image.size.height) - (detail.image.size.height/2.0)
                        Text("Simulated centerOffset: (\(String(format: "%.1f", offsetX)), \(String(format: "%.1f", -offsetY)))")
                    } else {
                        Text("Policy: normalized anchor (UV coords)")
                        Text("Anchor Point: (\(String(format: "%.3f", detail.anchor.x)), \(String(format: "%.3f", detail.anchor.y)))")
                    }
                    Text("Asset Size: \(Int(detail.image.size.width))x\(Int(detail.image.size.height)) px")
                }
                .font(.system(.caption, design: .monospaced))
            }
            .padding()
            .background(Color(.systemBackground))
        }
    }
}

struct GridBackground: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let step: CGFloat = 20
        for x in stride(from: 0, through: rect.width, by: step) {
            path.move(to: CGPoint(x: x, y: 0))
            path.addLine(to: CGPoint(x: x, y: rect.height))
        }
        for y in stride(from: 0, through: rect.height, by: step) {
            path.move(to: CGPoint(x: 0, y: y))
            path.addLine(to: CGPoint(x: rect.width, y: y))
        }
        return path
    }
}

struct PinDetail: Identifiable {
    let id = UUID()
    let provider: MapProviderPinCell.Provider
    let type: String
    let count: Int
    let image: UIImage
    let anchor: CGPoint
}
#endif

import SwiftUI

struct PinGalleryView: View {
    // 13 types found in Assets
    let allTypes = [
        "00", "01", "02",
        "10", "11", "12", "13", "14",
        "20", "21", "22", "23", "24"
    ]
    
    // Target types for Section 2 & 3
    let targetTypes = ["00", "10", "20"]
    
    // Layout
    let columns = [GridItem(.adaptive(minimum: 80, maximum: 100), spacing: 20)]
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 40) {
                    headerView
                    
                    // Section 1: All Bitmap Pins
                    VStack(alignment: .leading) {
                        SectionHeader(title: "1. All Bitmap Pins", subtitle: "Assets.xcassets 내의 모든 map_pin_XX 정적 이미지")
                        LazyVGrid(columns: columns, spacing: 20) {
                            ForEach(allTypes, id: \.self) { type in
                                PinCell(type: type)
                            }
                        }
                    }
                    .padding(.horizontal)
                    
                    Divider()
                    
                    // Section 2: Badged Pins + Anchor
                    VStack(alignment: .leading) {
                        SectionHeader(title: "2. Badged Pins + Anchor", subtitle: "뱃지(Count: 5) 적용 및 지도 좌표(Anchor) 빨간 점 표시")
                        HStack(spacing: 30) {
                            ForEach(targetTypes, id: \.self) { type in
                                AnchorPinCell(type: type, count: 5, showBadge: true)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal)
                    
                    Divider()
                    
                    // Section 3: Raw Pins + Anchor
                    VStack(alignment: .leading) {
                        SectionHeader(title: "3. Raw Pins + Anchor", subtitle: "뱃지 없음, 지도 좌표(Anchor) 빨간 점 표시")
                        HStack(spacing: 30) {
                            ForEach(targetTypes, id: \.self) { type in
                                AnchorPinCell(type: type, count: 0, showBadge: false)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal)
                }
                .padding(.vertical)
            }
            .navigationTitle("Pin Gallery")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
    
    var headerView: some View {
        VStack(spacing: 8) {
            Text("📌 Pin Gallery Refactored")
                .font(.title2.bold())
            Text("Static Asset Verification")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

// MARK: - Subviews

struct SectionHeader: View {
    let title: String
    let subtitle: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline)
            Text(subtitle)
                .font(.caption)
                .foregroundColor(.gray)
        }
        .padding(.bottom, 10)
    }
}

struct PinCell: View {
    let type: String
    
    var body: some View {
        VStack {
            if let params = getPinParameters(type: type) {
                // Resize for display consistency (simulating map sizing)
                Image(uiImage: params.image)
                    .resizable()
                    .scaledToFit()
                    .frame(height: 45) // Native height approx
                    .shadow(radius: 1)
                
                Text(type)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .monospacedDigit()
            } else {
                Image(systemName: "exclamationmark.triangle")
                    .foregroundColor(.red)
                Text("Missing: \(type)")
                    .font(.caption2)
            }
        }
        .frame(height: 80)
        .frame(maxWidth: .infinity)
        .background(Color.gray.opacity(0.05))
        .cornerRadius(8)
    }
    
    func getPinParameters(type: String) -> (image: UIImage, anchor: CGPoint)? {
        guard let img = PinImageHelper.shared.fetchPin(type: type) else { return nil }
        // Default Anchor for Naver (as reference)
        // x: 18.0 / 46.0 ~= 0.39, y: 1.0 (Bottom)
        let anchor = CGPoint(x: 18.0 / 46.0, y: 1.0)
        return (img, anchor)
    }
}

struct AnchorPinCell: View {
    let type: String
    let count: Int
    let showBadge: Bool
    
    var body: some View {
        VStack {
            ZStack(alignment: .topLeading) {
                // 1. Reference Box (Light Gray)
                Rectangle()
                    .fill(Color.gray.opacity(0.1))
                    .frame(width: 80, height: 80)
                    .overlay(
                        Rectangle().stroke(Color.gray.opacity(0.2), lineWidth: 1)
                    )
                
                // 2. Center Crosshair (The "Map Coordinate")
                Path { path in
                    path.move(to: CGPoint(x: 40, y: 0))
                    path.addLine(to: CGPoint(x: 40, y: 80))
                    path.move(to: CGPoint(x: 0, y: 40))
                    path.addLine(to: CGPoint(x: 80, y: 40))
                }
                .stroke(Color.blue.opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: [4]))
                
                // 3. The Red Dot (The Map Coordinate Point)
                Circle()
                    .fill(Color.red)
                    .frame(width: 4, height: 4)
                    .position(x: 40, y: 40)
                    .zIndex(100)
                
                // 4. The Pin Image (Positioned relative to Red Dot using Anchor)
                if let params = getPinExample() {
                    let img = params.image
                    let anchor = params.anchor
                    
                    // Calculation:
                    // We want the image's "Anchor Point" to be at (40, 40).
                    // Image Size
                    let w = img.size.width
                    let h = img.size.height
                    
                    // Offsets
                    // If AnchorX is 0.5, we shift left by 0.5 * W.
                    // If AnchorY is 1.0, we shift up by 1.0 * H.
                    let offsetX = -1 * anchor.x * w
                    let offsetY = -1 * anchor.y * h
                    
                    // SwiftUI Image Layout
                    // Position places the CENTER of the view.
                    // So we need to calculate where the CENTER should be.
                    // Target TopLeft = (40 + offsetX, 40 + offsetY)
                    // CenterX = TargetTopLeftX + w/2
                    // CenterY = TargetTopLeftY + h/2
                    
                    let targetCx = 40 + offsetX + (w/2)
                    let targetCy = 40 + offsetY + (h/2)
                    
                    Image(uiImage: img)
                        .position(x: targetCx, y: targetCy)
                }
            }
            .frame(width: 80, height: 80)
            
            Text(type)
                .font(.caption)
                .bold()
        }
    }
    
    func getPinExample() -> (image: UIImage, anchor: CGPoint)? {
        guard let base = PinImageHelper.shared.fetchPin(type: type) else { return nil }
        
        // Naver Anchor Logic (Standard for this project)
        let anchor = CGPoint(x: 18.0 / 46.0, y: 1.0)
        
        // Colors mapping
        var badgeColor: UIColor = .red
        if type == "10" { badgeColor = .allToDoGreen } // Local todo default? check logic
        else if type == "20" { badgeColor = .systemBlue }
        
        // Resize for Retina/consistency (Naver size 36x45)
        let targetSize = CGSize(width: 36, height: 45)
        guard let resized = base.resized(to: targetSize) else { return nil }
        
        if showBadge {
            let badged = PinImageHelper.shared.applyBadge(to: resized, count: count, badgeColor: badgeColor, badgeSize: 18)
            return (badged, anchor)
        } else {
            return (resized, anchor)
        }
    }
}

// Preview
struct PinGalleryView_Previews: PreviewProvider {
    static var previews: some View {
        PinGalleryView()
    }
}

import SwiftUI

struct PinGalleryView: View {
    let columns = [
        GridItem(.adaptive(minimum: 100))
    ]
    
    // Test Data Definition
    struct GalleryItem: Hashable, Identifiable {
        let id = UUID()
        let name: String
        let shield: String
        let mark: String
        let color: UIColor
    }
    
    let baseItems: [GalleryItem] = [
        GalleryItem(name: "Todo Ready", shield: "pin_shield_1X", mark: "pin_mark_10", color: .allToDoGreen),
        GalleryItem(name: "Todo Done", shield: "pin_shield_1X", mark: "pin_mark_12", color: .allToDoGreen),
        GalleryItem(name: "History", shield: "pin_shield_0X", mark: "pin_mark_01", color: .red),
        GalleryItem(name: "Server Msg", shield: "pin_shield_2X", mark: "pin_mark_20", color: .systemBlue),
        GalleryItem(name: "Current", shield: "pin_shield_0X", mark: "pin_mark_00", color: .red),
        GalleryItem(name: "Pin 23", shield: "pin_shield_2X", mark: "pin_mark_23", color: .systemBlue)
    ]
    
    let testCounts = [1, 5, 10, 99]
    
    // [NEW] Trigger for Regeneration
    @State private var refreshID = UUID()
    
    var body: some View {
        NavigationView {
                ScrollView {
                    VStack(spacing: 30) {
                        // Main Header
                        VStack(spacing: 8) {
                            Text("📌 핀 디자인 검증 갤러리")
                                .font(.title2)
                                .fontWeight(.bold)
                            Text("Dynamic Composition v4.0")
                                .font(.caption)
                                .foregroundColor(.blue)
                                .padding(.bottom, 5)
                            Text("iOS와 Android 간 핀 렌더링 일관성(크기, 뱃지 위치, 중심점)을 확인하는 도구입니다.")
                                .font(.caption)
                                .multilineTextAlignment(.center)
                                .foregroundColor(.secondary)
                                .padding(.horizontal)
                        }
                        .padding(.top)
                        
                        // Section 1: Base Composite Assets
                        VStack(alignment: .leading, spacing: 5) {
                            Text("1. 동적 합성 (Base Composite)")
                                .font(.headline)
                                .padding(.horizontal)
                            Text("Shield와 Mark가 런타임에 합성됩니다.")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .padding(.horizontal)
                            
                            LazyVGrid(columns: columns, spacing: 20) {
                                ForEach(baseItems, id: \.self) { item in
                                    VStack {
                                        if let img = generateBasePin(item: item) {
                                            Image(uiImage: img)
                                                .resizable()
                                                .scaledToFit()
                                                .frame(width: 40, height: 50)
                                                .border(Color.blue.opacity(0.3)) // Border to see bounds
                                            Text(item.name).font(.caption).foregroundColor(.gray)
                                        } else {
                                            Text("Fail\n\(item.name)")
                                                .font(.caption)
                                                .foregroundColor(.red)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Section 2: Badged Pins (Overhang Test)
                        VStack(alignment: .leading, spacing: 5) {
                            Text("2. 뱃지 오버행 (Overhang)")
                                .font(.headline)
                                .padding(.horizontal)
                            Text("✅ 정상: 붉은색 뱃지가 핀 우측 상단으로 튀어나와야 합니다.")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .padding(.horizontal)
                            
                            LazyVGrid(columns: columns, spacing: 30) {
                                ForEach(testItems) { testItem in
                                    VStack {
                                        if let img = generateBadgePin(item: testItem.baseItem, count: testItem.count) {
                                            Image(uiImage: img)
                                                .border(Color.red.opacity(0.3)) // Canvas Border
                                            Text("+\(testItem.count)")
                                                .font(.caption)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Section 3: Anchor Point Verification
                        VStack(alignment: .leading, spacing: 5) {
                            Text("3. 앵커 포인트 (중심점) 확인")
                                .font(.headline)
                                .padding(.horizontal)
                            Text("🔴 빨간 점 = 지도 좌표 (Anchor 0.4, 1.0)")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .padding(.horizontal)
                            
                            LazyVGrid(columns: columns, spacing: 40) {
                                ForEach(baseItems, id: \.self) { item in
                                    ZStack(alignment: .topLeading) {
                                        // The Anchor Dot (Simulated Map Point)
                                        Circle()
                                            .fill(Color.red)
                                            .frame(width: 4, height: 4)
                                            .position(x: 50, y: 50) // Center of cell
                                            .zIndex(10)
                                        
                                        // The Pin
                                        if let img = generateBadgePin(item: item, count: 5) {
                                            // Visual Logic (Same as before):
                                            // Anchor (0.4, 1.0) -> relative to image (0,0) is (width*0.4, height*1.0)
                                            // We align that point to Cell Center (50, 50)
                                            
                                            // Image Frame Calculation
                                            // Let img size be W, H
                                            // Anchor Point in Image: Ax = 0.4*W, Ay = 1.0*H
                                            // Target Position in Cell: Tx = 50, Ty = 50
                                            // Image Origin (TopLeft) in Cell: Ox = Tx - Ax, Oy = Ty - Ay
                                            // SwiftUI .position places the CENTER of the view.
                                            // Image Center: Cx = W/2, Cy = H/2
                                            // Position to set: Px = Ox + Cx, Py = Oy + Cy
                                            // Px = Tx - Ax + Cx = 50 - 0.4W + 0.5W = 50 + 0.1W
                                            // Py = Ty - Ay + Cy = 50 - H + 0.5H = 50 - 0.5H
                                            
                                            let W = img.size.width
                                            let H = img.size.height
                                            let Px = 50 + (0.1 * W)
                                            let Py = 50 - (0.5 * H)
                                            
                                            Image(uiImage: img)
                                                .position(x: Px, y: Py)
                                        }
                                    }
                                    .frame(width: 100, height: 100)
                                    .background(Color.gray.opacity(0.1))
                                }
                            }
                        }
                    }
                    .padding()
                    .id(refreshID) // Force Redraw
                }
                .navigationTitle("Pin Gallery")
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: regeneratePins) {
                            HStack {
                                Image(systemName: "arrow.clockwise")
                                Text("Regenerate")
                            }
                        }
                    }
                }
        }
    }
    
    // MARK: - Logic
    
    func regeneratePins() {
        // 1. Clear Helper Cache
        PinImageHelper.shared.clearCache()
        
        // 2. Trigger UI Refresh
        withAnimation {
            refreshID = UUID()
        }
        
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
    }
    
    func generateBasePin(item: GalleryItem) -> UIImage? {
        return PinImageHelper.shared.fetchCompositePin(shieldName: item.shield, markName: item.mark)
    }
    
    func generateBadgePin(item: GalleryItem, count: Int) -> UIImage? {
        return PinImageHelper.shared.createShieldPin(
            shieldName: item.shield,
            markName: item.mark,
            color: item.color,
            count: count
        )
    }
    
    // Helper Data for Unique IDs
    struct PinTestItem: Identifiable {
        let id = UUID()
        let baseItem: GalleryItem
        let count: Int
    }
    
    var testItems: [PinTestItem] {
        var items: [PinTestItem] = []
        for item in baseItems {
            for count in testCounts {
                items.append(PinTestItem(baseItem: item, count: count))
            }
        }
        return items
    }
}

// Preview Provider
struct PinGalleryView_Previews: PreviewProvider {
    static var previews: some View {
        PinGalleryView()
    }
}

import SwiftUI

struct PinGalleryView: View {
    let columns = [
        GridItem(.adaptive(minimum: 100))
    ]
    
    // Test Data
    let baseNames = ["PinTodoReady", "PinTodoDone", "PinHistory", "PinReceiveReady", "PinCurrent"]
    let testCounts = [1, 5, 10, 99]
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 30) {
                    // Main Header
                    VStack(spacing: 8) {
                        Text("📌 핀 디자인 검증 갤러리")
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("iOS와 Android 간 핀 렌더링 일관성(크기, 뱃지 위치, 중심점)을 확인하는 도구입니다.")
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                    }
                    .padding(.top)
                    
                    // Section 1: Base Assets
                    VStack(alignment: .leading, spacing: 5) {
                        Text("1. 기본 에셋 (40x50)")
                            .font(.headline)
                            .padding(.horizontal)
                        Text("원본 이미지가 깨지지 않고 선명한지 확인하세요.")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .padding(.horizontal)
                        
                        LazyVGrid(columns: columns, spacing: 20) {
                            ForEach(baseNames, id: \.self) { name in
                                VStack {
                                    if let img = UIImage(named: name) {
                                        Image(uiImage: img)
                                            .resizable()
                                            .scaledToFit()
                                            .frame(width: 40, height: 50)
                                            .border(Color.blue.opacity(0.3)) // Border to see bounds
                                        Text(name).font(.caption).foregroundColor(.gray)
                                    } else {
                                        Text("Missing\n\(name)")
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
                        Text("✅ 정상: 붉은색 뱃지가 핀 우측 상단으로 튀어나와야 합니다.\n❌ 실패: 뱃지가 잘리거나 핀 안쪽에 갇혀있으면 안 됩니다.")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .padding(.horizontal)
                        
                        LazyVGrid(columns: columns, spacing: 30) {
                            ForEach(testItems) { item in
                                VStack {
                                    if let img = generatePin(name: item.name, count: item.count) {
                                        Image(uiImage: img)
                                            .border(Color.red.opacity(0.3)) // Canvas Border
                                        Text("+\(item.count)")
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
                        Text("🔴 빨간 점 = 지도 좌표\n✅ 정상: 핀의 뾰족한 끝이 빨간 점 정중앙에 닿아야 합니다.\n(Anchor Point 0.4, 1.0 검증)")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .padding(.horizontal)
                        
                        LazyVGrid(columns: columns, spacing: 40) {
                            ForEach(baseNames, id: \.self) { name in
                                ZStack(alignment: .topLeading) {
                                    // The Anchor Dot (Simulated Map Point)
                                    Circle()
                                        .fill(Color.red)
                                        .frame(width: 4, height: 4)
                                        .position(x: 50, y: 50) // Center of cell
                                        .zIndex(10)
                                    
                                    // The Pin
                                    if let img = generatePin(name: name, count: 5) { // Test with badge
                                        // Visual Logic:
                                        // Image Size: 50x60 (Canvas)
                                        // Anchor (0.4, 1.0) -> x=20, y=60 (relative to top-left)
                                        // We want (20, 60) of image to be at Center (50, 50) of Cell.
                                        
                                        // Image Center relative to its origin is (25, 30).
                                        // Anchor relative to image origin is (20, 60).
                                        // Vector from Center to Anchor = (20-25, 60-30) = (-5, 30).
                                        
                                        // If we place Image Center at Cell Center (50, 50), the Anchor is at (50-5, 50+30) = (45, 80).
                                        // We want Anchor at (50, 50).
                                        // So we must move Image such that Anchor moves from (45, 80) to (50, 50).
                                        // Delta = (+5, -30).
                                        // So new Image Center = (50+5, 50-30) = (55, 20).
                                        
                                        Image(uiImage: img)
                                            .position(x: 55, y: 20)
                                    }
                                }
                                .frame(width: 100, height: 100)
                                .background(Color.gray.opacity(0.1))
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Pin Gallery")
        }
    }
    
    // Helper Data for Unique IDs
    struct PinTestItem: Identifiable {
        let id = UUID()
        let name: String
        let count: Int
    }
    
    var testItems: [PinTestItem] {
        var items: [PinTestItem] = []
        for name in baseNames {
            for count in testCounts {
                items.append(PinTestItem(name: name, count: count))
            }
        }
        return items
    }
    
    func generatePin(name: String, count: Int) -> UIImage? {
        let color: UIColor
        if name == "PinHistory" { color = .red }
        else if name == "PinReceiveReady" { color = .blue }
        else { color = UIColor(red: 0.2, green: 0.8, blue: 0.2, alpha: 1.0) }
        
        return PinImageHelper.shared.createShieldPin(imageName: name, color: color, count: count)
    }
}

// Preview Provider
struct PinGalleryView_Previews: PreviewProvider {
    static var previews: some View {
        PinGalleryView()
    }
}

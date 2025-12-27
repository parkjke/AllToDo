import SwiftUI

/* 
 * 🚨 CRITICAL: DO NOT MODIFY THIS FILE 🚨
 * [수정 절대 금지] 사용자 요청에 의해 이 파일의 디자인 및 로직은 최종 확정되었습니다.
 * 특히 다음 사항은 절대 수정해서는 안 됩니다:
 * 1. 그림자(.shadow) 추가 금지
 * 2. 반투명 배경(.opacity, Material) 추가 금지
 * 3. 레이아웃 및 버튼 스타일 변경 금지
 * 4. .disabled(true) 속성 제거 금지
 * 이 규격은 'AllToDo 평면 디자인 표준'입니다.
 */

/// [사용자 요구사항] 경로 표시 색, 경로표시 굵기를 선택하는 독립적인 설정창
struct PathSettingsView: View {
    @Binding var selectedColor: Color
    @Binding var selectedWidth: CGFloat
    var isLightModeForced: Bool // 카카오/네이버 맵인 경우 true
    
    private let colors: [(name: String, color: Color)] = [
        ("R", .red), ("G", .allToDoGreen), ("B", .blue)
    ]
    private let widths: [CGFloat] = [4, 8, 16]
    
    // 시맨틱 컬러 정의
    private var backgroundColor: Color {
        isLightModeForced ? Color.white : Color(.systemBackground)
    }
    
    private var textColor: Color {
        isLightModeForced ? Color.gray : Color(.secondaryLabel)
    }
    
    private var strokeColor: Color {
        isLightModeForced ? Color.gray.opacity(0.2) : Color(.separator)
    }

    var body: some View {
        VStack(spacing: 16) {
            // 1. 색상 선택부 (Compact: Fixed Spacing)
            HStack(spacing: 16) {
                Text("경로 색상")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(textColor)
                    .frame(width: 80, alignment: .leading)
                
                HStack(spacing: 8) {
                    ForEach(colors, id: \.name) { item in
                        Button(action: { 
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                                selectedColor = item.color 
                            }
                        }) {
                            ZStack {
                                Color.clear // 터치 영역 확보용
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(item.color)
                                    .frame(width: 32, height: 32)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(selectedColor == item.color ? (isLightModeForced ? Color.black : Color.white) : Color.clear, lineWidth: 3)
                                            .frame(width: 38, height: 38)
                                    )
                            }
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            
            // 2. 굵기 선택부 (Compact: Fixed Spacing)
            HStack(spacing: 16) {
                Text("경로 두께")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(textColor)
                    .frame(width: 80, alignment: .leading)
                
                HStack(spacing: 8) {
                    ForEach(widths, id: \.self) { w in
                        Button(action: { 
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                                selectedWidth = w 
                            }
                        }) {
                            ZStack {
                                Color.clear // 터치 영역 확보용
                                
                                // 점 그리기
                                Circle()
                                    .fill(isLightModeForced ? Color.black : Color(.label))
                                    .frame(width: w/1.5, height: w/1.5)
                            }
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(selectedWidth == w ? selectedColor : Color.clear, lineWidth: 3)
                                    .frame(width: 38, height: 38)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(backgroundColor)
        .cornerRadius(20)
    }
}

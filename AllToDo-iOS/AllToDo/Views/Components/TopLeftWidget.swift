import SwiftUI

struct TopLeftWidget: View {
    var historyCount: Int
    var localTodoCount: Int
    var serverTodoCount: Int
    var compassRotation: Double // [NEW] 0 = North
    var onCompassClick: () -> Void = {} // [NEW]
    var onExpandClick: () -> Void
    
    var isNorth: Bool {
        abs(compassRotation.truncatingRemainder(dividingBy: 360)) < 1.0
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // 1. Checklist Icon & Text (Expand Click)
            Button(action: onExpandClick) {
                HStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .resizable()
                        .frame(width: 20, height: 20)
                        .foregroundColor(Color(white: 0.2))
                    
                    Text("할 일")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(white: 0.2))
                }
            }
            
            // 2. Compass (Reset Click)
            Button(action: onCompassClick) {
                ZStack {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 32, height: 32)
                    
                    Image(systemName: "safari.fill")
                        .resizable()
                        .frame(width: 20, height: 20)
                        .rotationEffect(.degrees(-compassRotation)) // [FIX] Match map rotation
                        .foregroundColor(isNorth ? .gray : .red)
                }
            }
            
            // 3. Badges (Expand Click area for convenience too)
            Button(action: onExpandClick) {
                HStack(spacing: 6) {
                    StatBadge(color: .allToDoRed, count: historyCount)
                    StatBadge(color: .allToDoGreen, count: localTodoCount)
                    StatBadge(color: .allToDoBlue, count: serverTodoCount)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.allToDoGreen.opacity(0.7))
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
    }
}

struct StatBadge: View {
    var color: Color
    var count: Int
    
    var body: some View {
        ZStack {
            Circle()
                .fill(color)
                .frame(width: 24, height: 24)
            
            Text("\(count)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
        }
    }
}

#Preview {
    ZStack {
        Color.white
        TopLeftWidget(historyCount: 5, localTodoCount: 3, serverTodoCount: 1, onExpandClick: {})
    }
}

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
                    StatBadge(color: .allToDoBlue, count: serverTodoCount)  // 1. Server (Blue)
                    StatBadge(color: .allToDoGreen, count: localTodoCount) // 2. Local (Green)
                    StatBadge(color: .allToDoRed, count: historyCount)    // 3. History (Red)
                }
            }
        }
        
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.white) // Use clear white for better contrast
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 3)
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
        TopLeftWidget(historyCount: 5, localTodoCount: 3, serverTodoCount: 1, compassRotation: 0, onCompassClick: {}, onExpandClick: {})
    }
}

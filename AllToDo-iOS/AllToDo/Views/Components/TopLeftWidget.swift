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
        Button(action: onExpandClick) {
            HStack(spacing: 12) {
                // 1. Checklist Icon & Text
                HStack(spacing: 6) {
                    Image(systemName: "checklist")
                        .resizable()
                        .frame(width: 20, height: 20)
                        .foregroundColor(Color(white: 0.2))
                    
                    Text("할 일")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(white: 0.2))
                }
                
                // 2. Badges (Blue, Green, Red)
                HStack(spacing: 4) {
                    StatBadge(color: .allToDoBlue, count: serverTodoCount)  // 1. Server (Blue)
                    StatBadge(color: .allToDoGreen, count: localTodoCount) // 2. Local (Green)
                    StatBadge(color: .allToDoRed, count: historyCount)    // 3. History (Red)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 48) // Match ControlIcon height
            .background(Color.allToDoGreen.opacity(0.8)) // Match Button background
            .cornerRadius(12) // Match ControlIcon corner radius
        }
        .buttonStyle(.plain)
    }
}

struct StatBadge: View {
    var color: Color
    var count: Int
    
    var body: some View {
        ZStack {
            Circle()
                .fill(color.opacity(0.8))
                .frame(width: 28, height: 28)
            
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

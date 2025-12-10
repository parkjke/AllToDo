import SwiftUI

struct TopLeftWidget: View {
    var historyCount: Int
    var localTodoCount: Int
    var serverTodoCount: Int
    var onExpandClick: () -> Void
    
    var body: some View {
        Button(action: onExpandClick) {
            HStack(spacing: 8) {
                Image(systemName: "checklist")
                    .resizable()
                    .frame(width: 20, height: 20)
                    .foregroundColor(Color(white: 0.2))
                
                Text("할 일")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(white: 0.2))
                
                // 1. Red Badge (History)
                StatBadge(color: .allToDoRed, count: historyCount)
                
                // 2. Green Badge (Local)
                StatBadge(color: .allToDoGreen, count: localTodoCount)
                
                // 3. Blue Badge (Server)
                StatBadge(color: .allToDoBlue, count: serverTodoCount)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.allToDoGreen.opacity(0.7))
            .cornerRadius(16)
        }
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

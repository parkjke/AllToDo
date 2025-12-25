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
            HStack(spacing: 8) { // Badge spacing 8pt
                // 1. Checklist Icon & Text
                HStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .resizable()
                        .frame(width: 22, height: 22)
                        .foregroundColor(Color(white: 0.2))
                    
                    Text("할 일")
                        .font(.system(size: 21, weight: .bold)) // 21pt bold
                        .foregroundColor(Color(white: 0.2))
                }
                .padding(.horizontal, 16) // 16pt both sides
                
                // 2. Badges (Blue, Green, Red)
                HStack(spacing: 8) { // 8pt spacing
                    StatBadge(color: .allToDoBlue, count: serverTodoCount)
                    StatBadge(color: .allToDoGreen, count: localTodoCount)
                    StatBadge(color: .allToDoRed, count: historyCount)
                }
                .padding(.trailing, 10) 
            }
            .frame(height: 56) // Increased height for larger elements
            .background(Color.allToDoGreen.opacity(0.8))
            .cornerRadius(14)
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
                .fill(color)

                .frame(width: 32, height: 32) // +2pt (30 -> 32)
            
            // Border Gradient: 5 o'clock (gray5) to 12 o'clock (white)
            Circle()
                .stroke(
                    AngularGradient(
                        gradient: Gradient(stops: [
                            .init(color: .white, location: 0.0),   // 12 o'clock
                            .init(color: .gray5, location: 0.416), // 5 o'clock (5/12)
                            .init(color: .white, location: 1.0)    // Wrapping back to 12
                        ]),
                        center: .center,
                        angle: .degrees(-90) // Start from 12 o'clock
                    ),
                    lineWidth: 1
                )
                .frame(width: 32, height: 32) // +2pt

            
            Text("\(count)")
                .font(.system(size: 14, weight: .bold)) // Enlarged text inside circle
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

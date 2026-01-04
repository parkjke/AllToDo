import SwiftUI

struct RippleEffectView: View {
    var isDark: Bool = false
    @State private var ripples: [Int] = [0, 1, 2]
    @State private var animate = false
    
    private var rippleColor: Color {
        Color.Search.ripple(isDark: isDark)
    }
    
    var body: some View {
        ZStack {
            ForEach(ripples, id: \.self) { index in
                Circle()
                    .stroke(rippleColor, lineWidth: 4) // Increased thickness
                    .frame(width: 20, height: 20)
                    .scaleEffect(animate ? 4.0 : 1.0)
                    .opacity(animate ? 0.0 : 0.6)
                    .animation(
                        Animation.easeOut(duration: 1.5)
                            .repeatCount(2, autoreverses: false) // "팅팅팅" - 2 repeats (total 3 beats)
                            .delay(Double(index) * 0.4),
                        value: animate
                    )
            }
        }
        .onAppear {
            animate = true
        }
    }
}

#Preview {
    ZStack {
        Color.black
        RippleEffectView()
    }
}

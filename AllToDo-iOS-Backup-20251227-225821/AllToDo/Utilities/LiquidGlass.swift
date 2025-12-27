import SwiftUI

struct LiquidGlass: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    // 1. Stronger Blur Material for better visibility against noisy maps
                    Rectangle().fill(.regularMaterial)
                    
                    // 2. White Tint for "Milky/Frosted Glass" look
                    Rectangle().fill(Color.white.opacity(0.2))
                    
                    // 3. Surface Shine (Top-Left to Bottom-Right)
                    LinearGradient(
                        colors: [.white.opacity(0.4), .clear],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(
                        LinearGradient(
                            colors: [
                                .white.opacity(0.9), // Bright Highlight
                                .white.opacity(0.2)  // Fades out
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5 // Thicker Edge
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

extension View {
    func liquidGlass() -> some View {
        self.modifier(LiquidGlass())
    }
}

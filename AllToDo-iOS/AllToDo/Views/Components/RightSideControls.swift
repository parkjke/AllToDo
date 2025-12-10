import SwiftUI

struct RightSideControls: View {
    var compassRotation: Double
    var showHistoryMode: Bool // [NEW]
    var onHistoryClick: () -> Void // [NEW]
    var onNotificationClick: () -> Void
    var onLoginClick: () -> Void
    var onLocationClick: () -> Void
    var onZoomInClick: () -> Void
    var onZoomOutClick: () -> Void
    var onCompassClick: () -> Void
    
    var body: some View {
        Column(horizontalAlignment: .trailing) {
            // Top Group: Notification & Login
            HStack(spacing: 16) {
                // [NEW] History Toggle
                ControlIcon(
                    iconName: showHistoryMode ? "calendar" : "clock.arrow.circlepath", 
                    onClick: onHistoryClick
                )
                
                ControlIcon(
                    iconName: "bell.fill",
                    onClick: onNotificationClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
                
                ControlIcon(
                    iconName: "person.fill",
                    onClick: onLoginClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
            }
            .padding(.bottom, 24)
            
            // Center Group: Location, Zoom, Compass
            VStack(spacing: 16) {
                ControlIcon(
                    iconName: "location.fill", // My Location
                    onClick: onLocationClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
                
                ControlIcon(
                    iconName: "plus",
                    onClick: onZoomInClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
                
                ControlIcon(
                    iconName: "minus",
                    onClick: onZoomOutClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
                
                // Compass: Show only when rotated (North is 0 or 360)
                let r = compassRotation.truncatingRemainder(dividingBy: 360)
                if abs(r) > 1.0 && abs(r) < 359.0 {
                    Button(action: onCompassClick) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.allToDoGreen.opacity(0.7))
                                .frame(width: 48, height: 48)
                            
                            // Custom Compass Needle (Red/White with Outline)
                            ZStack {
                                // Top Half (Red)
                                Path { path in
                                    path.move(to: CGPoint(x: 6, y: 0))   // Top Tip
                                    path.addLine(to: CGPoint(x: 12, y: 18)) // Right Middle
                                    path.addLine(to: CGPoint(x: 0, y: 18))  // Left Middle
                                    path.closeSubpath()
                                }
                                .fill(Color.allToDoRed)
                                
                                // Bottom Half (White)
                                Path { path in
                                    path.move(to: CGPoint(x: 0, y: 18))  // Left Middle
                                    path.addLine(to: CGPoint(x: 12, y: 18)) // Right Middle
                                    path.addLine(to: CGPoint(x: 6, y: 36))  // Bottom Tip
                                    path.closeSubpath()
                                }
                                .fill(Color.white)
                                
                                // Outline
                                Path { path in
                                    path.move(to: CGPoint(x: 6, y: 0))
                                    path.addLine(to: CGPoint(x: 12, y: 18))
                                    path.addLine(to: CGPoint(x: 6, y: 36))
                                    path.addLine(to: CGPoint(x: 0, y: 18))
                                    path.closeSubpath()
                                }
                                .stroke(Color(white: 0.2), lineWidth: 1)
                            }
                            .frame(width: 12, height: 36)
                            .rotationEffect(.degrees(-compassRotation)) // Counter-rotate to point North
                        }
                    }
                }
            }
        }
    }
}

// Helper View for reusable buttons
struct ControlIcon: View {
    var iconName: String
    var onClick: () -> Void
    var rotation: Double = 0
    
    var body: some View {
        Button(action: onClick) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.allToDoGreen.opacity(0.7))
                    .frame(width: 48, height: 48)
                
                Image(systemName: iconName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 24, height: 24)
                    .foregroundColor(Color(white: 0.2))
                    .rotationEffect(.degrees(rotation))
            }
        }
    }
}

// Temporary layout helper to match Android's Column(horizontalAlignment = End)
struct Column<Content: View>: View {
    var horizontalAlignment: HorizontalAlignment
    var content: () -> Content
    
    init(horizontalAlignment: HorizontalAlignment, @ViewBuilder content: @escaping () -> Content) {
        self.horizontalAlignment = horizontalAlignment
        self.content = content
    }
    
    var body: some View {
        VStack(alignment: horizontalAlignment, content: content)
    }
}

#Preview {
    ZStack {
        Color.gray
        RightSideControls(
            compassRotation: 45,
            showHistoryMode: false,
            onHistoryClick: {},
            onNotificationClick: {},
            onLoginClick: {},
            onLocationClick: {},
            onZoomInClick: {},
            onZoomOutClick: {},
            onCompassClick: {}
        )
    }
}

import SwiftUI

struct RightSideControls: View {
    var compassRotation: Double
    var showHistoryMode: Bool
    var onHistoryClick: () -> Void
    var onNotificationClick: () -> Void
    var onLoginClick: () -> Void
    var onLocationClick: () -> Void
    var onZoomInClick: () -> Void
    var onZoomOutClick: () -> Void
    var onCompassClick: () -> Void
    var onExpandClick: () -> Void
    
    // [MODIFIED] Path Visualization Toggle
    var showActivePath: Bool
    var onRecordClick: () -> Void // Repurposed as toggle action
    
    var body: some View {
        Column(horizontalAlignment: .trailing) {
            // Top Group: Notification & Login
            HStack(spacing: 16) {
                ControlIcon(
                    iconName: showHistoryMode ? "calendar" : "clock.arrow.circlepath", 
                    onClick: onHistoryClick
                )
                
                ControlIcon(
                    iconName: "person.fill",
                    onClick: onLoginClick
                )
                .disabled(showHistoryMode)
                .opacity(showHistoryMode ? 0.3 : 1.0)
            }
            .padding(.bottom, 24)
            
            // Center Group: Location, Zoom, Compass, Path Toggle
            VStack(spacing: 16) {
                ControlIcon(
                    iconName: "location.fill",
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
                
                // Compass
                let r = compassRotation.truncatingRemainder(dividingBy: 360)
                if abs(r) > 1.0 && abs(r) < 359.0 {
                    Button(action: onCompassClick) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.allToDoGreen)
                                .frame(width: 48, height: 48)
                            
                            ZStack {
                                Path { path in
                                    path.move(to: CGPoint(x: 6, y: 0))
                                    path.addLine(to: CGPoint(x: 12, y: 18))
                                    path.addLine(to: CGPoint(x: 0, y: 18))
                                    path.closeSubpath()
                                }
                                .fill(Color.allToDoRed)
                                
                                Path { path in
                                    path.move(to: CGPoint(x: 0, y: 18))
                                    path.addLine(to: CGPoint(x: 12, y: 18))
                                    path.addLine(to: CGPoint(x: 6, y: 36))
                                    path.closeSubpath()
                                }
                                .fill(Color.white)
                                
                                Path { path in
                                    path.move(to: CGPoint(x: 6, y: 0))
                                    path.addLine(to: CGPoint(x: 12, y: 18))
                                    path.addLine(to: CGPoint(x: 6, y: 36))
                                    path.addLine(to: CGPoint(x: 0, y: 18))
                                    path.closeSubpath()
                                }
                                .stroke(Color.gray8, lineWidth: 1)
                            }
                            .frame(width: 12, height: 36)
                            .rotationEffect(.degrees(-compassRotation))
                        }
                    }
                    .buttonStyle(.plain)
                }
                
                // [MODIFIED] Path Visualization Toggle (Debugging Tool)
                Button(action: onRecordClick) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(showActivePath ? Color.allToDoGreen : Color.white)
                            .opacity(0.8)
                            .frame(width: 48, height: 48)
                        
                        Image(systemName: "point.topleft.down.curvedto.point.bottomright.up")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(width: 24, height: 24)
                            .foregroundColor(.gray8)
                    }
                }
                .buttonStyle(.plain)
                .opacity(showActivePath ? 1.0 : 0.8)
            }
        }
    }
}

struct ControlIcon: View {
    var iconName: String
    var onClick: () -> Void
    var rotation: Double = 0
    var bgColor: Color = Color.allToDoGreen
    
    var body: some View {
        Button(action: onClick) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(bgColor.opacity(0.8))
                    .frame(width: 48, height: 48)
                
                Image(systemName: iconName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 24, height: 24)
                    .foregroundColor(.gray8)
                    .rotationEffect(.degrees(rotation))
            }
        }
        .buttonStyle(.plain)
    }
}

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

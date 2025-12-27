import SwiftUI

struct BottomBar: View {
    @Binding var showProfile: Bool
    @Binding var showTasks: Bool
    var taskCount: Int
    
    var body: some View {
        HStack {
            Button(action: {
                showTasks = true
            }) {
                VStack(spacing: 4) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "list.bullet.circle.fill")
                            .font(.system(size: 28))
                        
                        if taskCount > 0 {
                            Text("\(taskCount > 99 ? "99+" : "\(taskCount)")")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .padding(4)
                                .background(Color.red)
                                .clipShape(Circle())
                                .offset(x: 5, y: -5)
                        }
                    }
                    Text("Tasks")
                        .font(.caption2)
                        .fontWeight(.medium)
                }
            }
            .foregroundColor(.primary)
            
            Spacer()
            
            Button(action: {
                showProfile = true
            }) {
                VStack(spacing: 4) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 28))
                    Text("Profile")
                        .font(.caption2)
                        .fontWeight(.medium)
                }
            }
            .foregroundColor(.primary)
        }
        .padding(.horizontal, 40)
        .padding(.vertical, 16)
        .liquidGlass()
        .padding(.horizontal, 20)
        .padding(.bottom, 10)
    }
}

#Preview {
    ZStack {
        Color.blue // Background to see the effect
        VStack {
            Spacer()
            BottomBar(showProfile: .constant(false), showTasks: .constant(false), taskCount: 5)
        }
    }
}

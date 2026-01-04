import SwiftUI

struct CreateTodoLayer: View {
    @Environment(\.colorScheme) var colorScheme
    var title: String = ""
    var defaultName: String = "요기"
    var initialName: String = ""
    var existingItem: ToDoItem?
    
    var onRegister: (String, String, String, String, String) -> Void
    var onCancel: () -> Void
    
    @State private var todoName: String = ""
    @State private var person: String = ""
    @State private var date: String = ""
    @State private var time: String = ""
    @State private var memo: String = ""
    
    @State private var activeInputMode: InputMode = .none

    enum InputMode {
        case none, name, person, memo
    }
    
    private var isDark: Bool { colorScheme == .dark }

    var body: some View {
        ZStack {
            // Main Bottom Sheet
            VStack(spacing: 0) {
                Spacer()
                
                VStack(spacing: 16) {
                    // Header
                    HStack {
                        Text(existingItem != nil ? "할 일 상세" : title)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Color.TodoLayer.headerText(isDark: isDark))
                        
                        Spacer()
                        
                        HStack(spacing: 16) {
                            Button(action: {
                                let finalName = todoName.isEmpty ? defaultName : todoName
                                let now = Date()
                                let finalDate = date.isEmpty ? now.formatted(.dateTime.year().month(.twoDigits).day(.twoDigits)) : date
                                let finalTime = time.isEmpty ? now.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits)) : time
                                onRegister(finalName, person, finalDate, finalTime, memo)
                            }) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                            }
                            .buttonStyle(PlainButtonStyle()) // [FIX] Remove system ghost shadow
                            
                            Button(action: onCancel) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                            }
                            .buttonStyle(PlainButtonStyle()) // [FIX] Remove system ghost shadow
                        }
                    }
                    .padding(.bottom, 8)
                    
                    ScrollView {
                        VStack(spacing: 24) {
                            InputField(
                                label: "할 일 이름",
                                value: todoName.isEmpty ? "할 일 이름을 넣어주세요 (미입력 시 '\(defaultName)')" : todoName,
                                isPlaceholder: todoName.isEmpty,
                                isDark: isDark,
                                onClick: { activeInputMode = .name }
                            )
                            
                            InputField(
                                label: "같이 할 사람이 있나요",
                                value: person.isEmpty ? "연락처에서 선택" : person,
                                isPlaceholder: person.isEmpty,
                                isDark: isDark,
                                onClick: { activeInputMode = .person }
                            )
                            
                            HStack(spacing: 12) {
                                InputField(
                                    label: "날짜",
                                    value: date.isEmpty ? "날짜" : date,
                                    isPlaceholder: date.isEmpty,
                                    isDark: isDark,
                                    onClick: { /* Open Date Picker */ }
                                )
                                .frame(maxWidth: .infinity)
                                
                                InputField(
                                    label: "시간",
                                    value: time.isEmpty ? "시간" : time,
                                    isPlaceholder: time.isEmpty,
                                    isDark: isDark,
                                    onClick: { /* Open Time Picker */ }
                                )
                                .frame(maxWidth: .infinity)
                            }
                            
                            InputField(
                                label: "메모",
                                value: memo.isEmpty ? "기억을 위한 메모" : memo,
                                isPlaceholder: memo.isEmpty,
                                isDark: isDark,
                                onClick: { activeInputMode = .memo }
                            )
                        }
                        .padding(.top, 8)
                    }
                }
                .padding(24)
                .frame(maxWidth: .infinity)
                .frame(height: 550) 
                .background(Color.TodoLayer.background(isDark: isDark))
                .cornerRadius(24, corners: [.topLeft, .topRight])
            }
            .ignoresSafeArea(edges: .bottom)
            
            // Sub-views overlays
            if activeInputMode != .none {
                ZStack {
                    Color.TodoLayer.background(isDark: isDark).ignoresSafeArea()
                    
                    VStack(spacing: 24) {
                        HStack {
                            Button(action: { activeInputMode = .none }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                            }
                            .buttonStyle(PlainButtonStyle()) // [FIX] Remove system ghost shadow
                            
                            Spacer()
                            
                            Text(overlayTitle)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(Color.TodoLayer.headerText(isDark: isDark))
                            
                            Spacer()
                            
                            Button(action: { activeInputMode = .none }) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                            }
                            .buttonStyle(PlainButtonStyle()) // [FIX] Remove system ghost shadow
                        }
                        .padding(.top, 16)
                        
                        if activeInputMode == .name {
                            TextField("할 일 이름을 넣어주세요", text: $todoName)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .font(.system(size: 16))
                        } else if activeInputMode == .memo {
                            TextEditor(text: $memo)
                                .frame(height: 200)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.TodoLayer.labelText(isDark: isDark).opacity(0.2)))
                        } else if activeInputMode == .person {
                            Text("연락처 검색 UI (준비 중)")
                                .foregroundColor(Color.TodoLayer.placeholderText(isDark: isDark))
                        }
                        
                        Spacer()
                    }
                    .padding(24)
                }
                .transition(.move(edge: .bottom))
                .animation(.spring(), value: activeInputMode)
                .ignoresSafeArea(.keyboard) // [FIX] Ensure overlay sub-view also stays fixed
            }
        }
        .onAppear {
            if let item = existingItem {
                todoName = item.todo_name
                memo = item.memo
                if let dt = item.date_time {
                    date = dt.formatted(.dateTime.year().month(.twoDigits).day(.twoDigits))
                    time = dt.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
                }
            } else if !initialName.isEmpty {
                todoName = initialName
            }
        }
        .ignoresSafeArea(.keyboard) // [FIX] Prevent layout shifts when keyboard appears
    }
    
    private var overlayTitle: String {
        switch activeInputMode {
        case .name: return "할 일 이름"
        case .person: return "연락처 검색"
        case .memo: return "메모 입력"
        default: return ""
        }
    }
}

struct InputField: View {
    let label: String
    let value: String
    let isPlaceholder: Bool
    let isDark: Bool
    let onClick: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color.TodoLayer.labelText(isDark: isDark))
            
            Button(action: onClick) {
                Text(value)
                    .font(.system(size: 16))
                    .foregroundColor(isPlaceholder ? Color.TodoLayer.placeholderText(isDark: isDark) : Color.TodoLayer.primaryText(isDark: isDark))
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.TodoLayer.inputBackground(isDark: isDark))
                    .cornerRadius(8)
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
}

// Helper for rounded corners
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape( RoundedCorner(radius: radius, corners: corners) )
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}

import SwiftUI
import SwiftData

struct CreateTodoLayer: View {
    @Environment(\.colorScheme) var colorScheme
    var title: String = ""
    var defaultName: String = "요기"
    var initialName: String = ""
    var existingItem: ToDoItem?
    
    var onRegister: (String, [Contact], String, String, String) -> Void
    var onCancel: () -> Void
    
    @Environment(\.modelContext) private var modelContext
    
    @State private var todoName: String = ""
    @State private var person: String = ""
    @State private var date: String = ""
    @State private var time: String = ""
    @State private var memo: String = ""
    
    @State private var activeInputMode: InputMode = .none
    @FocusState private var focusedField: InputMode?

    @State private var selectedContacts: [Contact] = []

    enum InputMode: Hashable {
        case none, name, person, memo
    }
    
    private var isDark: Bool { colorScheme == .dark }

    var body: some View {
        ZStack {
            mainBottomSheet
            if activeInputMode != .none {
                overlaySection
            }
        }
        .interactiveDismissDisabled(activeInputMode != .none)
    }

    private var mainBottomSheet: some View {
        VStack(spacing: 0) {
            Spacer()
            VStack(spacing: 16) {
                headerView
                formView
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .frame(height: 550)
            .background(Color.TodoLayer.background(isDark: isDark))
            .cornerRadius(24, corners: [.topLeft, .topRight])
        }
        .ignoresSafeArea(edges: .bottom)
        .onAppear {
            initializeForm()
        }
        .onChange(of: activeInputMode) { _, newValue in
            focusedField = newValue != .none ? newValue : nil
        }
        .onChange(of: selectedContacts) { _, newValue in
            updatePersonString(newValue)
        }
        .ignoresSafeArea(.keyboard)
    }

    private var headerView: some View {
        HStack {
            Text(existingItem != nil ? "할 일 상세" : title)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(Color.TodoLayer.headerText(isDark: isDark))
            
            Spacer()
            
            HStack(spacing: 16) {
                Button(action: handleRegister) {
                    Image(systemName: "checkmark")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                }
                .buttonStyle(PlainButtonStyle())
                
                Button(action: onCancel) {
                    Image(systemName: "xmark")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.bottom, 8)
    }

    private var formView: some View {
        ScrollView {
            VStack(spacing: 20) {
                InputField(
                    label: "",
                    value: todoName.isEmpty ? "할 일에 이름을 지어주세요" : todoName,
                    isPlaceholder: todoName.isEmpty,
                    isDark: isDark,
                    onClick: { activeInputMode = .name }
                )
                
                InputField(
                    label: "",
                    value: person.isEmpty ? "알릴 사람을 연락처에서 찾아보세요." : person,
                    isPlaceholder: person.isEmpty,
                    isDark: isDark,
                    onClick: { activeInputMode = .person }
                )
                
                HStack(spacing: 12) {
                    InputField(
                        label: "",
                        value: date.isEmpty ? "날짜" : date,
                        isPlaceholder: date.isEmpty,
                        isDark: isDark,
                        onClick: { /* Open Date Picker */ }
                    )
                    .frame(maxWidth: .infinity)
                    
                    InputField(
                        label: "",
                        value: time.isEmpty ? "시간" : time,
                        isPlaceholder: time.isEmpty,
                        isDark: isDark,
                        onClick: { /* Open Time Picker */ }
                    )
                    .frame(maxWidth: .infinity)
                }
                
                InputField(
                    label: "",
                    value: memo.isEmpty ? "기억을 위한 메모" : memo,
                    isPlaceholder: memo.isEmpty,
                    isDark: isDark,
                    onClick: { activeInputMode = .memo }
                )
                .frame(minHeight: 180, alignment: .top)
            }
            .padding(.top, 8)
        }
    }

    private var overlaySection: some View {
        ZStack {
            Color.TodoLayer.background(isDark: isDark).ignoresSafeArea()
            
            VStack(spacing: 24) {
                overlayHeader
                overlayContent
                
                Spacer()
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)
            .padding(.bottom, 24)
        }
        .transition(.move(edge: .bottom))
        .animation(.spring(), value: activeInputMode)
        .ignoresSafeArea(.keyboard)
        .highPriorityGesture(
            DragGesture()
                .onEnded { value in
                    if value.translation.height > 100 {
                        activeInputMode = .none
                    }
                }
        )
    }

    private var overlayHeader: some View {
        HStack {
            Button(action: { activeInputMode = .none }) {
                Image(systemName: "xmark")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color.TodoLayer.primaryText(isDark: isDark))
            }
            .buttonStyle(PlainButtonStyle())
            
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
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.top, 16)
    }

    @ViewBuilder
    private var overlayContent: some View {
        if activeInputMode == .name {
            TextField("할 일에 이름을 지어주세요", text: $todoName)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .font(.system(size: 19))
                .focused($focusedField, equals: .name)
                .submitLabel(.done)
        } else if activeInputMode == .memo {
            TextEditor(text: $memo)
                .frame(height: 200)
                .focused($focusedField, equals: .memo)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.TodoLayer.labelText(isDark: isDark).opacity(0.2)))
        } else if activeInputMode == .person {
            ContactSearchView(
                selectedContacts: $selectedContacts,
                isFocused: Binding(
                    get: { focusedField == .person },
                    set: { if $0 { focusedField = .person } else if focusedField == .person { focusedField = nil } }
                )
            )
        }
    }

    private func handleRegister() {
        let finalName = todoName.isEmpty ? defaultName : todoName
        let now = Date()
        let finalDate = date.isEmpty ? now.formatted(.dateTime.year().month(.twoDigits).day(.twoDigits)) : date
        let finalTime = time.isEmpty ? now.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits)) : time
        onRegister(finalName, selectedContacts, finalDate, finalTime, memo)
    }

    private func initializeForm() {
        if let item = existingItem {
            todoName = item.todo_name
            memo = item.memo
            if let dt = item.date_time {
                date = dt.formatted(.dateTime.year().month(.twoDigits).day(.twoDigits))
                time = dt.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
            }
            
            let todoId = item.todo_id
            let descriptor = FetchDescriptor<TodoContact>(predicate: #Predicate { $0.todo_id == todoId })
            if let bridges = try? modelContext.fetch(descriptor) {
                let contactIds = bridges.map { $0.contact_id }
                let allContactsDesc = FetchDescriptor<Contact>(predicate: #Predicate { contact in 
                    contactIds.contains(contact.id)
                })
                if let loaded = try? modelContext.fetch(allContactsDesc) {
                    selectedContacts = loaded
                }
            }
        } else if !initialName.isEmpty {
            todoName = initialName
        }
    }

    private func updatePersonString(_ newValue: [Contact]) {
        if newValue.isEmpty {
            person = ""
        } else {
            let names = newValue.map { $0.name }
            if names.count > 2 {
                person = "\(names[0]), \(names[1]) 외 \(names.count - 2)명"
            } else {
                person = names.joined(separator: ", ")
            }
        }
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
        Button(action: onClick) {
            Text(value)
                .font(.system(size: 19)) // [FIX] Increased from 16 for better visibility
                .foregroundColor(isPlaceholder ? Color.TodoLayer.placeholderText(isDark: isDark) : Color.TodoLayer.primaryText(isDark: isDark))
                .padding(12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(Color.TodoLayer.inputBackground(isDark: isDark))
                .cornerRadius(8)
        }
        .buttonStyle(PlainButtonStyle())
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

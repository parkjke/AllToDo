import SwiftUI
import SwiftData

struct TodoListLayer: View {
    @ObservedObject var viewModel: MapFeatureViewModel
    @Query private var allItems: [ToDoItem] // 실데이터 직접 구독
    
    @Environment(\.colorScheme) var colorScheme
    var isDark: Bool { colorScheme == .dark }
    
    // 필터 상태 (Green, Blue, Red)
    @State private var isServerFilter: Bool = true
    @State private var isTodoFilter: Bool = true
    @State private var isHistoryFilter: Bool = true
    @State private var sortByTime: Bool = true
    
    var filteredItems: [ToDoItem] {
        allItems.filter { item in
            let typeMatch = (item.type == "10" && isTodoFilter) || (item.type == "00" && isHistoryFilter)
            return typeMatch
        }.sorted { 
            if sortByTime {
                let d1 = $0.begin_time ?? $0.date_time ?? Date(timeIntervalSince1970: Double($0.created_at)/1000.0)
                let d2 = $1.begin_time ?? $1.date_time ?? Date(timeIntervalSince1970: Double($1.created_at)/1000.0)
                return d1 > d2
            } else {
                // 색상 정렬 (Blue-Green-Red 순 - 안드로이드 규준)
                return $0.type > $1.type 
            }
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
                // 1. Header Row (안드로이드 TodoListLayer.kt 57행 규격)
                HStack(spacing: 0) {
                    HStack(spacing: 4) {
                        // 추가 버튼
                        Button(action: { withAnimation { viewModel.isCreatingTodo = true } }) {
                            Image(systemName: "plus")
                                .resizable()
                                .frame(width: 24, height: 24)
                                .padding(6)
                                .foregroundColor(Color.TodoList.iconTint(isDark: isDark))
                        }
                        .frame(width: 44, height: 44)
                        .buttonStyle(.plain)
                        
                        // 정렬 버튼
                        Button(action: { sortByTime.toggle() }) {
                            Image(systemName: sortByTime ? "clock" : "paintpalette")
                                .resizable()
                                .frame(width: 24, height: 24)
                                .padding(6)
                                .foregroundColor(Color.TodoList.iconTint(isDark: isDark))
                        }
                        .frame(width: 44, height: 44)
                        .buttonStyle(.plain)
                        
                        Spacer().frame(width: 12)
                        
                        // 필터 버튼들 (안드로이드 FilterIconButton.kt 규격)
                        HStack(spacing: 8) {
                            FilterBox(color: .allToDoBlue, isOn: $isServerFilter)
                            FilterBox(color: .allToDoGreen, isOn: $isTodoFilter)
                            FilterBox(color: .allToDoRed, isOn: $isHistoryFilter)
                        }
                    }
                    
                    Spacer()
                    
                    HStack(spacing: 4) {
                        // 캘린더 아이콘 (안드로이드 Dynamic Calendar Icon 규격)
                        Button(action: { withAnimation { viewModel.mainSheetTab = 1 } }) {
                            ZStack {
                                Image(systemName: "calendar")
                                    .resizable()
                                    .frame(width: 24, height: 24)
                                
                                Text("\(Calendar.current.component(.day, from: Date()))")
                                    .font(.system(size: 10, weight: .bold))
                                    .padding(.top, 4)
                            }
                            .foregroundColor(Color.TodoList.iconTint(isDark: isDark))
                        }
                        .frame(width: 44, height: 44)
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 16)
                
                // 2. List Content
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(filteredItems) { item in
                            TodoItemCard(
                                item: item,
                                isDark: isDark,
                                onPathClick: {
                                    viewModel.shouldRestoreList = true
                                    viewModel.viewingHistoryItem = item
                                    viewModel.showTodoList = false
                                },
                                onEditClick: {
                                    viewModel.shouldRestoreList = true
                                    viewModel.selectedItem = item
                                    viewModel.showTodoList = false
                                },
                                onDeleteClick: {
                                    // ContentView의 deleteItem 호출을 위해 viewModel에 요청하거나 직접 삭제
                                    // 여기서는 일단 구조만 잡음
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 4)
                    .padding(.bottom, 80)
                }
            }
        }
    }
}

// MARK: - Filter Box (Rounded Rectangle)
struct FilterBox: View {
    let color: Color
    @Binding var isOn: Bool
    
    var body: some View {
        Button(action: { isOn.toggle() }) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(isOn ? color : color.opacity(0.2))
                    .frame(width: 36, height: 36)
                
                if isOn {
                    Image(systemName: "checkmark")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - TodoItemCard (Android Spec Restoration)
struct TodoItemCard: View {
    let item: ToDoItem
    let isDark: Bool
    let onPathClick: () -> Void
    let onEditClick: () -> Void
    let onDeleteClick: () -> Void
    
    var body: some View {
        let typeColor = item.type == "00" ? Color.allToDoRed : (item.source != "local" ? Color.allToDoBlue : Color.allToDoGreen)
        let backgroundColor = Color.TodoList.itemBackground(color: typeColor, isDark: isDark)
        let textColor = Color.TodoList.primaryText(isDark: isDark)
        
        HStack(spacing: 0) {
            // 1. Map Icon [지] - [SPEC] 30pt Icon, 42pt Button
            if item.no_of_path > 0 {
                Button(action: onPathClick) {
                    Image(systemName: "map.fill")
                        .resizable()
                        .frame(width: 30, height: 30) // [FIX] Enlarge to 30pt
                        .foregroundColor(item.no_of_path > 1 ? typeColor : .gray.opacity(0.5))
                }
                .frame(width: 42, height: 42)
                .buttonStyle(.plain) // [FIX] 박멸
                .disabled(item.no_of_path <= 1)
            } else {
                Spacer().frame(width: 42)
            }
            
            Spacer().frame(width: 4)
            
            // 2 & 3. Date & Time - [SPEC] 15pt (+2pt)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    Text(dateString)
                        .font(.system(size: 15))
                    Text(timeString)
                        .font(.system(size: 15, weight: .bold))
                }
                .foregroundColor(textColor)
            }
            .frame(width: 95, alignment: .leading)
            
            Spacer().frame(width: 8)
            
            // 4. Name - [SPEC] 17pt Medium (+2pt)
            Button(action: onEditClick) {
                Text(item.todo_name)
                    .font(.system(size: 17, weight: .medium))
                    .lineLimit(1)
                    .foregroundColor(textColor)
            }
            .buttonStyle(.plain) // [FIX] 박멸
            
            Spacer()
            
            // 5. Person Icon - [NEW] 임시 "99" 표시
            HStack(spacing: 2) {
                Image(systemName: "person.fill")
                    .resizable()
                    .frame(width: 14, height: 14)
                Text("99")
                    .font(.system(size: 13, weight: .bold))
            }
            .foregroundColor(textColor.opacity(0.8))
            .padding(.trailing, 8)
            
            // 6. Delete Icon [휴] - [SPEC] 30pt Icon, 42pt Button
            Button(action: onDeleteClick) {
                Image(systemName: "trash.fill")
                    .resizable()
                    .frame(width: 30, height: 30) // [FIX] Enlarge to 30pt
                    .foregroundColor(.allToDoRed)
            }
            .frame(width: 42, height: 42)
            .buttonStyle(.plain) // [FIX] 박멸
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(backgroundColor)
        .cornerRadius(8)
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }
    
    var dateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "M/d"
        let date = item.begin_time ?? item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
        return formatter.string(from: date)
    }
    
    var timeString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        let date = item.begin_time ?? item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
        return formatter.string(from: date)
    }
}

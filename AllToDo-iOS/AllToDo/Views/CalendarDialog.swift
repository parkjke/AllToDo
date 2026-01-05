import SwiftUI
import SwiftData

struct CalendarDialog: View {
    @ObservedObject var viewModel: MapFeatureViewModel
    @Query private var allItems: [ToDoItem] // 실데이터 직접 구독
    
    @Environment(\.colorScheme) var colorScheme
    var isDark: Bool { colorScheme == .dark }
    
    // 캘린더 상태 (ViewModel과 연동 가능하지만 다이얼로그 내부용으로 관리)
    @State private var currentMonth: Date = Date()
    @State private var selectedDate: Date = Date()
    @State private var isLoading: Bool = true
    
    // 필터 상태 (Green, Blue, Red)
    @State private var isServerFilter: Bool = true
    @State private var isTodoFilter: Bool = true
    @State private var isHistoryFilter: Bool = true
    
    private let calendar = Calendar.current
    
    @State private var dragOffset: CGFloat = 0
    
    var body: some View {
        ZStack {
            // 배경: 불투명 솔리드 배경 [SPEC]
            Color.Calendar.background(isDark: isDark)
                .ignoresSafeArea()
                .offset(y: dragOffset > 0 ? dragOffset : 0)
            
            VStack(spacing: 0) {
                // Header (연/월 & 닫기)
                CalendarHeader(
                    currentMonth: currentMonth,
                    onMonthChange: { year, month in 
                        if year != 0 { moveYear(by: year) }
                        if month != 0 { moveMonth(by: month) }
                    },
                    onClose: { withAnimation { viewModel.showCalendar = false } },
                    isDark: isDark
                )
                
                // Content (Grid + Summary)
                VStack(spacing: 0) {
                    // Grid Area
                    CalendarGrid(
                        currentMonth: currentMonth,
                        selectedDate: $selectedDate,
                        allItems: allItems,
                        isDark: isDark
                    )
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    
                    Divider()
                        .background(Color.Calendar.divider(isDark: isDark))
                        .padding(.top, 16)
                    
                    // Summary Area (Bottom List)
                    TodoSummaryArea(
                        viewModel: viewModel,
                        allItems: allItems,
                        selectedDate: selectedDate,
                        isServerFilter: $isServerFilter,
                        isTodoFilter: $isTodoFilter,
                        isHistoryFilter: $isHistoryFilter,
                        isDark: isDark
                    )
                }
            }
            .padding(.top, 40) // Status bar 배려
            .offset(y: dragOffset > 0 ? dragOffset : 0)
        }
        .gesture(
            DragGesture()
                .onChanged { value in
                    if value.translation.height > 0 {
                        dragOffset = value.translation.height
                    }
                }
                .onEnded { value in
                    if value.translation.height > 100 {
                        withAnimation {
                            viewModel.showCalendar = false
                            dragOffset = 0
                        }
                    } else {
                        withAnimation {
                            dragOffset = 0
                        }
                    }
                }
        )
        .onAppear {
            selectedDate = viewModel.selectedDate
            currentMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: selectedDate)) ?? Date()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                isLoading = false
            }
        }
    }
    
    private func moveMonth(by value: Int) {
        if let next = calendar.date(byAdding: .month, value: value, to: currentMonth) {
            currentMonth = next
        }
    }
    
    private func moveYear(by value: Int) {
        if let next = calendar.date(byAdding: .year, value: value, to: currentMonth) {
            currentMonth = next
        }
    }
}

// MARK: - SubViews

struct CalendarHeader: View {
    let currentMonth: Date
    let onMonthChange: (Int, Int) -> Void // (YearOffset, MonthOffset)
    let onClose: () -> Void
    let isDark: Bool
    
    private let calendar = Calendar.current
    
    var yearStr: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy"
        return formatter.string(from: currentMonth)
    }
    
    var monthStr: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "M월"
        return formatter.string(from: currentMonth)
    }
    
    var body: some View {
        HStack(spacing: 0) {
            // [LEFT] Year & Month Nav
            HStack(spacing: 0) {
                // 1. Year Nav - [SPEC] 16pt Medium
                HStack(spacing: 0) {
                    navButton(icon: "chevron.left", action: { onMonthChange(-1, 0) }, size: 32, iconSize: 20)
                    Text(yearStr)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
                        .frame(minWidth: 45)
                    navButton(icon: "chevron.right", action: { onMonthChange(1, 0) }, size: 32, iconSize: 20)
                }
                
                Spacer().frame(width: 16)
                
                // 2. Month Nav - [SPEC] 28pt Bold, Large Buttons
                HStack(spacing: 0) {
                    navButton(icon: "chevron.left", action: { onMonthChange(0, -1) }, size: 48, iconSize: 32)
                    Text(monthStr)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
                        .frame(minWidth: 70)
                    navButton(icon: "chevron.right", action: { onMonthChange(0, 1) }, size: 48, iconSize: 32)
                }
            }
            
            Spacer()
            
            // [RIGHT] Close Button - [SPEC] 32pt Icon
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .resizable()
                    .frame(width: 24, height: 24)
                    .padding(8)
                    .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
            }
            .frame(width: 44, height: 44)
            .buttonStyle(.plain) // [FIX] No Shadow
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 8)
    }
    
    private func navButton(icon: String, action: @escaping () -> Void, size: CGFloat, iconSize: CGFloat) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: iconSize, height: iconSize)
                .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
        }
        .frame(width: size, height: size)
        .buttonStyle(.plain) // [FIX] No Shadow
    }
}

struct CalendarGrid: View {
    let currentMonth: Date
    @Binding var selectedDate: Date
    let allItems: [ToDoItem]
    let isDark: Bool
    
    private let calendar = Calendar.current
    private let weekdays = ["일", "월", "화", "수", "목", "금", "토"]
    
    var body: some View {
        VStack(spacing: 8) {
            // 요일 헤더
            HStack(spacing: 0) {
                ForEach(0..<7) { i in
                    Text(weekdays[i])
                        .font(.system(size: 15, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .foregroundColor(i == 0 ? .allToDoRed : (i == 6 ? .allToDoBlue : Color.Calendar.primaryText(isDark: isDark)))
                }
            }
            
            // 일자 그리드
            let days = daysInMonth()
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 0) {
                ForEach(days, id: \.self) { date in
                    if let date = date {
                        DayCell(
                            date: date,
                            stats: getStats(for: date),
                            isToday: calendar.isDateInToday(date),
                            isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                            isDark: isDark,
                            onClick: { selectedDate = date }
                        )
                    } else {
                        Color.clear.aspectRatio(0.7, contentMode: .fill)
                    }
                }
            }
        }
    }
    
    private func daysInMonth() -> [Date?] {
        guard let monthInterval = calendar.dateInterval(of: .month, for: currentMonth),
              let firstDay = calendar.date(from: calendar.dateComponents([.year, .month], from: monthInterval.start)) else { return [] }
        
        let weekdayOfFirst = calendar.component(.weekday, from: firstDay) - 1
        let numberOfDays = calendar.range(of: .day, in: .month, for: currentMonth)!.count
        
        var days: [Date?] = Array(repeating: nil, count: weekdayOfFirst)
        for day in 0..<numberOfDays {
            if let date = calendar.date(byAdding: .day, value: day, to: firstDay) {
                days.append(date)
            }
        }
        return days
    }
    
    private func getStats(for date: Date) -> (Int, Int, Int) {
        let dayItems = allItems.filter { item in
            let itemDate = item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
            return calendar.isDate(itemDate, inSameDayAs: date)
        }
        
        // Android 규준: Blue(Server), Green(Local), Red(History)
        let blue = 0 // iOS 현재 서버 필드 부재
        let green = dayItems.filter { $0.type == "10" }.count
        let red = dayItems.filter { $0.type == "00" }.count
        
        return (blue, green, red)
    }
}

struct DayCell: View {
    let date: Date
    let stats: (Int, Int, Int)
    let isToday: Bool
    let isSelected: Bool
    let isDark: Bool
    let onClick: () -> Void
    
    private let calendar = Calendar.current
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(calendar.component(.day, from: date))")
                    .font(.system(size: 14, weight: (isSelected || isToday) ? .bold : .regular)) // [SPEC] Conditional Bold
                    .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                VStack(alignment: .leading, spacing: 2) {
                    if stats.0 > 0 { PinInfo(color: .allToDoBlue, count: stats.0, isDark: isDark) }
                    if stats.1 > 0 { PinInfo(color: .allToDoGreen, count: stats.1, isDark: isDark) }
                    if stats.2 > 0 { PinInfo(color: .allToDoRed, count: stats.2, isDark: isDark) }
                    
                    // 정렬을 위한 스페이서
                    let count = (stats.0 > 0 ? 1 : 0) + (stats.1 > 0 ? 1 : 0) + (stats.2 > 0 ? 1 : 0)
                    ForEach(0..<(3-count), id: \.self) { _ in
                        Spacer().frame(height: 12)
                    }
                }
            }
            .padding(6)
            .aspectRatio(0.7, contentMode: .fill)
            .background(isSelected ? Color.Calendar.selectedBackground(isDark: isDark) : Color.clear)
            .border(isToday ? Color.Calendar.todayBorder(isDark: isDark) : Color.clear, width: 2)
        }
        .buttonStyle(.plain) // [FIX] Shadow free
    }
}

struct PinInfo: View {
    let color: Color
    let count: Int
    let isDark: Bool
    
    var body: some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 1.5)
                .fill(color)
                .frame(width: 9, height: 9)
            
            Text("\(count)")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(Color.Calendar.primaryText(isDark: isDark).opacity(0.9))
        }
    }
}

struct TodoSummaryArea: View {
    @ObservedObject var viewModel: MapFeatureViewModel
    let allItems: [ToDoItem]
    let selectedDate: Date
    @Binding var isServerFilter: Bool
    @Binding var isTodoFilter: Bool
    @Binding var isHistoryFilter: Bool
    let isDark: Bool
    
    private let calendar = Calendar.current
    
    var filteredItems: [ToDoItem] {
        allItems.filter { item in
            let itemDate = item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
            let dateMatch = calendar.isDate(itemDate, inSameDayAs: selectedDate)
            let typeMatch = (item.type == "10" && isTodoFilter) || (item.type == "00" && isHistoryFilter)
            return dateMatch && typeMatch
        }.sorted { 
            let d1 = $0.date_time ?? Date(timeIntervalSince1970: Double($0.created_at)/1000.0)
            let d2 = $1.date_time ?? Date(timeIntervalSince1970: Double($1.created_at)/1000.0)
            return d1 > d2
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Summary Header & Filter
            HStack {
                HStack(spacing: 4) {
                    Text("\(dateString) 할 일") // [SPEC] Match Android "M월 d일 할 일"
                        .font(.system(size: 18, weight: .bold)) // [SPEC] 18pt
                }
                .foregroundColor(Color.Calendar.primaryText(isDark: isDark))
                
                Spacer()
                
                // Filter Buttons (파/녹/빨)
                HStack(spacing: 8) {
                    FilterButton(color: .allToDoBlue, isOn: $isServerFilter)
                    FilterButton(color: .allToDoGreen, isOn: $isTodoFilter)
                    FilterButton(color: .allToDoRed, isOn: $isHistoryFilter)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
            
            // List Area
            ScrollView {
                VStack(spacing: 8) {
                    if filteredItems.isEmpty {
                        Text("기록이 없습니다.")
                            .font(.system(size: 14))
                            .foregroundColor(Color.Calendar.secondaryText(isDark: isDark))
                            .padding(.top, 40)
                    } else {
                        ForEach(filteredItems) { item in
                            // iOS 전용 TodoItemCard (Simplified for Calendar)
                            CalendarTodoItemCard(
                                item: item,
                                isDark: isDark,
                                onPathClick: {
                                    viewModel.shouldRestoreCalendar = true
                                    viewModel.viewingHistoryItem = item
                                    viewModel.showCalendar = false
                                },
                                onEditClick: {
                                    viewModel.shouldRestoreCalendar = true
                                    viewModel.selectedItem = item
                                    viewModel.showCalendar = false
                                },
                                onDeleteClick: {
                                    // TodoListLayer와 동일하게 뷰모델의 삭제 로직 연결 대기 (현재는 프레임워크만)
                                }
                            )
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            .frame(maxHeight: 250)
        }
    }
    
    private var dateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "M월 d일"
        return formatter.string(from: selectedDate)
    }
    
    private var dayOfWeek: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "EEEE"
        return formatter.string(from: selectedDate)
    }
}

struct FilterButton: View {
    let color: Color
    @Binding var isOn: Bool
    
    var body: some View {
        Button(action: { isOn.toggle() }) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(isOn ? color : color.opacity(0.2))
                    .frame(width: 36, height: 36) // [FIX] Enlarge to 36pt (Match TodoList)
                
                if isOn {
                    Image(systemName: "checkmark")
                        .font(.system(size: 16, weight: .bold)) // [FIX] Enlarge
                        .foregroundColor(.white)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

struct CalendarTodoItemCard: View {
    let item: ToDoItem
    let isDark: Bool
    let onPathClick: () -> Void
    let onEditClick: () -> Void
    let onDeleteClick: () -> Void
    
    var body: some View {
        let typeColor = item.type == "00" ? Color.allToDoRed : (item.source != "local" ? Color.allToDoBlue : Color.allToDoGreen)
        let backgroundColor = isDark ? typeColor.opacity(0.2) : typeColor.opacity(0.1)
        let textColor = isDark ? Color.white : Color.black
        
        HStack(spacing: 0) {
            // 1. Map Icon [지] - [SPEC] 30pt Icon, 42pt Button
            if item.no_of_path > 0 {
                Button(action: onPathClick) {
                    Image(systemName: "map.fill")
                        .resizable()
                        .frame(width: 30, height: 30)
                        .foregroundColor(item.no_of_path > 1 ? typeColor : .gray.opacity(0.5))
                }
                .frame(width: 42, height: 42)
                .buttonStyle(.plain)
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
            .buttonStyle(.plain)
            
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
                    .frame(width: 30, height: 30)
                    .foregroundColor(.allToDoRed)
            }
            .frame(width: 42, height: 42)
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(backgroundColor)
        .cornerRadius(8)
    }
    
    var dateString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "M/d"
        let date = item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
        return formatter.string(from: date)
    }
    
    var timeString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        let date = item.date_time ?? Date(timeIntervalSince1970: Double(item.created_at)/1000.0)
        return formatter.string(from: date)
    }
}

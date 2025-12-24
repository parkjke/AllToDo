import SwiftUI
import CoreLocation
import Combine
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var allItems: [ToDoItem]
    
    @State private var showProfile = false
    @State private var showTasks = false
    
    // Map Control State
    @State private var mapAction: MapAction = .none
    @State private var compassRotation: Double = 0.0
    
    @StateObject private var locationManager = AppLocationManager()
    
    @State private var showLocationHistory = false
    
    @State private var selectedItem: ToDoItem?
    @State private var selectedClusterItems: [UnifiedMapItem]?
    @State private var viewingHistoryItem: ToDoItem?
    @State private var showHistoryMode = false
    @State private var selectedDate = Date()
    @State private var showCalendar = false
    @State private var showListView = false
    @State private var backgroundStartTime: Date?
    @State private var tapPosition: CGPoint?
    
    // Far Item / Cluster State
    @State private var farItemMessage: String?
    @State private var farMessageTask: Task<Void, Never>?
    @State private var hasShownFarItemToast = false
    @State private var farItemsCount = 0
    @State private var showFarNotification = false
    @State private var clusterRadius: Double? = 100.0

    // Create Todo State
    @State private var isCreatingTodo = false
    @State private var creatingTodoLocation: CLLocationCoordinate2D?
    @State private var initialTodoName = ""
    @State private var initialTodoTitle = "할 일 만들기"

    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    @AppStorage("maxPopupItems") private var maxPopupItems = 5
    @AppStorage("popupFontSize") private var popupFontSize = 1

    // MARK: - Filtered Data
    var filteredTodos: [ToDoItem] {
        let centerDate = showHistoryMode ? selectedDate : Date()
        let min = Calendar.current.date(byAdding: .hour, value: -24, to: centerDate)!
        let max = Calendar.current.date(byAdding: .hour, value: 24, to: centerDate)!
        
        return allItems.filter {
            $0.type == "10" && (
                $0.date_time == nil || ($0.date_time! >= min && $0.date_time! <= max)
            )
        }
    }
    
    var filteredLogs: [ToDoItem] {
        let centerDate = showHistoryMode ? selectedDate : Date()
        let min = Calendar.current.date(byAdding: .hour, value: -24, to: centerDate)!
        let max = Calendar.current.date(byAdding: .hour, value: 24, to: centerDate)!
        
        return allItems.filter {
            $0.type == "00" && (
                ($0.begin_time ?? Date(timeIntervalSince1970: Double($0.created_at)/1000.0)) >= min &&
                ($0.begin_time ?? Date(timeIntervalSince1970: Double($0.created_at)/1000.0)) <= max
            )
        }
    }

    // MARK: - Body
    var body: some View {
        ZStack {
            // Map Layer
            Group {
                switch mapProvider {
                case .apple:
                    AppleMapView(
                        action: $mapAction,
                        rotation: $compassRotation,
                        locationManager: locationManager,
                        todoItems: filteredTodos,
                        userLogs: filteredLogs,
                        selectedItem: $selectedItem,
                        selectedClusterItems: $selectedClusterItems,
                        tapPosition: $tapPosition,
                        clusterRadius: $clusterRadius,
                        creatingTodoLocation: $creatingTodoLocation,
                        onLongTap: { coord in
                            self.creatingTodoLocation = coord
                            self.initialTodoName = ""
                            self.initialTodoTitle = "할 일 만들기"
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                self.isCreatingTodo = true
                            }
                        },
                        onUserLocationTap: {},
                        onDelete: { deleteItem($0) },
                        onDeleteLog: { deleteItem($0) },
                        onSelectLog: { viewingHistoryItem = $0 }, onSelectItem: { selectedItem = $0 },
                        onFarItemsDetected: { count in
                            self.farItemsCount = count
                            self.showFarNotification = true
                        }
                    )
                case .kakao:
                     KakaoMapView(
                         action: $mapAction,
                         rotation: $compassRotation,
                         locationManager: locationManager,
                         todoItems: filteredTodos,
                         userLogs: filteredLogs,
                         selectedItem: $selectedItem,
                         selectedClusterItems: $selectedClusterItems,
                         tapPosition: $tapPosition,
                         clusterRadius: $clusterRadius,
                         creatingTodoLocation: $creatingTodoLocation, // [NEW]
                         onLongTap: { coord in
                            self.creatingTodoLocation = coord
                            self.initialTodoName = ""
                            self.initialTodoTitle = "할 일 만들기"
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                self.isCreatingTodo = true
                            }
                         },
                         onDelete: { deleteItem($0) },
                         onDeleteLog: { deleteItem($0) },
                         onSelectLog: { viewingHistoryItem = $0 },
                         onSelectItem: { selectedItem = $0 },
                         onFarItemsDetected: { count in
                             self.farItemsCount = count
                             self.showFarNotification = true
                         }
                     )
                case .naver:
                    NaverMapView(
                        action: $mapAction,
                        rotation: $compassRotation,
                        locationManager: locationManager,
                        todoItems: filteredTodos,
                        userLogs: filteredLogs,
                        selectedItem: $selectedItem,
                        selectedClusterItems: $selectedClusterItems,
                        tapPosition: $tapPosition,
                        clusterRadius: $clusterRadius,
                        creatingTodoLocation: $creatingTodoLocation,
                        onLongTap: { coord in
                            self.creatingTodoLocation = coord
                            self.initialTodoName = ""
                            self.initialTodoTitle = "할 일 만들기"
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                self.isCreatingTodo = true
                            }
                        },
                        onUserLocationTap: {},
                        onDelete: { deleteItem($0) },
                        onDeleteLog: { deleteItem($0) },
                        onSelectLog: { viewingHistoryItem = $0 }, onSelectItem: { selectedItem = $0 },
                        onFarItemsDetected: { count in
                            self.farItemsCount = count
                            self.showFarNotification = true
                        }
                    )
                case .google:
                    GoogleMapView(
                        action: $mapAction,
                        rotation: $compassRotation,
                        locationManager: locationManager,
                        todoItems: filteredTodos,
                        userLogs: filteredLogs,
                        selectedItem: $selectedItem,
                        selectedClusterItems: $selectedClusterItems,
                        tapPosition: $tapPosition,
                        clusterRadius: $clusterRadius,
                        creatingTodoLocation: $creatingTodoLocation, // [NEW]
                        hasItems: !filteredTodos.isEmpty || !filteredLogs.isEmpty,
                        onLongTap: { coord in
                            self.creatingTodoLocation = coord
                            self.initialTodoName = ""
                            self.initialTodoTitle = "할 일 만들기"
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                self.isCreatingTodo = true
                            }
                        },
                        onUserLocationTap: {},
                        onDelete: { deleteItem($0) },
                        onDeleteLog: { deleteItem($0) },
                        onSelectLog: { viewingHistoryItem = $0 }, onSelectItem: { selectedItem = $0 },
                        onFarItemsDetected: { count in
                            self.farItemsCount = count
                            self.showFarNotification = true
                        }
                    )
                }
            }
            .ignoresSafeArea()
            
            // UI Layer
            VStack {
                 HStack(alignment: .top) {
                     statusWidget
                     Spacer()
                     VStack(spacing: 12) {
                        if showFarNotification {
                            Button(action: { showFarNotification = false }) {
                                HStack {
                                    Image(systemName: "mappin.circle.fill")
                                    Text("\(farItemsCount)개의 핀이 멀리 있어요")
                                        .font(.system(size: 12, weight: .bold))
                                }
                                .padding(10)
                                .background(Color.white.opacity(0.8)) // 80% transparency
                                .foregroundColor(.red)
                                .cornerRadius(20)
                            }
                        }
                        navigationControls
                     }
                     .padding(.trailing, 8)
                     .padding(.bottom, isCreatingTodo ? 350 : 0)
                     .animation(.spring(), value: isCreatingTodo)
                 }
                 .padding(.top, 32)
                 .padding(.horizontal, 8)
                 Spacer()
            }
            
            // Overlays
            clusterOverlay
            todoDetailOverlay
            allItemsOverlay
            sideMenuLayer
            
            // Create Todo Layer
            if isCreatingTodo {
                CreateTodoLayer(
                    title: initialTodoTitle,
                    defaultName: "새 할 일",
                    initialName: initialTodoName,
                    onRegister: { name, person, dateStr, timeStr, memo in
                        if let loc = creatingTodoLocation {
                            let formatter = DateFormatter()
                            formatter.dateFormat = "yyyy.MM.dd HH:mm"
                            let combinedStr = "\(dateStr) \(timeStr)"
                            let dateTime = formatter.date(from: combinedStr) ?? Date()
                            handleLongTap(lat: loc.latitude, lon: loc.longitude, name: name, dateTime: dateTime)
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { centerMapOn(loc) }
                        }
                        isCreatingTodo = false
                        creatingTodoLocation = nil
                        initialTodoName = ""
                    },
                    onCancel: {
                        if let loc = creatingTodoLocation {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { centerMapOn(loc) }
                        }
                        isCreatingTodo = false
                        creatingTodoLocation = nil
                        initialTodoName = ""
                    }
                )
                .preferredColorScheme(.light)
                .transition(.move(edge: .bottom))
                .animation(.spring(), value: isCreatingTodo)
            }
        }
        .sheet(item: $viewingHistoryItem) { item in
            PathHistoryView(item: item, onClose: { viewingHistoryItem = nil })
                .preferredColorScheme(.light)
        }
        .sheet(isPresented: $showCalendar) {
             VStack {
                 Text("Time Travel")
                     .font(.headline)
                     .padding(.top)
                 
                 DatePicker("Select Date", selection: $selectedDate, displayedComponents: [.date, .hourAndMinute])
                     .datePickerStyle(.graphical)
                     .padding()
                 
                 HStack {
                     Button(action: {
                         showCalendar = false
                         showHistoryMode = false
                         selectedDate = Date()
                         mapAction = .zoomToFit
                         DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                             mapAction = .currentLocation
                         }
                     }) {
                         HStack {
                             Image(systemName: "arrow.counterclockwise")
                             Text("Comeback to Now")
                         }
                         .foregroundColor(.white)
                         .padding()
                         .background(Color.red)
                         .cornerRadius(8)
                     }
                     
                     Spacer()
                     
                     Button("Go") {
                         showCalendar = false
                         mapAction = .zoomToFit
                     }
                     .padding()
                 }
                 .padding()
             }
             .presentationDetents([.medium, .large])
             .preferredColorScheme(.light)
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("MapRotationChanged"))) { notification in
            if let rotation = notification.userInfo?["rotation"] as? Double {
                compassRotation = rotation
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            switch newPhase {
            case .background, .inactive:
                backgroundStartTime = Date()
                Task {
                    if let session = await locationManager.endSession() {
                        let log = ToDoItem(
                            todo_name: "이동 경로",
                            date_time: session.start,
                            begin_time: session.start,
                            end_time: session.end,
                            type: "00",
                            latitude: session.midLat,
                            longitude: session.midLon
                        )
                        modelContext.insert(log)
                        for point in session.points {
                            let path = PathItem(
                                todo_id: log.todo_id,
                                latitude: point.latitude,
                                longitude: point.longitude
                            )
                            modelContext.insert(path)
                        }
                        try? modelContext.save()
                        locationManager.startSession()
                    }
                }
            case .active:
                if let start = backgroundStartTime {
                    let elapsed = Date().timeIntervalSince(start)
                    if elapsed > 5 {
                        NotificationCenter.default.post(name: NSNotification.Name("TriggerLaunchAnimation"), object: nil)
                    }
                }
                backgroundStartTime = nil
                locationManager.startSession()
            @unknown default:
                break
            }
        }
    }

    // MARK: - Computed Properties / Helper Views
    var sortedAllItems: [UnifiedMapItem] {
        (filteredTodos.map { UnifiedMapItem.todo($0) } +
         filteredLogs.map { UnifiedMapItem.history($0) })
        .sorted { getItemDate($0) > getItemDate($1) }
    }

    var statusWidget: some View {
        TopLeftWidget(
            historyCount: allItems.filter { $0.type == "00" }.count,
            localTodoCount: allItems.filter { $0.type == "10" && $0.int_lat != 0 }.count, 
            serverTodoCount: 0,
            compassRotation: compassRotation,
            onCompassClick: { mapAction = .rotateNorth },
            onExpandClick: { withAnimation { showListView = true } }
        )
    }
    
    var navigationControls: some View {
        RightSideControls(
            compassRotation: compassRotation,
            showHistoryMode: showHistoryMode,
            onHistoryClick: handleHistoryClick,
            onNotificationClick: {},
            onLoginClick: { showProfile = true },
            onLocationClick: { mapAction = .currentLocation },
            onZoomInClick: { mapAction = .zoomIn },
            onZoomOutClick: { mapAction = .zoomOut },
            onCompassClick: { mapAction = .rotateNorth },
            onExpandClick: { withAnimation { showListView = true } }
        )
    }

    var clusterOverlay: some View {
        Group {
            if let clusterItems = selectedClusterItems {
                ZStack {
                    Color.black.opacity(0.01)
                        .contentShape(Rectangle())
                        .onTapGesture { selectedClusterItems = nil }
                        .ignoresSafeArea()
                    
                    VStack(spacing: 0) {
                        VStack(spacing: 0) {
                            ClusterListCallout(
                                items: clusterItems.sorted(by: { $0.date > $1.date }),
                                isCluster: true,
                                onClose: { selectedClusterItems = nil },
                                onDeleteToDo: { item in deleteItem(item) },
                                onDeleteLog: { item in deleteItem(item) },
                                onSelectLog: { item in
                                    viewingHistoryItem = item
                                    selectedClusterItems = nil
                                },
                                onSelectItem: { item in
                                    selectedItem = item
                                    selectedClusterItems = nil
                                }
                            )
                            .padding(.bottom, 8)
                        }
                        .frame(width: 320)
                        .background(Color.white)
                        .cornerRadius(12)
                        .preferredColorScheme(.light)
                        
                        CalloutTriangle()
                            .fill(Color.white)
                            .frame(width: 20, height: 10)
                            .padding(.top, -1)
                    }
                    .shadow(radius: 10)
                    .frame(width: 320, height: 0, alignment: .bottom) // [사용자 원천기술] 하단 고정 상단 확장
                    .position(x: (tapPosition?.x ?? 0) + mapXOffset, y: (tapPosition?.y ?? 0) + mapYOffset)
                    .transition(.scale.combined(with: .opacity))
                }
                .zIndex(999)
            }
        }
    }
    
    var mapXOffset: CGFloat {
        switch mapProvider {
        case .kakao: return -6
        case .naver: return -4
        default: return -1
        }
    }
    
    var mapYOffset: CGFloat {
        switch mapProvider {
        case .kakao, .naver: return -120
        default: return -110
        }
    }
    
    var sideMenuLayer: some View {
        Group {
            if showProfile {
                ZStack {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { withAnimation { showProfile = false } }
                    
                    HStack {
                        UserProfileView(isPresented: $showProfile, locationManager: locationManager)
                            .frame(width: 300)
                            .background(Color.white)
                            .shadow(radius: 5)
                        Spacer()
                    }
                    .transition(.move(edge: .leading))
                }
                .preferredColorScheme(.light)
                .zIndex(300)
            }
        }
    }
    
    var todoDetailOverlay: some View {
        Group {
            if let item = selectedItem {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { selectedItem = nil }
                    
                    CreateTodoLayer(
                        existingItem: item,
                        onRegister: { name, person, dateStr, timeStr, memo in
                            // Update existing item
                            item.todo_name = name
                            item.memo = memo
                            try? modelContext.save()
                            selectedItem = nil
                        },
                        onCancel: { selectedItem = nil }
                    )
                }
                .preferredColorScheme(.light)
                .transition(.move(edge: .bottom))
                .zIndex(400)
            }
        }
    }
}

extension ContentView {
    
    var allItemsOverlay: some View {
        Group {
            if showListView {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { withAnimation { showListView = false } }
                    
                    VStack(spacing: 0) {
                        HStack {
                            Text("모든 항목 (\(filteredTodos.count + filteredLogs.count))")
                                .font(.headline)
                            Spacer()
                            Button(action: { withAnimation { showListView = false } }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                                    .font(.title2)
                             }
                        }
                        .padding()
                        .background(Color(.systemGroupedBackground))
                        
                        let allItemsList = sortedAllItems
                        
                        if allItemsList.isEmpty {
                            Text("표시할 항목이 없습니다.")
                                .foregroundColor(.gray)
                                .frame(height: 200)
                                .frame(maxWidth: .infinity)
                        } else {
                            ClusterListCallout(
                                items: allItemsList,
                                isCluster: true,
                                onClose: { withAnimation { showListView = false } },
                                onDeleteToDo: { deleteItem($0) },
                                onDeleteLog: { deleteItem($0) },
                                onSelectLog: { log in
                                    withAnimation { showListView = false }
                                    viewingHistoryItem = log
                                },
                                onSelectItem: { item in
                                    withAnimation { showListView = false }
                                    selectedItem = item
                                }
                            )
                            .frame(maxHeight: 400)
                        }
                    }
                    .background(Color.white)
                    .cornerRadius(16)
                    .preferredColorScheme(.light)
                    .transition(.move(edge: .bottom))
                }
                .zIndex(500)
            }
        }
    }

    // MARK: - Actions
    private func handleHistoryClick() {
        if !showHistoryMode {
            showHistoryMode = true
            selectedDate = Date()
            mapAction = .zoomToFit
        } else {
            showCalendar = true
        }
    }

    private func handleLongTap(lat: Double, lon: Double, name: String, dateTime: Date) {
        let newItem = ToDoItem(
            todo_name: name,
            date_time: dateTime,
            type: "10",
            latitude: lat,
            longitude: lon
        )
        modelContext.insert(newItem)
        try? modelContext.save()
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
    }

    private func deleteItem(_ item: ToDoItem) {
        // Clear selection first
        if selectedItem?.todo_id == item.todo_id { selectedItem = nil }
        if let idx = selectedClusterItems?.firstIndex(where: {
            if case .todo(let t) = $0 { return t.todo_id == item.todo_id }
            if case .history(let t) = $0 { return t.todo_id == item.todo_id }
            return false
        }) {
            selectedClusterItems?.remove(at: idx)
            if selectedClusterItems?.isEmpty == true { selectedClusterItems = nil }
        }
        
        // Delete from DB and Save
        modelContext.delete(item)
        do {
            try modelContext.save()
            print("Successfully deleted and saved \(item.todo_name)")
        } catch {
            print("Failed to save after deletion: \(error)")
        }
    }

    private func getItemDate(_ item: UnifiedMapItem) -> Date {
        switch item {
        case .todo(let t): return t.date_time ?? Date(timeIntervalSince1970: Double(t.created_at)/1000.0)
        case .history(let t): return t.begin_time ?? Date(timeIntervalSince1970: Double(t.created_at)/1000.0)
        case .serverMessage: return Date()
        case .userLocation: return Date()
        }
    }

    private func centerMapOn(_ loc: CLLocationCoordinate2D) {
        NotificationCenter.default.post(name: NSNotification.Name("CenterMapOnLocation"), object: loc)
    }
}

// MARK: - Helper Views
struct CalloutTriangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

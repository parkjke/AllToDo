import SwiftUI
import CoreLocation
import Combine
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var todoItems: [ToDoItem]
    // Fetch logs for history count (simplistic query)
    @Query private var userLogs: [UserLog]
    
    @State private var showProfile = false
    @State private var showTasks = false
    
    // Map Control State
    @State private var mapAction: MapAction = .none
    @State private var compassRotation: Double = 0.0
    
    @StateObject private var locationManager = AppLocationManager()
    
    @State private var showLocationHistory = false
    

    
    @State private var selectedItem: ToDoItem?
    @State private var selectedClusterItems: [UnifiedMapItem]? // [NEW]
    @State private var selectedLogForPath: UserLog? // [NEW] For Popup
    @State private var showHistoryMode = false // [NEW] History Toggle
    @State private var selectedDate = Date() // [NEW] Time Travel Date
    @State private var showCalendar = false
    @State private var showListView = false // List of All Items // [NEW] Calendar Sheet
    
    // Settings
    @AppStorage("maxPopupItems") private var maxPopupItems = 5
    @AppStorage("popupFontSize") private var popupFontSize = 1
    
    // Time Filtering Logic
    var filteredTodos: [ToDoItem] {
        let centerDate = showHistoryMode ? selectedDate : Date()
        let min = Calendar.current.date(byAdding: .hour, value: -24, to: centerDate)!
        let max = Calendar.current.date(byAdding: .hour, value: 12, to: centerDate)!
        return todoItems.filter {
            guard let d = $0.dueDate else { return true }
            return d >= min && d <= max
        }
    }
    
    var filteredLogs: [UserLog] {
        let centerDate = showHistoryMode ? selectedDate : Date()
        let min = Calendar.current.date(byAdding: .hour, value: -24, to: centerDate)!
        // If history mode, maybe we show logs UP TO that date?
        // Logic: "24h window ending at selectedDate" (similar to Now).
        return userLogs.filter { $0.startTime >= min && $0.startTime <= centerDate }
    }
    
    // MapProvider Enum moved to UnifiedMapModels.swift
    
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    
    var mapLayer: some View {
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
                    onLongTap: handleLongTap,
                    onUserLocationTap: {},
                    onDelete: deleteItem,
                    onDeleteLog: deleteLog,
                    onSelectLog: { selectedLogForPath = $0 }
                )
            case .kakao:
                KakaoMapView(
                    action: $mapAction,
                    rotation: $compassRotation,
                    locationManager: locationManager,
                    todoItems: filteredTodos,
                    userLogs: filteredLogs, // [Fixed] Added missing arg
                    selectedItem: $selectedItem,
                    selectedClusterItems: $selectedClusterItems, // [Fixed] Added missing arg
                    onLongTap: handleLongTap
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
                    onLongTap: handleLongTap,
                    onUserLocationTap: {},
                    onDelete: deleteItem,
                    onDeleteLog: deleteLog,
                    onSelectLog: { selectedLogForPath = $0 }
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
                    hasItems: !filteredTodos.isEmpty || !filteredLogs.isEmpty,
                    onLongTap: handleLongTap,
                    onUserLocationTap: {},
                    onDelete: deleteItem,
                    onDeleteLog: deleteLog,
                    onSelectLog: { selectedLogForPath = $0 }
                )
            }
        }
        .ignoresSafeArea()
        // [NEW] Feed LayoutManager with items for WASM Clustering
        // Note: Clustering logic resides in MapViews or specialized manager, not AppLocationManager.
        // If we want WASM clustering global, we need a dedicated manager or update AppLocationManager.
        // For now, assuming MapViews handle it.
        // .onChange(of: filteredTodos) { newTodos in
        //     locationManager.setItems(todos: newTodos, logs: filteredLogs)
        // }
        // .onChange(of: filteredLogs) { newLogs in
        //     locationManager.setItems(todos: filteredTodos, logs: newLogs)
        // }
        // .onAppear {
        //      locationManager.setItems(todos: filteredTodos, logs: filteredLogs)
        // }
    }
    
    private func handleLongTap(_ coord: CLLocationCoordinate2D) {
        let newItem = ToDoItem(
            title: "New Task",
            dueDate: Date(),
            location: LocationData(latitude: coord.latitude, longitude: coord.longitude, name: "Pinned Location")
        )
        modelContext.insert(newItem)
        try? modelContext.save()
        print("ContentView: Inserted Item.")
        selectedItem = newItem
    }
    
    private func deleteItem(_ item: ToDoItem) {
        modelContext.delete(item)
        try? modelContext.save()
    }

    private func deleteLog(_ log: UserLog) {
        modelContext.delete(log)
        try? modelContext.save()
    }
    
    private func getItemDate(_ item: UnifiedMapItem) -> Date {
        switch item {
        case .todo(let t): return t.dueDate ?? t.createdAt
        case .history(let l): return l.startTime
        case .serverMessage: return Date()
        case .userLocation: return Date()
        }
    }

    private func handleHistoryClick() {
        if !showHistoryMode {
            showHistoryMode = true
            selectedDate = Date()
            mapAction = .zoomToFit
        } else {
            showCalendar = true
        }
    }
    
    var statusWidget: some View {
        TopLeftWidget(
            historyCount: userLogs.count,
            localTodoCount: todoItems.filter { $0.location != nil }.count, 
            serverTodoCount: 0,
            onExpandClick: { withAnimation { showListView = true } }
        )
    }
    
    var navigationControls: some View {
        RightSideControls(
            compassRotation: compassRotation,
            showHistoryMode: showHistoryMode,
            onHistoryClick: handleHistoryClick,
            onNotificationClick: {
                // TODO: Notifications
            },
            onLoginClick: { showProfile = true },
            onLocationClick: { mapAction = .currentLocation },
            onZoomInClick: { mapAction = .zoomIn },
            onZoomOutClick: { mapAction = .zoomOut },
            onCompassClick: { mapAction = .rotateNorth }
        )
    }

    var uiLayer: some View {
        VStack {
             HStack(alignment: .top) {
                 statusWidget
                 Spacer()
                 navigationControls
             }
             .padding(.top, 32)
             .padding(.horizontal, 8)
             Spacer()
        }
    }
    
     var debugLayer: some View {
          VStack(alignment: .leading) {
              Spacer()
              VStack(alignment: .leading, spacing: 4) {
                  Text(locationManager.debugStatus).font(.caption).foregroundColor(.white)
                  // Text(locationManager.lastResult).font(.caption2).foregroundColor(.yellow)
                  Text("ID: \(RemoteLogger.shared.deviceID.prefix(8).description)").font(.caption2).foregroundColor(.gray)
              }
              .padding(8)
              .background(Color.black.opacity(0.6))
              .cornerRadius(8)
              .padding(.bottom, 60)
              .padding(.leading, 12)
          }
          .frame(maxWidth: .infinity, alignment: .leading)
          .allowsHitTesting(false)
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
                        HStack {
                            Spacer()
                            Button(action: { selectedClusterItems = nil }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                                    .font(.title3)
                            }
                            .padding([.top, .trailing], 8)
                        }
                        .background(Color.white)
                        
                        ClusterListCallout(
                            items: clusterItems.sorted(by: { $0.date > $1.date }),
                            isCluster: true,
                            onDeleteToDo: { item in
                                modelContext.delete(item)
                                if let idx = selectedClusterItems?.firstIndex(where: { 
                                    if case .todo(let t) = $0 { return t.id == item.id }
                                    return false
                                }) {
                                    selectedClusterItems?.remove(at: idx)
                                    if selectedClusterItems?.isEmpty == true { selectedClusterItems = nil }
                                }
                            },
                            onDeleteLog: { log in
                                modelContext.delete(log)
                                if let idx = selectedClusterItems?.firstIndex(where: {
                                    if case .history(let h) = $0 { return h.id == log.id }
                                    return false
                                }) {
                                    selectedClusterItems?.remove(at: idx)
                                    if selectedClusterItems?.isEmpty == true { selectedClusterItems = nil }
                                }
                            },
                            onSelectLog: { log in
                                selectedLogForPath = log
                                selectedClusterItems = nil
                            }
                        )
                        .padding(.bottom, 8)
                    }
                    .frame(width: 260)
                    .frame(height: min(
                        CGFloat(clusterItems.count) * (popupFontSize == 0 ? 38 : (popupFontSize == 1 ? 42 : 52)) + 36, 
                        CGFloat(maxPopupItems) * (popupFontSize == 0 ? 38 : (popupFontSize == 1 ? 42 : 52)) + 36
                    ))
                    .background(Color.white)
                    .cornerRadius(12)
                    .shadow(radius: 10)
                    .transition(.scale.combined(with: .opacity))
                }
                .zIndex(200)
            }
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
                        UserProfileView(isPresented: $showProfile)
                            .frame(width: UIScreen.main.bounds.width - 80) // Unified with Login icon left margin
                            .background(Color.white)
                            .shadow(radius: 5)
                        Spacer()
                    }
                    .transition(.move(edge: .leading))
                }
                .zIndex(300)
            }
        }
    }
    
    var todoDetailOverlay: some View {
        Group {
            if let item = selectedItem {
                ZStack(alignment: .bottom) {
                    // Scrim
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { 
                            selectedItem = nil 
                        }
                    
                    // Bottom Sheet
                    VStack(spacing: 0) {
                         // Header
                         HStack {
                             Text("할 일")
                                 .font(.headline)
                             Spacer()
                             Button(action: { selectedItem = nil }) {
                                 Image(systemName: "xmark.circle.fill")
                                     .foregroundColor(.gray)
                                     .font(.title2)
                             }
                         }
                         .padding()
                         .background(Color(.systemGroupedBackground))
                         
                         // Content
                         ScrollView {
                             VStack(alignment: .leading, spacing: 16) {
                                 Text(item.title)
                                     .font(.title2)
                                     .fontWeight(.semibold)
                                 
                                 if let date = item.dueDate {
                                     HStack {
                                         Image(systemName: "calendar")
                                         Text(date.formatted(date: .long, time: .shortened))
                                     }
                                     .foregroundColor(.secondary)
                                 }
                                 
                                 Spacer()
                             }
                             .padding()
                             .frame(maxWidth: .infinity, alignment: .leading)
                         }
                         .frame(height: UIScreen.main.bounds.height * 0.5)
                         .background(Color.white)
                    }
                    .cornerRadius(16) // Rounded top corners visually by padding/clipping? standard cornerRadius is fine
                    .shadow(radius: 10)
                    .transition(.move(edge: .bottom))
                }
                .zIndex(400) // Top of everything
            }
        }
    }

    var allItemsOverlay: some View {
        Group {
            if showListView {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { withAnimation { showListView = false } }
                    
                    VStack(spacing: 0) {
                        // Header
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
                        
                        // Content
                        let allItems: [UnifiedMapItem] = (
                            filteredTodos.map { UnifiedMapItem.todo($0) } +
                            filteredLogs.map { UnifiedMapItem.history($0) }
                        ).sorted { getItemDate($0) > getItemDate($1) }
                        
                        if allItems.isEmpty {
                            Text("표시할 항목이 없습니다.")
                                .foregroundColor(.gray)
                                .frame(height: 200)
                                .frame(maxWidth: .infinity)
                        } else {
                            ClusterListCallout(
                                items: allItems,
                                isCluster: true,
                                onDeleteToDo: { deleteItem($0) },
                                onDeleteLog: { deleteLog($0) },
                                onSelectLog: { log in
                                    withAnimation { showListView = false }
                                    selectedLogForPath = log
                                }
                            )
                            .frame(height: UIScreen.main.bounds.height * 0.5)
                        }
                    }
                    .background(Color.white)
                    .cornerRadius(16)
                    .transition(.move(edge: .bottom))
                }
                .zIndex(500)
            }
        }
    }

    var body: some View {
        ZStack {
            mapLayer
            uiLayer
            debugLayer
            clusterOverlay
            sideMenuLayer
            todoDetailOverlay
            allItemsOverlay
        }
        .sheet(item: $selectedLogForPath) { log in
            PathHistoryView(log: log) {
                selectedLogForPath = nil
            }
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
                     // Return to Current Time Button
                     Button(action: {
                         showCalendar = false
                         showHistoryMode = false
                         selectedDate = Date() // Reset
                         // Sequence: Show All Current -> Wait 3s -> Zoom User
                         mapAction = .zoomToFit
                         DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                             withAnimation(.easeInOut(duration: 0.5)) {
                                 // View animation handled by Map logic, triggers region change
                             }
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
                     
                     // Confirm
                     Button("Go") {
                         showCalendar = false
                         // Filter updates automatically via selectedDate
                         mapAction = .zoomToFit
                     }
                     .padding()
                 }
                 .padding()
             }
             .presentationDetents([.medium, .large])
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("MapRotationChanged"))) { notification in
            if let rotation = notification.userInfo?["rotation"] as? Double {
                compassRotation = rotation
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            switch newPhase {
            case .active:
                // Start Recording Session
                locationManager.startSession()
            case .background, .inactive:
                // End Recording Session and Save
                Task {
                    if let session = await locationManager.endSession() {
                        let log = UserLog(
                            startTime: session.start,
                            endTime: session.end,
                            latitude: session.midLat,
                            longitude: session.midLon,
                            pathData: session.pathData
                        )
                        modelContext.insert(log)
                        try? modelContext.save()
                        
                        if let data = session.pathData, let jsonString = String(data: data, encoding: .utf8) {
                            print("ContentView: Session JSON: \(jsonString)")
                        } else {
                            print("ContentView: Saved Session Log (No JSON Path Data)")
                        }
                    }
                }
            default:
                break
            }
        }
    }
    
    // Timer removed entirely.
    @Environment(\.scenePhase) private var scenePhase: ScenePhase
}

#Preview {
    ContentView()
        .modelContainer(for: ToDoItem.self, inMemory: true)
}

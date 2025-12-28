import SwiftUI
import CoreLocation
import Combine
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var allItems: [ToDoItem]
    
    // [Phase 1 Refactoring] Central Controller
    @StateObject private var viewModel = MapFeatureViewModel()
    
    @StateObject private var locationManager = AppLocationManager()
    
    // UI State (Non-Map)
    @State private var showProfile = false
    @State private var backgroundStartTime: Date?
    
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple

    // MARK: - Body
    var body: some View {
        ZStack {
            // Map Layer
            mapLayer

            .ignoresSafeArea()
            
            // UI Layer
            VStack {
                 HStack(alignment: .top) {
                     statusWidget
                     Spacer()
                     VStack(spacing: 12) {
                        if viewModel.showFarNotification {
                            Button(action: { viewModel.showFarItems() }) { // [FIX] Show Items
                                HStack {
                                    Image(systemName: "mappin.circle.fill")
                                    Text("\(viewModel.farItemsCount)개의 핀이 멀리 있어요")
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
                     .padding(.bottom, viewModel.isCreatingTodo ? 350 : 0)
                     .animation(.spring(), value: viewModel.isCreatingTodo)
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
            if viewModel.isCreatingTodo {
                CreateTodoLayer(
                    title: viewModel.initialTodoTitle,
                    defaultName: "새 할 일",
                    initialName: viewModel.initialTodoName,
                    onRegister: { name, person, dateStr, timeStr, memo in
                        if let loc = viewModel.creatingTodoLocation {
                            let formatter = DateFormatter()
                            formatter.dateFormat = "yyyy.MM.dd HH:mm"
                            let combinedStr = "\(dateStr) \(timeStr)"
                            let dateTime = formatter.date(from: combinedStr) ?? Date()
                            handleLongTap(lat: loc.latitude, lon: loc.longitude, name: name, dateTime: dateTime)
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { centerMapOn(loc) }
                        }
                        viewModel.isCreatingTodo = false
                        viewModel.creatingTodoLocation = nil
                        viewModel.initialTodoName = ""
                    },
                    onCancel: {
                        if let loc = viewModel.creatingTodoLocation {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { centerMapOn(loc) }
                        }
                        viewModel.isCreatingTodo = false
                        viewModel.creatingTodoLocation = nil
                        viewModel.initialTodoName = ""
                    }
                )
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
                .transition(.move(edge: .bottom))

                .animation(.spring(), value: viewModel.isCreatingTodo)
            }
        }
        .sheet(item: $viewModel.viewingHistoryItem) { item in
            PathHistoryView(item: item, onClose: { viewModel.viewingHistoryItem = nil })
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
        }

        .sheet(isPresented: $viewModel.showCalendar) {
             VStack {
                 Text("Time Travel")
                     .font(.headline)
                     .padding(.top)
                 
                 DatePicker("Select Date", selection: $viewModel.selectedDate, displayedComponents: [.date, .hourAndMinute])
                     .datePickerStyle(.graphical)
                     .padding()
                 
                 HStack {
                     Button(action: {
                         viewModel.showCalendar = false
                         viewModel.showHistoryMode = false
                         viewModel.selectedDate = Date()
                         viewModel.mapAction = .zoomToFit
                         DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                             viewModel.mapAction = .currentLocation
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
                         viewModel.showCalendar = false
                         viewModel.mapAction = .zoomToFit
                     }
                     .padding()
                 }
                 .padding()
             }
             .presentationDetents([.medium, .large])
             .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)

        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("MapRotationChanged"))) { notification in
            if let rotation = notification.userInfo?["rotation"] as? Double {
                viewModel.compassRotation = rotation
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            switch newPhase {
            case .background, .inactive:
                backgroundStartTime = Date()
                // [FIX] Store session and last representative location on exit
                Task {
                    await locationManager.endSession()
                    if let loc = locationManager.currentLocation {
                        UserDefaults.standard.set(loc.coordinate.latitude, forKey: "last_latitude")
                        UserDefaults.standard.set(loc.coordinate.longitude, forKey: "last_longitude")
                        UserDefaults.standard.set(true, forKey: "has_saved_location")
                        print(">>> scenePhase: Saved last location and ended session.")
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
                // Automatic startSession removed to allow user control
            @unknown default:
                break
            }
        }
        .onChange(of: allItems) { _, _ in updateMapItems() }
        .onChange(of: viewModel.anchorDate) { _, _ in updateMapItems() }
        .onChange(of: viewModel.showHistoryMode) { _, _ in updateMapItems() }
        .onChange(of: viewModel.selectedDate) { _, _ in updateMapItems() }
        .onAppear {
             updateMapItems()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            self.viewModel.anchorDate = Date() // [FIX] Update Anchor on Resume
            viewModel.mapAction = .none
        }
        .onChange(of: locationManager.currentLocation) { _, newLocation in
            // [OPTIMIZATION] Smart Diffing: Only update heavy map items if moved significantly
            guard let loc = newLocation else { return }
            
            if viewModel.shouldUpdateMapItems(for: loc) {
                updateMapItems()
            } else {
                 // Even if we skip full update, we MUST ensure the MapView knows about the location change.
                 // Since MapViews observe `locationManager` directly, they will receive the update via their own `updateUIView`.
                 // We just log it here for debugging.
                 // print(">>> Smart Diffing: Skipping full map update (moved < 50m)")
            }
        }
    }


    // MARK: - Computed Properties / Helper Views
    var sortedAllItems: [UnifiedMapItem] {
        viewModel.cachedMapItems.sorted { getItemDate($0) > getItemDate($1) }
    }

    var statusWidget: some View {
        TopLeftWidget(
            historyCount: allItems.filter { $0.type == "00" }.count,
            localTodoCount: allItems.filter { $0.type == "10" && $0.int_lat != 0 }.count, 
            serverTodoCount: 0,
            compassRotation: viewModel.compassRotation,
            onCompassClick: { viewModel.mapAction = .rotateNorth },
            onExpandClick: { withAnimation { viewModel.showListView = true } }
        )
    }
    
    var navigationControls: some View {
        RightSideControls(
            compassRotation: viewModel.compassRotation,
            showHistoryMode: viewModel.showHistoryMode,
            onHistoryClick: handleHistoryClick,
            onNotificationClick: {},
            onLoginClick: { showProfile = true },
            onLocationClick: { viewModel.mapAction = .currentLocation },
            onZoomInClick: { viewModel.mapAction = .zoomIn },
            onZoomOutClick: { viewModel.mapAction = .zoomOut },
            onCompassClick: { viewModel.mapAction = .rotateNorth },
            onExpandClick: { withAnimation { viewModel.showListView = true } },
            showActivePath: locationManager.showActivePath,
            onRecordClick: handleRecordClick
        )
    }

    var clusterOverlay: some View {
        Group {
            if let clusterItems = viewModel.selectedClusterItems {
                ZStack {
                    Color.black.opacity(0.01)
                        .contentShape(Rectangle())
                        .onTapGesture { viewModel.selectedClusterItems = nil }
                        .ignoresSafeArea()
                    
                    VStack(spacing: 0) {
                        VStack(spacing: 0) {
                            ClusterListCallout(
                                items: clusterItems.sorted(by: { $0.date > $1.date }),
                                isCluster: true,
                                onClose: { viewModel.selectedClusterItems = nil },
                                onDeleteToDo: { item in deleteItem(item) },
                                onDeleteLog: { item in deleteItem(item) },
                                onSelectLog: { item in
                                    viewModel.viewingHistoryItem = item
                                    viewModel.selectedClusterItems = nil
                                },
                                onSelectItem: { item in
                                    viewModel.selectedItem = item
                                    viewModel.selectedClusterItems = nil
                                }
                            )
                            .padding(.bottom, 8)
                        }
                        .frame(width: 320)
                        .background(Color(.systemBackground))
                        .cornerRadius(20) // Changed from 12 to 20
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
                        .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
                        
                            .padding(.top, -1)
                    }
                    .frame(width: 320, height: 0, alignment: .bottom) // [사용자 원천기술] 하단 고정 상단 확장
                    .position(x: (viewModel.tapPosition?.x ?? 0) + mapXOffset, y: (viewModel.tapPosition?.y ?? 0) + mapYOffset)
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
                            .background(Color(.systemBackground)) // Dynamic background
                        Spacer()
                    }
                    .transition(.move(edge: .leading))
                }
                // [FIX] Apple/Google: Dynamic Mode, Kakao/Naver: Force Light Mode
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)

                .zIndex(300)
            }
        }
    }
    
    var todoDetailOverlay: some View {
        Group {
            if let item = viewModel.selectedItem {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { viewModel.selectedItem = nil }
                    
                    CreateTodoLayer(
                        existingItem: item,
                        onRegister: { name, person, dateStr, timeStr, memo in
                            // Update existing item
                            item.todo_name = name
                            item.memo = memo
                            try? modelContext.save()
                            viewModel.selectedItem = nil
                        },
                        onCancel: { viewModel.selectedItem = nil }
                    )
                }
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
                .transition(.move(edge: .bottom))

                .zIndex(400)
            }
        }
    }
}

extension ContentView {
    
    var allItemsOverlay: some View {
        Group {
            if viewModel.showListView {
                ZStack(alignment: .bottom) {
                    Color.black.opacity(0.3)
                        .ignoresSafeArea()
                        .onTapGesture { withAnimation { viewModel.showListView = false } }
                    
                    VStack(spacing: 0) {
                        HStack {
                            Text("모든 항목 (\(viewModel.cachedMapItems.count))")
                                .font(.headline)
                            Spacer()
                            Button(action: { withAnimation { viewModel.showListView = false } }) {
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
                                onClose: { withAnimation { viewModel.showListView = false } },
                                onDeleteToDo: { deleteItem($0) },
                                onDeleteLog: { deleteItem($0) },
                                onSelectLog: { log in
                                    withAnimation { viewModel.showListView = false }
                                    viewModel.viewingHistoryItem = log
                                },
                                onSelectItem: { item in
                                    withAnimation { viewModel.showListView = false }
                                    viewModel.selectedItem = item
                                }
                            )
                            .frame(maxHeight: 400)
                        }
                    }
                    .background(Color(.systemBackground))
                    .cornerRadius(16)

                    .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
                    .transition(.move(edge: .bottom))

                }
                .zIndex(500)
            }
        }
    }

    // MARK: - Actions
    private func handleHistoryClick() {
        viewModel.handleHistoryClick()
    }

    private func handleRecordClick() {
        // [MODIFIED] Now used as a debugging toggle for path visualization
        locationManager.showActivePath.toggle()
        print(">>> Debug Path Visibility: \(locationManager.showActivePath)")
    }

    private func handleLongTap(lat: Double, lon: Double, name: String, dateTime: Date) {
        let newItem = ToDoItem(
            todo_name: name,
            date_time: dateTime,
            is_exist_location_path: true
        )
        newItem.type = "10"
        newItem.latitude = lat
        newItem.longitude = lon
        modelContext.insert(newItem)
        try? modelContext.save()
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
    }

    private func deleteItem(_ item: ToDoItem) {
        // Clear selection first
        if viewModel.selectedItem?.todo_id == item.todo_id { viewModel.selectedItem = nil }
        if let idx = viewModel.selectedClusterItems?.firstIndex(where: {
            if case .todo(let t) = $0 { return t.todo_id == item.todo_id }
            if case .history(let t) = $0 { return t.todo_id == item.todo_id }
            return false
        }) {
            viewModel.selectedClusterItems?.remove(at: idx)
            if viewModel.selectedClusterItems?.isEmpty == true { viewModel.selectedClusterItems = nil }
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
    
    private func updateMapItems() {
        viewModel.updateMapItems(
            allItems: allItems,
            currentLocation: locationManager.currentLocation,
            showHistory: viewModel.showHistoryMode,
            anchor: viewModel.anchorDate,
            selectedDate: viewModel.selectedDate,

            filterByKorea: (mapProvider == .kakao || mapProvider == .naver) // [RENAME]
        )
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

// MARK: - Map Logic Separation
extension ContentView {
    
    var mapLayer: some View {
        Group {
            switch mapProvider {
            case .apple: appleMapView
            case .kakao: kakaoMapView
            case .naver: naverMapView
            case .google: googleMapView
            }
        }
    }
    
    var appleMapView: some View {
        AppleMapView(
            action: $viewModel.mapAction,
            rotation: $viewModel.compassRotation,
            locationManager: locationManager,
            allItems: viewModel.displayItems,
            selectedItem: $viewModel.selectedItem,
            viewingHistoryItem: $viewModel.viewingHistoryItem,
            selectedClusterItems: $viewModel.selectedClusterItems,

            tapPosition: $viewModel.tapPosition,
            clusterRadius: $viewModel.clusterRadius,
            creatingTodoLocation: $viewModel.creatingTodoLocation,
            onLongTap: { coord in
                viewModel.creatingTodoLocation = coord
                viewModel.initialTodoName = ""
                viewModel.initialTodoTitle = "할 일 만들기"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    viewModel.isCreatingTodo = true
                }
            },
            onUserLocationTap: {},
            onDelete: { deleteItem($0) },
            onDeleteLog: { deleteItem($0) },
            onSelectLog: { viewModel.viewingHistoryItem = $0 },
            onSelectItem: { viewModel.selectedItem = $0 },
            // onFarItemsDetected handled by ViewModel
            activePoints: locationManager.processedSessionPoints,
            showActivePath: locationManager.showActivePath
        )
    }
    
    var kakaoMapView: some View {
        KakaoMapView(
             action: $viewModel.mapAction,
             rotation: $viewModel.compassRotation,
             locationManager: locationManager,
             allItems: viewModel.cachedMapItems,
             selectedItem: $viewModel.selectedItem,
             viewingHistoryItem: $viewModel.viewingHistoryItem,
             selectedClusterItems: $viewModel.selectedClusterItems,

             tapPosition: $viewModel.tapPosition,
             clusterRadius: $viewModel.clusterRadius,
             creatingTodoLocation: $viewModel.creatingTodoLocation,
             onLongTap: { coord in
                viewModel.creatingTodoLocation = coord
                viewModel.initialTodoName = ""
                viewModel.initialTodoTitle = "할 일 만들기"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    viewModel.isCreatingTodo = true
                }
             },
             onDelete: { deleteItem($0) },
             onDeleteLog: { deleteItem($0) },
             onSelectLog: { viewModel.viewingHistoryItem = $0 },
             onSelectItem: { viewModel.selectedItem = $0 },
             onFarItemsDetected: { count in
                 viewModel.farItemsCount = count
                 viewModel.showFarNotification = true
             },
             activePoints: locationManager.processedSessionPoints,
             showActivePath: locationManager.showActivePath
         )
    }
    
    var naverMapView: some View {
        NaverMapView(
            action: $viewModel.mapAction,
            rotation: $viewModel.compassRotation,
            locationManager: locationManager,
            allItems: viewModel.cachedMapItems,
            selectedItem: $viewModel.selectedItem,
            viewingHistoryItem: $viewModel.viewingHistoryItem,
            selectedClusterItems: $viewModel.selectedClusterItems,

            tapPosition: $viewModel.tapPosition,
            clusterRadius: $viewModel.clusterRadius,
            creatingTodoLocation: $viewModel.creatingTodoLocation,
            onLongTap: { coord in
                viewModel.creatingTodoLocation = coord
                viewModel.initialTodoName = ""
                viewModel.initialTodoTitle = "할 일 만들기"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    viewModel.isCreatingTodo = true
                }
            },
            onUserLocationTap: {},
            onDelete: { deleteItem($0) },
            onDeleteLog: { deleteItem($0) },
            onSelectLog: { viewModel.viewingHistoryItem = $0 },
            onSelectItem: { viewModel.selectedItem = $0 },
            onFarItemsDetected: { count in
                viewModel.farItemsCount = count
                viewModel.showFarNotification = true
            },
            activePoints: locationManager.processedSessionPoints,
            showActivePath: locationManager.showActivePath
        )
    }
    
    var googleMapView: some View {
        GoogleMapView(
            action: $viewModel.mapAction,
            rotation: $viewModel.compassRotation,
            locationManager: locationManager,
            allItems: viewModel.cachedMapItems,
            selectedItem: $viewModel.selectedItem,
            viewingHistoryItem: $viewModel.viewingHistoryItem,
            selectedClusterItems: $viewModel.selectedClusterItems,

            tapPosition: $viewModel.tapPosition,
            clusterRadius: $viewModel.clusterRadius,
            creatingTodoLocation: $viewModel.creatingTodoLocation,
            hasItems: !viewModel.cachedMapItems.isEmpty,
            onLongTap: { coord in
                viewModel.creatingTodoLocation = coord
                viewModel.initialTodoName = ""
                viewModel.initialTodoTitle = "할 일 만들기"
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    viewModel.isCreatingTodo = true
                }
            },
            onUserLocationTap: {},
            onDelete: { deleteItem($0) },
            onDeleteLog: { deleteItem($0) },
            onSelectLog: { viewModel.viewingHistoryItem = $0 },
            onSelectItem: { viewModel.selectedItem = $0 },
            onFarItemsDetected: { count in
                viewModel.farItemsCount = count
                viewModel.showFarNotification = true
            },
            activePoints: locationManager.processedSessionPoints,
            showActivePath: locationManager.showActivePath
        )
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

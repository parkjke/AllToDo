import SwiftUI
import CoreLocation
import Combine
import SwiftData

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var allItems: [ToDoItem]
    
    // [Phase 1 Refactoring] Central Controller
    @StateObject private var viewModel = MapFeatureViewModel()
    @StateObject private var searchViewModel = SearchViewModel() // [NEW]
    
    @StateObject private var locationManager = AppLocationManager()
    
    // UI State (Non-Map)
    @State private var showProfile = false
    @State private var backgroundStartTime: Date?
    @State private var pendingEndSessionTask: Task<Void, Never>? = nil
    
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple

    // MARK: - Body
    var body: some View {
        ZStack {
            mapLayer.ignoresSafeArea()
            uiLayer
            clusterOverlay
            searchLayer
            
            // ripple effect
            
            // Ripple Effect (Centered for Search Result)
            if viewModel.showRipple {
                let isDarkRipple = (mapProvider == .apple || mapProvider == .google) ? (UIScreen.main.traitCollection.userInterfaceStyle == .dark) : false
                RippleEffectView(isDark: isDarkRipple)
                    .allowsHitTesting(false)
            }
        }
        .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
        .sheet(isPresented: $viewModel.showAllTodoSheet) {
            MainTodoSheet(viewModel: viewModel)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $viewModel.viewingHistoryItem) { item in
            PathHistoryView(item: item, onClose: { 
                viewModel.viewingHistoryItem = nil 
            })
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
                .interactiveDismissDisabled()
        }
        // Legacy DatePicker Sheet Removed
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("MapRotationChanged"))) { notification in
            if let rotation = notification.userInfo?["rotation"] as? Double {
                viewModel.compassRotation = rotation
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            switch newPhase {
            case .background: // [FIX] Removed .inactive to prevent endSession on Control Center/Notif Center
                backgroundStartTime = Date()
                // [NEW] 5-second Grace Period for path continuity
                pendingEndSessionTask = Task {
                    try? await Task.sleep(nanoseconds: 5_000_000_000) // 5 seconds
                    if !Task.isCancelled {
                        print(">>> APP STATE: Background -> Grace Period Expired -> Ending Session")
                        await locationManager.endSession()
                        if let loc = locationManager.currentLocation {
                            UserDefaults.standard.set(loc.coordinate.latitude, forKey: "last_latitude")
                            UserDefaults.standard.set(loc.coordinate.longitude, forKey: "last_longitude")
                            UserDefaults.standard.set(true, forKey: "has_saved_location")
                            print(">>> scenePhase: Saved last location.")
                        }
                    }
                }
            case .active:
                if let task = pendingEndSessionTask {
                    print(">>> APP STATE: Active -> Cancelling pending endSession (Grace Period Success)")
                    task.cancel()
                    pendingEndSessionTask = nil
                }
                
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
        .ignoresSafeArea(.keyboard) // [FIX] Disconnect UI from keyboard shifts for stable window-like experience
    }


    // MARK: - Computed Properties / Helper Views
    var sortedAllItems: [UnifiedMapItem] {
        viewModel.cachedMapItems.sorted { 
            let d1 = getItemDate($0)
            let d2 = getItemDate($1)
            if d1 == d2 {
                return $0.id.uuidString > $1.id.uuidString
            }
            return d1 > d2
        }
    }

    var statusWidget: some View {
        TopLeftWidget(
            historyCount: allItems.filter { $0.type == "00" }.count,
            localTodoCount: allItems.filter { $0.source == "local" && $0.type == "10" }.count, 
            serverTodoCount: allItems.filter { $0.source != "local" && $0.type == "10" }.count,
            compassRotation: viewModel.compassRotation,
            onCompassClick: { viewModel.mapAction = .rotateNorth },
            onExpandClick: { 
                viewModel.mainSheetTab = 0
                withAnimation { viewModel.showAllTodoSheet = true } 
            }
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
                        }
                        .frame(width: 380) // Fixed Width 380pt (to prevent date/time wrap on large devices)
                        .background(
                            // Adaptive Theme Policy
                            (mapProvider == .google && UIScreen.main.traitCollection.userInterfaceStyle == .dark)
                            ? Color(red: 27/255, green: 138/255, blue: 43/255).opacity(0.85) // #1B8A2B
                            : Color.allToDoGreen.opacity(0.8) // AllToDoGreen 80%
                        )
                        .cornerRadius(12) // Corner Radius 12pt
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .strokeBorder(Color.white.opacity(0.2), lineWidth: 1) // White 20% Border
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        
                        // [Tail]
                        CalloutTriangle()
                            .fill(
                                (mapProvider == .google && UIScreen.main.traitCollection.userInterfaceStyle == .dark)
                                ? Color(red: 27/255, green: 138/255, blue: 43/255).opacity(0.85)
                                : Color.allToDoGreen.opacity(0.8)
                            )
                            .frame(width: 20, height: 10) // 20x10pt Tail
                            .padding(.top, -0.5)
                    }
                    .shadow(color: .black.opacity(0.15), radius: 8, x: 0, y: 4) // 8dp elevation shadow
                    .frame(width: 380, height: 0, alignment: .bottom)
                    .position(x: (viewModel.tapPosition?.x ?? 0), y: (viewModel.tapPosition?.y ?? 0))
                    .transition(.scale.combined(with: .opacity))
                    .animation(.spring(response: 0.3, dampingFraction: 0.7), value: viewModel.selectedClusterItems != nil)
                }
                .zIndex(999)
                .ignoresSafeArea() // [FIX] Required for 8:8 Centering (absolute positioning)
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
    
}

extension ContentView {
    
    var uiLayer: some View {
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
    }


    var searchLayer: some View {
        ZStack {
            // Search Button (Bottom Center)
            VStack {
                Spacer()
                SearchButton {
                    withAnimation {
                        searchViewModel.isOverlayVisible = true
                    }
                }
                .padding(.bottom, 16) // [FIX] Added 16pt margin from Safe Area
            }
            .ignoresSafeArea()

            // Search Overlay
            if searchViewModel.isOverlayVisible {
                // [NEW] Tap background map area to close search
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation {
                            searchViewModel.isOverlayVisible = false
                        }
                    }
                    .zIndex(999)

                SearchOverlay(
                    viewModel: searchViewModel,
                    mapProvider: mapProvider, // [NEW] Pass provider for theme control
                    latitude: locationManager.currentLocation?.coordinate.latitude,
                    longitude: locationManager.currentLocation?.coordinate.longitude
                ) { result in
                    viewModel.moveToLocation(result.coordinate)
                    withAnimation {
                        searchViewModel.isOverlayVisible = false
                    }
                }
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
                .transition(.opacity)
                .zIndex(1000)
            }
        }
    }

    // allItemsOverlay removed. See CalendarDialog.swift for integrated list.

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
            no_of_path: 1
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
            targetLocation: $viewModel.targetLocation, // [NEW]
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
             targetLocation: $viewModel.targetLocation, // [NEW]
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
            targetLocation: $viewModel.targetLocation, // [NEW]
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
            targetLocation: $viewModel.targetLocation, // [NEW]
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

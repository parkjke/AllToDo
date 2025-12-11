import SwiftUI
import SwiftData
import CoreLocation
import KakaoMapsSDK
import GoogleMaps

@main
struct AllToDoApp: App {
    
    init() {
        // Initialize KakaoMapsSDK
        // NOTE: Replace the key if the Android one doesn't work for iOS
        SDKInitializer.InitSDK(appKey: "73c078184e5277946f8078004f60bd51")
        
        // Initialize Google Maps SDK
        GMSServices.provideAPIKey("AIzaSyCeE1yauStrXS0Xw6EUkUEgm5wFK_yBHcE")
        
        // Initialize WASM Engine
        WasmManager.shared.initialize { success in
            // Logs are already handled inside Manager
        }
    }

    @Environment(\.scenePhase) var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    BackgroundService.shared.register()
                    BackgroundService.shared.scheduleAppRefresh()
                }
                .onChange(of: scenePhase) { _, newPhase in
                    if newPhase == .active {
                        logUsage()
                    }
                }
        }
        .modelContainer(AllToDoApp.sharedModelContainer)
    }
    
    func logUsage() {
        let manager = CLLocationManager()
        // Request permission if needed, though usually handled by LocationManager in views
        // manager.requestWhenInUseAuthorization() 
        
        if let loc = manager.location {
            Task {
                if let uuid = UserDefaults.standard.string(forKey: "user_uuid") {
                    do {
                        _ = try await APIManager.shared.logUsage(uuid: uuid, lat: loc.coordinate.latitude, long: loc.coordinate.longitude)
                        print("App Active: Usage Logged")
                    } catch {
                        print("App Active: Log Failed: \(error)")
                    }
                }
            }
        }
    }
    
    // Static accessor for BackgroundService to use
    static var sharedModelContainer: ModelContainer = {
        let schema = Schema([
            ToDoItem.self,
            Appointment.self,
            Contact.self,
            UserLog.self
        ])
        let modelConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)

        do {
            return try ModelContainer(for: schema, configurations: [modelConfiguration])
        } catch {
            fatalError("Could not create ModelContainer: \(error)")
        }
    }()
}

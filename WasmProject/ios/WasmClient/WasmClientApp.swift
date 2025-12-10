import SwiftUI

@main
struct WasmClientApp: App {
    init() {
        WasmManager.shared.initialize { success in
            print("WASM init success: \(success)")
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        Text("WASM Client Running")
            .padding()
    }
}

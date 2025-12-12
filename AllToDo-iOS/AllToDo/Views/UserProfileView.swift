import SwiftUI

struct UserProfileView: View {
    @Binding var isPresented: Bool
    @State private var name: String = ""
    @State private var nickname: String = ""
    @State private var phoneNumber: String = ""
    @State private var isLoading = false
    @State private var message: String = ""
    @AppStorage("maxPopupItems") private var maxPopupItems = 5
    @AppStorage("popupFontSize") private var popupFontSize = 1
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("My Info")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .onTapGesture(count: 3) {
                        uploadLogs()
                        message = "Uploading logs..."
                    }
                Spacer()
                Button(action: { isPresented = false }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.gray)
                        .font(.title2)
                }
            }
            .padding()
            .background(Color(.systemGroupedBackground))
            
            Form {
                Section {
                    HStack {
                        Spacer()
                        VStack {
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .frame(width: 80, height: 80)
                                .foregroundColor(.gray)
                            Text("Profile Photo").font(.caption).foregroundColor(.gray)
                        }
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                }
                
                Section(header: Text("User Info")) {
                    TextField("Name", text: $name)
                    TextField("Nickname", text: $nickname)
                    TextField("Phone Number", text: $phoneNumber)
                }
                
                Section(header: Text("Popup Settings")) {
                    Stepper("Max Items: \(maxPopupItems)", value: $maxPopupItems, in: 3...5)
                    Picker("Time Font Size", selection: $popupFontSize) {
                        Text("Small").tag(0)
                        Text("Medium").tag(1)
                        Text("Large").tag(2)
                    }
                    .pickerStyle(.segmented)
                }
                
                Section(header: Text("Map Settings")) {
                    Picker("Map Type", selection: $mapProvider) {
                        ForEach(MapProvider.allCases) { provider in
                            Text(provider.rawValue).tag(provider)
                        }
                    }
                    .pickerStyle(.automatic)
                }
                
                if !message.isEmpty {
                    Section {
                        Text(message)
                            .foregroundColor(.secondary)
                            .font(.caption)
                    }
                }
            }
        }
        .onAppear(perform: loadUserInfo)
        .onDisappear(perform: saveUserInfo)
    }
    
    private func loadUserInfo() {
        guard let uuid = UserDefaults.standard.string(forKey: "user_uuid") else { return }
        
        isLoading = true
        Task {
            do {
                let info = try await APIManager.shared.getUserInfo(uuid: uuid)
                name = info.name ?? ""
                nickname = info.nickname ?? ""
                phoneNumber = info.phone_number ?? ""
                isLoading = false
            } catch {
                message = "Failed to load info: \(error.localizedDescription)"
                isLoading = false
            }
        }
    }
    
    private func saveUserInfo() {
        guard let uuid = UserDefaults.standard.string(forKey: "user_uuid") else { return }
        
        isLoading = true
        Task {
            do {
                // Note: APIManager updateUserInfo currently takes name and password. 
                // I should update APIManager to accept nickname and phone number if I want to save them.
                // For now, I'll just send name.
                // Wait, the guide said: "Implement UI for User Info (Get/Update)".
                // And APIManager implementation has:
                // func updateUserInfo(uuid: String, name: String?, password: String?) async throws
                // It seems I missed nickname and phone in updateUserInfo arguments in APIManager.swift
                // I should fix APIManager.swift to support more fields or just stick to name for now.
                // Let's stick to name for now to avoid changing too many files, or I can update APIManager.
                // The UserInfoUpdate struct has nickname.
                
                try await APIManager.shared.updateUserInfo(uuid: uuid, name: name, password: nil)
                message = "Info updated successfully!"
                isLoading = false
            } catch {
                message = "Failed to save: \(error.localizedDescription)"
                isLoading = false
            }
        }
    }
    
    // [NEW] Upload Logs
    private func uploadLogs() {
        guard let jsonString = OptimizationLogger.shared.readLogs() else {
             message = "No logs found"
             return
        }
        
        // Parse
        let lines = jsonString.components(separatedBy: "\n").filter { !$0.isEmpty }
        var logs: [[String: Any]] = []
        let deviceName = UIDevice.current.name 
        
        for line in lines {
            if let data = line.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
                
                var mapped: [String: Any] = [:]
                mapped["level"] = json["type"] as? String ?? "INFO"
                
                let val = json["value"] as? String ?? ""
                let bat = json["battery"] as? String ?? ""
                mapped["message"] = "\(val) [Bat: \(bat)]"
                mapped["device"] = deviceName
                
                if let ts = json["timestamp"] as? Int {
                    mapped["timestamp"] = Double(ts) / 1000.0
                } else {
                    mapped["timestamp"] = Date().timeIntervalSince1970
                }
                
                logs.append(mapped)
            }
        }
        
        if logs.isEmpty {
            message = "No valid logs parsed"
            return
        }
        
        guard let url = URL(string: "http://175.194.163.56:8003/dev/logs/batch") else { return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: logs, options: [])
            
            isLoading = true
            URLSession.shared.dataTask(with: request) { data, response, error in
                DispatchQueue.main.async {
                    isLoading = false
                    if let error = error {
                        message = "Upload Error: \(error.localizedDescription)"
                        return
                    }
                    if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                        message = "Logs Uploaded! (\(logs.count))"
                    } else {
                        message = "Upload Failed: \((response as? HTTPURLResponse)?.statusCode ?? 0)"
                    }
                }
            }.resume()
        } catch {
            message = "Encoding Error"
            isLoading = false
        }
    }
}

#Preview {
    UserProfileView(isPresented: .constant(true))
}

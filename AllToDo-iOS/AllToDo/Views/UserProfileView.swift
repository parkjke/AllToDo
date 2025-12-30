import SwiftUI
import CoreLocation
import SwiftData

struct UserProfileView: View {
    @Binding var isPresented: Bool
    @ObservedObject var locationManager: AppLocationManager
    @State private var name: String = ""
    @State private var nickname: String = ""
    @State private var phoneNumber: String = ""
    @State private var isLoading = false
    @State private var message: String = ""
    @AppStorage("maxPopupItems") private var maxPopupItems = 5
    @AppStorage("popupFontSize") private var popupFontSize = 1
    @AppStorage("selectedMapProvider") private var mapProvider: MapProvider = .apple
    #if DEBUG
    @State private var showPinGallery = false
    #endif
    @State private var showDeleteAlert = false
    @Environment(\.modelContext) private var modelContext
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("내 정보")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .onTapGesture(count: 3) {
                        uploadLogs()
                        message = "로그 업로드 중..."
                    }
                Spacer()
                Button(action: { isPresented = false }) {
                    Image(systemName: "xmark")
                        .foregroundColor(.gray)
                        .font(.title2)
                }
            }

            .padding()
            .background(Color(.systemGroupedBackground))
            
            Form {
                Section {
                    HStack {
                        // Blue Pin Icon (Left)
                        #if DEBUG
                        Button(action: { showPinGallery = true }) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color(.secondarySystemBackground))
                                    .frame(width: 50, height: 50)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                                    )
                                
                                // [FIX] Use fetchPin
                                Image(uiImage: PinImageHelper.shared.fetchPin(type: "00") ?? UIImage())
                                    .resizable()
                                    .scaledToFit()
                                    .frame(height: 32)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                        #endif
                        
                        Spacer()
                        
                        VStack {
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .frame(width: 80, height: 80)
                                .foregroundColor(.gray)
                            Text("프로필 사진").font(.caption).foregroundColor(.gray)
                        }
                        
                        Spacer()
                        
                        // [FIX] Path Tracking (GPS Recording) - Sync with Android
                        Button(action: {
                            if locationManager.isRecording {
                                Task { await locationManager.endSession() }
                            } else {
                                locationManager.startSession()
                            }
                        }) {
                            Image("ic_path_tracking")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 44, height: 44)
                                .foregroundColor(locationManager.isRecording ? Color.allToDoRed : Color.blue)
                        }
                        // [NEW] Delete All Data Button (Trash Icon)
                        Button(action: { showDeleteAlert = true }) {
                            Image(systemName: "trash.circle.fill")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 44, height: 44)
                                .foregroundColor(.allToDoRed)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .alert("데이터 초기화", isPresented: $showDeleteAlert) {
                            Button("취소", role: .cancel) { }
                            Button("전체 삭제", role: .destructive) { deleteAllData() }
                        } message: {
                            Text("모든 할 일과 이동 경로 데이터가 영구적으로 삭제됩니다. 계속하시겠습니까?")
                        }
                    }
                    .padding(.vertical, 10)
                    .listRowBackground(Color.clear)
                }
                
                Section(header: Text("사용자 정보")) {
                    TextField("이름", text: $name)
                    TextField("닉네임", text: $nickname)
                    TextField("전화번호", text: $phoneNumber)
                }
                
                Section(header: Text("팝업 설정")) {
                    Stepper("최대 항목 수: \(maxPopupItems)", value: $maxPopupItems, in: 3...5)
                        .padding(.vertical, 4)
                    Picker("글꼴 크기", selection: $popupFontSize) {

                        Text("작게").tag(0)
                        Text("보통").tag(1)
                        Text("크게").tag(2)
                    }
                    .pickerStyle(.segmented)
                }
                
                Section(header: Text("지도 설정")) {
                    Picker("지도 종류", selection: $mapProvider) {
                        ForEach(MapProvider.allCases) { provider in
                            Text(provider.rawValue).tag(provider)
                        }
                    }
                    .pickerStyle(.automatic)
                    .onChange(of: mapProvider) { _ in
                        // [FIX] Close panel immediately on map change to show transition
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            isPresented = false
                        }
                    }
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
        #if DEBUG
        .sheet(isPresented: $showPinGallery) {
            PinGalleryView()
                .preferredColorScheme((mapProvider == .apple || mapProvider == .google) ? nil : .light)
        }
        #endif

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
                message = "정보 로드 실패: \(error.localizedDescription)"
                isLoading = false
            }

        }
    }
    
    private func saveUserInfo() {
        guard let uuid = UserDefaults.standard.string(forKey: "user_uuid") else { return }
        
        isLoading = true
        Task {
            do {
                try await APIManager.shared.updateUserInfo(uuid: uuid, name: name, password: nil)
                message = "정보가 업데이트되었습니다!"
                isLoading = false
            } catch {
                message = "저장 실패: \(error.localizedDescription)"
                isLoading = false
            }

        }
    }
    
    private func uploadLogs() {
        guard let jsonString = OptimizationLogger.shared.readLogs() else {
             message = "로그를 찾을 수 없습니다"
             return
        }

        
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
    
    private func deleteAllData() {

        do {
            try modelContext.delete(model: ToDoItem.self)
            try modelContext.delete(model: PathItem.self)
            try modelContext.save()
            
            // [FIX] Also reset the active session in memory to prevent phantom pin creation
            locationManager.resetSession()
            
            message = "모든 데이터가 삭제되었습니다."
            
            // Re-trigger launch animation if needed
            NotificationCenter.default.post(name: NSNotification.Name("TriggerLaunchAnimation"), object: nil)
        } catch {
            message = "데이터 삭제 실패: \(error.localizedDescription)"
        }
    }
}

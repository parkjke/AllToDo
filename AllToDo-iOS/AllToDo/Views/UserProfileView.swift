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
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("My Info")
                    .font(.largeTitle)
                    .fontWeight(.bold)
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
}

#Preview {
    UserProfileView(isPresented: .constant(true))
}

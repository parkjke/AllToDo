import SwiftUI

// [NEW] Helper for recognition flash animation
struct ConditionalInvert: ViewModifier {
    var isActive: Bool
    func body(content: Content) -> some View {
        if isActive {
            content.colorInvert()
        } else {
            content
        }
    }
}

struct SearchOverlay: View {
    @ObservedObject var viewModel: SearchViewModel
    var mapProvider: MapProvider = .apple // [NEW] Theme reference
    @Environment(\.colorScheme) var colorScheme
    var latitude: Double?
    var longitude: Double?
    var onResultClick: (SearchResult) -> Void
    
    @FocusState private var isFocused: Bool // [NEW] For auto-keyboard
    @State private var isFlashing: Bool = false // [NEW] For UI recognition animation
    
    private var isDark: Bool {
        if mapProvider == .apple || mapProvider == .google {
            return colorScheme == .dark
        }
        return false // Kakao/Naver: Always Light
    }
    
    private var overlayBackground: Color {
        Color.Search.background(isDark: isDark)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Integrated Premium Container
            VStack(spacing: 0) {
                // 1. Search Bar Area
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color.Search.searchBarTint(isDark: isDark))
                    
                    ZStack(alignment: .leading) {
                        if viewModel.query.isEmpty {
                            Text("찾을 곳")
                                .foregroundColor(Color.Search.searchBarPlaceholder(isDark: isDark))
                                .font(.system(size: 18))
                                .padding(.leading, 4)
                        }
                        TextField("", text: $viewModel.query)
                            .textFieldStyle(PlainTextFieldStyle())
                            .foregroundColor(Color.Search.searchBarTint(isDark: isDark))
                            .font(.system(size: 18))
                            .focused($isFocused) // [NEW] Request focus
                            .submitLabel(.search)
                            .onSubmit {
                                viewModel.performSearch(latitude: latitude, longitude: longitude)
                            }
                            .onChange(of: viewModel.query) { _, newValue in
                                if newValue.count >= 2 {
                                    viewModel.performSearch(latitude: latitude, longitude: longitude)
                                }
                            }
                    }
                    
                    if !viewModel.query.isEmpty {
                        Button(action: { viewModel.query = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(Color.Search.searchBarTint(isDark: isDark))
                        }
                        .buttonStyle(PlainButtonStyle()) // [FIX] Remove system background/shadow
                    }
                }
                .padding()
                
                // 2. Search Results List (Integrated)
                if !viewModel.results.isEmpty {
                    Rectangle()
                        .fill(Color.Search.divider(isDark: isDark))
                        .frame(height: 1)
                    
                    ScrollView {
                        VStack(spacing: 0) {
                            ForEach(viewModel.results) { result in
                                SearchResultRow(result: result, isDark: isDark) {
                                    onResultClick(result)
                                }
                                
                                // Barely visible divider (Thin)
                                Rectangle()
                                    .fill(Color.Search.divider(isDark: isDark))
                                    .frame(height: 0.5)
                                    .padding(.horizontal, 12)
                            }
                        }
                    }
                    .frame(maxHeight: 300)
                } else if let error = viewModel.errorMessage {
                    Divider()
                    Text(error)
                        .font(.system(size: 14))
                        .foregroundColor(.red)
                        .padding()
                }
            }
            .background(overlayBackground)
            .cornerRadius(14)
            .overlay(
                // [FIX] Simple divider-colored border to avoid visual noise/shadow illusion
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.Search.divider(isDark: isDark), lineWidth: 1)
            )
            .modifier(ConditionalInvert(isActive: isFlashing))
            .padding(.leading, 8)
            .padding(.trailing, 16)
            .padding(.top, 110)
            
            Spacer()
        }
        .ignoresSafeArea(.keyboard) // [FIX] Ensure search overlay doesn't shift when keyboard appears
        .onAppear {
            // 1. Trigger Flash Animation (0.3s)
            isFlashing = true
            withAnimation(.easeInOut(duration: 0.3)) {
                isFlashing = false
            }
            
            // 2. Request Focus (with slight delay for reliable keyboard appearance)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                isFocused = true
            }
        }
    }
}

struct SearchResultRow: View {
    let result: SearchResult
    let isDark: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 4) {
                Text(result.name)
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(Color.Search.resultName(isDark: isDark))
                
                Text(result.address)
                    .font(.system(size: 16))
                    .foregroundColor(Color.Search.resultAddress(isDark: isDark))
                
                if let distStr = result.distance, let dist = Int(distStr) {
                    let formattedDist = dist >= 1000 ? String(format: "%.1fkm", Double(dist)/1000.0) : "\(dist)m"
                    Text(formattedDist)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color.Search.distance(isDark: isDark))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 12)
            .padding(.horizontal, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle()) // [FIX] Essential: Remove system gray bubble/shadow in list
    }
}

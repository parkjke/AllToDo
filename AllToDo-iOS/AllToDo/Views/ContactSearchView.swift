import SwiftUI
import SwiftData

struct ContactSearchView: View {
    @Binding var selectedContacts: [Contact]
    @Binding var isFocused: Bool
    @FocusState private var internalIsFocused: Bool
    
    @Query(sort: \Contact.name) private var allContacts: [Contact]
    @State private var searchText: String = ""

    @Environment(\.colorScheme) var colorScheme
    private var isDark: Bool { colorScheme == .dark }

    var filteredContacts: [Contact] {
        if searchText.isEmpty { return [] }
        return allContacts.filter { contact in
            KoreanUtils.matchesChoseong(query: searchText, target: contact.name) ||
            contact.primary_phone.contains(searchText)
        }
    }

    var body: some View {
        VStack(spacing: 20) {
            searchBarSection
            selectedChipsSection
            resultsListSection
        }
    }

    @ViewBuilder
    private var searchBarSection: some View {
            HStack {
                HStack {
                    TextField("초성, 전화번호를 넣으세요.", text: $searchText)
                        .textFieldStyle(PlainTextFieldStyle())
                        .padding(10)
                        .background(Color.Contact.searchBarBackground(isDark: isDark))
                        .cornerRadius(12)
                        .focused($internalIsFocused)
                }
                .padding(2)
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.Contact.searchBarBorder(isDark: isDark), lineWidth: 1))
            }
        }

    @ViewBuilder
    private var selectedChipsSection: some View {
        HStack {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(selectedContacts) { contact in
                        Text(contact.name)
                            .font(.system(size: 14))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.Contact.chipBackground(isDark: isDark))
                            .cornerRadius(20)
                            .onTapGesture {
                                selectedContacts.removeAll { $0.id == contact.id }
                            }
                    }
                    if selectedContacts.isEmpty {
                        Text("...")
                            .font(.system(size: 14))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.gray2)
                            .cornerRadius(20)
                    }
                }
            }
            .frame(minHeight: 40)
            .padding(8)
            .background(isDark ? Color.gray9 : Color.white)
            .cornerRadius(12)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.Contact.chipBorder(isDark: isDark), lineWidth: 1))
        }
    }

    @ViewBuilder
    private var resultsListSection: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 12) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(filteredContacts) { contact in
                            Button(action: {
                                if !selectedContacts.contains(where: { $0.id == contact.id }) {
                                    selectedContacts.append(contact)
                                }
                                searchText = "" // Clear search after selection
                                isFocused = false
                            }) {
                                HStack(spacing: 12) {
                                    Text(contact.name)
                                        .font(.system(size: 14, weight: .medium))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.Contact.resultBubble(isDark: isDark))
                                        .cornerRadius(12)
                                    
                                    Text(contact.primary_phone)
                                        .font(.system(size: 14))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(isDark ? Color.gray8 : Color.gray2)
                                        .cornerRadius(12)
                                }
                                .foregroundColor(isDark ? .white : .black)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 150)
            .background(Color.Contact.resultBackground(isDark: isDark))
            .cornerRadius(16)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.Contact.resultBorder(isDark: isDark), lineWidth: 1))
        }
        .onAppear {
            internalIsFocused = isFocused
        }
        .onChange(of: isFocused) { _, newValue in
            internalIsFocused = newValue
        }
        .onChange(of: internalIsFocused) { _, newValue in
            isFocused = newValue
        }
    }
}

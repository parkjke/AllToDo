import SwiftUI
import SwiftData

struct TodoListSheet: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \ToDoItem.createdAt, order: .reverse) private var todoItems: [ToDoItem]
    
    // Theme Colors
    let headerColor = Color(red: 0.0, green: 0.39, blue: 0.0) // Dark Green #006400
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("할 일 목록") // "Task List"
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Spacer()
                
                // Count Badge in Header
                Text("\(todoItems.count)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(headerColor)
                    .padding(8)
                    .background(Circle().fill(Color.white))
            }
            .padding()
            .background(headerColor)
            
            // List
            if todoItems.isEmpty {
                VStack {
                    Spacer()
                    Text("No tasks yet.")
                        .foregroundColor(.gray)
                    Spacer()
                }
            } else {
                List {
                    ForEach(todoItems) { item in
                        HStack {
                            // Custom Checkbox
                            Button(action: {
                                toggleComplete(item)
                            }) {
                                Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                                    .font(.system(size: 24))
                                    .foregroundColor(item.isCompleted ? headerColor : .gray)
                            }
                            .buttonStyle(PlainButtonStyle()) // remove default list button style
                            
                            Text(item.title)
                                .font(.body)
                                .foregroundColor(.black) // Requested Black Color
                                .strikethrough(item.isCompleted, color: .gray)
                                .padding(.leading, 8)
                            
                            Spacer()
                        }
                        .padding(.vertical, 4)
                        .listRowSeparator(.hidden)
                    }
                    .onDelete(perform: deleteItems)
                }
                .listStyle(.plain)
            }
        }
    }
    
    private func toggleComplete(_ item: ToDoItem) {
        item.isCompleted.toggle()
        // SwiftData autosaves, but we can verify changes if needed
    }
    
    private func deleteItems(offsets: IndexSet) {
        withAnimation {
            for index in offsets {
                modelContext.delete(todoItems[index])
            }
        }
    }
}

#Preview {
    TodoListSheet()
        .modelContainer(for: ToDoItem.self, inMemory: true)
}

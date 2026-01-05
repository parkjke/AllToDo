import SwiftUI
import SwiftData
import CoreLocation


struct MainTodoSheet: View {
    @ObservedObject var viewModel: MapFeatureViewModel
    @Environment(\.colorScheme) var colorScheme
    var isDark: Bool { colorScheme == .dark }
    
    @Environment(\.modelContext) private var modelContext
    
    var body: some View {
        ZStack {
            // 1. Base Layer: List / Calendar Tabs
            TabView(selection: $viewModel.mainSheetTab) {
                TodoListLayer(viewModel: viewModel)
                    .tag(0)
                
                CalendarDialog(viewModel: viewModel)
                    .tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .background(Color.TodoList.background(isDark: isDark))
            
            // 2. Overlay: Todo Detail / Edit
            if let item = viewModel.selectedItem {
                ZStack {
                    Color.black.opacity(0.4)
                        .ignoresSafeArea()
                        .onTapGesture { viewModel.selectedItem = nil }
                    
                    VStack {
                        Spacer()
                        CreateTodoLayer(
                            existingItem: item,
                            onRegister: { name, person, dateStr, timeStr, memo in
                                item.todo_name = name
                                item.memo = memo
                                try? modelContext.save()
                                viewModel.selectedItem = nil
                            },
                            onCancel: { viewModel.selectedItem = nil }
                        )
                        .transition(.move(edge: .bottom))
                    }
                }
                .zIndex(100)
            }
            
            // 3. Overlay: Create New Todo
            if viewModel.isCreatingTodo {
                ZStack {
                    Color.black.opacity(0.4)
                        .ignoresSafeArea()
                        .onTapGesture { viewModel.isCreatingTodo = false }
                    
                    VStack {
                        Spacer()
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
                                    
                                    let newItem = ToDoItem(
                                        todo_name: name,
                                        date_time: dateTime,
                                        no_of_path: 1
                                    )
                                    newItem.type = "10"
                                    newItem.latitude = loc.latitude
                                    newItem.longitude = loc.longitude
                                    newItem.memo = memo
                                    
                                    modelContext.insert(newItem)
                                    try? modelContext.save()
                                    
                                    let generator = UINotificationFeedbackGenerator()
                                    generator.notificationOccurred(.success)
                                }
                                viewModel.isCreatingTodo = false
                                viewModel.creatingTodoLocation = nil
                                viewModel.initialTodoName = ""
                            },
                            onCancel: { 
                                viewModel.isCreatingTodo = false
                                viewModel.creatingTodoLocation = nil
                                viewModel.initialTodoName = ""
                            }
                        )
                        .transition(.move(edge: .bottom))
                    }
                }
                .zIndex(200)
            }
        }
        .sheet(item: $viewModel.viewingHistoryItem) { item in
            PathHistoryView(item: item, onClose: {
                viewModel.viewingHistoryItem = nil
            })
        }
    }
}

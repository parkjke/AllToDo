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
                            onRegister: { name, selectedContacts, dateStr, timeStr, memo in
                                item.todo_name = name
                                item.memo = memo
                                item.is_exist_person = !selectedContacts.isEmpty
                                
                                // Update relationships (For simplicity in this sync phase, we'll replace them)
                                let todoId = item.todo_id
                                try? modelContext.delete(model: TodoContact.self, where: #Predicate { $0.todo_id == todoId })
                                
                                for contact in selectedContacts {
                                    let bridge = TodoContact(todo_id: todoId, contact_id: contact.id)
                                    modelContext.insert(bridge)
                                }
                                
                                try? modelContext.save()
                                
                                if viewModel.todoEntrySource == .callout {
                                    viewModel.showAllTodoSheet = false
                                }
                                viewModel.selectedItem = nil
                                viewModel.todoEntrySource = .none
                            },
                            onCancel: { 
                                if viewModel.todoEntrySource == .callout {
                                    viewModel.showAllTodoSheet = false
                                }
                                viewModel.selectedItem = nil
                                viewModel.todoEntrySource = .none
                            }
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
                            onRegister: { name, selectedContacts, dateStr, timeStr, memo in
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
                                    newItem.is_exist_person = !selectedContacts.isEmpty
                                    
                                    for contact in selectedContacts {
                                        let bridge = TodoContact(todo_id: newItem.todo_id, contact_id: contact.id)
                                        modelContext.insert(bridge)
                                    }
                                    
                                    try? modelContext.save()
                                    
                                    let generator = UINotificationFeedbackGenerator()
                                    generator.notificationOccurred(.success)
                                }
                                
                                // [MODIFIED] State Diagram: Long-tap entry should return to IdleMap (close entire sheet)
                                if viewModel.todoEntrySource == .longTap {
                                    viewModel.showAllTodoSheet = false
                                }
                                
                                viewModel.isCreatingTodo = false
                                viewModel.creatingTodoLocation = nil
                                viewModel.initialTodoName = ""
                                viewModel.todoEntrySource = .none
                            },
                            onCancel: { 
                                if viewModel.todoEntrySource == .longTap {
                                    viewModel.showAllTodoSheet = false
                                }
                                viewModel.isCreatingTodo = false
                                viewModel.creatingTodoLocation = nil
                                viewModel.initialTodoName = ""
                                viewModel.todoEntrySource = .none
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

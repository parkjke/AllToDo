import Foundation
import SwiftData
import CoreLocation

// MARK: - 1. ToDoItem Table (Integrated with History)
@Model
final class ToDoItem {
    @Attribute(.unique) var todo_id: UUID
    var todo_name: String
    var is_exist_person: Bool
    var date_time: Date?
    var memo: String
    var no_of_path: Int
    var begin_time: Date?
    var end_time: Date?
    var type: String // 00: History, 10: To-do, 20: Server
    var is_completed: Bool = false
    var created_at: Int64
    
    // Coordinates (Integer storage x100,000)
    var int_lat: Int
    var int_long: Int
    
    var isSelected: Bool = false
    var source: String = "local"
    
    // Computed Properties
    var latitude: Double {
        get { Double(int_lat) / 100_000.0 }
        set { int_lat = Int((newValue * 100_000.0).rounded()) }
    }
    
    var longitude: Double {
        get { Double(int_long) / 100_000.0 }
        set { int_long = Int((newValue * 100_000.0).rounded()) }
    }
    
    var coordinate: CLLocationCoordinate2D {
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    
    var location: CLLocationCoordinate2D? {
        guard int_lat != 0 || int_long != 0 else { return nil }
        return coordinate
    }
    
    var latInt: Int { int_lat }
    var lonInt: Int { int_long }
    
    var isCompleted: Bool {
        get { is_completed }
        set { is_completed = newValue }
    }
    
    init(
        todo_id: UUID = UUID(),
        todo_name: String,
        is_exist_person: Bool = false,
        date_time: Date? = nil,
        memo: String = "",
        no_of_path: Int = 0,
        begin_time: Date? = nil,
        end_time: Date? = nil,
        type: String = "10",
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        is_completed: Bool = false,
        source: String = "local"
    ) {
        self.todo_id = todo_id
        self.todo_name = todo_name
        self.is_exist_person = is_exist_person
        self.date_time = date_time
        self.memo = memo
        self.no_of_path = no_of_path
        self.begin_time = begin_time
        self.end_time = end_time
        self.type = type
        self.created_at = Int64(Date().timeIntervalSince1970 * 1000)
        self.int_lat = Int((latitude * 100_000.0).rounded())
        self.int_long = Int((longitude * 100_000.0).rounded())
        self.is_completed = is_completed
        self.source = source
    }

    // [NEW] Integer-based Initializer (Updated with Time Fields)
    init(
        todo_id: UUID = UUID(),
        todo_name: String,
        no_of_path: Int = 0,
        type: String = "10",
        int_lat: Int,
        int_long: Int,
        begin_time: Date? = nil,
        end_time: Date? = nil,
        created_at: Int64? = nil
    ) {
        self.todo_id = todo_id
        self.todo_name = todo_name
        self.is_exist_person = false
        self.date_time = nil
        self.memo = ""
        self.no_of_path = no_of_path
        self.begin_time = begin_time
        self.end_time = end_time
        self.type = type
        self.created_at = created_at ?? Int64(Date().timeIntervalSince1970 * 1000)
        self.int_lat = int_lat
        self.int_long = int_long
        self.is_completed = false
        self.source = "local"
    }
}

// MARK: - 2. PathItem Table
@Model
final class PathItem {
    var todo_id: UUID
    var int_long: Int
    var int_lat: Int
    var time: Date = Date()
    
    init(todo_id: UUID, latitude: Double, longitude: Double, time: Date = Date()) {
        self.todo_id = todo_id
        self.int_lat = Int((latitude * 100_000.0).rounded())
        self.int_long = Int((longitude * 100_000.0).rounded())
        self.time = time
    }
    
    // [NEW] Integer-based Initializer
    init(todo_id: UUID, int_lat: Int, int_long: Int, time: Date = Date()) {
        self.todo_id = todo_id
        self.int_lat = int_lat
        self.int_long = int_long
        self.time = time
    }
    
    var coordinate: CLLocationCoordinate2D {
        return CLLocationCoordinate2D(
            latitude: Double(int_lat) / 100_000.0,
            longitude: Double(int_long) / 100_000.0
        )
    }
}



// MARK: - 3. Contact Table (Normalized)
@Model
final class Contact {
    @Attribute(.unique) var id: UUID
    var name: String
    var name_consonants: String
    var primary_phone: String
    var primary_email: String
    
    init(id: UUID = UUID(), name: String, name_consonants: String = "", primary_phone: String = "", primary_email: String = "") {
        self.id = id
        self.name = name
        self.name_consonants = name_consonants
        self.primary_phone = primary_phone
        self.primary_email = primary_email
    }
}

// MARK: - 4. ContactPhone Table
@Model
final class ContactPhone {
    @Attribute(.unique) var id: UUID
    var contact_id: UUID
    var type: String // mobile, home, fax
    var number: String
    var is_primary: Bool
    
    init(id: UUID = UUID(), contact_id: UUID, type: String = "mobile", number: String, is_primary: Bool = false) {
        self.id = id
        self.contact_id = contact_id
        self.type = type
        self.number = number
        self.is_primary = is_primary
    }
}

// MARK: - 5. ContactAddress Table
@Model
final class ContactAddress {
    @Attribute(.unique) var id: UUID
    var contact_id: UUID
    var type: String // home, work, etc
    var address_text: String
    var lat_int: Int
    var lng_int: Int
    
    init(id: UUID = UUID(), contact_id: UUID, type: String = "home", address_text: String = "", lat_int: Int = 0, lng_int: Int = 0) {
        self.id = id
        self.contact_id = contact_id
        self.type = type
        self.address_text = address_text
        self.lat_int = lat_int
        self.lng_int = lng_int
    }
}

// MARK: - 6. TodoContact Table (Bridge)
@Model
final class TodoContact {
    @Attribute(.unique) var id: UUID
    var todo_id: UUID
    var contact_id: UUID
    var role: String // owner, participant, watcher
    var status: String // accepted, pending
    var created_at: Int64
    
    init(id: UUID = UUID(), todo_id: UUID, contact_id: UUID, role: String = "participant", status: String = "pending") {
        self.id = id
        self.todo_id = todo_id
        self.contact_id = contact_id
        self.role = role
        self.status = status
        self.created_at = Int64(Date().timeIntervalSince1970 * 1000)
    }
}

// MARK: - Deprecated Models (To be removed after migration)
@Model
final class AddressBookItem {
    @Attribute(.unique) var address_id: UUID
    var last_name: String
    var first_name: String
    var name: String
    var name_consonants: String
    var phone_name1: String?
    var phone_name2: String?
    var phone_name3: String?
    var phone_name4: String?
    var phone_name5: String?
    var home_address: String
    var int_long_home: Int
    var int_lat_home: Int
    var company_address: String
    var company_int_long: Int
    var company_int_lat: Int
    init(name: String, lastName: String = "", firstName: String = "", consonants: String = "", homeAddress: String = "", companyAddress: String = "") {
        self.address_id = UUID(); self.name = name; self.last_name = lastName; self.first_name = firstName; self.name_consonants = consonants; self.home_address = homeAddress; self.int_long_home = 0; self.int_lat_home = 0; self.company_address = companyAddress; self.company_int_long = 0; self.company_int_lat = 0
    }
}

@Model
final class ContactItem {
    var todo_id: UUID
    var address_id: UUID?
    var name: String
    var p_name: String
    var int_long: Int?
    var int_lat: Int?
    init(todo_id: UUID, address_id: UUID? = nil, name: String, phoneNumber: String) {
        self.todo_id = todo_id; self.address_id = address_id; self.name = name; self.p_name = phoneNumber
    }
}

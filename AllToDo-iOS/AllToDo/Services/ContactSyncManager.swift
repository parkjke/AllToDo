import Foundation
import Contacts
import SwiftData

@MainActor
class ContactSyncManager {
    static let shared = ContactSyncManager()
    
    private init() {}
    
    func syncContacts(context: ModelContext) async {
        let store = CNContactStore()
        
        do {
            let status = CNContactStore.authorizationStatus(for: .contacts)
            if status == .notDetermined {
                _ = try await store.requestAccess(for: .contacts)
            } else if status == .denied || status == .restricted {
                print("ContactSyncManager: Access Denied")
                return
            }
            
            let keys = [
                CNContactGivenNameKey,
                CNContactFamilyNameKey,
                CNContactPhoneNumbersKey,
                CNContactEmailAddressesKey,
                CNContactPostalAddressesKey
            ] as [CNKeyDescriptor]
            
            let request = CNContactFetchRequest(keysToFetch: keys)
            
            // Collect contacts to sync
            var cnContacts: [CNContact] = []
            try store.enumerateContacts(with: request) { contact, _ in
                cnContacts.append(contact)
            }
            
            for cnContact in cnContacts {
                // Determine Name
                let lastName = cnContact.familyName
                let firstName = cnContact.givenName
                
                // For Korean naming: FamilyName + GivenName
                // For Western naming: GivenName + FamilyName
                // We'll use a simple heuristic or just join them
                let fullName = (lastName + firstName).trimmingCharacters(in: .whitespaces)
                let nameToUse = fullName.isEmpty ? (firstName + " " + lastName).trimmingCharacters(in: .whitespaces) : fullName
                
                guard !nameToUse.isEmpty else { continue }
                
                // Identify by name and primary phone to avoid duplicates
                let primaryPhone = cnContact.phoneNumbers.first?.value.stringValue ?? ""
                
                let predicate = #Predicate<Contact> { contact in
                    contact.name == nameToUse && contact.primary_phone == primaryPhone
                }
                
                let descriptor = FetchDescriptor<Contact>(predicate: predicate)
                let existing = try context.fetch(descriptor)
                
                if existing.isEmpty {
                    // Create New Contact
                    let consonants = KoreanUtils.getChoseong(nameToUse)
                    let primaryEmail = cnContact.emailAddresses.first?.value as String? ?? ""
                    
                    let newContact = Contact(
                        name: nameToUse,
                        name_consonants: consonants,
                        primary_phone: primaryPhone,
                        primary_email: primaryEmail
                    )
                    context.insert(newContact)
                    
                    // Sync Phone Numbers
                    for labeledValue in cnContact.phoneNumbers {
                        let label = labeledValue.label ?? "_$!<Mobile>!$_"
                        let type = CNLabeledValue<NSString>.localizedString(forLabel: label)
                        let phoneNum = ContactPhone(
                            contact_id: newContact.id,
                            type: type,
                            number: labeledValue.value.stringValue,
                            is_primary: labeledValue == cnContact.phoneNumbers.first
                        )
                        context.insert(phoneNum)
                    }
                    
                    // Sync Addresses
                    for labeledValue in cnContact.postalAddresses {
                        let label = labeledValue.label ?? "_$!<Home>!$_"
                        let type = CNLabeledValue<NSString>.localizedString(forLabel: label)
                        let addr = labeledValue.value
                        let fullAddr = [addr.street, addr.city, addr.state, addr.postalCode]
                            .filter { !$0.isEmpty }
                            .joined(separator: " ")
                        
                        let contactAddr = ContactAddress(
                            contact_id: newContact.id,
                            type: type,
                            address_text: fullAddr
                        )
                        context.insert(contactAddr)
                    }
                }
            }
            
            try context.save()
            print("ContactSyncManager: Sync Completed. (\(cnContacts.count) contacts checked)")
            
        } catch {
            print("ContactSyncManager: Sync Failed: \(error)")
        }
    }
}

package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

@Entity(tableName = "address_book")
data class AddressBookItem(
    @PrimaryKey
    @ColumnInfo(name = "address_id")
    val address_id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "last_name")
    val last_name: String? = null,
    
    @ColumnInfo(name = "first_name")
    val first_name: String? = null,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "name_consonants")
    val name_consonants: String? = null,
    
    @ColumnInfo(name = "phone_name1") val phone_name1: String? = null,
    @ColumnInfo(name = "phone_name2") val phone_name2: String? = null,
    @ColumnInfo(name = "phone_name3") val phone_name3: String? = null,
    @ColumnInfo(name = "phone_name4") val phone_name4: String? = null,
    @ColumnInfo(name = "phone_name5") val phone_name5: String? = null,
    
    @ColumnInfo(name = "home_address")
    val home_address: String? = null,
    
    @ColumnInfo(name = "int_long_home")
    val int_long_home: Int? = null,
    
    @ColumnInfo(name = "int_lat_home")
    val int_lat_home: Int? = null,
    
    @ColumnInfo(name = "company_address")
    val company_address: String? = null,
    
    @ColumnInfo(name = "company_int_long")
    val company_int_long: Int? = null,
    
    @ColumnInfo(name = "company_int_lat")
    val company_int_lat: Int? = null
)

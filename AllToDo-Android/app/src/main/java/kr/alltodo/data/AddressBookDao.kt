package kr.alltodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressBookDao {
    @Query("SELECT * FROM address_book ORDER BY name ASC")
    fun getAll(): Flow<List<AddressBookItem>>

    @Query("SELECT * FROM address_book WHERE name_consonants LIKE :query || '%' OR name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<AddressBookItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AddressBookItem)

    @Update
    suspend fun update(item: AddressBookItem)

    @Delete
    suspend fun delete(item: AddressBookItem)
}

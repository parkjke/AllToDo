package kr.alltodo.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Singleton
class LocationRepository @Inject constructor(private val locationDao: LocationDao) {
    suspend fun saveLocation(latitude: Double, longitude: Double) {
        val location = LocationEntity(
            int_lat = (latitude * 100_000).toInt(),
            int_long = (longitude * 100_000).toInt(),
            timestamp = System.currentTimeMillis()
        )
        locationDao.insertLocation(location)
    }

    fun getTodayLocations(): Flow<List<LocationEntity>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000
        
        return locationDao.getLocationsForDay(startOfDay, endOfDay)
    }

    suspend fun delete(location: LocationEntity) {
        locationDao.delete(location)
    }
}

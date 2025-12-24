package kr.alltodo.di

import android.content.Context
import androidx.room.Room
import kr.alltodo.data.AppDatabase
import kr.alltodo.data.TodoDao
import kr.alltodo.data.LocationDao
import kr.alltodo.data.GpsAuthDao
import kr.alltodo.data.LocationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "alltodo_database"
        ).fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    @Singleton
    fun provideTodoDao(database: AppDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: AppDatabase): LocationDao {
        return database.locationDao()
    }

    @Provides
    @Singleton
    fun provideUserLogDao(database: AppDatabase): kr.alltodo.data.UserLogDao {
        return database.userLogDao()
    }

    @Provides
    @Singleton
    fun provideGpsAuthDao(database: AppDatabase): GpsAuthDao {
        return database.gpsAuthDao()
    }
}

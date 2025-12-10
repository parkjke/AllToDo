package com.example.alltodo.di

import android.content.Context
import androidx.room.Room
import com.example.alltodo.data.AppDatabase
import com.example.alltodo.data.TodoDao
import com.example.alltodo.data.LocationDao
import com.example.alltodo.data.LocationRepository
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
    fun provideUserLogDao(database: AppDatabase): com.example.alltodo.data.UserLogDao {
        return database.userLogDao()
    }
}

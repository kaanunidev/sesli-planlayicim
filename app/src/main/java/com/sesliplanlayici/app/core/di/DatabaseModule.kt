package com.sesliplanlayici.app.core.di

import android.content.Context
import androidx.room.Room
import com.sesliplanlayici.app.core.data.RoomTaskRepository
import com.sesliplanlayici.app.core.data.db.AppDatabase
import com.sesliplanlayici.app.core.data.db.TaskDao
import com.sesliplanlayici.app.core.domain.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideTaskDao(database: AppDatabase): TaskDao = database.taskDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTaskRepository(impl: RoomTaskRepository): TaskRepository
}

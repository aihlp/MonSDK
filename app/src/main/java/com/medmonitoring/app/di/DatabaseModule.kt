package com.medmonitoring.app.di

import android.content.Context
import androidx.room.Room
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.storage.db.DatabaseMigrations
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.repository.EventRepositoryImpl
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
    fun provideDatabase(@ApplicationContext context: Context): MedDatabase {
        return Room.databaseBuilder(
            context,
            MedDatabase::class.java,
            "med_database"
        )
            .addMigrations(*DatabaseMigrations.ALL)
            .enableMultiInstanceInvalidation()
            .build()
    }

    @Provides
    @Singleton
    fun provideEventRepository(impl: EventRepositoryImpl): EventRepository {
        return impl
    }
}

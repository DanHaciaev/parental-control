package com.teo.child.di

import android.content.Context
import androidx.room.Room
import com.teo.child.data.local.AppDatabase
import com.teo.child.data.local.HourlyUsageDao
import com.teo.child.data.local.RuleCacheDao
import com.teo.child.data.local.ScheduleCacheDao
import com.teo.child.data.local.UsageDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "parental_control.db")
            // No production data to preserve yet (still active dev/testing) — revisit with a real
            // Migration + exportSchema=true before this app carries real families' data long-term.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideUsageDao(database: AppDatabase): UsageDao = database.usageDao()

    @Provides
    fun provideRuleCacheDao(database: AppDatabase): RuleCacheDao = database.ruleCacheDao()

    @Provides
    fun provideHourlyUsageDao(database: AppDatabase): HourlyUsageDao = database.hourlyUsageDao()

    @Provides
    fun provideScheduleCacheDao(database: AppDatabase): ScheduleCacheDao = database.scheduleCacheDao()
}

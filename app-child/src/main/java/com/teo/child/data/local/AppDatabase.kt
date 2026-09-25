package com.teo.child.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UsageEntity::class, RuleCacheEntity::class, HourlyUsageEntity::class, ScheduleCacheEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun ruleCacheDao(): RuleCacheDao
    abstract fun hourlyUsageDao(): HourlyUsageDao
    abstract fun scheduleCacheDao(): ScheduleCacheDao
}

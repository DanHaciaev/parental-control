package com.teo.child.monitor

import com.teo.child.data.local.ScheduleCacheDao
import com.teo.child.data.local.ScheduleCacheEntity
import com.teo.core.model.Schedule
import com.teo.core.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private fun Schedule.toEntity() = ScheduleCacheEntity(
    id = id,
    name = name,
    startMinutes = startMinutes,
    endMinutes = endMinutes,
    blockAllApps = blockAllApps,
    blockedPackageNamesCsv = blockedPackageNames.joinToString(","),
    daysOfWeekCsv = daysOfWeek.joinToString(","),
    enabled = enabled
)

/** Keeps the Room schedule mirror in sync with Firestore for as long as the monitor service is alive. */
@Singleton
class ScheduleSyncListener @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleCacheDao: ScheduleCacheDao
) {
    fun start(familyId: String, scope: CoroutineScope) {
        scope.launch {
            while (true) {
                try {
                    scheduleRepository.observeSchedules(familyId).collect { schedules ->
                        scheduleCacheDao.replaceAll(schedules.map { it.toEntity() })
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}

package com.teo.child.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.LocalUsageStore
import com.teo.core.model.HourlyUsageEntry
import com.teo.core.repository.HourlyUsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Batches local Room hourly-usage rows up to Firestore every ~15 min, same as [UsageUploadWorker]. */
@HiltWorker
class HourlyUsageUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localUsageStore: LocalUsageStore,
    private val hourlyUsageRepository: HourlyUsageRepository,
    private val childPreferences: ChildPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val familyId = childPreferences.familyId.first() ?: return Result.success()
        val pending = localUsageStore.getPendingHourlyUpload()
        if (pending.isEmpty()) return Result.success()

        return try {
            val entries = pending.map {
                HourlyUsageEntry(
                    dateKey = it.dateKey,
                    hour = it.hour,
                    minutesUsed = it.minutesUsed
                )
            }
            hourlyUsageRepository.uploadHourlyUsage(familyId, entries)
            localUsageStore.markHourlyUploaded(pending)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

package com.teo.child.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.teo.child.data.ChildPreferences
import com.teo.child.data.local.LocalUsageStore
import com.teo.core.model.UsageEntry
import com.teo.core.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Batches local Room usage rows up to Firestore every ~15 min — the WorkManager floor, and plenty for this use case. */
@HiltWorker
class UsageUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val localUsageStore: LocalUsageStore,
    private val usageRepository: UsageRepository,
    private val childPreferences: ChildPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val familyId = childPreferences.familyId.first() ?: return Result.success()
        val pending = localUsageStore.getPendingUpload()
        if (pending.isEmpty()) return Result.success()

        return try {
            val entries = pending.map {
                UsageEntry(
                    dateKey = it.dateKey,
                    packageName = it.packageName,
                    minutesUsedToday = it.minutesUsedToday
                )
            }
            usageRepository.uploadUsage(familyId, entries)
            localUsageStore.markUploaded(pending)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

package com.teo.child.work

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.teo.child.data.ChildPreferences
import com.teo.core.model.LocationPoint
import com.teo.core.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** One fresh fix every ~15 min (the WorkManager floor) — plenty for a family-safety use case. */
@HiltWorker
class LocationUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val childPreferences: ChildPreferences
) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val familyId = childPreferences.familyId.first() ?: return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        return try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .build()
            // A fresh fix can time out indoors or when the device's location mode is GPS-only —
            // fall back to the last cached fix (still recent enough for a family-safety map) before giving up.
            val location = client.getCurrentLocation(request, null).await()
                ?: client.lastLocation.await()
                ?: return Result.retry()

            locationRepository.updateLocation(
                familyId,
                LocationPoint(lat = location.latitude, lng = location.longitude, accuracy = location.accuracy)
            )
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Location upload failed, will retry", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "LocationUploadWorker"
    }
}

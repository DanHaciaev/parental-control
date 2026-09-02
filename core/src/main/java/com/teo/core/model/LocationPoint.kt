package com.teo.core.model

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class LocationPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val accuracy: Float = 0f,
    @ServerTimestamp val capturedAt: Date? = null
)

data class GeofenceZone(
    val id: String = "",
    val label: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val radiusMeters: Float = 150f,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
)

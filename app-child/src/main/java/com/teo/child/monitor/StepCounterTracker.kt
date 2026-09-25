package com.teo.child.monitor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.teo.child.data.ChildPreferences
import com.teo.core.repository.SkillRepository
import com.teo.core.util.DayBoundary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto-tracks the "Шаги" (steps) skill from the phone's built-in step counter instead of the
 * child manually tapping "+1" — TYPE_STEP_COUNTER reports a cumulative total since the device's
 * last boot, so a persisted per-day baseline (see ChildPreferences) turns that into "steps today".
 */
@Singleton
class StepCounterTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val childPreferences: ChildPreferences,
    private val skillRepository: SkillRepository
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var familyId: String? = null
    @Volatile private var timezone: String = "Europe/Moscow"
    @Volatile private var baselineSteps: Long = -1
    @Volatile private var baselineDateKey: String = ""
    @Volatile private var lastWrittenSteps: Int = -1

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val total = event.values[0].toLong()
            scope.launch { handleReading(total) }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(familyId: String, timezone: String) {
        this.familyId = familyId
        this.timezone = timezone
        val sensor = stepSensor ?: return
        scope.launch {
            val (savedBaseline, savedDateKey) = childPreferences.getStepBaseline()
            val today = DayBoundary.todayKey(timezone)
            if (savedDateKey == today) {
                baselineSteps = savedBaseline
                baselineDateKey = savedDateKey
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        scope.cancel()
    }

    private suspend fun handleReading(totalSinceBoot: Long) {
        val fid = familyId ?: return
        val dateKey = DayBoundary.todayKey(timezone)

        // New day, first-ever reading, or the counter reset (device rebooted) — re-baseline to
        // "zero steps counted so far today", losing only whatever steps happened before this
        // point (unavoidable without a pre-existing baseline for a day that just started).
        if (baselineDateKey != dateKey || totalSinceBoot < baselineSteps) {
            baselineSteps = totalSinceBoot
            baselineDateKey = dateKey
            childPreferences.setStepBaseline(baselineSteps, dateKey)
            lastWrittenSteps = -1
        }

        val todaySteps = (totalSinceBoot - baselineSteps).toInt().coerceAtLeast(0)
        if (todaySteps == lastWrittenSteps) return
        lastWrittenSteps = todaySteps
        runCatching { skillRepository.setProgress(fid, STEPS_SKILL_ID, dateKey, todaySteps) }
    }

    companion object {
        const val STEPS_SKILL_ID = "steps"
    }
}

package com.teo.child.sos

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate hard-shake-several-times gesture as an SOS trigger. Chosen over hardware
 * volume-button interception (tried first) because AccessibilityService.onKeyEvent for volume keys
 * is unreliable in practice — Samsung and other OEMs often handle those keys at a level below where
 * accessibility services can see them, regardless of the requested capability. The accelerometer has
 * no such OS/OEM gatekeeping and works the same on every device.
 */
class ShakeDetector(private val onShakeDetected: () -> Unit) : SensorEventListener {
    private var shakeCount = 0
    private var lastShakeAtMs = 0L
    private var windowStartAtMs = 0L
    private var cooldownUntilMs = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
        if (magnitude <= SHAKE_THRESHOLD_MS2) return

        val now = System.currentTimeMillis()
        if (now < cooldownUntilMs) return
        if (now - lastShakeAtMs < MIN_SHAKE_INTERVAL_MS) return // debounce a single physical jolt into one count
        lastShakeAtMs = now

        if (now - windowStartAtMs > SHAKE_WINDOW_MS) {
            windowStartAtMs = now
            shakeCount = 0
        }
        shakeCount++
        if (shakeCount >= REQUIRED_SHAKES) {
            shakeCount = 0
            cooldownUntilMs = now + TRIGGER_COOLDOWN_MS
            onShakeDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        // Above-gravity acceleration magnitude a deliberate hard shake produces — well above what
        // normal handling, walking, or the phone bouncing in a pocket/bag generates.
        private const val SHAKE_THRESHOLD_MS2 = 18.0
        private const val MIN_SHAKE_INTERVAL_MS = 250L
        private const val SHAKE_WINDOW_MS = 1500L
        private const val REQUIRED_SHAKES = 4
        private const val TRIGGER_COOLDOWN_MS = 30_000L
    }
}

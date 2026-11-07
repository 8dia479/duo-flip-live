package org.duofold.live

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock

/** Flip 6 runtime: HAL hinge samples plus a capture-only binder; never changes display state. */
internal class Flip6LiveRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val estimator = FlipSamsungHingeEstimator()
    private val androidSensorEstimator = FlipSamsungHingeEstimator()
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val hingeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    private var source: FlipSamsungAngleSource? = null
    private var sensorRegistered = false
    @Volatile private var lastAndroidSensorAtMs = 0L
    private var halSuppressedByAndroidSensor = false

    private val hingeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val raw = event.values.firstOrNull() ?: return
            if (!raw.isFinite() || raw !in 0f..180f) return
            val stamp = event.timestamp.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos()
            lastAndroidSensorAtMs = SystemClock.elapsedRealtime()
            halSuppressedByAndroidSensor = true
            androidSensorEstimator.add(raw, stamp, FlipSensorHalParser.SOURCE_FOLDING)
            val value = androidSensorEstimator.estimate(SystemClock.elapsedRealtimeNanos())
            if (value.isFinite()) LiveAngles.publishExternalAngle(value)
            source?.updatePanelAngle(raw)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (source != null) return
        estimator.reset()
        androidSensorEstimator.reset()
        lastAndroidSensorAtMs = 0L
        halSuppressedByAndroidSensor = false
        val next = FlipSamsungAngleSource(
            appContext,
            onReading = { reading ->
                val sensorAge = SystemClock.elapsedRealtime() - lastAndroidSensorAtMs
                if (lastAndroidSensorAtMs == 0L || sensorAge > ANDROID_SENSOR_GRACE_MS) {
                    if (halSuppressedByAndroidSensor) {
                        estimator.reset()
                        halSuppressedByAndroidSensor = false
                    }
                    estimator.add(reading.angle, reading.timestampNanos, reading.source)
                    val value = estimator.estimate(SystemClock.elapsedRealtimeNanos())
                    if (value.isFinite()) LiveAngles.publishExternalAngle(value)
                }
            },
            onStatus = { message ->
                if (!LiveAngles.fresh()) LiveAngles.status = message
            },
            onDiagnostics = { message -> LiveAngles.handoffStatus = message },
            onCaptureBinder = { binder: IBinder -> LiveAngles.setExternalCaptureBinder(binder) }
        )
        source = next
        next.start()
        val sensor = hingeSensor
        sensorRegistered = sensor != null && sensorManager?.registerListener(
            hingeListener, sensor, SensorManager.SENSOR_DELAY_GAME
        ) == true
        LiveAngles.readerDiagnostics = if (sensorRegistered) {
            "Android TYPE_HINGE_ANGLE active; Samsung HAL retained as fallback"
        } else if (sensor == null) {
            "Android TYPE_HINGE_ANGLE unavailable; using Samsung HAL log reader"
        } else {
            "Android TYPE_HINGE_ANGLE registration failed; using Samsung HAL log reader"
        }
        RecoveryLog.add(LiveAngles.readerDiagnostics)
    }

    private companion object { const val ANDROID_SENSOR_GRACE_MS = 500L }

    fun stop(message: String) {
        if (sensorRegistered) sensorManager?.unregisterListener(hingeListener)
        sensorRegistered = false
        lastAndroidSensorAtMs = 0L
        halSuppressedByAndroidSensor = false
        androidSensorEstimator.reset()
        val previous = source
        source = null
        previous?.stop()
        estimator.reset()
        LiveAngles.setExternalCaptureBinder(null)
        LiveAngles.clearExternalAngle(message)
    }
}

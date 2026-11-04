package org.duofold.live

import android.content.Context
import android.os.IBinder
import android.os.SystemClock

/** Flip 6 runtime: HAL hinge samples plus a capture-only binder; never changes display state. */
internal class Flip6LiveRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val estimator = FlipSamsungHingeEstimator()
    private var source: FlipSamsungAngleSource? = null

    fun start() {
        if (source != null) return
        estimator.reset()
        val next = FlipSamsungAngleSource(
            appContext,
            onReading = { reading ->
                estimator.add(reading.angle, reading.timestampNanos, reading.source)
                val value = estimator.estimate(SystemClock.elapsedRealtimeNanos())
                if (value.isFinite()) LiveAngles.publishExternalAngle(value)
            },
            onStatus = { message ->
                if (!LiveAngles.fresh()) LiveAngles.status = message
            },
            onDiagnostics = {},
            onCaptureBinder = { binder: IBinder -> LiveAngles.setExternalCaptureBinder(binder) }
        )
        source = next
        next.start()
    }

    fun stop(message: String) {
        val previous = source
        source = null
        previous?.stop()
        estimator.reset()
        LiveAngles.setExternalCaptureBinder(null)
        LiveAngles.clearExternalAngle(message)
    }
}

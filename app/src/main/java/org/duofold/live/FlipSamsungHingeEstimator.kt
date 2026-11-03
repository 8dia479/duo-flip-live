package org.duofold.live

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * Interpolates between confirmed Samsung HAL samples.
 *
 * A large one-frame discontinuity is held as a candidate until the next
 * same-source sample confirms it. The raw reader remains untouched; only the
 * animation estimate is protected from transient spikes.
 */
internal class FlipSamsungHingeEstimator {
    private class Track(val step: Float) {
        var angle = Float.NaN
        var nanos = 0L
        var speed = Float.NaN
        var live = false
        var pendingAngle = Float.NaN
        var pendingNanos = 0L

        fun clearPending() {
            pendingAngle = Float.NaN
            pendingNanos = 0L
        }
    }

    private val tracks = arrayOf(Track(11f), Track(10f))
    private var lastAngle = Float.NaN
    private var lastNanos = 0L
    private var direction = 0
    private var velocity = 0f
    private var held = Float.NaN

    fun add(angle: Float, sampleNanos: Long, source: Int) {
        if (!angle.isFinite() || angle !in 0f..180f || sampleNanos <= 0L) return
        if (source == FlipSensorHalParser.SOURCE_SHUT) {
            reset(0f, sampleNanos)
            return
        }
        val track = tracks.getOrNull(source) ?: run {
            reset(angle, sampleNanos)
            return
        }
        if (track.live && sampleNanos <= track.nanos) return

        val previous = track.angle
        val delta = if (previous.isFinite()) angle - previous else 0f
        val dtNanos = if (track.live) sampleNanos - track.nanos else Long.MAX_VALUE

        // A real fast hinge movement is confirmed by the following sample.
        // An isolated jump which immediately returns near the last accepted
        // angle is discarded before it can affect the animation.
        if (track.pendingAngle.isFinite()) {
            val pendingAge = sampleNanos - track.pendingNanos
            if (pendingAge in 0L..PENDING_TIMEOUT_NANOS) {
                val pendingDelta = track.pendingAngle - previous
                val confirmsCandidate =
                    abs(angle - track.pendingAngle) <= CONFIRM_RADIUS_DEG ||
                    (pendingDelta != 0f &&
                        sign(angle - previous) == sign(pendingDelta) &&
                        abs(delta) >= abs(pendingDelta) - CONTINUATION_TOLERANCE_DEG)
                val returnedToBaseline = abs(delta) <= SPIKE_RETURN_RADIUS_DEG
                if (confirmsCandidate) {
                    track.clearPending()
                } else if (returnedToBaseline) {
                    track.clearPending()
                } else {
                    track.pendingAngle = angle
                    track.pendingNanos = sampleNanos
                    return
                }
            } else {
                track.clearPending()
            }
        } else if (previous.isFinite() &&
            dtNanos in 1L..SPIKE_WINDOW_NANOS &&
            abs(delta) >= SPIKE_THRESHOLD_DEG
        ) {
            track.pendingAngle = angle
            track.pendingNanos = sampleNanos
            return
        }

        if (lastNanos != 0L && sampleNanos - lastNanos > GAP_NANOS) beginMovement()

        val moved = if (previous.isFinite()) angle - previous else 0f
        val reversed = moved != 0f && direction != 0 && sign(moved).toInt() != direction
        if (reversed) beginMovement()

        if (previous.isFinite() && moved != 0f) {
            val dt = (sampleNanos - track.nanos) / 1e9f
            track.speed = if (track.live && dt in MIN_DT_S..MAX_STEP_S) moved / dt else Float.NaN
            direction = sign(moved).toInt()
        }
        track.angle = angle
        track.nanos = sampleNanos
        track.live = true

        val speeds = tracks.filter { it.live && it.speed.isFinite() }.map { it.speed }
        velocity = if (speeds.isNotEmpty()) speeds.average().toFloat()
            else crossSpeed(sampleNanos)
        if (direction == 0 && velocity != 0f) direction = sign(velocity).toInt()
        if (direction != 0 && sign(velocity).toInt() != direction) velocity = 0f

        lastAngle = angle
        lastNanos = sampleNanos
    }

    fun reset() {
        beginMovement()
        lastAngle = Float.NaN
        lastNanos = 0L
        held = Float.NaN
    }

    private fun reset(angle: Float, sampleNanos: Long) {
        beginMovement()
        lastAngle = angle
        lastNanos = sampleNanos
        held = angle
    }

    fun estimate(nowNanos: Long): Float {
        if (!lastAngle.isFinite()) return Float.NaN
        if (velocity == 0f || direction == 0) return hold(lastAngle)
        if (nowNanos < lastNanos || nowNanos - lastNanos > MAX_COAST_NANOS) {
            return hold(held.takeIf { it.isFinite() } ?: lastAngle)
        }

        var sum = 0f
        var count = 0
        for (track in tracks) {
            if (!track.live) continue
            sum += track.angle + velocity * ((nowNanos - track.nanos) / 1e9f)
            count++
        }
        val glide = ((if (count > 0) sum / count else lastAngle) - lastAngle) * direction
        val slack = abs(velocity) * SLACK_S
        var room = Float.MAX_VALUE
        for (track in tracks) {
            if (!track.angle.isFinite()) continue
            room = minOf(room, (track.angle - lastAngle) * direction + track.step + slack)
        }
        val x = glide.coerceAtLeast(0f)
        val limit = room.coerceAtLeast(0f)
        val value = lastAngle + direction * softLimit(x, limit)
        val monotonic = if (held.isFinite()) {
            if (direction > 0) maxOf(value, held) else minOf(value, held)
        } else value
        return hold(monotonic.coerceIn(0f, 180f))
    }

    private fun softLimit(x: Float, limit: Float): Float {
        val knee = limit * CRUISE
        if (x <= knee || limit <= 0f) return minOf(x, limit)
        val tail = limit - knee
        return knee + tail * (1f - exp(-(x - knee) / tail))
    }

    private fun hold(value: Float): Float {
        held = value
        return value
    }

    private fun beginMovement() {
        for (track in tracks) {
            track.live = false
            track.speed = Float.NaN
            track.clearPending()
        }
        direction = 0
        velocity = 0f
        held = Float.NaN
    }

    private fun crossSpeed(nowNanos: Long): Float {
        val a = tracks[0]
        val b = tracks[1]
        if (!a.live || !b.live || a.nanos == b.nanos) return 0f
        val older = if (a.nanos < b.nanos) a else b
        val newer = if (a.nanos < b.nanos) b else a
        val dt = (newer.nanos - older.nanos) / 1e9f
        if (dt !in MIN_DT_S..MAX_STEP_S || nowNanos < newer.nanos) return 0f
        val speed = (newer.angle - older.angle) / dt
        return if (abs(newer.angle - older.angle) >= MIN_CROSS_DEG) speed else 0f
    }

    private companion object {
        const val GAP_NANOS = 1_500_000_000L
        const val MAX_STEP_S = 1.5f
        const val MIN_DT_S = 0.02f
        const val MIN_CROSS_DEG = 3f
        const val CRUISE = 0.7f
        const val SLACK_S = 0.06f
        const val MAX_COAST_NANOS = 1_500_000_000L
        const val SPIKE_WINDOW_NANOS = 180_000_000L
        const val PENDING_TIMEOUT_NANOS = 280_000_000L
        const val SPIKE_THRESHOLD_DEG = 18f
        const val CONFIRM_RADIUS_DEG = 11f
        const val CONTINUATION_TOLERANCE_DEG = 8f
        const val SPIKE_RETURN_RADIUS_DEG = 10f
    }
}

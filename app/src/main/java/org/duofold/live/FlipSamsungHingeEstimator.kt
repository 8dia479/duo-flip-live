package org.duofold.live

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/**
 * Estimates motion only between real Samsung HAL readings.
 *
 * Raw samples remain untouched and are shown separately. The 11°/10° step
 * limits were observed on a different Galaxy Fold model; they are provisional
 * on Flip 6 and must be checked against its diagnostic CSV.
 */
internal class FlipSamsungHingeEstimator {
    private class Track(val step: Float) {
        var angle = Float.NaN
        var nanos = 0L
        var speed = Float.NaN
        var live = false
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
        if (lastNanos != 0L && sampleNanos - lastNanos > GAP_NANOS) beginMovement()

        val previous = track.angle
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
    }
}

package org.duofold.live

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import rikka.shizuku.Shizuku

/** Reads Samsung's hinge-only sensor HAL log stream through an authorised Shizuku user service. */
internal class FlipSamsungAngleSource(
    context: Context,
    private val onReading: (Reading) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onDiagnostics: (String) -> Unit,
    private val onCaptureBinder: (IBinder) -> Unit = {},
) {
    data class Reading(
        val angle: Float,
        val timestampNanos: Long,
        val source: Int,
        val sequence: Long,
    ) {
        val sourceName: String
            get() = when (source) {
                FlipSensorHalParser.SOURCE_FOLDING -> "folding_angle"
                FlipSensorHalParser.SOURCE_LID -> "lid_angle_fusion"
                FlipSensorHalParser.SOURCE_SHUT -> "Hall device_mode"
                else -> "unknown"
            }
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var args: Shizuku.UserServiceArgs? = null
    private var binder: IBinder? = null
    private var binding = false
    @Volatile private var active = false
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var lastSequence = 0L
    private var lastState = ""
    private var startedAtMs = 0L
    private var lastStatusAtMs = 0L
    private var lastDiagnostics = ""
    @Volatile private var pendingPanelAngle = Float.NaN
    private val panelStateUpdate = Runnable { sendPanelStateAngle(pendingPanelAngle) }
    private val timeout = Runnable {
        if (active && binding && binder == null) {
            binding = false
            active = false
            onStatus(appContext.getString(R.string.flip_hal_bind_timeout))
            releaseBinding()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            main.removeCallbacks(timeout)
            binding = false
            if (!active) {
                binder = service
                releaseBinding()
                return
            }
            binder = service
            ensureWorker()
            worker?.post {
                val started = transact(FlipSensorHalReader.CMD_START) != null
                val capture = if (started) requestCaptureBinder() else null
                main.post {
                    if (!active) return@post
                    if (capture != null) onCaptureBinder(capture)
                    onStatus(appContext.getString(
                        if (started) R.string.flip_hal_waiting else R.string.flip_hal_start_failed
                    ))
                }
                if (started) {
                    if (pendingPanelAngle.isFinite()) sendPanelStateAngle(pendingPanelAngle)
                    worker?.post(poll)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            binder = null
            binding = false
            val wasActive = active
            active = false
            main.removeCallbacks(timeout)
            if (wasActive) onStatus(appContext.getString(R.string.flip_hal_disconnected))
            releaseBinding()
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active || binder == null) return
            val result = transact(FlipSensorHalReader.CMD_READ)
            if (result == null) {
                main.post { if (active) onStatus(appContext.getString(R.string.flip_hal_read_failed)) }
                worker?.postDelayed(this, POLL_INTERVAL_MS)
                return
            }
            val diagnostics = buildString {
                append(result.getString("recentLines").orEmpty())
                val panelState = result.getString("panelState").orEmpty()
                if (panelState.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append("Panel state: ").append(panelState)
                }
            }
            if (diagnostics.isNotBlank() && diagnostics != lastDiagnostics) {
                lastDiagnostics = diagnostics
                main.post { if (active) onDiagnostics(diagnostics) }
            }
            val state = result.getString("state", "unknown")
            val logLines = result.getLong("logLines")
            val parsedLines = result.getLong("parsedLines")
            val staleLines = result.getLong("staleLines")
            val ageMs = result.getLong("ageMs", -1L)
            val nowMs = SystemClock.elapsedRealtime()
            val message = when {
                state.startsWith("reader error") -> appContext.getString(R.string.flip_hal_logcat_error)
                state == "log reader ended" -> appContext.getString(R.string.flip_hal_logcat_ended)
                parsedLines > 0L -> appContext.getString(
                    R.string.flip_hal_stats, parsedLines, logLines, ageMs.coerceAtLeast(0L)
                )
                logLines > 0L -> appContext.getString(
                    R.string.flip_hal_unparsed, logLines, staleLines
                )
                state == "listening" && nowMs - startedAtMs >= NO_LOG_NOTICE_MS ->
                    appContext.getString(R.string.flip_hal_no_lines)
                state == "listening" -> appContext.getString(R.string.flip_hal_waiting)
                else -> null
            }
            val stateChanged = state != lastState
            if (message != null && (stateChanged || nowMs - lastStatusAtMs >= STATUS_INTERVAL_MS)) {
                lastState = state
                lastStatusAtMs = nowMs
                main.post { if (active) onStatus(message) }
            }

            val sequence = result.getLong("sequence")
            val angle = result.getFloat("angle", Float.NaN)
            val timestamp = result.getLong("timestampNanos")
            val source = result.getInt("source", -1)
            if (sequence > lastSequence && angle.isFinite() && timestamp > 0L) {
                lastSequence = sequence
                val reading = Reading(angle, timestamp, source, sequence)
                main.post { if (active) onReading(reading) }
            }
            worker?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    fun start() {
        if (active || binding) return
        when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> {
                onStatus(appContext.getString(R.string.flip_hal_shizuku_off))
                return
            }
            runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                onStatus(appContext.getString(R.string.flip_hal_permission_needed))
                return
            }
        }
        active = true
        binding = true
        lastSequence = 0L
        lastState = ""
        lastDiagnostics = ""
        startedAtMs = SystemClock.elapsedRealtime()
        lastStatusAtMs = 0L
        onStatus(appContext.getString(R.string.flip_hal_connecting))
        val nextArgs = Shizuku.UserServiceArgs(
            ComponentName(appContext, FlipSensorHalReader::class.java)
        ).daemon(false).processNameSuffix("flip_sensor_hal")
            .debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
        args = nextArgs
        main.postDelayed(timeout, BIND_TIMEOUT_MS)
        try {
            Shizuku.bindUserService(nextArgs, connection)
        } catch (error: Exception) {
            main.removeCallbacks(timeout)
            binding = false
            active = false
            onStatus(appContext.getString(R.string.flip_hal_bind_failed))
            releaseBinding()
            Log.w(TAG, "Could not bind Samsung hinge reader", error)
        }
    }

    /** Keeps the Shizuku device-state controller in sync with the public Android hinge sensor. */
    fun updatePanelAngle(angle: Float) {
        if (!angle.isFinite() || angle !in 0f..180f) return
        pendingPanelAngle = angle
        val currentWorker = worker ?: return
        currentWorker.removeCallbacks(panelStateUpdate)
        currentWorker.post(panelStateUpdate)
    }

    private fun sendPanelStateAngle(angle: Float) {
        if (!angle.isFinite() || angle !in 0f..180f) return
        val remote = binder ?: return
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(FlipSensorHalReader.DESCRIPTOR)
            data.writeFloat(angle)
            if (!remote.transact(FlipSensorHalReader.CMD_UPDATE_PANEL_ANGLE, data, reply, 0)) return
            reply.readException()
        } catch (error: Exception) {
            Log.w(TAG, "Could not forward Android hinge sensor angle", error)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun stop() {
        main.removeCallbacks(timeout)
        val wasBound = binder != null || binding
        active = false
        val currentWorker = worker
        if (currentWorker != null && binder != null) {
            currentWorker.post {
                transact(FlipSensorHalReader.CMD_STOP)
                main.post { releaseBinding() }
            }
        } else if (wasBound) {
            releaseBinding()
        }
        currentWorker?.removeCallbacks(poll)
    }

    private fun ensureWorker() {
        if (worker != null) return
        val thread = HandlerThread("duo-flip-hal-poll")
        thread.start()
        workerThread = thread
        worker = Handler(thread.looper)
    }

    private fun requestCaptureBinder(): IBinder? {
        val remote = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FlipSensorHalReader.DESCRIPTOR)
            if (!remote.transact(FlipSensorHalReader.CMD_CAPTURE_BINDER, data, reply, 0)) return null
            reply.readException()
            reply.readStrongBinder()
        } catch (error: Exception) {
            Log.w(TAG, "Could not obtain capture-only binder", error)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun transact(code: Int): Bundle? {
        val remote = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(FlipSensorHalReader.DESCRIPTOR)
            if (!remote.transact(code, data, reply, 0)) return null
            reply.readException()
            if (code == FlipSensorHalReader.CMD_READ) {
                @Suppress("DEPRECATION")
                reply.readBundle(FlipSamsungAngleSource::class.java.classLoader)
            } else Bundle()
        } catch (error: Exception) {
            Log.w(TAG, "Samsung hinge reader transaction $code failed", error)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun releaseBinding() {
        val previousArgs = args
        args = null
        binder = null
        if (previousArgs != null) {
            runCatching { Shizuku.unbindUserService(previousArgs, connection, true) }
                .onFailure { Log.w(TAG, "Could not unbind Samsung hinge reader", it) }
        }
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        binding = false
    }

    private companion object {
        const val TAG = "DuoFlipHinge"
        const val POLL_INTERVAL_MS = 40L
        const val BIND_TIMEOUT_MS = 10_000L
        const val NO_LOG_NOTICE_MS = 3_000L
        const val STATUS_INTERVAL_MS = 1_000L
    }
}

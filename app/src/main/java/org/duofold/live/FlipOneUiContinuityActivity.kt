package org.duofold.live

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * One UI continuity diagnostics for Galaxy Z Flip 6.
 *
 * Samsung/One UI owns the actual cover-display handoff. This activity only
 * observes public configuration, display and WindowManager events plus the
 * existing read-only hinge sources. It does not force a panel switch, move
 * another application's task, or alter Samsung settings.
 */
class FlipOneUiContinuityActivity : ComponentActivity(), SensorEventListener,
    DisplayManager.DisplayListener {

    private lateinit var displayManager: DisplayManager
    private lateinit var sensorManager: SensorManager
    private lateinit var prefs: android.content.SharedPreferences
    private var hingeSensor: Sensor? = null
    private var publicSensorRegistered = false
    private var publicAngle by mutableFloatStateOf(Float.NaN)
    private var publicSensorStatus by mutableStateOf("")
    private val publicSamples = FlipDiagnostics.Hinge()

    private val samsungEstimator = FlipSamsungHingeEstimator()
    private var samsungSource: FlipSamsungAngleSource? = null
    private var samsungRawAngle by mutableFloatStateOf(Float.NaN)
    private var samsungReceivedAtNs by mutableLongStateOf(0L)
    private var samsungSourceName by mutableStateOf("")
    private var samsungStatus by mutableStateOf("")

    private var displaySnapshot by mutableStateOf("")
    private var foldingFeatureSummary by mutableStateOf("")
    private var configurationChanges by mutableIntStateOf(0)
    private var displayEvents by mutableIntStateOf(0)
    private var lastDisplayId = -1
    private val eventLog = mutableStateListOf<String>()
    private var layoutJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("flip_oneui_continuity", MODE_PRIVATE)
        prefs.getString("events", "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(MAX_EVENTS)
            .forEach { eventLog.add(it) }
        configurationChanges = prefs.getInt("configuration_changes", 0)
        displayEvents = prefs.getInt("display_events", 0)

        displayManager = getSystemService(DisplayManager::class.java)
        sensorManager = getSystemService(SensorManager::class.java)
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        publicSensorStatus = getString(
            if (hingeSensor == null) R.string.flip_oneui_sensor_missing
            else R.string.flip_oneui_sensor_waiting
        )
        samsungStatus = getString(R.string.flip_hal_idle)

        samsungSource = FlipSamsungAngleSource(
            this,
            onReading = { reading ->
                samsungEstimator.add(reading.angle, reading.timestampNanos, reading.source)
                samsungRawAngle = reading.angle
                samsungReceivedAtNs = SystemClock.elapsedRealtimeNanos()
                samsungSourceName = reading.sourceName
                samsungStatus = getString(R.string.flip_hal_last_reading, reading.sourceName)
            },
            onStatus = { message -> samsungStatus = message },
            onDiagnostics = { /* This screen records continuity events, not raw HAL logs. */ }
        )

        refreshDisplaySnapshot()
        appendEvent("ACTIVITY_CREATED", "savedState=${savedInstanceState != null}; $displaySnapshot")
        setContent { DuoTheme { ContinuityScreen() } }
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        publicSensorRegistered = hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        if (!publicSensorRegistered && hingeSensor != null) {
            publicSensorStatus = getString(R.string.flip_oneui_sensor_register_failed)
        }
        samsungSource?.start()
        refreshDisplaySnapshot()
        appendEvent("ACTIVITY_STARTED", displaySnapshot)

        layoutJob?.cancel()
        layoutJob = lifecycleScope.launch {
            WindowInfoTracker.getOrCreate(this@FlipOneUiContinuityActivity)
                .windowLayoutInfo(this@FlipOneUiContinuityActivity)
                .collect { info ->
                    val feature = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    val next = if (feature == null) {
                        getString(R.string.flip_oneui_feature_none)
                    } else {
                        getString(
                            R.string.flip_oneui_feature_value,
                            feature.orientation.toString(),
                            feature.state.toString(),
                            feature.isSeparating.toString(),
                            feature.bounds.left,
                            feature.bounds.top,
                            feature.bounds.right,
                            feature.bounds.bottom
                        )
                    }
                    if (next != foldingFeatureSummary) {
                        foldingFeatureSummary = next
                        appendEvent("WINDOW_LAYOUT", next)
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDisplaySnapshot()
        appendEvent("ACTIVITY_RESUMED", displaySnapshot)
        samsungSource?.start()
    }

    override fun onPause() {
        appendEvent("ACTIVITY_PAUSED", displaySnapshot)
        super.onPause()
    }

    override fun onRestart() {
        super.onRestart()
        appendEvent("ACTIVITY_RESTARTED", displaySnapshot)
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        refreshDisplaySnapshot()
        appendEvent("MULTI_WINDOW_MODE_CHANGED", "active=$isInMultiWindowMode; $displaySnapshot")
    }

    override fun onStop() {
        layoutJob?.cancel()
        layoutJob = null
        displayManager.unregisterDisplayListener(this)
        sensorManager.unregisterListener(this)
        publicSensorRegistered = false
        samsungSource?.stop()
        appendEvent("ACTIVITY_STOPPED", displaySnapshot)
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChanges += 1
        prefs.edit().putInt("configuration_changes", configurationChanges).apply()
        refreshDisplaySnapshot()
        appendEvent("CONFIGURATION_CHANGED", displaySnapshot)
    }

    override fun onDisplayAdded(displayId: Int) = recordDisplayEvent("DISPLAY_ADDED", displayId)
    override fun onDisplayRemoved(displayId: Int) = recordDisplayEvent("DISPLAY_REMOVED", displayId)
    override fun onDisplayChanged(displayId: Int) = recordDisplayEvent("DISPLAY_CHANGED", displayId)

    private fun recordDisplayEvent(event: String, displayId: Int) {
        displayEvents += 1
        prefs.edit().putInt("display_events", displayEvents).apply()
        val changed = displayManager.getDisplay(displayId)
        val detail = if (changed == null) {
            "displayId=$displayId; no longer available"
        } else {
            "displayId=$displayId; state=${changed.state}; mode=${changed.mode.physicalWidth}x${changed.mode.physicalHeight}; rotation=${changed.rotation}"
        }
        refreshDisplaySnapshot()
        appendEvent(event, detail)
    }

    private fun refreshDisplaySnapshot() {
        val current = window.decorView.display
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = current?.mode
        val currentDisplayId = current?.displayId ?: -1
        if (lastDisplayId >= 0 && currentDisplayId != lastDisplayId) {
            appendEvent("DISPLAY_CONTEXT_CHANGED", "from=$lastDisplayId; to=$currentDisplayId")
        }
        lastDisplayId = currentDisplayId
        val bounds = runCatching { windowManager.currentWindowMetrics.bounds }.getOrNull()
        val panel = if (mode == null) FlipDiagnostics.Panel.UNKNOWN
            else FlipDiagnostics.panelCandidate(mode.physicalWidth, mode.physicalHeight)
        val panelLabel = getString(when (panel) {
            FlipDiagnostics.Panel.INNER_CANDIDATE -> R.string.flip_oneui_inner_candidate
            FlipDiagnostics.Panel.COVER_CANDIDATE -> R.string.flip_oneui_cover_candidate
            FlipDiagnostics.Panel.INVALID -> R.string.flip_oneui_panel_invalid
            FlipDiagnostics.Panel.UNKNOWN -> R.string.flip_oneui_panel_unknown
        })
        val config = resources.configuration
        displaySnapshot = getString(
            R.string.flip_oneui_display_metrics,
            panelLabel,
            current?.displayId ?: -1,
            mode?.physicalWidth ?: 0,
            mode?.physicalHeight ?: 0,
            bounds?.width() ?: 0,
            bounds?.height() ?: 0,
            config.screenWidthDp,
            config.screenHeightDp,
            current?.rotation ?: -1
        )
    }

    private fun appendEvent(name: String, detail: String) {
        val line = "${SystemClock.elapsedRealtime()}	$name	${detail.replace('\n', ' ')}"
        if (eventLog.size >= MAX_EVENTS) eventLog.removeAt(0)
        eventLog.add(line)
        prefs.edit().putString("events", eventLog.joinToString("\n")).apply()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!publicSensorRegistered || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        val value = event.values[0]
        if (publicSamples.add(value, event.timestamp, SystemClock.elapsedRealtimeNanos(), 0f)) {
            publicAngle = publicSamples.raw()
            publicSensorStatus = getString(R.string.flip_oneui_sensor_active)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            publicSensorStatus = getString(R.string.flip_oneui_sensor_unreliable)
        }
    }

    @Composable
    private fun ContinuityScreen() {
        var frameNow by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
        LaunchedEffect(samsungReceivedAtNs) {
            if (samsungReceivedAtNs > 0L) {
                while (SystemClock.elapsedRealtimeNanos() - samsungReceivedAtNs <= 3_000_000_000L) {
                    frameNow = SystemClock.elapsedRealtimeNanos()
                    delay(16)
                }
                frameNow = SystemClock.elapsedRealtimeNanos()
            }
        }
        val samsungFresh = samsungReceivedAtNs > 0L &&
            frameNow >= samsungReceivedAtNs &&
            frameNow - samsungReceivedAtNs <= 3_000_000_000L
        val samsungEstimate = if (samsungFresh) samsungEstimator.estimate(frameNow) else Float.NaN
        val shownAngle = if (samsungEstimate.isFinite()) samsungEstimate else publicAngle
        val inputSource = when {
            samsungFresh -> getString(R.string.flip_oneui_hal_source, samsungSourceName)
            publicAngle.isFinite() -> getString(R.string.flip_oneui_public_source)
            else -> publicSensorStatus
        }

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(getString(R.string.flip_oneui_title), style = MaterialTheme.typography.headlineSmall)
                        Text("Galaxy Z Flip 6", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { finish() }) { Text(getString(R.string.flip_home_12)) }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(getString(R.string.flip_oneui_setting_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(getString(R.string.flip_oneui_setting_path))
                        Text(getString(R.string.flip_oneui_setting_detail), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { startActivity(Intent(Settings.ACTION_SETTINGS)) }) {
                            Text(getString(R.string.flip_oneui_open_settings))
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(getString(R.string.flip_oneui_actual_handoff), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(getString(R.string.flip_oneui_actual_handoff_detail))
                        Text(getString(R.string.flip_oneui_setting_not_readable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(getString(R.string.flip_oneui_runtime_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(displaySnapshot.ifBlank { getString(R.string.flip_oneui_waiting_display) })
                        Text(getString(R.string.flip_oneui_feature, foldingFeatureSummary.ifBlank {
                            getString(R.string.flip_oneui_feature_waiting)
                        }))
                        Text(getString(
                            R.string.flip_oneui_angle,
                            if (shownAngle.isFinite()) String.format(Locale.getDefault(), "%.1f°", shownAngle) else "—",
                            inputSource
                        ))
                        if (samsungFresh) {
                            Text(getString(R.string.flip_hal_raw_and_estimate, samsungSourceName, samsungRawAngle, shownAngle),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(samsungStatus, style = MaterialTheme.typography.bodySmall)
                        Text(getString(R.string.flip_oneui_configuration_count, configurationChanges))
                        Text(getString(R.string.flip_oneui_display_event_count, displayEvents))
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(getString(R.string.flip_oneui_log_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(getString(R.string.flip_oneui_log_help), style = MaterialTheme.typography.bodySmall)
                        Text(eventLog.takeLast(12).asReversed().joinToString("\n").ifBlank {
                            getString(R.string.flip_oneui_log_empty)
                        }, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { copyEvents() }, enabled = eventLog.isNotEmpty()) {
                                Text(getString(R.string.flip_oneui_copy_log))
                            }
                            OutlinedButton(onClick = { clearEvents() }) {
                                Text(getString(R.string.flip_oneui_clear_log))
                            }
                        }
                    }
                }

                Text(getString(R.string.flip_oneui_footer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    private fun copyEvents() {
        val header = "elapsed_ms\tevent\tdetail"
        val text = (listOf(header) + eventLog.toList()).joinToString("\n")
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Duo Flip One UI continuity", text))
        android.widget.Toast.makeText(this, R.string.flip_oneui_log_copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun clearEvents() {
        eventLog.clear()
        configurationChanges = 0
        displayEvents = 0
        prefs.edit().remove("events")
            .putInt("configuration_changes", 0)
            .putInt("display_events", 0)
            .apply()
    }

    private companion object {
        const val MAX_EVENTS = 80
    }
}

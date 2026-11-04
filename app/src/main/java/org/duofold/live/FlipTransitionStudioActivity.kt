package org.duofold.live

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/** A live, in-app Flip 6 handoff preview. It never controls One UI or display power. */
class FlipTransitionStudioActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var hingeSensor: Sensor? = null
    private var publicRegistered = false
    private var publicAngle = Float.NaN
    private var publicReceivedAtNs = 0L
    private val samsungEstimator = FlipSamsungHingeEstimator()
    private var samsungRawAngle by mutableFloatStateOf(Float.NaN)
    private var samsungReceivedAtNs = 0L
    private var samsungSourceName by mutableStateOf("")
    private var samsungSource: FlipSamsungAngleSource? = null
    private var displayedAngle by mutableFloatStateOf(Float.NaN)
    private var displayedSource by mutableStateOf("")
    private var sensorStatus by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersivePreview()
        sensorManager = getSystemService(SensorManager::class.java)
        hingeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        sensorStatus = getString(R.string.flip_studio_waiting)
        samsungSource = FlipSamsungAngleSource(
            this,
            onReading = { reading ->
                samsungEstimator.add(reading.angle, reading.timestampNanos, reading.source)
                samsungRawAngle = reading.angle
                samsungReceivedAtNs = SystemClock.elapsedRealtimeNanos()
                samsungSourceName = reading.sourceName
                refreshDisplayedAngle()
            },
            onStatus = { sensorStatus = it },
            onDiagnostics = { /* Raw HAL logs are available in the dedicated preview screen. */ }
        )
        setContent { DuoTheme { StudioContent() } }
    }

    private fun enterImmersivePreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersivePreview()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // One UI may re-apply window insets while moving the task between panels.
        enterImmersivePreview()
    }

    override fun onStart() {
        super.onStart()
        publicRegistered = hingeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        if (hingeSensor == null || !publicRegistered) {
            sensorStatus = getString(R.string.flip_studio_no_sensor)
        }
        samsungSource?.start()
    }

    override fun onResume() {
        super.onResume()
        samsungSource?.start()
    }

    override fun onStop() {
        sensorManager.unregisterListener(this)
        publicRegistered = false
        samsungSource?.stop()
        // Keep the last timestamped sample briefly across a fold/unfold handoff.
        // refreshDisplayedAngle() expires it after 3 seconds, so stale values never
        // masquerade as live sensor data if the Activity stays in the background.
        super.onStop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!publicRegistered || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
        val value = event.values[0]
        if (FlipTransitionPolicy.isValidAngle(value)) {
            publicAngle = value
            publicReceivedAtNs = SystemClock.elapsedRealtimeNanos()
            refreshDisplayedAngle()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun refreshDisplayedAngle() {
        val now = SystemClock.elapsedRealtimeNanos()
        val halFresh = FlipTransitionPolicy.isFreshSample(
            samsungReceivedAtNs, now, 3_000_000_000L
        )
        val publicFresh = FlipTransitionPolicy.isFreshSample(
            publicReceivedAtNs, now, 3_000_000_000L
        )
        val estimate = if (halFresh) samsungEstimator.estimate(now) else Float.NaN
        when {
            halFresh && FlipTransitionPolicy.isValidAngle(estimate) -> {
                displayedAngle = estimate
                displayedSource = getString(R.string.flip_studio_hal, samsungSourceName)
            }
            publicFresh && FlipTransitionPolicy.isValidAngle(publicAngle) -> {
                displayedAngle = publicAngle
                displayedSource = getString(R.string.flip_studio_public)
            }
            else -> {
                displayedAngle = Float.NaN
                displayedSource = sensorStatus.ifBlank { getString(R.string.flip_studio_waiting) }
            }
        }
    }

    @Composable
    private fun StudioContent() {
        val prefs = remember { getSharedPreferences("flip_preview", MODE_PRIVATE) }
        // Open directly into a usable manual preview; live sensor input is optional.
        var manualMode by rememberSaveable { mutableStateOf(false) }
        var manualAngle by rememberSaveable { mutableFloatStateOf(90f) }
        var demoRunning by rememberSaveable { mutableStateOf(false) }
        val demoAngle = remember { Animatable(90f) }
        var style by rememberSaveable {
            mutableStateOf(prefs.getString("transition_style", "fade")
                ?.takeIf { it == "fade" || it == "slide" || it == "zoom" } ?: "fade")
        }
        LaunchedEffect(demoRunning) {
            if (demoRunning) {
                while (true) {
                    demoAngle.snapTo(0f)
                    demoAngle.animateTo(180f, tween(1600, easing = LinearEasing))
                    demoAngle.animateTo(0f, tween(1600, easing = LinearEasing))
                }
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                refreshDisplayedAngle()
                delay(16)
            }
        }

        val previewAngle = when {
            demoRunning -> demoAngle.value
            manualMode -> manualAngle
            displayedAngle.isFinite() -> displayedAngle
            else -> manualAngle
        }
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(getString(R.string.flip_studio_title), style = MaterialTheme.typography.headlineSmall)
                        Text("Galaxy Z Flip 6", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { finish() }) { Text(getString(R.string.flip_home_12)) }
                }
                Text(getString(R.string.flip_studio_description))
                TransitionPreview(previewAngle, style)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            getString(R.string.flip_studio_angle,
                                if (previewAngle.isFinite()) String.format(Locale.getDefault(), "%.1f°", previewAngle) else "—"),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                        )
                        Text(postureLabel(previewAngle), color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !manualMode, onClick = { manualMode = false },
                                label = { Text(getString(R.string.flip_studio_live)) })
                            FilterChip(selected = manualMode, onClick = { manualMode = true; demoRunning = false },
                                label = { Text(getString(R.string.flip_studio_manual)) })
                        }
                        Button(
                            onClick = { demoRunning = !demoRunning },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getString(if (demoRunning) R.string.flip_studio_stop_demo else R.string.flip_studio_demo))
                        }
                        if (manualMode) {
                            Text(getString(R.string.flip_studio_manual_help), style = MaterialTheme.typography.bodySmall)
                            Slider(value = manualAngle, onValueChange = { manualAngle = it }, valueRange = 0f..180f)
                        } else {
                            Text(getString(R.string.flip_studio_status, displayedSource.ifBlank { sensorStatus }),
                                style = MaterialTheme.typography.bodySmall)
                            if (samsungRawAngle.isFinite()) {
                                Text(getString(R.string.flip_hal_raw_and_estimate, samsungSourceName, samsungRawAngle, displayedAngle),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(getString(R.string.flip_studio_style), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = style == "fade", onClick = {
                                style = "fade"; prefs.edit().putString("transition_style", style).apply()
                            }, label = { Text(getString(R.string.flip_studio_fade)) })
                            FilterChip(selected = style == "slide", onClick = {
                                style = "slide"; prefs.edit().putString("transition_style", style).apply()
                            }, label = { Text(getString(R.string.flip_studio_slide)) })
                            FilterChip(selected = style == "zoom", onClick = {
                                style = "zoom"; prefs.edit().putString("transition_style", style).apply()
                            }, label = { Text(getString(R.string.flip_studio_zoom)) })
                        }
                        Text("수동 슬라이더와 실시간 추정 각도를 프레임마다 바로 반영합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(getString(R.string.flip_studio_visual_only), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(getString(R.string.flip_studio_disclaimer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    private fun postureLabel(value: Float): String = when (FlipTransitionPolicy.posture(value)) {
        FlipTransitionPolicy.Posture.COVER -> getString(R.string.flip_studio_posture_cover)
        FlipTransitionPolicy.Posture.TRANSITION -> getString(R.string.flip_studio_posture_transition)
        FlipTransitionPolicy.Posture.INNER -> getString(R.string.flip_studio_posture_inner)
        FlipTransitionPolicy.Posture.UNKNOWN -> getString(R.string.flip_studio_posture_unknown)
    }
}

@Composable
private fun TransitionPreview(angle: Float, style: String) {
    val valid = FlipTransitionPolicy.isValidAngle(angle)
    // The HAL estimator already interpolates between samples at frame cadence.
    // Manual input is also a direct position control, so never tween these values.
    val progress = if (valid) FlipTransitionPolicy.handoffProgress(angle) else 0.5f
    val coverTarget = 1f - progress
    val innerTarget = progress
    val coverAlpha = 0.04f + coverTarget * 0.96f
    val innerAlpha = 0.04f + innerTarget * 0.96f

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.flip_studio_preview_heading), style = MaterialTheme.typography.titleMedium)
            Text("전환 진행률 ${(progress * 100f).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom) {
                ScreenMock(
                    title = stringResource(R.string.flip_studio_cover),
                    subtitle = stringResource(R.string.flip_studio_cover_meta),
                    appTitle = stringResource(R.string.flip_studio_app_title),
                    appBody = stringResource(R.string.flip_studio_app_body),
                    time = stringResource(R.string.flip_studio_time),
                    isCover = true,
                    alpha = coverAlpha, visibility = coverTarget, style = style,
                    modifier = Modifier.weight(1f).height(148.dp)
                )
                ScreenMock(
                    title = stringResource(R.string.flip_studio_inner),
                    subtitle = stringResource(R.string.flip_studio_inner_meta),
                    appTitle = stringResource(R.string.flip_studio_app_title),
                    appBody = stringResource(R.string.flip_studio_app_body),
                    time = stringResource(R.string.flip_studio_time),
                    isCover = false,
                    alpha = innerAlpha, visibility = innerTarget, style = style,
                    modifier = Modifier.weight(1f).height(184.dp)
                )
            }
            if (!valid) {
                Text(stringResource(R.string.flip_studio_no_live_reading),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScreenMock(
    title: String,
    subtitle: String,
    appTitle: String,
    appBody: String,
    time: String,
    isCover: Boolean,
    alpha: Float,
    visibility: Float,
    style: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val offset = if (style == "slide") {
        with(density) { ((1f - visibility) * if (isCover) -48f else 48f).dp.toPx() }
    } else 0f
    val scale = if (style == "zoom") 0.76f + visibility * 0.24f else 1f
    Surface(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationX = offset
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(appTitle, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(appBody, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    Surface(Modifier.fillMaxWidth().height(24.dp), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)) {}
                }
            }
        }
    }
}

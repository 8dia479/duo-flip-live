package org.duofold.live

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/** Flip-only entry point. Samsung HAL logs are read-only and opt-in through Shizuku. */
class FlipHomeActivity : ComponentActivity(), SensorEventListener {
 private lateinit var sensors: SensorManager
 private lateinit var displays: DisplayManager
 private var sensor: Sensor? = null
 private val readings = FlipDiagnostics.Hinge()
 private var registered by mutableStateOf(false)
 private var badSample by mutableStateOf(false)
 private var angle by mutableFloatStateOf(Float.NaN)
 private var sensorStatus by mutableStateOf(FlipHomePolicy.SensorStatus.WAITING)
 private var resumeVersion by mutableIntStateOf(0)
 private var panelInfo by mutableStateOf("")
 private var innerInfo by mutableStateOf("")
 private var coverInfo by mutableStateOf("")
 private val samsungEstimator = FlipSamsungHingeEstimator()
 private var samsungRawAngle by mutableFloatStateOf(Float.NaN)
 private var samsungTimestampNs by mutableLongStateOf(0L)
 private var samsungReceivedAtNs by mutableLongStateOf(0L)
 private var samsungSourceName by mutableStateOf("")
 private var samsungStatus by mutableStateOf("")
 private var samsungSource: FlipSamsungAngleSource? = null
 private val displayListener = object : DisplayManager.DisplayListener {
  override fun onDisplayAdded(id: Int) = refreshDisplays()
  override fun onDisplayRemoved(id: Int) = refreshDisplays()
  override fun onDisplayChanged(id: Int) = refreshDisplays()
 }
 private val permissionListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  if (intent.action == "org.duofold.live.SUPPORT") { intent.action = null; SupportPrompts.open(this) }
  sensors = getSystemService(SensorManager::class.java)
  displays = getSystemService(DisplayManager::class.java)
  sensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
  samsungStatus = getString(R.string.flip_hal_idle)
  samsungSource = FlipSamsungAngleSource(
   this,
   onReading = { reading ->
    samsungEstimator.add(reading.angle, reading.timestampNanos, reading.source)
    samsungRawAngle = reading.angle
    samsungTimestampNs = reading.timestampNanos
    samsungReceivedAtNs = SystemClock.elapsedRealtimeNanos()
    samsungSourceName = reading.sourceName
    angle = reading.angle
    badSample = false
    samsungStatus = getString(R.string.flip_hal_last_reading, reading.sourceName)
    refreshSensorStatus()
   },
   onStatus = { samsungStatus = it },
   onDiagnostics = { /* Diagnostics are displayed in the dedicated preview screen. */ }
  )
  refreshDisplays()
  rikka.shizuku.Shizuku.addRequestPermissionResultListener(permissionListener)
  setContent { DuoTheme { FlipInterface() } }
 }
 override fun onStart() {
  super.onStart()
  displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
  refreshDisplays()
  readings.reset(); angle = Float.NaN; badSample = false
  registered = sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } ?: false
  refreshSensorStatus()
  samsungSource?.start()
 }
 override fun onResume() { super.onResume(); resumeVersion++; samsungSource?.start() }
 override fun onStop() {
  sensors.unregisterListener(this); registered = false
  samsungSource?.stop(); samsungTimestampNs = 0L; samsungReceivedAtNs = 0L
  samsungEstimator.reset(); samsungRawAngle = Float.NaN; samsungSourceName = ""
  displays.unregisterDisplayListener(displayListener)
  super.onStop()
 }
 override fun onDestroy() { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
 override fun onSensorChanged(event: SensorEvent) {
  if (!registered || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
  val samsungAge = if (samsungTimestampNs > 0L) SystemClock.elapsedRealtimeNanos() - samsungTimestampNs else Long.MAX_VALUE
  if (samsungAge in 0L..1_500_000_000L) return
  if (readings.add(event.values[0], event.timestamp, SystemClock.elapsedRealtimeNanos(), 0f)) {
   angle = readings.raw(); badSample = false
  } else badSample = true
  refreshSensorStatus()
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
  if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { badSample = true; refreshSensorStatus() }
 }
 private fun refreshSensorStatus() { sensorStatus = FlipHomePolicy.sensor(sensor != null, registered, badSample, readings.observation()) }
 private fun refreshDisplays() {
  val all = displays.displays
  fun summary(kind: FlipDiagnostics.Panel): String {
   val matches = all.filter { FlipDiagnostics.panelCandidate(it.mode.physicalWidth, it.mode.physicalHeight) == kind }
   if (matches.isEmpty()) return getString(R.string.flip_home_00)
   if (matches.size > 1) return getString(R.string.flip_home_01)
   val d = matches.first(); return getString(R.string.flip_panel, d.displayId, d.state)
  }
  innerInfo = summary(FlipDiagnostics.Panel.INNER_CANDIDATE)
  coverInfo = summary(FlipDiagnostics.Panel.COVER_CANDIDATE)
  panelInfo = all.joinToString("\n") { d ->
   val m = DisplayMetrics(); d.getRealMetrics(m)
   getString(R.string.flip_home_display, d.displayId, d.mode.physicalWidth, d.mode.physicalHeight, m.densityDpi, d.rotation, d.state)
  }.ifEmpty { getString(R.string.flip_prev_25) }
 }
 private fun sensorLabel(samsungFresh: Boolean): String {
  if (samsungFresh) return getString(R.string.flip_hal_sensor_active)
  return when (sensorStatus) {
  FlipHomePolicy.SensorStatus.UNAVAILABLE -> getString(R.string.flip_home_02)
  FlipHomePolicy.SensorStatus.ERROR -> getString(R.string.flip_home_03)
  FlipHomePolicy.SensorStatus.LIMITED -> getString(R.string.flip_home_04)
  FlipHomePolicy.SensorStatus.VARIED_UNVERIFIED -> getString(R.string.flip_home_05)
  else -> getString(R.string.flip_home_06)
  }
 }
 private fun posture(value: Float = angle): String = if (!value.isFinite()) getString(R.string.flip_home_00) else when {
  value <= 5 -> getString(R.string.flip_home_07)
  value >= 175 -> getString(R.string.flip_home_08)
  else -> getString(R.string.flip_home_09)
 }
 private fun showShizukuDialog() {
  val message = runCatching {
   when {
    !rikka.shizuku.Shizuku.pingBinder() -> getString(R.string.flip_shizuku_missing)
    rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> getString(R.string.flip_shizuku_granted)
    rikka.shizuku.Shizuku.shouldShowRequestPermissionRationale() -> getString(R.string.flip_shizuku_denied)
    else -> { rikka.shizuku.Shizuku.requestPermission(42); getString(R.string.flip_shizuku_request) }
   }
  }.getOrElse { getString(R.string.flip_home_41) }
  android.app.AlertDialog.Builder(this).setTitle(R.string.flip_shizuku_info).setMessage(message).setPositiveButton(R.string.flip_ok, null).show()
 }
 private fun openPreview() { startActivity(Intent(this, FlipPreviewActivity::class.java)) }
 private fun openOneUiContinuity() { startActivity(Intent(this, FlipOneUiContinuityActivity::class.java)) }

 private fun createTransitionPreviewFrame(): GlassFrame {
  val bitmap = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  canvas.drawColor(android.graphics.Color.rgb(34, 50, 75))
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  paint.color = android.graphics.Color.rgb(96, 205, 192)
  canvas.drawRoundRect(26f, 50f, 334f, 310f, 28f, 28f, paint)
  paint.color = android.graphics.Color.rgb(40, 52, 91)
  canvas.drawCircle(180f, 180f, 86f, paint)
  paint.color = android.graphics.Color.WHITE
  paint.textSize = 28f
  canvas.drawText("Duo Flip", 36f, 390f, paint)
  paint.color = android.graphics.Color.rgb(182, 170, 237)
  for (i in 0..3) canvas.drawRoundRect(32f, 430f + i * 50f, 328f, 462f + i * 50f, 12f, 12f, paint)
  return GlassFrame(bitmap, bitmap.width, bitmap.height, 0L)
 }
 @Composable private fun FlipInterface() {
  var page by rememberSaveable { mutableStateOf("home") }
  var shizuku by remember { mutableStateOf(getString(R.string.flip_home_10)) }
  val prefs = remember { getSharedPreferences("flip_preview", 0) }
  val transitionPreviewFrame = remember { createTransitionPreviewFrame() }
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
  val samsungAge = if (samsungReceivedAtNs > 0L) frameNow - samsungReceivedAtNs else Long.MAX_VALUE
  val samsungFresh = samsungAge in 0L..3_000_000_000L
  val samsungEstimate = if (samsungFresh) samsungEstimator.estimate(frameNow) else Float.NaN
  val displayAngle = if (samsungEstimate.isFinite()) samsungEstimate else angle
  var previewPanel by remember { mutableIntStateOf(FlipPreviewPolicy.COVER_PANEL) }
  LaunchedEffect(displayAngle) {
   previewPanel = FlipPreviewPolicy.nextPanel(previewPanel, displayAngle)
  }
  var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sensitivity", 1f).let { if (it.isFinite()) it.coerceIn(.1f, 5f) else 1f }) }
  var deadband by remember { mutableFloatStateOf(prefs.getFloat("deadband", 0f).let { if (it.isFinite()) it.coerceIn(0f, 3f) else 0f }) }
  var fold by remember { mutableStateOf(prefs.getBoolean("fold", true)) }
  var unfold by remember { mutableStateOf(prefs.getBoolean("unfold", true)) }
  LaunchedEffect(Unit) { while (true) {
   shizuku = runCatching { when {
    !rikka.shizuku.Shizuku.pingBinder() -> getString(R.string.flip_home_38)
    rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> getString(R.string.flip_home_39)
    else -> getString(R.string.flip_home_40)
   } }.getOrDefault(getString(R.string.flip_home_41))
   delay(1500)
  } }
  LaunchedEffect(resumeVersion) {
   sensitivity = prefs.getFloat("sensitivity", 1f).let { if (it.isFinite()) it.coerceIn(.1f, 5f) else 1f }
   deadband = prefs.getFloat("deadband", 0f).let { if (it.isFinite()) it.coerceIn(0f, 3f) else 0f }
   fold = prefs.getBoolean("fold", true); unfold = prefs.getBoolean("unfold", true)
  }
  BackHandler(page != "home") { page = "home" }
  Surface(Modifier.fillMaxSize()) {
   Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Column(Modifier.weight(1f)) {
      Text(if (page == "home") "Duo Flip" else getString(R.string.flip_home_11), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text("Galaxy Z Flip 6", color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     TextButton(onClick = { page = if (page == "home") "settings" else "home" }) { Text(if (page == "home") getString(R.string.flip_home_11) else getString(R.string.flip_home_12)) }
    }
    if (page == "home") {
     val model = if (DeviceCompatibility.isFlip6(Build.MODEL)) getString(R.string.flip_home_13) else getString(R.string.flip_other_device, Build.MODEL)
     Text(model, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
     FlipCard {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
       PhoneIllustration()
       Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(getString(R.string.flip_home_14), style = MaterialTheme.typography.titleMedium)
        Text(if (displayAngle.isFinite()) "${"%.1f".format(displayAngle)}°" else "—", style = MaterialTheme.typography.headlineLarge)
        Text(posture(displayAngle), color = MaterialTheme.colorScheme.secondary)
        Text(sensorLabel(samsungFresh), style = MaterialTheme.typography.bodySmall)
        Text(samsungStatus, style = MaterialTheme.typography.bodySmall)
        if (samsungFresh) Text(getString(R.string.flip_hal_raw_and_estimate, samsungSourceName, samsungRawAngle, displayAngle), style = MaterialTheme.typography.bodySmall)
       }
      }
     }
     FlipCard {
      Text(getString(R.string.flip_home_15), style = MaterialTheme.typography.titleMedium)
      Text(getString(R.string.flip_inner, innerInfo))
      Text(getString(R.string.flip_cover, coverInfo))
      Text(getString(R.string.flip_home_16), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     FlipCard {
      Text("iPhone Duo 스타일 화면 전환 미리보기", style = MaterialTheme.typography.titleMedium)
      Text(
       if (displayAngle.isFinite()) "실시간 힌지 각도 · ${"%.1f".format(displayAngle)}°" else "힌지 센서 측정 대기 중",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
       if (previewPanel == FlipPreviewPolicy.MAIN_PANEL)
        "메인 디스플레이 · ${HandoffSettings.DEFAULT.toInt()}°에서 즉시 전환"
       else
        "커버 디스플레이 · ${HandoffSettings.DEFAULT.toInt()}°까지 유지",
       style = MaterialTheme.typography.titleSmall,
       color = MaterialTheme.colorScheme.secondary
      )
      Text(
       "닫힐 때는 ${HandoffSettings.closing(HandoffSettings.DEFAULT).toInt()}° 이하에서 커버 화면으로 복귀합니다.",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      AndroidView(
       factory = { FrostSurface(it, preview = true, previewFlip = true) },
       modifier = Modifier.fillMaxWidth().height(320.dp),
       update = { view ->
        val naturalRotation = display?.rotation ?: 0
        val topDownRotation = (FlipPreviewPolicy.shaderRotation(naturalRotation) + 2) % 4
        val progress = if (previewPanel == FlipPreviewPolicy.MAIN_PANEL) 0f else 1f
        view.configure(
         transitionPreviewFrame,
         progress,
         .8f,
         true,
         topDownRotation,
         angle = displayAngle
        )
       }
      )
      Text(
       "실제 화면 전환 시스템을 바꾸는 기능은 아니며, 힌지 움직임에 맞춰 Duo 글래스 효과의 방향과 진행을 미리 보여줍니다.",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onSurfaceVariant
      )
     }
     FlipCard {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) { Text(getString(R.string.flip_home_17), style = MaterialTheme.typography.titleMedium); Text(getString(R.string.flip_home_18), style = MaterialTheme.typography.bodySmall) }
       Switch(checked = false, onCheckedChange = null, enabled = false)
      }
      OutlinedButton(onClick = { openOneUiContinuity() }, modifier = Modifier.fillMaxWidth()) { Text(getString(R.string.flip_home_43)) }
      Button(onClick = { openPreview() }, modifier = Modifier.fillMaxWidth()) { Text(getString(R.string.flip_home_19)) }
      OutlinedButton(onClick = { page = "diagnostics" }, modifier = Modifier.fillMaxWidth()) { Text(getString(R.string.flip_home_20)) }
     }
    } else if (page == "settings") {
     FlipCard {
      Text(getString(R.string.flip_home_21), style = MaterialTheme.typography.titleLarge)
      Text(getString(R.string.flip_home_22), style = MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(getString(R.string.flip_home_17), Modifier.weight(1f)); Switch(false, null, enabled = false) }
      Text(getString(R.string.flip_home_23), color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(getString(R.string.flip_sensitivity, sensitivity))
      Slider(sensitivity, { sensitivity = it; prefs.edit().putFloat("sensitivity", it).apply() }, valueRange = .1f..5f)
      Text(getString(R.string.flip_deadband_home, deadband))
      Slider(deadband, { deadband = it; prefs.edit().putFloat("deadband", it).apply() }, valueRange = 0f..3f)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(getString(R.string.flip_home_24), Modifier.weight(1f)); Switch(fold, { fold = it; prefs.edit().putBoolean("fold", it).apply() }) }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(getString(R.string.flip_home_25), Modifier.weight(1f)); Switch(unfold, { unfold = it; prefs.edit().putBoolean("unfold", it).apply() }) }
      OutlinedButton(onClick = { openPreview() }) { Text(getString(R.string.flip_home_26)) }
     }
     FlipCard { Text(getString(R.string.flip_home_27), style = MaterialTheme.typography.titleLarge); Text(getString(R.string.flip_sensor_name, sensor?.name ?: getString(R.string.flip_home_28))); Text(getString(R.string.flip_inner_short, innerInfo)); Text(getString(R.string.flip_cover_short, coverInfo)); Text(getString(R.string.flip_panel_warning, panelInfo), style = MaterialTheme.typography.bodySmall) }
     FlipCard {
      Text(getString(R.string.flip_home_29), style = MaterialTheme.typography.titleLarge)
      Text("Shizuku · $shizuku")
      Text(getString(R.string.flip_home_30), style = MaterialTheme.typography.bodySmall)
      OutlinedButton(onClick = { showShizukuDialog() }) { Text(getString(R.string.flip_home_31)) }
     }
     OutlinedButton(onClick = { page = "diagnostics" }, modifier = Modifier.fillMaxWidth()) { Text(getString(R.string.flip_home_32)) }
    } else {
     FlipCard {
      Text(getString(R.string.flip_home_32), style = MaterialTheme.typography.titleLarge)
      Text(sensorLabel(samsungFresh)); Text(getString(R.string.flip_angle_label, if (angle.isFinite()) getString(R.string.flip_angle_value, angle) else getString(R.string.flip_no_reading)))
      Text("Shizuku · $shizuku"); Text(panelInfo)
      Text(getString(R.string.flip_home_33), style = MaterialTheme.typography.bodySmall)
      Button(onClick = { openPreview() }) { Text(getString(R.string.flip_home_34)) }
      OutlinedButton(onClick = {
       RecoveryLog.init(this@FlipHomeActivity)
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Duo Flip 오류 로그", RecoveryLog.report()))
       android.widget.Toast.makeText(this@FlipHomeActivity, getString(R.string.flip_home_36), android.widget.Toast.LENGTH_SHORT).show()
      }) { Text(getString(R.string.flip_home_35)) }
     }
    }
    Text(getString(R.string.flip_home_37), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}
@Composable private fun FlipCard(content: @Composable ColumnScope.() -> Unit) {
 Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), RoundedCornerShape(26.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
@Composable private fun PhoneIllustration() {
 val outer = MaterialTheme.colorScheme.primary
 val screen = MaterialTheme.colorScheme.primaryContainer
 val hinge = MaterialTheme.colorScheme.background
 Canvas(Modifier.width(105.dp).height(190.dp)) {
  drawRoundRect(outer, cornerRadius = CornerRadius(size.width * .17f))
  val margin = size.width * .07f
  drawRoundRect(screen, topLeft = Offset(margin, margin), size = Size(size.width - 2 * margin, size.height - 2 * margin), cornerRadius = CornerRadius(size.width * .12f))
  drawLine(hinge, Offset(margin, size.height / 2), Offset(size.width - margin, size.height / 2), strokeWidth = size.width * .035f)
  drawCircle(hinge, radius = size.width * .025f, center = Offset(size.width / 2, margin * 1.8f))
 }
}

package org.duofold.live

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A sandboxed shader preview. It never connects to LiveAngles or controls system panels. */
class FlipPreviewActivity : ComponentActivity(), SensorEventListener {
 private lateinit var sensors: SensorManager
 private var sensor: Sensor? = null
 private val samples = FlipDiagnostics.Hinge()
 private lateinit var displayManager: DisplayManager
 private var displayText by mutableStateOf("")
 private var displayRevision by mutableIntStateOf(0)
 private var deadband by mutableFloatStateOf(0f)
 private val motion = FlipMotion()
 private var direction by mutableStateOf(FlipMotion.Direction.UNKNOWN)
 private var directionSensitivity by mutableFloatStateOf(1f)
 private fun trackDirection(angle: Float) { direction = motion.update(angle, directionSensitivity) }
 private var recording by mutableStateOf(false)
 private var recordCount by mutableIntStateOf(0)
 private val records = ArrayDeque<String>()
 private val displayEvents = ArrayDeque<String>()
 private var displayEventCount by mutableIntStateOf(0)
 private fun recordDisplay(event: String, id: Int) {
  if (!recording) return
  if (displayEvents.size == 32) displayEvents.removeFirst()
  displayEvents.addLast("${SystemClock.elapsedRealtimeNanos()},$event,$id")
  displayEventCount = displayEvents.size
 }
 private val displayListener = object : DisplayManager.DisplayListener {
  override fun onDisplayAdded(id: Int) { recordDisplay("added", id); refreshDisplays() }
  override fun onDisplayRemoved(id: Int) { recordDisplay("removed", id); refreshDisplays() }
  override fun onDisplayChanged(id: Int) { recordDisplay("changed", id); refreshDisplays() }
 }
 private fun refreshDisplays() { displayText = displaySnapshot(); displayRevision++ }
 private var listening = false
 private var started = false
 private var live by mutableStateOf(false)
 private var observed by mutableFloatStateOf(Float.NaN)
 private var sampleTime by mutableLongStateOf(0L)
 private var status by mutableStateOf("Manual angle; no sensor registered")

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  sensors = getSystemService(SensorManager::class.java)
  sensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
  displayManager = getSystemService(DisplayManager::class.java)
  refreshDisplays()
  val bitmap = createSample()
  val frame = GlassFrame(bitmap, bitmap.width, bitmap.height, 0L)
  setContent {
   DuoTheme {
    var manual by remember { mutableFloatStateOf(120f) }
    var foldEffect by remember { mutableStateOf(true) }
    var unfoldEffect by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var shizukuStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
     while (true) {
      now = SystemClock.elapsedRealtimeNanos()
      shizukuStatus = runCatching {
       if (!rikka.shizuku.Shizuku.pingBinder()) "Shizuku disconnected"
       else if (rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku connected and authorized"
       else "Shizuku connected; authorization not granted"
      }.getOrElse { "Shizuku status unavailable" }
      delay(1000)
     }
    }
    val angle = if (live && observed.isFinite()) observed else manual
    // The assumed preview axis is deliberately independent of physical display identity.
    val rotation = FlipPreviewPolicy.shaderRotation(display?.rotation ?: 0)
    @Suppress("UNUSED_VARIABLE") val currentDisplayRevision = displayRevision
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Flip experimental preview", style = MaterialTheme.typography.headlineSmall)
      Text("In-app synthetic image only. No wallpaper changes, screen capture, Shizuku, overlay, or system display handoff. Flip 6 behavior is unverified.")
      Text("Hinge direction is an assumed horizontal axis, not a hardware detection result.")
      Text("${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}; public hinge sensor: ${sensor?.name ?: "unavailable"} (${sensor?.vendor ?: "unknown vendor"})")
      Text("Angle: ${angle.roundToInt()}°")
      Slider(value = manual, onValueChange = { manual = it; trackDirection(it) }, valueRange = 0f..180f, enabled = !live)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       Button(onClick = { selectLiveMode(false) }) { Text("Manual") }
       OutlinedButton(onClick = { selectLiveMode(true) }) { Text("Public sensor") }
      }
      Text(status)
      Text("Observed: ${samples.observation()} · accepted ${samples.accepted()} · rejected ${samples.rejected()}. This is not proof of continuous sensor support.")
      Text("Direction: $direction · hysteresis ${String.format(java.util.Locale.US, "%.1f", directionSensitivity)}°")
      Slider(value = directionSensitivity, onValueChange = { directionSensitivity = it }, valueRange = .1f..5f)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       FilterChip(selected = foldEffect, onClick = { foldEffect = !foldEffect }, label = { Text("Fold effect") })
       FilterChip(selected = unfoldEffect, onClick = { unfoldEffect = !unfoldEffect }, label = { Text("Unfold effect") })
      }
      Text("Preview noise deadband: ${String.format(java.util.Locale.US, "%.1f", deadband)}° (0 = raw)")
      Slider(value = deadband, onValueChange = { deadband = it }, valueRange = 0f..3f)
      Text("$shizukuStatus · read-only status, not used by preview")
      Text(remember { samsungWallpaperStatus() })
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       OutlinedButton(onClick = { recording = !recording }) { Text(if (recording) "Stop recording" else "Record sensor") }
       OutlinedButton(onClick = { records.clear(); recordCount = 0; displayEvents.clear(); displayEventCount = 0 }) { Text("Clear") }
      }
      OutlinedButton(onClick = {
       val csv = "elapsed_nanos,angle_degrees,accuracy\n" + records.joinToString("\n")
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Flip sensor diagnostic", csv))
       android.widget.Toast.makeText(this@FlipPreviewActivity, "Copied $recordCount sensor events", android.widget.Toast.LENGTH_SHORT).show()
      }, enabled = recordCount > 0) { Text("Copy sensor CSV ($recordCount / 512)") }
      OutlinedButton(onClick = {
       val csv = "elapsed_nanos,event,display_id\n" + displayEvents.joinToString("\n")
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Flip display diagnostic", csv))
       android.widget.Toast.makeText(this@FlipPreviewActivity, "Copied $displayEventCount display events", android.widget.Toast.LENGTH_SHORT).show()
      }, enabled = displayEventCount > 0) { Text("Copy display events ($displayEventCount / 32)") }
      Text("Recording is opt-in and memory-only. Sensor events: 512 maximum; display events: 32 maximum. It stops when this screen is backgrounded, so absence of events does not prove continuity. Review before sharing.")
      if (sampleTime > 0) Text("Last event: ${((now - sampleTime).coerceAtLeast(0L) / 1_000_000_000L)} s ago. On-change sensors can be silent while stationary; the last value is held, not extrapolated.")
      Text("A public sensor may report only a few postures, not continuous angles. Inspect the readings before drawing conclusions.")
      AndroidView(
       factory = { FrostSurface(it, preview = true, previewFlip = true) },
       modifier = Modifier.fillMaxWidth().height(360.dp),
       update = { it.configure(frame, if (FlipMotion.effectEnabled(direction, foldEffect, unfoldEffect)) DuoShadeCurve.progress(angle, true) else 0f, .8f, true, rotation, angle = angle) }
      )
      Text("Display snapshot (size candidates only; never used for routing):", style = MaterialTheme.typography.titleSmall)
      Text(displayText)
      OutlinedButton(onClick = { finish() }) { Text("Close") }
     }
    }
   }
  }
 }
 private fun selectLiveMode(value: Boolean) {
  if (!value) { stopSensor(); motion.reset(); direction = FlipMotion.Direction.UNKNOWN; live = false; status = "Manual angle; no sensor registered"; return }
  if (sensor == null) { live = false; status = "TYPE_HINGE_ANGLE unavailable; manual preview remains active"; return }
  live = true
  if (started) startSensor()
 }
 private fun startSensor() {
  if (listening) return
  val current = sensor ?: return
  samples.reset(); motion.reset(); direction = FlipMotion.Direction.UNKNOWN; observed = Float.NaN; sampleTime = 0
  listening = sensors.registerListener(this, current, SensorManager.SENSOR_DELAY_GAME)
  if (!listening) { live = false; status = "Sensor registration failed; manual preview remains active" }
  else status = "Waiting for ${current.name}; manual value shown until first valid event"
 }
 private fun stopSensor() { if (listening) sensors.unregisterListener(this); listening = false; samples.reset(); observed = Float.NaN; sampleTime = 0 }
 override fun onStart() { super.onStart(); started = true; displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper())); refreshDisplays(); if (live) startSensor() }
 override fun onStop() { started = false; displayManager.unregisterDisplayListener(displayListener); recording = false; stopSensor(); super.onStop() }
 override fun onSensorChanged(event: SensorEvent) {
  if (!listening || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
  if (samples.add(event.values[0], event.timestamp, SystemClock.elapsedRealtimeNanos(), deadband)) {
   observed = samples.displayed(); sampleTime = samples.timestamp(); trackDirection(observed)
   if (recording) {
    if (records.size == 512) records.removeFirst()
    records.addLast("${event.timestamp},${samples.raw()},${event.accuracy}"); recordCount = records.size
   }
   status = "${event.sensor.name}: raw ${samples.raw()}°; preview ${observed}° · accuracy ${event.accuracy}"
  } else status = "Rejected invalid or out-of-order sensor sample; last value retained"
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
 private fun samsungWallpaperStatus(): String {
  val component = ComponentName("com.samsung.android.wallpaper.live", "com.samsung.android.wallpaper.live.fold.FoldInteractive")
  return try {
   packageManager.getServiceInfo(component, 0)
   "Samsung FoldInteractive component is visible. Angle responses and compatibility are NOT verified; no wallpaper changed."
  } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
   "Samsung FoldInteractive component not visible or unavailable; this does not establish Flip support."
  } catch (e: SecurityException) {
   "Cannot inspect Samsung wallpaper component due to package visibility or permission."
  }
 }
 private fun displaySnapshot(): String {
  val displays = displayManager.displays
  val innerCount = displays.count { FlipDiagnostics.panelCandidate(it.mode.physicalWidth,it.mode.physicalHeight) == FlipDiagnostics.Panel.INNER_CANDIDATE }
  val coverCount = displays.count { FlipDiagnostics.panelCandidate(it.mode.physicalWidth,it.mode.physicalHeight) == FlipDiagnostics.Panel.COVER_CANDIDATE }
  return displays.joinToString("\n") { d ->
   val metrics = DisplayMetrics(); d.getRealMetrics(metrics)
   val mode = d.mode
   val candidate = FlipDiagnostics.panelCandidate(mode.physicalWidth, mode.physicalHeight)
   val ambiguous = (candidate == FlipDiagnostics.Panel.INNER_CANDIDATE && innerCount > 1) || (candidate == FlipDiagnostics.Panel.COVER_CANDIDATE && coverCount > 1)
   "ID ${d.displayId}: ${if (ambiguous) "AMBIGUOUS " else ""}$candidate (size hint only); ${mode.physicalWidth}×${mode.physicalHeight} physical; ${metrics.widthPixels}×${metrics.heightPixels} logical; ${metrics.densityDpi} dpi; rotation ${d.rotation}; state ${d.state}"
  }.ifEmpty { "No displays reported" }
 }
 private fun createSample(): Bitmap {
  val bitmap = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  canvas.drawColor(Color.rgb(34, 50, 75))
  val p = Paint(Paint.ANTI_ALIAS_FLAG)
  p.color = Color.rgb(96, 205, 192); canvas.drawRoundRect(26f, 50f, 334f, 310f, 28f, 28f, p)
  p.color = Color.rgb(40, 52, 91); canvas.drawCircle(180f, 180f, 86f, p)
  p.color = Color.WHITE; p.textSize = 28f; canvas.drawText("DUO FLIP PREVIEW", 36f, 390f, p)
  p.color = Color.rgb(182, 170, 237)
  for (i in 0..3) canvas.drawRoundRect(32f, 430f + i * 50f, 328f, 462f + i * 50f, 12f, 12f, p)
  return bitmap
 }
}

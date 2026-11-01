package org.duofold.live

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
 private val samples = FlipPreviewPolicy.Samples()
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
  val bitmap = createSample()
  val frame = GlassFrame(bitmap, bitmap.width, bitmap.height, 0L)
  setContent {
   DuoTheme {
    var manual by remember { mutableFloatStateOf(120f) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var displayText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
     while (true) {
      now = SystemClock.elapsedRealtimeNanos()
      displayText = displaySnapshot()
      delay(1000)
     }
    }
    val angle = if (live && observed.isFinite()) observed else manual
    // The assumed preview axis is deliberately independent of physical display identity.
    val rotation = FlipPreviewPolicy.shaderRotation(display?.rotation ?: 0)
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Flip experimental preview", style = MaterialTheme.typography.headlineSmall)
      Text("In-app synthetic image only. No wallpaper changes, screen capture, Shizuku, overlay, or system display handoff. Flip 6 behavior is unverified.")
      Text("Hinge direction is an assumed horizontal axis, not a hardware detection result.")
      Text("Angle: ${angle.roundToInt()}°")
      Slider(value = manual, onValueChange = { manual = it }, valueRange = 0f..180f, enabled = !live)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       Button(onClick = { setLive(false) }) { Text("Manual") }
       OutlinedButton(onClick = { setLive(true) }) { Text("Public sensor") }
      }
      Text(status)
      if (sampleTime > 0) Text("Last event: ${((now - sampleTime).coerceAtLeast(0L) / 1_000_000_000L)} s ago. On-change sensors can be silent while stationary; the last value is held, not extrapolated.")
      Text("A public sensor may report only a few postures, not continuous angles. Inspect the readings before drawing conclusions.")
      AndroidView(
       factory = { FrostSurface(it, preview = true, previewFlip = true) },
       modifier = Modifier.fillMaxWidth().height(360.dp),
       update = { it.configure(frame, DuoShadeCurve.progress(angle, true), .8f, true, rotation, angle = angle) }
      )
      Text("Display snapshot (not classified as inner or cover):", style = MaterialTheme.typography.titleSmall)
      Text(displayText)
      OutlinedButton(onClick = { finish() }) { Text("Close") }
     }
    }
   }
  }
 }
 private fun setLive(value: Boolean) {
  if (!value) { stopSensor(); live = false; status = "Manual angle; no sensor registered"; return }
  if (sensor == null) { live = false; status = "TYPE_HINGE_ANGLE unavailable; manual preview remains active"; return }
  live = true
  if (started) startSensor()
 }
 private fun startSensor() {
  if (listening) return
  val current = sensor ?: return
  samples.reset(); observed = Float.NaN; sampleTime = 0
  listening = sensors.registerListener(this, current, SensorManager.SENSOR_DELAY_GAME)
  if (!listening) { live = false; status = "Sensor registration failed; manual preview remains active" }
  else status = "Waiting for ${current.name}; manual value shown until first valid event"
 }
 private fun stopSensor() { if (listening) sensors.unregisterListener(this); listening = false; samples.reset(); observed = Float.NaN; sampleTime = 0 }
 override fun onStart() { super.onStart(); started = true; if (live) startSensor() }
 override fun onStop() { started = false; stopSensor(); super.onStop() }
 override fun onSensorChanged(event: SensorEvent) {
  if (!listening || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
  if (samples.accept(event.values[0], event.timestamp, SystemClock.elapsedRealtimeNanos())) {
   observed = samples.value(); sampleTime = samples.timestamp()
   status = "${event.sensor.name}: ${observed}° · accuracy ${event.accuracy}"
  } else status = "Rejected invalid or out-of-order sensor sample; last value retained"
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
 private fun displaySnapshot(): String {
  val manager = getSystemService(DisplayManager::class.java)
  return manager.displays.joinToString("\n") { d ->
   val metrics = DisplayMetrics(); d.getRealMetrics(metrics)
   val mode = d.mode
   "ID ${d.displayId}: ${mode.physicalWidth}×${mode.physicalHeight} physical; ${metrics.widthPixels}×${metrics.heightPixels} logical; ${metrics.densityDpi} dpi; rotation ${d.rotation}; state ${d.state}"
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

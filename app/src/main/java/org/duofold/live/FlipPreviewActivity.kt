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
 private var sampleIntervalMs by mutableFloatStateOf(Float.NaN)
 private var status by mutableStateOf("")

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  sensors = getSystemService(SensorManager::class.java)
  status = getString(R.string.flip_manual_status)
  sensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
  val previewPrefs = getSharedPreferences("flip_preview", 0)
  directionSensitivity = previewPrefs.getFloat("sensitivity", 1f).let { if(it.isFinite()) it.coerceIn(.1f, 5f) else 1f }
  deadband = previewPrefs.getFloat("deadband", 0f).let { if(it.isFinite()) it.coerceIn(0f, 3f) else 0f }
  displayManager = getSystemService(DisplayManager::class.java)
  refreshDisplays()
  val bitmap = createSample()
  val frame = GlassFrame(bitmap, bitmap.width, bitmap.height, 0L)
  setContent {
   DuoTheme {
    var manual by remember { mutableFloatStateOf(120f) }
    var foldEffect by remember { mutableStateOf(previewPrefs.getBoolean("fold", true)) }
    var unfoldEffect by remember { mutableStateOf(previewPrefs.getBoolean("unfold", true)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var shizukuStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
     while (true) {
      now = SystemClock.elapsedRealtimeNanos()
      shizukuStatus = runCatching {
       if (!rikka.shizuku.Shizuku.pingBinder()) getString(R.string.flip_prev_07)
       else if (rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) getString(R.string.flip_prev_08)
       else getString(R.string.flip_prev_09)
      }.getOrElse { getString(R.string.flip_prev_10) }
      delay(1000)
     }
    }
    val angle = if (live) observed else manual
    var playing by remember { mutableStateOf(true) }
    // The assumed preview axis is deliberately independent of physical display identity.
    val rotation = FlipPreviewPolicy.shaderRotation(display?.rotation ?: 0)
    @Suppress("UNUSED_VARIABLE") val currentDisplayRevision = displayRevision
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(getString(R.string.flip_prev_00), style = MaterialTheme.typography.headlineSmall)
      Text(getString(R.string.flip_prev_01))
      Text(getString(R.string.flip_prev_02))
      Text(getString(R.string.flip_device_info, android.os.Build.MODEL, android.os.Build.VERSION.RELEASE, sensor?.name ?: getString(R.string.flip_sensor_none), sensor?.vendor ?: "—"))
      sensor?.let { Text(getString(R.string.flip_sensor_details, it.name, it.vendor, it.resolution.toString(), it.maximumRange.toString(), it.minDelay.toString()), style = MaterialTheme.typography.bodySmall) }
      Text(getString(R.string.flip_vendor_info, if (sensors.getSensorList(Sensor.TYPE_ALL).any { it.type == 65686 || it.name.equals("Folding Angle", true) }) getString(R.string.flip_vendor_found) else getString(R.string.flip_vendor_none)))
      Text(getString(R.string.flip_vendor_permission, if (checkSelfPermission("com.samsung.permission.SSENSOR") == android.content.pm.PackageManager.PERMISSION_GRANTED) getString(R.string.flip_perm_yes) else getString(R.string.flip_perm_no)))
      Text(if (live) getString(R.string.flip_live_source) else getString(R.string.flip_virtual_source), style = MaterialTheme.typography.titleMedium)
      Text(if (live && samples.raw().isFinite()) getString(R.string.flip_angle_value, samples.raw()) else if (!live) getString(R.string.flip_angle_value, manual) else getString(R.string.flip_waiting_angle), style = MaterialTheme.typography.headlineLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       Button(onClick = { playing = true }, enabled = !playing) { Text(getString(R.string.flip_start_preview)) }
       OutlinedButton(onClick = { playing = false }, enabled = playing) { Text(getString(R.string.flip_stop_preview)) }
      }
      Slider(value = manual, onValueChange = { manual = it; trackDirection(it) }, valueRange = 0f..180f, enabled = !live)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       Button(onClick = { selectLiveMode(false) }) { Text(getString(R.string.flip_prev_03)) }
       OutlinedButton(onClick = { selectLiveMode(true) }) { Text(getString(R.string.flip_prev_04)) }
      }
      Text(status)
      Text(getString(R.string.flip_observation, observationLabel(), samples.accepted(), samples.rejected()))
      Text(getString(R.string.flip_direction, directionLabel(), directionSensitivity))
      Slider(value = directionSensitivity, onValueChange = { directionSensitivity = it; previewPrefs.edit().putFloat("sensitivity", it).apply() }, valueRange = .1f..5f)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       FilterChip(selected = foldEffect, onClick = { foldEffect = !foldEffect; previewPrefs.edit().putBoolean("fold", foldEffect).apply() }, label = { Text(getString(R.string.flip_prev_05)) })
       FilterChip(selected = unfoldEffect, onClick = { unfoldEffect = !unfoldEffect; previewPrefs.edit().putBoolean("unfold", unfoldEffect).apply() }, label = { Text(getString(R.string.flip_prev_06)) })
      }
      Text(getString(R.string.flip_deadband, deadband))
      Slider(value = deadband, onValueChange = { deadband = it; previewPrefs.edit().putFloat("deadband", it).apply() }, valueRange = 0f..3f)
      Text(getString(R.string.flip_status_readonly, shizukuStatus))
      Text(remember { samsungWallpaperStatus() })
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
       OutlinedButton(onClick = { recording = !recording }) { Text(if (recording) getString(R.string.flip_prev_11) else getString(R.string.flip_prev_12)) }
       OutlinedButton(onClick = { records.clear(); recordCount = 0; displayEvents.clear(); displayEventCount = 0 }) { Text(getString(R.string.flip_prev_13)) }
      }
      OutlinedButton(onClick = {
       val csv = "elapsed_nanos,angle_degrees,accuracy\n" + records.joinToString("\n")
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Flip sensor diagnostic", csv))
       android.widget.Toast.makeText(this@FlipPreviewActivity, getString(R.string.flip_copied_sensor, recordCount), android.widget.Toast.LENGTH_SHORT).show()
      }, enabled = recordCount > 0) { Text(getString(R.string.flip_copy_sensor, recordCount)) }
      OutlinedButton(onClick = {
       val csv = "elapsed_nanos,event,display_id\n" + displayEvents.joinToString("\n")
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Flip display diagnostic", csv))
       android.widget.Toast.makeText(this@FlipPreviewActivity, getString(R.string.flip_copied_display, displayEventCount), android.widget.Toast.LENGTH_SHORT).show()
      }, enabled = displayEventCount > 0) { Text(getString(R.string.flip_copy_display, displayEventCount)) }
      Text(getString(R.string.flip_prev_16))
      if (sampleTime > 0) Text(getString(R.string.flip_age, ((now - sampleTime).coerceAtLeast(0L) / 1_000_000_000L)))
      if (sampleIntervalMs.isFinite()) Text(getString(R.string.flip_interval, sampleIntervalMs))
      Text(getString(R.string.flip_prev_15))
      AndroidView(
       factory = { FrostSurface(it, preview = true, previewFlip = true) },
       modifier = Modifier.fillMaxWidth().height(360.dp),
       update = { it.configure(frame, if (playing && angle.isFinite() && FlipMotion.effectEnabled(direction, foldEffect, unfoldEffect)) DuoShadeCurve.progress(angle, true) else 0f, .8f, true, rotation, angle = angle) }
      )
      Text(getString(R.string.flip_prev_17), style = MaterialTheme.typography.titleSmall)
      Text(displayText)
      OutlinedButton(onClick = { finish() }) { Text(getString(R.string.flip_prev_14)) }
     }
    }
   }
  }
 }
 private fun selectLiveMode(value: Boolean) {
  if (!value) { stopSensor(); motion.reset(); direction = FlipMotion.Direction.UNKNOWN; live = false; status = getString(R.string.flip_prev_18); return }
  if (sensor == null) { live = false; status = getString(R.string.flip_prev_19); return }
  live = true
  if (started) startSensor()
 }
 private fun startSensor() {
  if (listening) return
  val current = sensor ?: return
  samples.reset(); motion.reset(); direction = FlipMotion.Direction.UNKNOWN; observed = Float.NaN; sampleTime = 0
  listening = sensors.registerListener(this, current, SensorManager.SENSOR_DELAY_GAME)
  if (!listening) { live = false; status = getString(R.string.flip_prev_20) }
  else status = getString(R.string.flip_wait_sensor, current.name)
 }
 private fun stopSensor() { if (listening) sensors.unregisterListener(this); listening = false; samples.reset(); observed = Float.NaN; sampleTime = 0; sampleIntervalMs = Float.NaN }
 override fun onStart() { super.onStart(); started = true; displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper())); refreshDisplays(); if (live) startSensor() }
 override fun onStop() { started = false; displayManager.unregisterDisplayListener(displayListener); recording = false; stopSensor(); super.onStop() }
 override fun onSensorChanged(event: SensorEvent) {
  if (!listening || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
  if (samples.add(event.values[0], event.timestamp, SystemClock.elapsedRealtimeNanos(), deadband)) {
   val previous = sampleTime
   observed = samples.displayed(); sampleTime = samples.timestamp(); trackDirection(observed)
   sampleIntervalMs = if (previous > 0) (sampleTime - previous) / 1_000_000f else Float.NaN
   if (recording) {
    if (records.size == 512) records.removeFirst()
    records.addLast("${event.timestamp},${samples.raw()},${event.accuracy}"); recordCount = records.size
   }
   status = getString(R.string.flip_sensor_reading, event.sensor.name, samples.raw(), observed, event.accuracy)
  } else status = getString(R.string.flip_prev_21)
 }
 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
 private fun observationLabel(): String = getString(when (samples.observation()) {
  FlipDiagnostics.Observation.NO_SAMPLES -> R.string.flip_obs_none
  FlipDiagnostics.Observation.OBSERVING -> R.string.flip_obs_check
  FlipDiagnostics.Observation.ONLY_STANDARD_STEPS -> R.string.flip_obs_steps
  else -> R.string.flip_obs_varied
 })
 private fun directionLabel(): String = getString(when (direction) {
  FlipMotion.Direction.FOLDING -> R.string.flip_dir_fold
  FlipMotion.Direction.UNFOLDING -> R.string.flip_dir_unfold
  else -> R.string.flip_dir_unknown
 })
 private fun samsungWallpaperStatus(): String {
  val component = ComponentName("com.samsung.android.wallpaper.live", "com.samsung.android.wallpaper.live.fold.FoldInteractive")
  return try {
   packageManager.getServiceInfo(component, 0)
   getString(R.string.flip_prev_22)
  } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
   getString(R.string.flip_prev_23)
  } catch (e: SecurityException) {
   getString(R.string.flip_prev_24)
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
   val label = getString(when (candidate) {
    FlipDiagnostics.Panel.INNER_CANDIDATE -> R.string.flip_panel_inner
    FlipDiagnostics.Panel.COVER_CANDIDATE -> R.string.flip_panel_cover
    FlipDiagnostics.Panel.INVALID -> R.string.flip_panel_invalid
    else -> R.string.flip_panel_unknown
   })
   getString(R.string.flip_display_line, d.displayId, if (ambiguous) getString(R.string.flip_ambiguous) else "", label, mode.physicalWidth, mode.physicalHeight, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, d.rotation, d.state)
  }.ifEmpty { getString(R.string.flip_prev_25) }
 }
 private fun createSample(): Bitmap {
  val bitmap = Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  canvas.drawColor(Color.rgb(34, 50, 75))
  val p = Paint(Paint.ANTI_ALIAS_FLAG)
  p.color = Color.rgb(96, 205, 192); canvas.drawRoundRect(26f, 50f, 334f, 310f, 28f, 28f, p)
  p.color = Color.rgb(40, 52, 91); canvas.drawCircle(180f, 180f, 86f, p)
  p.color = Color.WHITE; p.textSize = 28f; canvas.drawText("Duo Flip", 36f, 390f, p)
  p.color = Color.rgb(182, 170, 237)
  for (i in 0..3) canvas.drawRoundRect(32f, 430f + i * 50f, 328f, 462f + i * 50f, 12f, 12f, p)
  return bitmap
 }
}

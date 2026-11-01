package org.duofold.live

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import kotlinx.coroutines.delay

/** Flip-only entry point. It observes public APIs and never starts a privileged helper. */
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
 private var innerInfo by mutableStateOf("확인되지 않음")
 private var coverInfo by mutableStateOf("확인되지 않음")
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
 }
 override fun onResume() { super.onResume(); resumeVersion++ }
 override fun onStop() {
  sensors.unregisterListener(this); registered = false
  displays.unregisterDisplayListener(displayListener)
  super.onStop()
 }
 override fun onDestroy() { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(permissionListener); super.onDestroy() }
 override fun onSensorChanged(event: SensorEvent) {
  if (!registered || event.sensor.type != Sensor.TYPE_HINGE_ANGLE || event.values.isEmpty()) return
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
   if (matches.isEmpty()) return "확인되지 않음"
   if (matches.size > 1) return "후보 여러 개 · 확인 필요"
   val d = matches.first(); return "크기 후보 · ID ${d.displayId} · 상태 ${d.state}"
  }
  innerInfo = summary(FlipDiagnostics.Panel.INNER_CANDIDATE)
  coverInfo = summary(FlipDiagnostics.Panel.COVER_CANDIDATE)
  panelInfo = all.joinToString("\n") { d ->
   val m = DisplayMetrics(); d.getRealMetrics(m)
   "ID ${d.displayId} · ${d.mode.physicalWidth}×${d.mode.physicalHeight} · ${m.densityDpi} dpi · 회전 ${d.rotation} · 상태 ${d.state}"
  }.ifEmpty { "디스플레이 정보가 없습니다" }
 }
 private fun sensorLabel() = when (sensorStatus) {
  FlipHomePolicy.SensorStatus.UNAVAILABLE -> "연결 안 됨 · 센서를 찾을 수 없음"
  FlipHomePolicy.SensorStatus.ERROR -> "오류 · 센서 또는 데이터 확인 필요"
  FlipHomePolicy.SensorStatus.LIMITED -> "제한적 · 단계값만 관측됨"
  FlipHomePolicy.SensorStatus.VARIED_UNVERIFIED -> "다양한 값 관측 · 연속성 미검증"
  else -> "확인 중 · 기기를 천천히 접어 보세요"
 }
 private fun posture(): String = if (!angle.isFinite() || sensorStatus == FlipHomePolicy.SensorStatus.ERROR) "확인되지 않음" else when {
  angle <= 5 -> "닫힘으로 관측됨"
  angle >= 175 -> "펼침으로 관측됨"
  else -> "부분적으로 접힘"
 }
 private fun openPreview() { startActivity(Intent(this, FlipPreviewActivity::class.java)) }
 @Composable private fun FlipInterface() {
  var page by rememberSaveable { mutableStateOf("home") }
  var shizuku by remember { mutableStateOf("확인 중") }
  val prefs = remember { getSharedPreferences("flip_preview", 0) }
  var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sensitivity", 1f).let { if (it.isFinite()) it.coerceIn(.1f, 5f) else 1f }) }
  var deadband by remember { mutableFloatStateOf(prefs.getFloat("deadband", 0f).let { if (it.isFinite()) it.coerceIn(0f, 3f) else 0f }) }
  var fold by remember { mutableStateOf(prefs.getBoolean("fold", true)) }
  var unfold by remember { mutableStateOf(prefs.getBoolean("unfold", true)) }
  LaunchedEffect(Unit) { while (true) {
   shizuku = runCatching { when {
    !rikka.shizuku.Shizuku.pingBinder() -> "연결 안 됨"
    rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "연결됨 · 권한 허용"
    else -> "연결됨 · 권한 필요"
   } }.getOrDefault("상태 확인 불가")
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
      Text(if (page == "home") "Duo Flip" else "설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
      Text("Galaxy Z Flip 6", color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     TextButton(onClick = { page = if (page == "home") "settings" else "home" }) { Text(if (page == "home") "설정" else "홈") }
    }
    if (page == "home") {
     val model = if (DeviceCompatibility.isFlip6(Build.MODEL)) "Galaxy Z Flip 6 감지됨" else "${Build.MODEL} · Flip 6 이외의 기기 또는 미확인"
     Text(model, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
     FlipCard {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
       PhoneIllustration()
       Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("힌지 상태", style = MaterialTheme.typography.titleMedium)
        Text(if (angle.isFinite() && sensorStatus != FlipHomePolicy.SensorStatus.ERROR) "${"%.0f".format(angle)}°" else "—", style = MaterialTheme.typography.headlineLarge)
        Text(posture(), color = MaterialTheme.colorScheme.secondary)
        Text(sensorLabel(), style = MaterialTheme.typography.bodySmall)
       }
      }
     }
     FlipCard {
      Text("화면 상태", style = MaterialTheme.typography.titleMedium)
      Text("내부 화면  ·  $innerInfo")
      Text("커버 화면  ·  $coverInfo")
      Text("화면 크기를 이용한 후보이며 실제 패널 식별은 검증되지 않았습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
     }
     FlipCard {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) { Text("시스템 애니메이션", style = MaterialTheme.typography.titleMedium); Text("Flip 6에서 아직 검증되지 않아 꺼져 있습니다.", style = MaterialTheme.typography.bodySmall) }
       Switch(checked = false, onCheckedChange = null, enabled = false)
      }
      Button(onClick = { openPreview() }, modifier = Modifier.fillMaxWidth()) { Text("애니메이션 미리보기") }
      OutlinedButton(onClick = { page = "diagnostics" }, modifier = Modifier.fillMaxWidth()) { Text("진단 보기") }
     }
    } else if (page == "settings") {
     FlipCard {
      Text("애니메이션", style = MaterialTheme.typography.titleLarge)
      Text("시스템 효과는 비활성화되어 있습니다. 아래 설정은 미리보기에만 적용됩니다.", style = MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("시스템 애니메이션", Modifier.weight(1f)); Switch(false, null, enabled = false) }
      Text("애니메이션 속도 · 시스템 통합 전 사용 불가", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("방향 민감도 · ${"%.1f".format(sensitivity)}°")
      Slider(sensitivity, { sensitivity = it; prefs.edit().putFloat("sensitivity", it).apply() }, valueRange = .1f..5f)
      Text("센서 흔들림 무시 · ${"%.1f".format(deadband)}°")
      Slider(deadband, { deadband = it; prefs.edit().putFloat("deadband", it).apply() }, valueRange = 0f..3f)
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("접을 때 효과", Modifier.weight(1f)); Switch(fold, { fold = it; prefs.edit().putBoolean("fold", it).apply() }) }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("펼칠 때 효과", Modifier.weight(1f)); Switch(unfold, { unfold = it; prefs.edit().putBoolean("unfold", it).apply() }) }
      OutlinedButton(onClick = { openPreview() }) { Text("미리보기 설정 및 실행") }
     }
     FlipCard { Text("기기", style = MaterialTheme.typography.titleLarge); Text("센서 · ${sensor?.name ?: "없음"}"); Text("내부 · $innerInfo"); Text("커버 · $coverInfo"); Text("$panelInfo\n패널 후보는 실제 식별이 아닙니다.", style = MaterialTheme.typography.bodySmall) }
     FlipCard {
      Text("권한 및 연결", style = MaterialTheme.typography.titleLarge)
      Text("Shizuku · $shizuku")
      Text("연결해도 시스템 제어는 자동 활성화되지 않습니다.", style = MaterialTheme.typography.bodySmall)
      OutlinedButton(onClick = { ShizukuAccess.show(this@FlipHomeActivity, ShizukuAccess.request(this@FlipHomeActivity, 42)) }) { Text("Shizuku 연결 및 권한 확인") }
     }
     OutlinedButton(onClick = { page = "diagnostics" }, modifier = Modifier.fillMaxWidth()) { Text("고급 진단") }
    } else {
     FlipCard {
      Text("고급 진단", style = MaterialTheme.typography.titleLarge)
      Text(sensorLabel()); Text("각도 · ${if (angle.isFinite()) "${angle}°" else "측정값 없음"}")
      Text("Shizuku · $shizuku"); Text(panelInfo)
      Text("센서·화면 이벤트 기록과 CSV 복사는 진단 화면에서 사용할 수 있습니다. 실제 기기 검증은 아직 완료되지 않았습니다.", style = MaterialTheme.typography.bodySmall)
      Button(onClick = { openPreview() }) { Text("실시간 진단 및 CSV") }
      OutlinedButton(onClick = {
       RecoveryLog.init(this@FlipHomeActivity)
       (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Duo Flip 오류 로그", RecoveryLog.report()))
       android.widget.Toast.makeText(this@FlipHomeActivity, "오류 로그를 복사했습니다", android.widget.Toast.LENGTH_SHORT).show()
      }) { Text("오류 로그 복사") }
     }
    }
    Text("NOT TESTED ON DEVICE · 실제 시스템 화면 전환은 지원 확인 전입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

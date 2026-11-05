package org.duofold.live

import android.app.AlertDialog
import android.content.*
import android.os.Build
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Authorization is independent of wallpaper compatibility and ADB transport. */
object ShizukuAccess {
 private fun ui(context:Context,english:String,korean:String):String =
  if(context.resources.configuration.locales[0]?.language=="ko") korean else english
 fun request(context: Context, code: Int): String = try {
  when {
   PrivilegedAccess.rootSelected() -> PrivilegedAccess.report()
   !Shizuku.pingBinder() -> ui(context, "Duo has not received Shizuku’s connection. Check that Shizuku says Running (pairing alone is not enough). Keep both apps in the same Android profile. If it is already running, restart Shizuku, then reopen Duo.", "Duo가 Shizuku 연결을 받지 못했습니다. Shizuku에 '실행 중(Running)'이라고 표시되는지 확인하세요(페어링만으로는 충분하지 않습니다). 두 앱이 동일한 Android 사용자 프로필에 설치되어 있어야 합니다. 이미 실행 중이라면 Shizuku를 다시 시작한 뒤 Duo를 다시 여세요.")
   Shizuku.isPreV11() -> ui(context, "This Shizuku version is unsupported. Update official Shizuku.", "현재 Shizuku 버전은 지원되지 않습니다. 공식 Shizuku를 최신 버전으로 업데이트하세요.")
   Shizuku.checkSelfPermission()==0 -> ui(context, "Shizuku permission is granted. This does not verify the angle reader or required wallpaper.", "Shizuku 권한이 허용되었습니다. 다만 이것만으로 힌지 각도 판독이나 필요한 배경화면의 정상 작동까지 확인된 것은 아닙니다.")
   Shizuku.shouldShowRequestPermissionRationale() -> ui(context, "Authorization was previously denied. Open Shizuku → Authorized applications and enable Duo Fold Live, then return here.", "이전에 권한이 거부되었습니다. Shizuku에서 'Authorized applications(권한이 허용된 앱)'을 열고 Duo Fold Live를 허용한 다음 여기로 돌아오세요.")
   else -> {Shizuku.requestPermission(code); ui(context, "Permission requested. Select Allow in Shizuku’s dialog. If no dialog appears, open Shizuku → Authorized applications and enable Duo Fold Live.", "권한 요청을 보냈습니다. Shizuku 대화상자에서 '허용(Allow)'을 선택하세요. 대화상자가 나타나지 않으면 Shizuku의 'Authorized applications(권한이 허용된 앱)'에서 Duo Fold Live를 직접 허용하세요.")}
  }
 } catch(e: Exception){ui(context, "Shizuku request failed: ${e.javaClass.simpleName}: ${e.message}", "Shizuku 권한 요청 실패: ${e.javaClass.simpleName}: ${e.message}")}
 fun report(context: Context): String = buildString {
  append("Duo Fold Live ${BuildConfig.VERSION_NAME} · connection report\n")
  append("${Build.MODEL} / Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
  append("Firmware: ${Build.DISPLAY}\n")
  append("Angle reader: ${LiveAngles.status}\n")
  append("${HelperBinding.report()}\n")
  append("Reader diagnostics: ${LiveAngles.readerDiagnostics}\n")
  append("Package: ${context.packageName}\n")
  append(PrivilegedAccess.report()+"\n")
  if(!PrivilegedAccess.rootSelected()){
  append("Official Shizuku installed in this profile: "+runCatching{context.packageManager.getPackageInfo("moe.shizuku.privileged.api",0);true}.getOrDefault(false)+"\n")
  val alive=runCatching{Shizuku.pingBinder()}.getOrDefault(false)
  append("Binder received and alive: $alive\n")
  if(alive) runCatching {
   append("Server version: ${Shizuku.getVersion()}\n")
   append("Server UID: ${Shizuku.getUid()} (ADB expects 2000)\n")
   append("Permission: ${Shizuku.checkSelfPermission()} (0 = authorized)\n")
   append("Previously denied: ${Shizuku.shouldShowRequestPermissionRationale()}\n")
  }.onFailure{append("Connection check failed: ${it.javaClass.simpleName}: ${it.message}\n")}
  }
  append("Wallpaper profile: ${runCatching{WallpaperProfile.resolve(Build.MODEL,context.getSharedPreferences("first_run",0).getString("wallpaper_profile",""))}.getOrDefault("Selection required")}\n")
  append("Wallpaper setup model/OS eligible: ${DeviceCompatibility.isEligible(Build.MODEL, Build.VERSION.SDK_INT)}\n")
  append("Wallpaper setup verified: ${context.getSharedPreferences("first_run",0).getBoolean("wallpaper_verified",false)}\n")
  append("Wireless and USB start the same Shizuku ADB service. An unsupported wallpaper profile is not an authorization failure.")
 }
 fun show(context: Context, message: String) {
  if(PrivilegedAccess.rootSelected()){AlertDialog.Builder(context).setTitle(ui(context, "Root connection", "루트 연결")).setMessage(message).setPositiveButton(ui(context, "OK", "확인"),null).setNeutralButton(ui(context, "Copy report", "보고서 복사")){_,_->copy(context)}.show();return}
  AlertDialog.Builder(context).setTitle(ui(context, "Shizuku connection", "Shizuku 연결")).setMessage(message)
   .setPositiveButton(ui(context, "OK", "확인"),null).setNeutralButton(ui(context, "Copy report", "보고서 복사")){_,_->copy(context)}
   .setNegativeButton("Open Shizuku"){_,_->
    val intent=context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if(intent!=null)runCatching{context.startActivity(intent)}.onFailure{Toast.makeText(context,ui(context, "Could not open Shizuku. Open your Shizuku app manually.", "Shizuku를 열 수 없습니다. Shizuku 앱을 직접 실행하세요."),Toast.LENGTH_LONG).show()}
    else Toast.makeText(context,ui(context, "Shizuku app shortcut unavailable. Open Shizuku or your installed fork manually.", "Shizuku 앱 바로가기를 찾을 수 없습니다. Shizuku 또는 설치한 변형 앱을 직접 실행하세요."),Toast.LENGTH_LONG).show()
   }.show()
 }
 fun copy(context: Context){
  (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Duo connection report",report(context)))
  Toast.makeText(context,ui(context, "Connection report copied", "연결 진단 정보를 복사했습니다."),Toast.LENGTH_SHORT).show()
 }
}

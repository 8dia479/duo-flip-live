package org.duofold.live

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** First-install wizard. Existing users never enter this activity automatically. */
class SetupActivity : ComponentActivity() {
 private val prefs by lazy { getSharedPreferences("first_run",0) }
 private var stage by mutableIntStateOf(0)
 private var message by mutableStateOf("")
 private var applying by mutableStateOf(false)
 private var attempted=false
 private var bound=false
 private val args by lazy { PrivilegedAccess.UserServiceArgs(ComponentName(this,WallpaperSetupService::class.java)).daemon(false).processNameSuffix("wallpaper_setup").version(BuildConfig.VERSION_CODE) }
 private val listener=Shizuku.OnRequestPermissionResultListener { _, result ->
  runOnUiThread { message=if(result==PackageManager.PERMISSION_GRANTED)ui("Shizuku authorized. Continue with wallpaper setup below.", "Shizuku 권한이 허용되었습니다. 아래에서 배경화면 설정을 계속하세요.") else ui("Shizuku access was declined. Tap Authorize to try again.", "Shizuku 권한이 거부되었습니다. 다시 시도하려면 '권한 허용'을 누르세요.") }
 }
 private val connection=object:ServiceConnection {
  override fun onServiceConnected(name:ComponentName,binder:IBinder){
   Thread {
    var success=false
    val result=try {
     val input=Parcel.obtain();val output=Parcel.obtain()
     try {
      input.writeInterfaceToken(WallpaperSetupService.TOKEN)
      input.writeInt(if(intent.getBooleanExtra("repair",false)||prefs.getBoolean("repair_required",false)) 1 else 0)
      input.writeString(prefs.getString("wallpaper_profile",""))
      check(binder.transact(1,input,output,0)){ui("Setup helper did not respond", "설정 도우미가 응답하지 않습니다.")}
      output.readException();success=output.readInt()==1;output.readString() ?: ui("No result", "결과가 없습니다.")
     } finally { input.recycle();output.recycle() }
    } catch(e:Exception){ui("Setup failed: ${e.message}. Tap Retry.", "설정 실패: ${e.message}. 다시 시도 버튼을 누르세요.")}
    runOnUiThread {
     applying=false;message=result
     if(success){prefs.edit().putBoolean("wallpaper_verified",true).remove("repair_required").apply();go(3)}
     releaseHelper()
    }
   }.start()
  }
  override fun onServiceDisconnected(name:ComponentName){bound=false;applying=false;message=ui("Setup helper disconnected. Restart Shizuku if needed, then retry.", "설정 도우미 연결이 끊겼습니다. 필요하면 Shizuku를 다시 시작한 뒤 재시도하세요.")}
 }
 private fun releaseHelper(){if(bound){runCatching{PrivilegedAccess.unbindUserService(args,connection,true)};bound=false}}
 private fun go(step:Int){stage=step;prefs.edit().putInt("step",step).apply()}
 private var applyGeneration=0
 private fun ui(english:String,korean:String):String =
  if(resources.configuration.locales[0]?.language=="ko") korean else english
 private fun startApply(){
  if(applying)return
  try{WallpaperProfile.resolve(Build.MODEL,prefs.getString("wallpaper_profile",""))}catch(e:IllegalArgumentException){message=e.message ?: ui("Choose your Fold model above", "위에서 Fold 모델을 선택하세요.");return}
  if(runCatching{PrivilegedAccess.getUid()}.getOrDefault(-1) !in listOf(0,2000)){message=ui("Wallpaper setup requires authorized Shizuku or root access.", "배경화면을 설정하려면 Shizuku 또는 루트 권한이 필요합니다.");return}
  val generation=++applyGeneration
  attempted=true;prefs.edit().putBoolean("wallpaper_verified",false).apply();applying=true;message=ui("Applying the required fold wallpaper to both home screens…", "안쪽 화면과 커버 화면 양쪽에 필요한 폴더블 배경화면을 적용하고 있습니다…")
  try {bound=true;PrivilegedAccess.bindUserService(args,connection);android.os.Handler(mainLooper).postDelayed({if(applying&&generation==applyGeneration){applying=false;message=ui("Wallpaper setup timed out. Check Shizuku, then tap Retry wallpaper setup.", "배경화면 설정 시간이 초과되었습니다. Shizuku 연결을 확인한 뒤 '배경화면 설정 다시 시도'를 누르세요.");releaseHelper()}},15000)}
  catch(e:Exception){bound=false;applying=false;message=ui("Could not start setup: ${e.message}. Tap Retry.", "설정을 시작할 수 없습니다: ${e.message}. 다시 시도 버튼을 누르세요.")}
 }
 private fun open(intent:Intent){runCatching{startActivity(intent)}.onFailure{Toast.makeText(this,ui("This screen is unavailable. Open it in Settings.", "이 화면을 열 수 없습니다. 설정 앱에서 직접 여세요."),Toast.LENGTH_LONG).show()}}
 private fun link(url:String)=open(Intent(Intent.ACTION_VIEW,Uri.parse(url)))
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val flip6=DeviceCompatibility.isFlip6(Build.MODEL)
  stage=if(flip6)2 else if(intent.getBooleanExtra("repair",false)) 2 else prefs.getInt("step",0)
  Shizuku.addRequestPermissionResultListener(listener)
  setContent {
   DuoTheme{
    val scope=rememberCoroutineScope()
    var custom by remember{mutableStateOf(prefs.getBoolean("custom",false))}
    var hasPhoto by remember{mutableStateOf(File(filesDir,"photo.jpg").exists())}
    var loading by remember{mutableStateOf(false)}
    var shizuku by remember{mutableStateOf(false)}
    var authorized by remember{mutableStateOf(false)}
    var overlay by remember{mutableStateOf(false)}
    var accessibility by remember{mutableStateOf(false)}
    var consent by remember{mutableStateOf(RuntimeConsent.ready(this@SetupActivity))}
    var usb by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(true){
     shizuku=runCatching{PrivilegedAccess.pingBinder()}.getOrDefault(false)
     authorized=shizuku && runCatching{if(flip6)Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED else PrivilegedAccess.checkSelfPermission()==PackageManager.PERMISSION_GRANTED}.getOrDefault(false)
     overlay=Settings.canDrawOverlays(this@SetupActivity)
     accessibility=StandaloneService.instance!=null
     consent=RuntimeConsent.ready(this@SetupActivity)

     delay(500)
    }}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
     loading=true;message=ui("Preparing your photo…", "사진을 준비하고 있습니다…")
     scope.launch {
      runCatching {withContext(Dispatchers.IO){
       val bitmap=WallpaperPhoto.decode(this@SetupActivity,uri)
       try {
        val temp=File(filesDir,"setup-photo.tmp")
        temp.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,100,it)){ui("Image encoding failed", "이미지를 변환하지 못했습니다.")}}
        Files.move(temp.toPath(),File(filesDir,"photo.jpg").toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
       }finally{bitmap.recycle()}
      }}.onSuccess{hasPhoto=true;message=ui("Photo selected. It will appear behind your home-screen icons.", "사진을 선택했습니다. 홈 화면 아이콘 뒤에 표시됩니다.")}
       .onFailure{message=ui("Photo could not be loaded: ${it.message}", "사진을 불러오지 못했습니다: ${it.message}")}
      loading=false
     }
    }}
    Surface(Modifier.fillMaxSize()){
     Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      Text("Duo Fold Live",color=Color(0xff91ded2),style=MaterialTheme.typography.labelLarge)
      Text(ui("Make the fold yours.", "나만의 폴더블 화면을 만들어 보세요."),style=MaterialTheme.typography.headlineLarge)
      Text(ui("Setup ${stage+1} of 4", "설정 ${stage+1}단계 / 4"),style=MaterialTheme.typography.labelLarge)
      LinearProgressIndicator(progress={(stage+1)/4f},modifier=Modifier.fillMaxWidth())
      if(!applying&&!flip6)DeviceProfileChoice{if(stage==3)go(2);message="Wallpaper profile selected. Apply required wallpapers to use it."}
      when(stage){
       0->{
        Text(ui("The wallpaper that powers the fold", "폴더블 동작을 지원하는 배경화면"),style=MaterialTheme.typography.headlineSmall)
        Text(ui("Duo uses Samsung’s interactive fold wallpaper to read the hinge angle. Setup will apply it to BOTH the inner and cover home screens, including the cover-screen option Samsung normally hides.", "Duo는 삼성의 인터랙티브 폴더블 배경화면을 통해 힌지 각도를 읽습니다. 설정을 진행하면 삼성에서 기본적으로 숨겨 둔 커버 화면 옵션을 포함해 안쪽 화면과 커버 화면의 홈 배경화면에 모두 적용합니다."))
        Text(ui("This replaces your current home wallpapers and remains applied if you disable Duo. You can change them later in Samsung wallpaper settings. Your lock-screen wallpaper is not changed by this setup.", "현재 홈 화면 배경화면이 교체되며 Duo를 끄더라도 적용 상태가 유지됩니다. 나중에 삼성 배경화면 설정에서 변경할 수 있습니다. 잠금 화면 배경화면은 변경하지 않습니다."))
        Text(ui("Next, choose whether to cover it with your own photo. Then we’ll connect Shizuku (or your existing root access) and apply the required wallpaper automatically. No root is needed.", "다음 단계에서 직접 고른 사진을 배경으로 사용할지 선택합니다. 그 후 Shizuku(또는 이미 사용 중인 루트 권한)를 연결해 필요한 배경화면을 자동으로 적용합니다. 별도로 기기를 루팅할 필요는 없습니다."))
        Text(ui("Regional SM-F971, SM-F976, and SM-F966 model numbers are recognized. Other models may also proceed with a warning. Android 17 is still required; Samsung wallpaper components and APIs are checked separately. Only SM-F971U has been tested.", "지역별 모델 번호 SM-F971, SM-F976, SM-F966을 인식합니다. 다른 모델도 경고와 함께 진행할 수 있습니다. Android 17이 필요하며 삼성 배경화면 구성 요소와 API 호환성은 별도로 확인합니다. 현재 실제 테스트가 완료된 모델은 SM-F971U뿐입니다."),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={startActivity(Intent(this@SetupActivity,FlipPreviewActivity::class.java))}){Text(ui("Experimental Flip preview (no system changes)", "실험용 Flip 미리보기(시스템 변경 없음)"))}
        Button(onClick={go(1)}){Text(ui("Continue", "계속"))}
       }
       1->{
        Text(ui("Use a custom wallpaper?", "직접 고른 배경화면을 사용할까요?"),style=MaterialTheme.typography.headlineSmall)
        Text(ui("Your photo can sit behind the icons while Samsung’s wallpaper keeps supplying live hinge angles underneath.", "삼성 배경화면이 아래에서 실시간 힌지 각도를 계속 제공하고, 선택한 사진은 아이콘 뒤쪽에 표시됩니다."))
        FilterChip(selected=!custom,onClick={custom=false;prefs.edit().putBoolean("custom",false).apply()},label={Text(ui("No · use Samsung’s wallpaper", "아니요 · 삼성 배경화면 사용"))})
        FilterChip(selected=custom,onClick={custom=true;prefs.edit().putBoolean("custom",true).apply()},label={Text(ui("Yes · choose my photo", "예 · 내 사진 선택"))})
        if(custom){
         OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!loading){Text(if(hasPhoto)ui("Change selected photo", "선택한 사진 변경") else ui("Choose photo", "사진 선택"))}
         Text(ui("The custom photo can briefly reveal Samsung’s wallpaper when unlocking. This existing limitation is still being worked on.", "잠금 해제 중 삼성 배경화면이 잠시 보일 수 있습니다. 현재 알려진 제한 사항이며 개선 작업 중입니다."),style=MaterialTheme.typography.bodySmall)
        }
        Button(onClick={message="";go(2)},enabled=!loading&&(!custom||hasPhoto)){Text(ui("Continue to connection", "연결 설정으로 계속"))}
       }
       2->{
        Text(if(flip6)ui("Connect Shizuku for the Flip 6 hinge sensor", "Flip 6 힌지 센서를 위해 Shizuku 연결") else "Connect privileged access",style=MaterialTheme.typography.headlineSmall)
        if(!flip6){Text(ui("FoldInteractive is part of Samsung’s built-in wallpaper system, not a separate download. Duo needs its interactive fold wallpaper on both HOME screens. Other models may not contain a compatible profile.", "FoldInteractive는 별도로 설치하는 앱이 아니라 삼성 기본 배경화면 시스템의 일부입니다. Duo가 힌지 각도를 읽으려면 양쪽 홈 화면에 인터랙티브 폴더블 배경화면이 필요합니다. 다른 모델에는 호환 프로필이 없을 수 있습니다."));RootAccessControls()}
        val rootSelected=remember { mutableStateOf(PrivilegedAccess.rootSelected()) }
        LaunchedEffect(Unit){while(true){rootSelected.value=PrivilegedAccess.rootSelected();delay(500)}}
        if(flip6&&rootSelected.value){
         Text(ui("Flip 6’s live sensor reader requires Shizuku. Root mode is not supported for this experimental path.", "Flip 6에서 힌지 센서를 실시간으로 읽으려면 Shizuku가 필요합니다. 이 실험 기능에서는 루트 모드를 지원하지 않습니다."))
         OutlinedButton(onClick={PrivilegedAccess.useShizuku()}){Text(ui("Switch to Shizuku", "Shizuku 사용으로 전환"))}
        }
        if(flip6||!rootSelected.value){
        Text(if(flip6)ui("Shizuku reads the Flip 6 hinge directly and provides the capture-only helper. Your wallpapers remain unchanged; One UI continues to switch the physical screens.", "Shizuku는 Flip 6의 힌지 센서를 직접 읽고 화면 캡처 전용 도우미를 제공합니다. 배경화면은 변경되지 않으며, 실제 안쪽·커버 화면 전환은 계속 One UI가 담당합니다.") else ui("Shizuku gives Duo the access needed to install the fold wallpaper on both screens and read live hinge angles. After authorization, tap Apply required wallpapers below. Wallpaper compatibility is checked separately.", "Shizuku는 양쪽 화면에 폴더블 배경화면을 설치하고 실시간 힌지 각도를 읽는 데 필요한 접근 권한을 제공합니다. 권한을 허용한 다음 아래의 '필요한 배경화면 적용'을 누르세요. 배경화면 호환성은 별도로 확인합니다."))
        Text(if(authorized)ui("Shizuku connected and authorized", "Shizuku 연결 및 권한 허용 완료") else if(shizuku)ui("Shizuku connected · authorization needed", "Shizuku 연결됨 · 권한 허용 필요") else ui("Duo has not received a Shizuku connection", "Duo와 Shizuku가 아직 연결되지 않았습니다."))
        Button(onClick={link("https://github.com/thejaustin/ShizukuPlus/releases")}){Text(ui("Download Shizuku+ (Recommended)", "Shizuku+ 다운로드(권장)"))}
        Text(ui("If you installed Shizuku, uninstall it first to avoid conflicts. Install Shizuku+ from the link above, start it using wireless or USB debugging (ADB mode), then authorize Duo again.", "기존 Shizuku 앱을 설치했다면 충돌을 피하기 위해 먼저 삭제하세요. 위 링크에서 Shizuku+를 설치하고 무선 디버깅 또는 USB 디버깅(ADB 모드)으로 실행한 뒤 Duo의 권한을 다시 허용하세요."),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={val intent=packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api");if(intent!=null)open(intent)else link("https://github.com/thejaustin/ShizukuPlus/releases")}){Text(ui("Open installed Shizuku", "설치된 Shizuku 열기"))}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
         FilterChip(selected=!usb,onClick={usb=false},label={Text(ui("Wireless", "무선"))})
         FilterChip(selected=usb,onClick={usb=true},label={Text(ui("USB / computer", "USB / 컴퓨터"))})
        }
        Text(ui("In Samsung Settings → About phone → Software information, tap Build number seven times. Then open Developer options.", "삼성 설정에서 '휴대전화 정보 → 소프트웨어 정보'로 이동해 '빌드번호'를 7번 누르세요. 그런 다음 '개발자 옵션'을 여세요."))
        if(!usb){
         Text(ui("Connect to Wi-Fi. Enable USB debugging and Wireless debugging. In Shizuku, tap Pairing; open Wireless debugging → Pair device with pairing code, and enter that code in Shizuku’s notification. Return to Shizuku and tap Start.", "Wi-Fi에 연결한 뒤 'USB 디버깅'과 '무선 디버깅'을 켜세요. Shizuku에서 '페어링'을 누르고, Android의 '무선 디버깅 → 페어링 코드로 기기 페어링'을 여세요. 화면에 표시된 코드를 Shizuku 알림에 입력한 다음 Shizuku로 돌아가 '시작'을 누르세요."))
        }else{
         Text(ui("Enable USB debugging and connect to your computer. Download and extract Google’s platform-tools. Open PowerShell inside that extracted folder, run .\\adb.exe devices, and accept the phone’s debugging prompt. Then run the start command below, or copy the current command shown in Shizuku.", "'USB 디버깅'을 켜고 휴대전화를 컴퓨터에 연결하세요. Google 플랫폼 도구(platform-tools)를 다운로드해 압축을 푸세요. 압축을 푼 폴더에서 PowerShell을 열고 .\\adb.exe devices를 실행한 뒤 휴대전화에 표시되는 디버깅 허용 창을 승인하세요. 그다음 아래 시작 명령을 실행하거나 Shizuku에 표시된 최신 명령을 복사해 사용하세요."))
         OutlinedButton(onClick={link("https://developer.android.com/tools/releases/platform-tools")}){Text(ui("Download platform-tools", "platform-tools 다운로드"))}
         OutlinedButton(onClick={
          (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Start Shizuku",".\\adb.exe shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"))
          Toast.makeText(this@SetupActivity,ui("Windows command copied", "Windows 명령어를 복사했습니다."),Toast.LENGTH_SHORT).show()
         }){Text("Copy Windows start command")}
         Text(ui("Mac / Linux: use ./adb instead of .\\adb.exe.", "Mac / Linux에서는 .\\adb.exe 대신 ./adb를 사용하세요."),style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}){Text(ui("Open Developer options", "개발자 옵션 열기"))}
        Button(onClick={message=ShizukuAccess.request(this@SetupActivity,74)},enabled=!applying){Text(if(authorized)ui("Check Shizuku authorization", "Shizuku 권한 확인") else ui("Authorize / check connection", "권한 허용 / 연결 확인"))}
        }
        OutlinedButton(onClick={ShizukuAccess.copy(this@SetupActivity)}){Text(ui("Copy connection report", "연결 진단 정보 복사"))}
        if(applying)CircularProgressIndicator()
        if(!applying&&authorized&&(flip6||prefs.getBoolean("wallpaper_verified",false)))Button(onClick={go(3)}){Text(ui("Continue", "계속")) }
        if(!flip6&&!applying&&authorized)Button(onClick={startApply()}){Text(if(attempted)ui("Retry wallpaper setup", "배경화면 설정 다시 시도") else ui("Apply required wallpapers", "필요한 배경화면 적용"))}
        if(!rootSelected.value){
        Text(ui("After reboot, start Shizuku again. Allow Shizuku and Duo to run in the background; keep Developer options enabled.", "휴대전화를 다시 시작한 뒤에는 Shizuku를 다시 실행하세요. Shizuku와 Duo가 백그라운드에서 실행되도록 허용하고 개발자 옵션을 켜 둬야 합니다."),style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={link("https://shizuku.rikka.app/guide/setup/")}){Text(ui("Official Shizuku setup guide", "Shizuku 공식 설정 안내"))}
        }
       }
       3->{
        OutlinedButton(onClick={open(Intent(this@SetupActivity,PrivacyActivity::class.java))}){Text(ui("Review privacy & consent", "개인정보 및 권한 동의 확인"))}
        Text(ui("Ready for the animation", "애니메이션을 시작할 준비가 됐어요"),style=MaterialTheme.typography.headlineSmall)
        Text(if(flip6)ui("Flip 6 uses Samsung’s hinge sensor directly. Duo does not replace your wallpaper or control One UI’s screen switching. Finish these Android permissions so the overlay can run.", "Flip 6은 삼성 힌지 센서를 직접 사용합니다. Duo는 배경화면을 바꾸거나 One UI의 실제 화면 전환을 제어하지 않습니다. 다른 앱 위에 애니메이션을 표시하려면 아래 Android 권한 설정을 완료하세요.") else ui("The required Samsung fold wallpaper is configured on both home screens. Finish these Android permissions so the animation can run over other apps.", "삼성 폴더블 배경화면이 양쪽 홈 화면에 설정되었습니다. 다른 앱 위에 애니메이션을 표시하려면 아래 Android 권한 설정을 완료하세요."))
        OutlinedButton(onClick={open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}){Text(if(overlay)ui("Overlay access granted", "다른 앱 위에 표시 권한 허용됨") else ui("Allow display over other apps", "다른 앱 위에 표시 허용"))}
        OutlinedButton(onClick={open(Intent(this@SetupActivity,PrivacyActivity::class.java))}){Text(if(accessibility)ui("Accessibility connected", "접근성 서비스 연결됨") else ui("Enable Duo accessibility", "Duo 접근성 서비스 켜기"))}
        Text(ui("1. Settings → Accessibility → Installed apps → Duo Fold Live: try enabling it. 2. If Restricted setting appears: Settings → Apps → Duo Fold Live → ⋮ → Allow restricted settings; confirm. 3. Return to Accessibility and enable Duo. If the ⋮ menu is missing, report the exact warning and your Android/One UI version. Overlay permission is separate. Enable accessibility only if you trust the app.", "1. '설정 → 접근성 → 설치된 앱 → Duo Fold Live'로 이동해 Duo를 켜세요. 2. '제한된 설정' 안내가 나오면 '설정 → 애플리케이션 → Duo Fold Live → 오른쪽 위 ⋮ → 제한된 설정 허용'을 선택하고 확인하세요. 3. 접근성 화면으로 돌아와 Duo를 켜세요. ⋮ 메뉴가 보이지 않으면 표시되는 경고 문구와 Android/One UI 버전을 확인해 주세요. '다른 앱 위에 표시' 권한은 별도로 설정해야 합니다. 앱을 신뢰하는 경우에만 접근성을 허용하세요."),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}){Text(ui("Open app info", "앱 정보 열기"))}
        OutlinedButton(onClick={if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),75)}){Text(ui("Allow service notifications", "서비스 알림 허용"))}
        Text(ui("Default: Windowed Glass, inspired by iPhone Duo with even more windowed glass. Choose styles in Folding animation styles. Keep-awake is optional; choose it in Privacy & controls.", "기본 효과는 iPhone Duo에서 영감을 받은 'Windowed Glass'입니다. '폴딩 애니메이션 스타일'에서 다른 스타일을 선택할 수 있습니다. 화면 켜짐 유지는 선택 사항이며 '개인정보 및 제어'에서 설정할 수 있습니다."))
        Button(onClick={
         if(!authorized){message=ui("Authorize your selected connection before finishing.", "완료하기 전에 선택한 연결 방식의 권한을 허용하세요.");attempted=false;go(2);return@Button}
         prefs.edit().putInt("state",2).commit()
         getSharedPreferences("standalone",0).edit().putBoolean("enabled",true).apply()
         startForegroundService(Intent(this@SetupActivity,FoldBackgroundService::class.java))
         StandaloneService.instance?.restart()
         startActivity(Intent(this@SetupActivity,FlipHomeActivity::class.java))
         if(!flip6&&custom&&hasPhoto)startActivity(Intent(this@SetupActivity,org.duofold.live.wallpaperlayer.WallpaperActivity::class.java).putExtra("auto_enable",true))
         finish()
        },enabled=consent&&overlay&&accessibility&&(flip6||prefs.getBoolean("wallpaper_verified",false))){Text(ui("Finish & enable animation", "완료하고 애니메이션 켜기"))}
       }
      }
      if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
      if(stage>0&&!applying)TextButton(onClick={message="";go(stage-1)}){Text(ui("Back", "뒤로"))}
     }
    }
   }
  }
 }
 override fun onDestroy(){Shizuku.removeRequestPermissionResultListener(listener);if(!applying)releaseHelper();super.onDestroy()}
}

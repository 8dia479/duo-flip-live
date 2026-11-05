package org.duofold.live

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Disclosures are separate, with explicit acceptance before access is used. */
class PrivacyActivity:ComponentActivity(){
 private fun ui(english:String,korean:String):String =
  if(resources.configuration.locales[0]?.language=="ko") korean else english
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  FoldAwakeDefault.reconnect();FoldAwakeDefault.tick(this@PrivacyActivity)
  setContent{DuoTheme{
   var revision by remember{mutableIntStateOf(0)}
   val access=remember(revision){RuntimeConsent.accepted(this@PrivacyActivity,"accessibility")}
   val screen=remember(revision){RuntimeConsent.accepted(this@PrivacyActivity,"screen")}
   var awake by remember{mutableStateOf(FoldAwakeDefault.enabled(this@PrivacyActivity))}
   var awakeStatus by remember{mutableStateOf(FoldAwakeDefault.status)}
   LaunchedEffect(Unit){while(true){awakeStatus=FoldAwakeDefault.status;delay(500)}}
   Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Text(ui("Privacy & controls", "개인정보 및 제어"),style=MaterialTheme.typography.headlineMedium)
    Text(ui("Choose whether to enable Duo’s access. Declining leaves the animation off. You can withdraw consent and stop background work here at any time.", "Duo가 필요한 권한을 사용하도록 허용할지 선택하세요. 동의하지 않으면 애니메이션은 꺼진 상태로 유지됩니다. 여기에서 언제든 동의를 철회하고 백그라운드 작업을 중지할 수 있습니다."))
    Button(onClick={disclose("accessibility"){revision++}},enabled=!access){Text(if(access)ui("Accessibility use accepted", "접근성 사용에 동의함") else ui("Review accessibility use", "접근성 사용 내용 확인"))}
    Button(onClick={disclose("screen"){revision++}},enabled=!screen){Text(if(screen)ui("Screen processing accepted", "화면 처리에 동의함") else ui("Review screen processing", "화면 처리 내용 확인"))}
    if(access)OutlinedButton(onClick={startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}){Text(ui("Android accessibility settings", "Android 접근성 설정 열기"))}
    Text(ui("Keep cover awake on close", "닫을 때 커버 화면 켜짐 유지"),style=MaterialTheme.typography.titleMedium)
    Text(ui("Optional. Changes Samsung’s fold-lock setting and uses a bounded wake hold during closing. May increase battery use. Power-button locking is unchanged. Turning this off restores the setting recorded before Duo changed it, if it has not since been changed elsewhere. For upgrades from older builds, the original setting is unknown; check Samsung’s Lock when folded setting yourself.", "선택 기능입니다. 삼성의 '접었을 때 잠금' 설정을 변경하고, 기기를 접는 동안 제한된 시간 동안 화면이 꺼지지 않도록 합니다. 배터리 사용량이 늘어날 수 있습니다. 전원 버튼으로 잠그는 동작은 바뀌지 않습니다. 이 기능을 끄면 다른 앱이나 사용자가 설정을 변경하지 않은 경우 Duo가 변경 전 기록한 값으로 복원합니다. 이전 버전에서 업그레이드한 경우 원래 설정을 알 수 없으므로 삼성 설정의 '접었을 때 잠금' 항목을 직접 확인하세요."))
    Switch(checked=awake,enabled=access&&screen,onCheckedChange={FoldAwakeDefault.setEnabled(this@PrivacyActivity,it);awake=FoldAwakeDefault.enabled(this@PrivacyActivity);if(awake)org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this@PrivacyActivity)})
    Text(awakeStatus,style=MaterialTheme.typography.bodySmall)
    Button(onClick={RuntimeControls.stopAll(this@PrivacyActivity);awake=false;revision++}){Text(ui("Stop all Duo background work", "Duo의 모든 백그라운드 작업 중지"))}
    OutlinedButton(onClick={RuntimeConsent.withdraw(this@PrivacyActivity);awake=false;revision++}){Text(ui("Withdraw both consents & stop", "두 가지 동의 철회 및 중지"))}
    Text(ui("Stopping retains your photos and preferences. Android permissions remain granted until you revoke them in Settings. Samsung’s underlying home wallpaper remains installed; choose another wallpaper in Samsung Settings if desired.", "중지해도 사진과 환경설정은 유지됩니다. Android 권한은 설정에서 직접 해제할 때까지 계속 허용된 상태입니다. 삼성의 홈 화면 배경화면도 그대로 유지됩니다. 원한다면 삼성 설정에서 다른 배경화면을 선택하세요."),style=MaterialTheme.typography.bodySmall)
    if(access&&screen)Button(onClick={org.duofold.live.wallpaperlayer.WallpaperRestore.resume(this@PrivacyActivity);StandaloneService.instance?.restart();finish()}){Text(ui("Done", "완료"))}
    else TextButton(onClick={finish()}){Text(ui("Back without enabling", "활성화하지 않고 돌아가기"))}
    HorizontalDivider()
    Text(remember { assets.open(if(resources.configuration.locales[0]?.language=="ko") "privacy-ko.txt" else "privacy.txt").bufferedReader().use { it.readText() } },style=MaterialTheme.typography.bodySmall)
   }
  }}
 }
 private fun disclose(scope:String,accepted:()->Unit){
  val accessibility=scope=="accessibility"
  AlertDialog.Builder(this@PrivacyActivity).setTitle(if(accessibility)ui("Accessibility service disclosure", "접근성 서비스 사용 안내") else ui("Screen processing disclosure", "화면 처리 안내"))
   .setMessage(if(accessibility)
    ui("Duo uses Android’s AccessibilityService to host non-interactive animation overlays across both displays while fold animation is enabled, including over other apps. It is not an accessibility tool for people with disabilities. This service does not read window text, retrieve UI content, take screenshots, perform gestures, or automate taps. Screen images are obtained separately through your authorized Shizuku or root helper. Separately, the optional custom-wallpaper instrumentation helper uses UiAutomation window events, root package names, focus and geometry to place your photo behind Home and lock-screen UI; it does not read node text. This metadata stays local and may appear in diagnostic reports you choose to share. Accept to permit this use; enabling the service in Android Settings is a separate step.", "Duo는 폴딩 애니메이션이 켜져 있는 동안 Android 접근성 서비스를 사용해 안쪽 화면과 커버 화면 모두에 다른 앱 위로도 표시되는 비대화형 애니메이션 오버레이를 띄웁니다. 이 서비스는 장애가 있는 사용자를 위한 보조 기술 기능이 아닙니다. 창의 글자나 앱 UI 내용을 읽지 않으며, 접근성 스크린샷을 찍거나 제스처·탭을 자동으로 실행하지 않습니다. 화면 이미지는 별도로 권한을 허용한 Shizuku 또는 루트 도우미를 통해 가져옵니다. 선택 기능인 사용자 배경화면 도우미는 UiAutomation의 창 이벤트, 앱 패키지 이름, 포커스 및 화면 위치 정보를 이용해 홈 화면과 잠금 화면 UI 뒤에 사진을 배치합니다. UI 요소의 글자를 읽거나 사용자 동작을 자동 실행하지 않습니다. 이 메타데이터는 기기 안에만 저장되며, 사용자가 직접 공유하기로 선택한 진단 보고서에 포함될 수 있습니다. 이 사용에 동의해도 Android 설정에서 접근성 서비스를 직접 켜는 단계는 별도로 진행해야 합니다.")
    else ui("While fold animation is enabled and the phone is unlocked, Duo’s authorized Shizuku or root helper captures and mirrors displayed app content, which may contain personal information, to render live glass and fold transitions. Frames are processed locally in memory; Duo does not save them as recordings or upload them. Secure/protected captures are rejected. The helper also reads filtered Samsung wallpaper/hinge diagnostics and controls display handoffs. This privileged capture route does not show Android’s MediaProjection prompt. Accept only if you want this screen processing. Stop all or withdraw consent here to stop it; chosen wallpaper photos and diagnostic reports are stored separately as described below.", "폴딩 애니메이션이 켜져 있고 휴대전화 잠금이 해제된 동안, Duo는 권한을 허용한 Shizuku 또는 루트 도우미를 통해 화면에 표시되는 앱 콘텐츠를 캡처하고 복제하여 실시간 글래스 효과와 폴딩 전환을 표현합니다. 화면에는 개인정보가 포함될 수 있습니다. 화면 프레임은 기기 메모리 안에서만 처리하며 녹화 파일로 저장하거나 업로드하지 않습니다. 보안 또는 보호된 화면은 캡처하지 않습니다. 도우미는 필터링된 삼성 배경화면·힌지 진단 정보도 읽고 화면 전환을 지원합니다. 이 권한 기반 캡처 방식에서는 Android MediaProjection 권한 창이 표시되지 않습니다. 화면 처리를 허용하려는 경우에만 동의하세요. 여기에서 모든 작업을 중지하거나 동의를 철회하면 화면 처리가 중단됩니다. 선택한 배경화면 사진과 진단 보고서는 아래 설명처럼 별도로 저장됩니다."))
   .setNegativeButton(ui("Decline", "동의 안 함"),null)
   .setPositiveButton(ui("Accept", "동의")){_,_->RuntimeConsent.accept(this@PrivacyActivity,scope);accepted()}.show()
 }
}

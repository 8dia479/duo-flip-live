# Galaxy Z Flip 6 포팅 조사 및 진행 상태

기준: 2026-11-01, 원본 `joeconsorti/duo-fold-live` main `d9f836b4069a38feaf63b17a2a5ac31ea2880613`. **포팅 미완성. Flip 6에서 실행하거나 APK를 설치해 검증하지 않았다.** 이 문서는 지원 선언이나 설치 지침이 아니다.

## Stage 1 — 출처 및 라이선스

- 작업 저장소 https://github.com/8dia479/duo-flip-live 는 이미 원본의 GitHub fork다. 이번 작업에서 새 fork를 만들지 않았다. 로컬 `origin`은 이 fork이고 `upstream`은 https://github.com/joeconsorti/duo-fold-live.git 로 설정해 main을 fetch했다. 로컬 Git 설정은 다른 클론에 자동 전달되지 않는다.
- 원본의 `LICENSE`는 MIT이며 저작권 및 허가 고지 보존이 조건이다. GitHub API의 라이선스 분류는 `NOASSERTION`이므로 파일 자체를 확인했다. `THIRD_PARTY_NOTICES.md`, `upstream/`, 앱의 licenses도 보존해야 한다. 삼성 배경화면 자산은 포함하지 않는다.
- 새로운 클론에서 원본 추적: `git remote add upstream https://github.com/joeconsorti/duo-fold-live.git` 후 `git fetch upstream`. 기존 upstream이 있으면 먼저 `git remote -v`로 확인한다.

## Stage 2 — 기존 구현

- `LiveAngles`가 투명 오버레이의 토큰으로 삼성 배경화면에 명령을 보내고, Shizuku ADB 또는 기존 root 권한의 `AngleReader`가 `logcat`의 `SprWallpaper|FoldInteractive` 응답에서 `AngleParser`로 0–180도 값을 읽는다. 액션과 가시성 및 타임스탬프를 검사한다. 지속적인 센서 이벤트가 보장되는 공용 API라고 가정하면 안 된다. 원본의 `FoldProbeActivity`에는 별도로 공용 `TYPE_HINGE_ANGLE` 진단 기능이 있으나 운영 애니메이션의 센서 대체재가 아니다.
- `ClosedHingeGate`, `AnimationModePolicy`, `FoldThreshold`와 최신 샘플 여부가 상태·방향·렌더링 허용을 결정한다. `AngleReader`는 샘플이 오래되면 관련 handoff 등을 해제하고, heartbeat가 중단되면 helper를 정리한다. 이 구조가 모든 강제 종료 상황에서 시스템 상태를 복구한다는 뜻은 아니다.
- `DuoGlass`, `ClassicGlassShader`, projection 관련 코드와 렌더링 경로는 힌지 각도 기반 효과를 제공한다. `MotionGlass`의 회전 센서는 별도 시각 효과이며 힌지 각도가 아니다. 프레임 제출은 화면에 실제 픽셀이 보인다는 증거가 아니다.
- `CoverHandoff`, `ConcurrentController`, `PreviewExpansion`, `HandoffFade`, `InnerLiveMirror` 등은 Shizuku 및 비공개 SurfaceControl/디스플레이 API와 오버레이를 사용해 삼성 전환 주변의 프리뷰·홀드·페이드를 관리한다. One UI의 물리적 패널 전환을 완전히 대체하거나 제어하는 것은 아니다. 원본 README에도 패널 전환 블랙아웃과 잠금 해제 깜빡임이 알려진 문제로 기록되어 있다.
- 권한은 Shizuku ADB 모드 또는 선택적 기존 root, 오버레이 및 접근성, 삼성의 설치된 인터랙티브 배경화면에 의존한다. `WallpaperSetupService`는 HOME 배경화면 두 슬롯을 변경할 수 있으므로 기기 검증 없이 실행하면 안 된다. 기존 UI에는 활성화, 동작 방향, 부드러움, 진단 등의 설정이 있으나 Flip 전용 가상 각도 테스트 모드는 확인되지 않았다.
- `BuiltInPanel`은 디스플레이 ID 0/1 및 비공개 타입 확인을 가정하며 실패 시 Fold 해상도로만 대체한다. `LiveAngles`에는 화면 비율 `.7`로 내부 패널을 판별하는 경로가 있다. Flip의 길쭉한 내부 화면에는 이 판별이 잘못될 수 있다. `FoldGeometry` 등에는 책 모양 경첩과 회전 가정이 있으므로 실제 렌더링 호출 경로를 추적해 축과 기준점을 수정해야 한다.
- `DeviceCompatibility.requireEligible`은 Android 17/SDK 37만 허용하며 wallpaper setup, handoff 등에서 사용된다. `WallpaperProfile`은 Fold 7/8 배경화면 자산만 선택한다. SDK 제한을 단순히 완화하거나 Fold 프로필을 Flip에 지정하면 안전성과 호환성이 입증되지 않는다. 이 조사에서는 제한을 변경하지 않았다.

## Stage 3 — Flip 6에서 확인해야 할 사항

기기 및 펌웨어별 실제 센서 목록, 연속 각도 수신 가능성, 삼성 wallpaper 컴포넌트/응답 및 배경화면 자산, 내부·커버 패널 식별과 ID 재할당, 디스플레이 회전/크기/밀도, SurfaceControl 권한과 패널 전환 시점은 아직 확인되지 않았다. 모델명이나 Android 버전만으로 이 기능을 추정하지 않는다. AndroidX WindowManager의 folding feature는 자세/위치 정보이지 연속 각도 제공을 보장하지 않는다.

실기기 조사 시 기존 진단 화면을 사용해 센서 이름, 타입, 샘플 시각과 값, 펌웨어 및 디스플레이 메트릭을 수집하고, 개인정보가 포함될 수 있는 로그는 공유 전에 검토한다. 삼성 wallpaper 변경이나 전원/회전 override는 명시적 동의와 복구 경로를 확보할 때까지 금지한다.

## 남은 Stage 4–10

1. 읽기 전용 진단으로 실기기 센서와 화면을 확인하고, 신선도·범위 검사 및 fail-closed를 갖춘 각도 소스 인터페이스를 만든다. 공용 센서가 연속 각도를 제공하지 않으면 기존 삼성 경로의 호환성을 별도 검증한다.
2. 물리 패널 정체성과 동적 치수/회전을 식별하고, 좌우형 가정을 상하형으로 바꾼다. 패널이 모호하면 효과 및 특권 제어를 중단한다.
3. 기존 엔진을 재사용하여 각도→진행률과 축을 연결한다. 가상 각도 테스트는 실기기 제어 경로와 격리하고 정지·역방향·빠른 변화와 중간 정지를 검증한다.
4. 실제 핸드셋에서 정상/오류 시 Samsung 기본 동작, Binder 종료, 오버레이 및 특권 상태 복구를 확인한 후에만 선택적 시스템 통합을 허용한다. 오작동 시 자동으로 기본 동작으로 돌아가야 한다.
5. 각 단계에서 빌드 및 단위 테스트를 실행하고, 에뮬레이터 테스트와 실제 기기 테스트를 분리해 기록한다. 시스템 전환의 시각적 성공은 실제 기기 촬영 등으로 확인해야 한다.

## 이번 환경의 검증 결과

- GitHub API 및 `git fetch --depth=1 upstream main`: 성공. 원본 main과 작업 시작 커밋 일치.
- `python3 tools/check_motion_projection.py`, `check_inner_stretch.py`, `check_window_shading.py`: 통과. 원본 수학적 검사이며 Flip이나 Android 런타임 테스트가 아니다.
- `./gradlew :app:assembleGithubDebug :app:testGithubDebugUnitTest`: Java 미설치로 시작 실패. Android SDK, adb, Gradle도 설치되어 있지 않다. 이 환경에서 Gradle 배포 서버 및 Google Android SDK 서버에 네트워크 연결이 차단되어 도구와 Android/Google Maven 의존성을 설치하지 못했다.
- 실제 기기, 에뮬레이터, APK 빌드, 설치, Shizuku 연결, 센서/디스플레이 및 시스템 전환: 미실시. **APK 제공 불가.**

## 후속 개발 — 격리된 Flip 미리보기 (2026-11-01)

- `FlipPreviewActivity`는 설정 첫 화면이나 메인 화면의 **Experimental Flip preview**에서 열 수 있다. 가상 각도 슬라이더와 선택적인 공용 `TYPE_HINGE_ANGLE` 리스너는 앱 내부 합성 이미지에만 적용된다. 보이는 미리보기는 `FrostSurface`와 원본 Duo 셰이더, `DuoShadeCurve`를 재사용한다. 수동 각도는 시간 기반 자동재생 없이 유지되고 역방향 이동도 가능하다.
- 이 화면은 Shizuku 연결, 화면 캡처, 배경화면 변경 또는 시스템 화면 전환을 하지 않는다. 센서 이벤트의 범위·유한성·시각 순서를 검사하며 이벤트가 멈추면 마지막 값을 표시하고 경과 시간을 보여준다. 이는 시스템 경로의 안전한 stale 처리로 검증된 것이 아니다. 공용 센서가 연속 값을 제공하지 않을 수 있으며, Flip 6의 실제 수신 여부는 미확인이다.
- `FlipPreviewPolicy`는 미리보기의 가정된 수평 축을 기존 셰이더 좌표로 변환한다. 실제 물리 방향과 커버 핸드오프의 검증을 대체하지 않는다. 디스플레이 목록은 동적 크기, 회전, 밀도, 상태를 표시하지만 패널을 추측해 분류하지 않는다.
- SM-F741 계열의 기존 Fold wallpaper 프로필, 라이브 각도 리더 및 주 오버레이 시작을 차단한다. 이 차단은 Flip 지원 완료를 뜻하지 않으며 모든 시스템 영향 경로의 안전성이 확인된 것도 아니다. 다른 모델의 기존 동작은 유지한다.
- [삼성 Flip6 사양](https://www.samsung.com/ph/business/smartphones/galaxy-z/galaxy-z-flip6-sm-f741bzsephl/)은 메인 2640×1080 및 커버 720×748을 명시한다. 모델/모드별 실제 논리 크기, 접근 가능한 센서와 화면 ID의 증거로 사용하지 않으며 코드에 해상도를 고정하지 않았다. [Android 센서 API](https://developer.android.com/reference/android/hardware/Sensor#TYPE_HINGE_ANGLE)는 공용 센서 타입을 정의하나 특정 기기에서의 존재나 연속성은 확인해야 한다.
- GitHub Actions에서 `09b21e9`의 `testGithubDebugUnitTest assembleGithubDebug`가 통과하고 디버그 APK가 Actions artifact로 생성됐다. 이는 미리보기 빌드이며 Flip 6용 검증된 APK나 서명된 릴리스가 아니다. 이후 변경은 별도 CI로 확인해야 한다. 로컬에서는 PyPI `jdk4py` Java 17 런타임을 설치했으나 Gradle 배포 다운로드가 차단되어 빌드하지 못했다.

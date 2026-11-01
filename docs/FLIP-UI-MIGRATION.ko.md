# Duo Flip UI 전환

## 기존 화면 조사

- **Flip에서도 필요한 기능:** `FlipPreviewActivity`의 합성 이미지 미리보기와 공용 센서·방향·노이즈 처리, CSV 기록, `FrostSurface`/Duo 셰이더, Shizuku 연결 확인. 새 홈과 설정에서 진입한다.
- **Fold 전용으로 일반 UI에서 숨김:** 기존 `MainActivity`, `SetupActivity`의 Fold 7/8 wallpaper 선택과 자동 적용, 커버 미러·전원/회전/고급 handoff 실험, 사진 wallpaper 호스트, Fold 전용 설정. 소스와 기존 구현은 삭제하지 않았다. 앱의 설정 진입점·알림·타일은 새 홈을 연다.
- **미검증/비활성:** Flip 6의 시스템 화면 전환, Fold wallpaper 프로필, Fold 방식의 keep-awake·커버 미러. 기존 차단은 유지되며 Shizuku 연결만으로 기능을 활성화하지 않는다. 미리보기의 가상 각도는 실제 센서와 분리된다.

## Setup 3/4의 원인

기존 `MainActivity`는 `FirstRun.required`가 true면 항상 `SetupActivity`로 보낸다. `SetupActivity`는 `first_run.step`을 저장해 다음 실행에 복원하고, 단계 2를 `Setup 3 of 4`로 표시한다. 이 단계의 Continue는 Shizuku 권한 **및** `wallpaper_verified`를 요구한다. Flip 6은 `WallpaperProfile.resolve`에서 Fold 프로필을 거부하므로 권한을 허용해도 wallpaper 검증이 완료되지 않아 Continue가 나타나지 않는다. Shizuku 연결 자체는 기존 단계 또는 메인 화면 전환을 보장하지 않는다. 이번 변경은 새 런처에서 기존 온보딩으로 자동 진입하지 않으며 저장된 상태를 삭제하지 않는다.

## 새 구조

`FlipHomeActivity`는 권한과 구형 온보딩 상태에 관계없이 홈에서 시작한다. 홈 → 설정 → 고급 진단과 홈 → 미리보기 경로가 있다. 미리보기 설정은 `flip_preview`에 저장하고 기존 미리보기가 읽는다. 연결 상태는 읽기 전용으로 표시하고 Shizuku 권한 요청은 명시적 버튼만 사용한다. 화면 식별은 물리 크기에 의한 후보 표시일 뿐 패널 라우팅에 사용하지 않는다. 센서가 다양한 값을 내보내더라도 연속 센서로 인증하지 않는다. 시스템 애니메이션과 속도는 지원 검증 전까지 꺼져 있다.

## 검증 상태

새 화면은 **NOT TESTED ON DEVICE**. 첫 실행·Shizuku 연결 변화·회전·재실행·작은 화면·접근성·실제 센서 및 화면 전환은 기기/에뮬레이터에서 검증이 필요하다. `FlipHomePolicyTest`는 센서 상태 분류를 검사하고 `tools/tests/test_flip_launcher.py`는 런처와 홈의 구조를 검사하지만 실제 Activity 화면 테스트를 대체하지 않는다.

# 힌지 각도와 현지화 조사

`FlipHomeActivity`와 `FlipPreviewActivity`는 `SensorManager.getDefaultSensor(TYPE_HINGE_ANGLE)`로 공용 센서를 등록한다. 이벤트의 첫 번째 float은 `FlipDiagnostics.Hinge` → `FlipPreviewPolicy.Samples`에서 범위(0–180), 유한성, 타임스탬프를 검사한 뒤 `raw()`로 유지된다. 0·90·180으로 입력을 강제하는 코드는 없다. `Math.round`는 관측 유형을 판단하는 정수 버킷에만 사용되며 원시값이나 렌더링 값은 바꾸지 않는다. 기본 deadband는 0이다. 이전 홈과 미리보기 UI는 각각 소수점 없는 포맷과 `roundToInt()`로 표시 정밀도를 줄였다. 현재는 원시 각도를 소수점 한 자리로 표시한다.

3개 값만 나타나는 원인이 센서/펌웨어에서 실제로 단계값을 내보내기 때문인지는 연결된 Flip 6이 없어 확인하지 못했다. 공용 센서의 값과 빈도를 기록해 확인해야 한다. 미리보기에서 가상 입력과 실제 입력은 별도로 표시하며 실제 센서 첫 이벤트 전에는 가상값을 실제값처럼 표시하지 않는다. 미리보기 필터는 사용자가 조절할 수 있으며 원시값과 필터 적용값을 별도로 보여준다. 센서 메타데이터와 이벤트 간격도 표시한다. 이벤트는 on-change일 수 있으므로 정지 중 간격만으로 센서 오류를 단정하지 않는다.

기존 `FoldProbeActivity`에는 삼성 유형 65686 및 `com.samsung.permission.SSENSOR` 진단이 있다. Flip 미리보기는 해당 유형/이름의 센서가 목록에 있는지와 권한 상태만 확인한다. 이 유형의 의미·접근·범위·연속성은 Flip 6에서 검증하지 않았으므로 새로 자동 등록하거나 공용 센서를 대체하지 않는다. 기존 Fold 경로의 삼성 FoldInteractive wallpaper 명령 및 Shizuku logcat 접근도 별도 경로이며 Flip 6 호환성은 미검증으로 차단되어 있다.

Flip 홈과 미리보기의 신규 문자열은 기본 `values` 영어, `values-ko` 한국어로 제공한다. Android 시스템 설정에 따라 선택된다. 보존된 구형 Fold 내부 화면 및 백그라운드 알림 전체의 번역은 아직 완료되지 않았다. 기기에서 값의 연속성, 센서 권한, 번역 화면 및 시스템 전환은 모두 **NOT TESTED ON DEVICE**.

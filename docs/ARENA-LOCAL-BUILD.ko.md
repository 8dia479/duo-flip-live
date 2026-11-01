# Arena 샌드박스 직접 빌드

## 프로젝트 요구사항

- Java 17 JDK, Gradle Wrapper 8.13, Android Gradle Plugin 8.10.1, Kotlin 2.1.20.
- Android compile SDK 36, min SDK 34. GitHub Actions 기존 설정은 Build Tools 35.0.0을 설치한다.
- 앱은 `github`과 `play` flavor를 갖는다. 아래 명령은 `githubDebug`를 사용한다.

## 현재 샌드박스 상태 (2026-11-01)

`/home/user/jdk4py/jdk4py/java-runtime`에 Temurin 17.0.9 **런타임**만 있다. 전체 JDK, Gradle 배포판, Android SDK 및 의존성 캐시는 설치되지 않았다. `api.adoptium.net`, `dl.google.com`, `services.gradle.org`, Maven Central, JitPack에 대한 직접 연결이 실패했다. 공식 GitHub 릴리스도 허용되지 않은 별도 다운로드 호스트로 리디렉션되어 사용할 수 없었다. 이 환경에서 직접 Android 빌드나 APK 검증을 수행하지 못했다. GitHub Actions 성공은 별도 CI 결과다.

네트워크 허용이나 검증된 오프라인 도구·의존성 캐시가 제공되면 아래 절차로 재구성한다. **다운로드가 차단된 현재 환경에서 실행 완료된 절차가 아니다.**

## 설치 절차

1. Temurin의 공식 Adoptium API에서 Linux x64 **JDK 17**을 다운로드하고 공식 체크섬을 검증한 뒤, 작업 공간 내 `toolchain/jdk-17`에 압축 해제한다. JRE만으로는 충분하지 않다.
2. Google 공식 Android Command-line Tools를 내려받아 Google이 제공하는 체크섬으로 검증한다. SDK 루트의 `cmdline-tools/latest/` 아래에 `bin/sdkmanager`가 위치하도록 설치한다. 라이선스에 동의한 뒤 `platform-tools`, `platforms;android-36`, `build-tools;35.0.0`을 설치한다.
3. 이 저장소의 `./gradlew`가 Gradle 8.13을 설치한다. 배포판을 직접 준비할 경우 Gradle 공식 SHA-256으로 검증한 동일 버전을 사용한다. Google Maven, Maven Central 및 JitPack의 프로젝트 의존성도 접근 가능하거나 검증된 오프라인 캐시가 있어야 한다.
4. 실제 설치 경로를 반영하여 환경을 설정한다:

```sh
export JAVA_HOME="$HOME/toolchain/jdk-17"
export ANDROID_HOME="$HOME/toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$HOME/toolchain/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
java -version
javac -version
sdkmanager --list_installed
./gradlew --version
```

## 단계별 명령

루트에서 순서대로 실행한다. 각 단계의 실패를 수정한 뒤 해당 단계부터 재개한다. 태스크 목록은 `./gradlew :app:tasks --all`로 먼저 확인한다. 아래 태스크는 현재 flavor 구성을 기준으로 한 이름이며, 환경 구성 후 확인이 필요하다.

```sh
opts="--no-daemon --max-workers=1 --console=plain"
./gradlew $opts -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' :app:compileGithubDebugKotlin
./gradlew $opts -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' :app:compileGithubDebugUnitTestKotlin
./gradlew $opts -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' :app:testGithubDebugUnitTest
./gradlew $opts -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' :app:assembleGithubDebug
```

성공하면 `deliverables/`에 APK와 빌드·테스트 결과를 복사하고, 설치된 Build Tools의 `apksigner verify --verbose --print-certs`, `aapt dump badging`, `unzip -l`, `sha256sum`으로 검증한다. 디버그 서명 APK와 공식 릴리스 서명 APK는 다르며 기존 설치를 덮어쓰지 못할 수 있다. APK를 만들지 못했으면 경로나 해시를 만들어 적지 않는다.

## 별도 로컬 테스트

2026-11-01에 허용된 npm 레지스트리에서 내려받은 `coc-java`의 Eclipse Java compiler 및 JUnit과 위 Java 런타임을 이용해 `FlipPreviewPolicyTest`, `FlipMotionTest`, `FlipDiagnosticsTest` 총 8개를 로컬에서 실행해 통과했다. 이는 순수 Java 테스트이며 Kotlin 컴파일, Android APK, 에뮬레이터 또는 실기기 테스트가 아니다. 도구는 저장소 밖의 `/home/user/toolchain`에 임시 설치되었고 영구 환경으로 가정하지 않는다.

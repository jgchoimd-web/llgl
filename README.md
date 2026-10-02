# llgl — NEON DESCENT

구슬이 사이버펑크 도시의 내리막길을 굴러 내려가는 안드로이드 게임입니다.
Kotlin + Jetpack Compose로 만들었고, 게임 엔진 없이 Compose `Canvas`로 직접 그립니다.

| 항목 | 값 |
|---|---|
| 패키지명 | `com.llgl.app` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 37.2 |
| 언어 · UI | Kotlin · Jetpack Compose (Material 3) |
| 빌드 | Android Gradle Plugin 9.4, Gradle 9.8 (wrapper) |

## 게임 방법

- 화면을 **탭**하면 시작합니다.
- 화면을 **좌우로 드래그**해 구슬을 조종합니다. 손가락을 따라 구슬이 1:1로 움직입니다.
- 마젠타 **장벽**에 부딪히면 끝. 시안 **네온 오브**를 지나가면 +50점.
- 내려갈수록 속도가 빨라지고 장벽 간격이 좁아집니다. 점수는 거리 + 오브로 계산되고, 최고 기록은 기기에 저장됩니다.
- 충돌 후 잠깐 뒤에 탭하면 바로 다시 시작합니다.

## 빌드하기

필요한 것: JDK 17 이상, `curl`, `unzip`. Android SDK는 아래 스크립트가 설치합니다.

```bash
# 1. Android SDK 설치 (기본 위치 /opt/android-sdk, 다시 실행해도 안전)
bash scripts/setup-android-sdk.sh

# 2. 디버그 APK 빌드
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 3. 단위 테스트와 lint
./gradlew testDebugUnitTest lintDebug
```

SDK를 다른 곳에 설치하려면 `ANDROID_HOME=/원하는/경로 bash scripts/setup-android-sdk.sh`처럼
실행하세요. 스크립트는 Gradle이 SDK를 찾을 수 있도록 `local.properties`(git 추적 제외)도 만들어 줍니다.

Android Studio를 쓴다면 이 폴더를 그대로 열면 됩니다. Studio가 SDK를 관리하므로 스크립트는 필요 없습니다.

디버그 APK는 저장소에 들어 있는 `app/debug.keystore`로 서명됩니다. 어느 컴퓨터(또는 CI)에서
빌드하든 서명이 같아서, 이미 설치된 앱 위에 덮어 설치(업데이트)가 됩니다. 스토어 배포용 키는 아닙니다.

## 폰에 설치하기

이 저장소는 push마다 GitHub Actions가 디버그 APK를 빌드해 아티팩트로 올립니다.

1. GitHub 저장소의 **Actions** 탭 → 최신 **Android CI** 실행 → 하단 **Artifacts**에서 `app-debug` 다운로드
2. 압축을 풀어 나온 `app-debug.apk`를 폰으로 옮긴 뒤(카카오톡 나에게 보내기, Google Drive 등) 탭해서 설치
3. "출처를 알 수 없는 앱" 설치 허용을 물으면 허용

USB 디버깅이 켜진 폰이 연결되어 있다면 `adb install app/build/outputs/apk/debug/app-debug.apk`로도 설치할 수 있습니다.

## 프로젝트 구조

```
app/src/main/java/com/llgl/app/
├── MainActivity.kt            # 진입점. 전체 화면 + 화면 꺼짐 방지, GameScreen 표시
├── game/
│   ├── GameEngine.kt          # 순수 Kotlin 게임 로직 (장애물 생성, 충돌, 점수, 속도). Android 의존성 없음
│   ├── Camera.kt              # 도로 좌표(거리 d, 좌우 u) → 화면 좌표 1/d 원근 투영
│   └── GameScreen.kt          # Compose Canvas 렌더링(하늘, 스카이라인, 도로, 건물, 구슬, 파티클)과 HUD, 입력
└── ui/theme/Theme.kt          # Material 3 테마
app/src/test/                  # JVM 단위 테스트 (GameEngineTest, CameraTest)
gradle/libs.versions.toml      # 의존성 버전 카탈로그
scripts/setup-android-sdk.sh   # Android SDK 설치 스크립트
.github/workflows/android.yml  # CI: 빌드 + 테스트 + lint, APK 아티팩트 업로드
```

게임 로직(`GameEngine`)과 그리기(`GameScreen`)가 분리되어 있어서, 규칙을 바꾸고 싶으면
엔진만 고치고 테스트로 확인한 뒤 빌드하면 됩니다.

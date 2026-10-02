# llgl — 거북이 펫

안드로이드 홈 화면(과 다른 앱) 위를 느릿느릿 돌아다니는 픽셀 아트 거북이 "꼬북"입니다.
Kotlin + Jetpack Compose로 만들었고, 거북이는 "다른 앱 위에 표시" 권한으로 띄우는 작은 오버레이 창 안에서 삽니다.

| 항목 | 값 |
|---|---|
| 패키지명 | `com.llgl.app` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 37.2 |
| 언어 · UI | Kotlin · Jetpack Compose (Material 3) |
| 빌드 | Android Gradle Plugin 9.4, Gradle 9.8 (wrapper) |

## 거북이가 하는 일

- 화면 아래를 천천히 걷다가 멈춰서 두리번거리고, 가끔 등껍질에 들어갑니다.
- **탭**하면 놀라서 껍질에 숨었다가 나옵니다. **길게 누르면** 메뉴가 뜹니다: 🥬 상추 주기 · ✋ 쓰다듬기 · ⚙️ 설정 · ✕ 닫기.
- **드래그**하면 들어 올려 옮길 수 있고(다리를 버둥거림), 놓으면 바닥으로 떨어져 다시 걷습니다.
- 상추를 주면 걸어가서 먹습니다. **배부름**과 **행복**은 앱을 꺼 두어도 실제 시간에 따라 천천히 변하고 기기에 저장됩니다. 배부르면 거절해요.
- 가끔 말풍선으로 한마디 합니다(배고프면 "배고파…", 점심·저녁 인사 등). **밤 11시~아침 7시**에는 잠을 자고, 깨우면 투덜댑니다. 아침엔 인사를 합니다.
- 잠금 화면에서는 보이지 않고, 화면이 꺼지면 애니메이션을 멈춰 배터리를 아낍니다. 알림에서 일시정지·종료할 수 있습니다.

## 설치하고 시작하기

1. APK를 설치하고 앱을 엽니다. ("출처를 알 수 없는 앱" 허용 필요)
2. **1. 다른 앱 위에 표시** → 권한 설정 열기 → 허용. (Android 13 이상이면 **2. 알림 허용**도 눌러 주세요.)
3. **펫 시작**을 누르면 거북이가 홈 화면 아래에 나타납니다. 이름은 설정 화면에서 바꿀 수 있습니다.

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

SDK를 다른 곳에 설치하려면 `ANDROID_HOME=/원하는/경로 bash scripts/setup-android-sdk.sh`처럼 실행하세요.
스크립트는 Gradle이 SDK를 찾을 수 있도록 `local.properties`(git 추적 제외)도 만들어 줍니다.
Android Studio를 쓴다면 이 폴더를 그대로 열면 됩니다.

디버그 APK는 저장소에 들어 있는 `app/debug.keystore`로 서명됩니다. 어느 컴퓨터(또는 CI)에서 빌드하든
서명이 같아서 이미 설치된 앱 위에 덮어 설치됩니다. 스토어 배포용 키는 아닙니다.

push마다 GitHub Actions가 디버그 APK를 빌드해 **Actions → Android CI → Artifacts → `app-debug`**에 올립니다.

## 동작 원리

- `pet/PetBrain.kt` — 순수 Kotlin 상태 기계(걷기·대기·숨기·잠·먹기·들림·낙하), 배고픔/행복 수치, 말풍선 선택.
  시계와 난수를 주입받아 JVM 단위 테스트로 검증합니다.
- `pet/PetSprites.kt` — 24×16 픽셀 프레임을 문자 그리드로 정의. 걷기 2, 깜빡임, 숨기 2, 잠 2, 먹기 3, 들림 2 프레임과 상추.
- `pet/OverlayWindow.kt` — `TYPE_APPLICATION_OVERLAY` 창을 거북이 크기만큼만 띄워 홈 화면 터치를 가리지 않습니다.
  말풍선·메뉴·상추가 있을 때만 창이 위로 커집니다. 드래그는 화면 raw 좌표로 계산합니다.
- `pet/OverlayHost.kt` — 서비스 창 안에서 Compose가 돌도록 Lifecycle/SavedState owner를 제공합니다.
  화면이 꺼지면 lifecycle을 내려 Compose 프레임 클럭을 멈춥니다.
- `pet/PetService.kt` — `specialUse` 포그라운드 서비스. 알림 액션(일시정지/계속/종료), 화면 꺼짐 수신, 상태 저장.
- `MainActivity.kt` — 설정 화면: 이름, 권한 안내, 시작/중지, 배부름·행복 게이지.

## 기기에서 확인할 것 (이 저장소의 CI는 기기 없이 빌드·테스트·lint만 합니다)

권한 흐름 → 펫 시작 → 걷기/탭/롱프레스 메뉴/드래그 → 알림 일시정지·종료 → 화면 회전 → 화면 끄고 켜기 → 밤 시간 수면.

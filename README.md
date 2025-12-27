# SAM Battery (Android Battery Health Checker)

## 📱 앱 소개 (Introduction)
**SAM Battery**는 안드로이드 14/15 이상의 최신 기기에서도 루팅 없이 배터리 상태를 정밀하게 진단할 수 있는 도구입니다. 삼성 갤럭시 기기를 포함한 대부분의 안드로이드 기기에서 작동하며, 두 가지 버전(Non-Root / Full)으로 제공되어 사용자의 환경에 맞게 선택할 수 있습니다.

### 주요 기능
- **실시간 모니터링**: 전압(V), 전류(mA), 전력(W), 온도(°C)를 1초 단위로 정밀 추적.
- **배터리 건강(SOH) 진단**: 충전량과 레벨을 기반으로 실질적인 배터리 수명을 추정합니다.
- **사이클(Cycle) 조회**: (Full 버전) 기기 내부의 정확한 충방전 횟수를 보여줍니다.
- **직관적인 UI**: 충/방전 상태에 따라 직관적인 부호(+/-)와 색상 표기를 지원합니다.

---

## �️ 버전별 차이 (Versions)

| 특징 | Non-Root (기본 버전) | Full Version (시스템 권한) |
| :--- | :--- | :--- |
| **권한 요구** | **없음** (즉시 실행 가능) | **Shizuku / ADB** 필요 |
| **작동 방식** | Android 표준 API (SDK) | 표준 API + 시스템 덤프(dumpsys) |
| **사이클 조회** | 일부 기기만 확인 가능 | **가능** (삼성 기기 확인됨) |
| **추천 대상** | 일반 사용자, 빠른 확인용 | 고급 사용자, 정확한 사이클 확인용 |

---

## ⚙️ 작동 원리 및 구조 (Architecture)

### 1. 데이터 수집 (Data Collection)
이 앱은 안드로이드 시스템(BatteryService)으로부터 데이터를 가져오지만, 버전별로 접근 방식이 다릅니다.

- **Direct Polling (동기화 폴링)**: `Non-Root` 버전은 1초마다 `BatteryManager`와 `Sticky Intent`를 직접 조회합니다. 이를 통해 단순히 이벤트를 기다리는 방식보다 훨씬 빠르고 정확하게 현재 상태(충전/방전)를 파악합니다.
- **Shizuku Injection**: `Full` 버전은 ADB 권한을 가진 Shizuku 프로세스를 통해 `dumpsys batterystats` 명령어를 내부적으로 수행하여, 일반 앱에서는 접근 불가능한 **Cycle Count(충전 횟수)**를 가져옵니다. (주로 삼성 기기에서 작동 확인됨)

### 2. 정밀 부호 로직 (Sign Logic)
많은 안드로이드 기기가 하드웨어 센서 값의 부호(+, -)를 제멋대로 보고하는 문제가 있습니다. SAM Battery는 이를 해결하기 위해 **UI 기반 강제 동기화(WYSIWYG)** 기술을 사용합니다.
- **작동 방식**: 내부 데이터 값이 어떻든, 화면에 **"방전 중"**이라는 텍스트가 표시되는 순간, 앱은 강제로 전류와 전력 값에 마이너스(-) 부호를 할당합니다.
- **결과**: 사용자가 보는 화면의 상태와 숫자의 부호가 100% 일치하게 됩니다.

### 3. 용량 추정 알고리즘 (Capacity Estimation)
배터리 수명(SOH)은 제조사가 제공하는 공식 API가 아닙니다. 이 앱은 자체 알고리즘으로 이를 추정합니다.
- **공식**: `(현재 충전된 전하량 / 현재 배터리 %) * 100 = 완충 시 예상 용량`
- **단위 보정**: 일부 기기는 uAh(마이크로암페어시), 일부는 mAh(밀리암페어시)를 반환합니다. 앱은 20,000 단위를 기준으로 이를 자동 감지하여 단위를 통일합니다.

---

## � 프로젝트 구조 (Project Structure)

```
H:\github\android_battery_info\
├── SAM_Battery_NonRoot/    # [표준 API 버전] 소스 코드
│   ├── app/src/main/java.../BatteryRepository.kt  # 핵심 로직 (Polling)
│   └── app/src/main/java.../MainActivity.kt       # UI & 부호 로직
├── SAM_Battery_Full/       # [Shizuku 버전] 소스 코드
│   ├── app/src/main/java.../ShizukuHelper.kt      # ADB 명령어 처리
│   └── (UI 및 로직은 Non-Root와 동기화됨)
└── Final_Release/          # [최종 빌드된 APK 파일들]
    ├── SAM_Battery_NonRoot_Final_v35.apk
    └── SAM_Battery_Full_Final_v38.apk
```

## 🚀 설치 및 사용 방법
1. **Final_Release** 폴더의 APK를 휴대전화에 복사 및 설치합니다.
2. **Non-Root 버전**: 실행 즉시 사용 가능합니다.
3. **Full 버전**: [Shizuku 앱](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)을 먼저 설치하고 실행한 뒤, SAM Battery를 실행하여 권한을 허용해주세요.

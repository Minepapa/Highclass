# 📚 알림장봇 (AlimjangBot)

> 하이클래스 앱 알림장을 자동으로 캡처하여 MMS로 전송하는 Android 앱

## 🎯 동작 방식

```
1. 하이클래스 앱에서 "알림장" 알림 수신
        ↓
2. 알림의 PendingIntent 자동 실행 → 게시글 오픈
        ↓
3. N초 대기 (페이지 로드)
        ↓
4. 화면 캡처 (MediaProjection)
        ↓
5. MMS로 딸 번호에 전송 📱
```

## 🛠️ 빌드 방법

### 요구사항
- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- Android SDK 34

### 빌드
```bash
./gradlew assembleDebug
```

APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

## 📱 최초 설정 (앱 설치 후)

앱을 설치하고 열면 **필요 권한 4가지**를 순서대로 허용해야 합니다.

### 1. 알림 접근 허용 ⚠️ 가장 중요
```
설정 → 앱 → 특별한 앱 접근 → 알림 접근 허용 → "알림장봇" 켜기
```
> 이 권한이 없으면 알림 감지가 불가능합니다.

### 2. 화면 캡처 권한
앱 내 **[화면 캡처 권한 허용]** 버튼 클릭 후 시스템 다이얼로그에서 허용

### 3. SMS 전송 권한
앱 내 **[SMS 권한 허용]** 버튼 클릭

### 4. 배터리 최적화 제외 ⚠️ 중요
```
설정 → 배터리 → 배터리 사용 최적화 → "알림장봇" → 최적화 안 함
```
> 이 설정이 없으면 백그라운드에서 앱이 강제 종료될 수 있습니다.

### 5. 전화번호 입력
딸의 전화번호를 입력하고 **[저장]** 클릭

---

## ⚙️ 고급 설정

### 하이클래스 패키지명 확인 방법
하이클래스 앱의 실제 패키지명이 다를 수 있습니다. 확인 방법:

1. Play Store에서 하이클래스 검색
2. URL에서 `id=` 이후 값 확인
   - 예: `https://play.google.com/store/apps/details?id=com.hischool.highclass`
   - 패키지명: `com.hischool.highclass`

또는 앱 내 **고급 설정**에서 직접 변경 가능.

### 캡처 딜레이 조정
- 인터넷이 빠른 경우: 2~3초
- 인터넷이 느린 경우: 5~7초
- LTE/5G 기준 3초 권장

---

## ❓ 문제 해결

### 알림이 와도 캡처가 안 돼요
1. 알림 접근 권한 확인 (가장 흔한 원인)
2. 배터리 최적화 제외 여부 확인
3. 하이클래스 패키지명이 올바른지 확인 (고급 설정)
4. 딜레이를 더 길게 설정해보세요

### 화면이 캡처되지만 하이클래스 내용이 안 보여요
- 하이클래스가 스크린샷을 보안으로 차단한 경우
- 캡처 딜레이를 더 길게 설정 (게시글 로드 시간)

### MMS가 전송되지 않아요
1. SMS 권한 확인
2. 전화번호 형식 확인 (010-XXXX-XXXX 또는 010XXXXXXXX)
3. 기기의 기본 문자 앱 설정 확인
4. 데이터 연결 상태 확인 (MMS는 데이터 필요)

---

## 📁 프로젝트 구조

```
app/src/main/java/com/alimjangbot/
├── AlimjangApplication.kt          # 앱 진입점, 알림 채널 생성
├── data/
│   ├── AppSettings.kt              # 설정 저장 (SharedPreferences)
│   └── SendLog.kt                  # 전송 이력 관리
├── service/
│   ├── NotificationWatcher.kt      # 알림 감지 (NotificationListenerService)
│   ├── ScreenCaptureService.kt     # 화면 캡처 (MediaProjection)
│   └── MmsDispatcher.kt           # MMS 전송
├── ui/
│   ├── MainActivity.kt             # 메인 설정 화면
│   └── LogAdapter.kt              # 이력 목록 어댑터
├── receiver/
│   └── BootReceiver.kt            # 부팅 시 서비스 복구
└── util/
    └── ImageUtils.kt              # 이미지 처리, 텍스트→이미지 폴백
```

---

## 🔒 개인정보

이 앱은:
- 모든 데이터를 **기기 내에만** 저장합니다
- 외부 서버로 어떤 데이터도 전송하지 않습니다
- 알림 내용과 캡처 이미지는 MMS 전송 후 즉시 삭제됩니다

---

## 📜 기술 스택

- **언어**: Kotlin
- **최소 SDK**: Android 8.0 (API 26)
- **핵심 API**: NotificationListenerService, MediaProjection, SmsManager
- **UI**: Material Design 3, ViewBinding

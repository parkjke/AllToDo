# Work Log - 2025-12-20

## 📝 작업 요약 (Summary)
안드로이드 네이버 지도 연동 및 카카오맵 안정화 작업을 진행했으나, **최종적으로 해결되지 않음(Unresolved)** 상태로 세션을 종료합니다.

## 🚫 미해결 이슈 (Unresolved Issues)

### 1. Naver Map 401 Unauthorized Error
- **증상**: 네이버 지도 로딩 시 `401 Unauthorized client` 에러 발생 및 지도 타일 로드 실패.
- **시도**: 
  - `AndroidManifest.xml`에 Client ID 확인 (`i7652syq10`).
  - `NaverMapContent.kt` 테마 래퍼(`ContextThemeWrapper`) 적용으로 크래시 해결.
- **원인 추정**: 네이버 클라우드 플랫폼 콘솔에 등록된 패키지명(`kr.alltodo`) 또는 서명 키(SHA-1) 불일치.
- **상태**: **해결 안 됨 (Not Resolved)**

### 2. Kakao Map Android Logic Issues
- **증상**: 
  1. **블랙 스크린**: 맵 공급자 전환(Google -> Kakao) 시 화면이 까맣게 나옴.
  2. **초기 줌 실패**: 앱 최초 실행 시 핀이 없으면 한반도 뷰에서 멈춤 (내 위치로 안 옴).
- **시도**:
  - **초기 줌**: `validPoints > 0` 조건 밖으로 `delay(3000)` 및 줌 로직 이동 (무조건 실행).
  - **블랙 스크린**: `AndroidView` 생성 시 `lifecycle.currentState.isAtLeast(RESUMED)`인 경우 강제 `resume()` 호출 추가.
- **상태**: **해결 안 됨 (Not Resolved)** (사용자 확인 결과 여전히 문제 지속 또는 추가 확인 필요)

## 🔄 변경 사항 (Changes Applied)
- `MainScreen.kt`: 맵 생명주기(`DisposableEffect`) 및 초기화 상태 복구.
- `KakaoMapContent.kt`: 줌 로직 및 리줌(Resume) 방어 코드 적용.
- `NaverMapContent.kt`: 테마 적용 (`Theme.AppCompat`).
- `build.gradle`: 네이버 지도 SDK 의존성 추가.

## 🔜 다음 단계 (Next Steps)
1. **네이버 콘솔 확인**: 실제 등록된 패키지명/SHA-1 해시 재검증.
2. **카카오맵 디버깅**: `resume()` 강제 호출이 실제로 먹히는지 로그 확인, `AndroidView` 재사용 문제 검토.

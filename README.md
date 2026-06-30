# Passkey RP 샘플 패키지

passkey-app(패스키 서버)과 연동하는 **RP(Relying Party) 서버 샘플**과, RP가 임베드하는
**Java SDK(라이브러리)**를 함께 담은 독립 패키지다. 고객사는 이 패키지를 출발점으로 자사 RP
서버를 구축한다.

## 구성

| 경로 | 내용 |
|---|---|
| `rp-app/` | 샘플 RP 서버(Spring Boot). 등록/인증 중계·사용자 매핑·well-known 호스팅. |
| `libs/sdk-java-1.0.0.jar` | RP가 임베드하는 SDK 라이브러리(+ `-sources.jar`). |
| `docs/rp-app-guide.md` | rp-app 사용자 가이드(설정·보안·교체 포인트). |
| `docs/sdk-java-guide.md` | SDK 사용자 가이드(API·ID Token 검증·릴레이 코덱). |

## 빌드 / 실행

```bash
# 실행 가능한 jar 생성 → deploy/rp-app.jar
./gradlew :rp-app:bootJar

# 또는 개발 모드 실행
./gradlew :rp-app:bootRun

# 테스트
./gradlew :rp-app:test
```

Java 17 + Gradle wrapper(8.10) 포함. 별도 Gradle 설치 불필요.

## 필수 설정

rp-app 은 외부 passkey-app 백엔드를 호출한다. 다음을 환경변수/yml 로 주입한다(상세는
[docs/rp-app-guide.md](docs/rp-app-guide.md)):

- `PASSKEY_BASE_URL` — passkey-app 베이스 URL
- `PASSKEY_API_KEY` — 발급받은 API Key
- `PASSKEY_TENANT_ID` — 자사 테넌트 ID
- `PASSKEY_ISSUER_BASE` — ID Token iss 검증용 issuer-base(passkey-app 설정과 일치)
- `RP_RELAY_SECRET` — 등록 릴레이 토큰 HMAC 키(운영은 강한 키 필수)

## SDK jar 교체

새 SDK 버전을 받으면:
1. `libs/` 의 `sdk-java-*.jar` 를 교체한다.
2. `rp-app/build.gradle.kts` 의 `files("$rootDir/libs/sdk-java-1.0.0.jar")` 파일명을 갱신한다.

SDK 사용법은 [docs/sdk-java-guide.md](docs/sdk-java-guide.md) 참고.

## 자사 적용 시 교체 포인트

- **사용자 저장소:** `InMemoryUserStore` 는 데모용(인메모리). 자사 DB(JPA/MyBatis 등)로 교체한다.
- **릴레이 secret / API Key:** 운영용 강한 값으로 주입(데모 키는 운영 프로필에서 기동 차단).
- **well-known:** `rp-app.well-known.*` 설정에 자사 앱 메타데이터(Android 지문 / iOS TeamID·BundleID)
  를 채운다.

자세한 내용은 [docs/rp-app-guide.md](docs/rp-app-guide.md) 를 참고한다.

# 핫리로드 제거 후 운영 절차·설정 주석 상세화 — 설계

- 날짜: 2026-07-01
- 대상 프로젝트: passkey-rp (Spring Boot 3.5.x, Java 17, Gradle)
- 작업 성격: 문서/주석 보강(코드 동작 변경 없음)
- 선행 작업: API Key 파일 핫리로드 제거(merge `f186771`)

## 1. 배경

직전 작업에서 API Key 파일 핫리로드(`ReloadableApiKeySupplier`)를 제거하고, `apiKeySupplier` 빈을 기동 시 env(`passkey.api-key`)를 한 번 캡처하는 `() -> key`로 대체했다. 잔존 흔적은 0건이고 사실관계 서술도 정확하다.

그러나 이 변경으로 생긴 **운영 함의 — "API Key 교체는 이제 env 갱신 후 재기동이 필요하다"** 가 코드 주석·설정·가이드 어디에도 명시돼 있지 않다. 또한 `application.yml`의 `api-key`는 다른 키(`issuer-base`, `rp.relay.secret`)와 달리 설명 주석이 없어 일관성이 떨어진다.

## 2. 목표 / 비목표

### 목표
- "API Key 교체 = env 변경 후 재기동" 운영 절차를 문서·주석에 명시한다.
- `application.yml`의 `api-key`에 다른 키와 같은 수준의 설명 주석을 단다.
- 재기동 필요 메시지를 네 곳에서 일관된 표현("기동 시 1회 읽어 고정", "교체는 재기동")으로 통일한다.

### 비목표
- 코드 동작 변경 없음(주석/문서만).
- Relay/JWKS/ID Token 등 다른 영역 문서는 건드리지 않는다.
- 새 섹션 신설 없이 기존 위치에 보강만 한다.
- 분량 늘리기가 목적이 아니다 — 빠진 운영 맥락만 채운다.

## 3. 편집 대상 4곳 (구체 문안)

### 3.1 `src/main/resources/application.yml` — `api-key` 주석 추가

현재:
```yaml
  api-key:     ${PASSKEY_API_KEY:}
```
교체:
```yaml
  # passkey-app 발급 API Key(X-API-Key). 기동 시 1회 읽어 고정 사용하므로,
  # 키를 교체하려면 이 값을 바꾼 뒤 재기동한다(무중단 핫리로드는 지원하지 않음).
  api-key:     ${PASSKEY_API_KEY:}
```

### 3.2 `PasskeyClientConfiguration.java` — apiKeySupplier javadoc 보강

현재(20-23줄):
```java
    /**
     * SDK 가 매 요청 get() 을 호출하지만, RP 레퍼런스는 기동 시 env(api-key) 값을
     * 한 번 읽어 고정 공급한다. 키 교체는 재기동으로 반영한다.
     */
```
교체:
```java
    /**
     * SDK 는 매 요청 get() 을 호출하지만(런타임 키 교체를 허용하는 계약), RP 레퍼런스는
     * 기동 시 env(passkey.api-key)를 한 번 읽어 고정 공급한다. 따라서 키를 교체하려면
     * PASSKEY_API_KEY(또는 application.yml 의 api-key)를 바꾼 뒤 애플리케이션을 재기동한다.
     */
```

### 3.3 `docs/rp-app-guide.md` §7 보안 노트 — 운영 항목 1줄 추가

보안 노트 목록(로그 마스킹 bullet 다음, 무상태/CSRF bullet 앞 또는 목록 끝) 위치에 추가:
```markdown
- **API Key 교체**: `passkey.api-key`(env `PASSKEY_API_KEY`)는 기동 시 1회 읽혀 고정된다. 키를 회전/폐기하려면 env 를 갱신하고 재기동한다(파일 기반 무중단 핫리로드는 제거됨).
```
(삽입 위치는 목록 맨 끝을 기본으로 한다. 기존 bullet 순서는 바꾸지 않는다.)

### 3.4 `docs/sdk-java-guide.md` §4 — SDK 계약과 RP 선택 분리

현재(100-103줄):
```markdown
**API Key Supplier:** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에 호출된다.
Supplier 구현에 따라 동적 교체도 가능하나, 본 패키지의 RP 레퍼런스는 기동 시 env(api-key)
값을 캡처해 고정 공급한다(키 교체는 재기동으로 반영). 반환값이 null/blank 면 그 요청은
`PasskeyConfigurationException` 으로 fail-fast.
```
교체(두 문단으로 분리):
```markdown
**API Key Supplier (SDK 계약):** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에
호출된다. 반환값이 null/blank 면 그 요청은 `PasskeyConfigurationException` 으로 fail-fast.
Supplier 구현을 시크릿 매니저·파일 감시 등에 연결하면 재기동 없는 키 교체도 가능하다.

**RP 레퍼런스의 선택:** 본 패키지의 RP(`PasskeyClientConfiguration`)는 기동 시
env(`passkey.api-key`)를 한 번 캡처해 고정 공급한다. 키 교체는 env 갱신 후 재기동으로 반영한다
(운영 단순성을 위해 파일 핫리로드는 두지 않음). 무중단 교체가 필요하면 이 빈만 Supplier
구현으로 바꾸면 된다.
```

## 4. 원칙

- **사실 정확성 유지**: SDK가 매 요청 `get()`을 호출한다는 계약은 사실이므로 유지. RP가 그 위에서 "고정 공급"을 선택했다는 관계를 명확히 한다.
- **일관 표현**: 네 곳 모두 "기동 시 1회 읽어 고정" / "교체는 env 갱신 후 재기동" 취지로 통일.
- **되살리지 않기**: 새 문안이 과거 핫리로드 기능을 되살리는 것처럼 읽히면 안 된다 — "핫리로드는 제거/지원 안 함"이라는 부정 서술만 허용.

## 5. 검증

1. 새 문안이 과거 기능을 부활시키지 않는지 확인:
   `grep -rin "api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|mtime\|폴링" src/ docs/`
   → `docs/superpowers/`(기록) 외 0건 유지. (`핫리로드`는 "핫리로드는 제거/지원 안 함" 부정 서술로만 등장 가능.)
2. 주석 편집이 컴파일을 깨지 않는지: `./gradlew compileJava` — BUILD SUCCESSFUL.
3. 네 곳의 재기동-필요 메시지가 서로 모순 없이 일관되는지 육안 확인.

## 6. 리스크

- 낮음. 동작 변경이 없고 주석/문서만 만진다.
- 유일한 주의점: `핫리로드`라는 단어가 문서에 다시 등장하되 반드시 부정문("지원하지 않음/제거됨") 맥락이어야 한다. 검증 1에서 확인한다.

# API Key 핫리로드 제거 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RP 애플리케이션의 파일 기반 API Key 핫리로드 메커니즘을 완전히 제거하고, API Key를 기동 시 env에서 한 번 읽어 고정하는 단순 Supplier로 대체한다.

**Architecture:** SDK 계약(`Supplier<String>`)은 유지한다. SDK의 `RedactingRequestInterceptor`가 매 요청 `apiKeySupplier.get()`을 호출하므로, RP는 항상 같은 값을 반환하는 람다(`() -> key`)를 주입한다. `ReloadableApiKeySupplier`와 파일 폴링·캐시 로직, 관련 설정/필드/테스트/문서를 전부 삭제한다. SDK jar는 무변경.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Gradle, JUnit 5, AssertJ.

## Global Constraints

- 빈 이름 `apiKeySupplier`와 타입 `Supplier<String>`은 변경 금지 — `PasskeyClientConfiguration#passkeyClient(PasskeyProperties, Supplier<String>)`가 이 빈을 주입받는다.
- SDK jar(`libs/sdk-java-1.0.0*.jar`)는 변경하지 않는다.
- `passkey.api-key`(env) 설정과 `PasskeyProperties#apiKey()`는 유지한다.
- `web/SecretRedactor.java`의 `pk_` 마스킹은 유지한다.
- Relay secret(`RelayProperties`/`RelayKeyGuard`/`RegistrationRelayCodec`), JWKS, ID Token 계열은 건드리지 않는다.
- 삭제 후 미사용 import가 남지 않도록 정리한다.
- 검증 기준(최종): `./gradlew test` 전체 green, 그리고
  `grep -ri "reloadable\|api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|핫리로드\|mtime\|폴링" src/ docs/` 결과 0건.

---

### Task 1: 핫리로드 코어 제거 + 고정 Supplier 배선

이 태스크가 끝나면 컴파일과 전체 테스트가 통과하는 상태가 된다. 코드·설정·테스트를 한 번에 정리해야 컴파일이 깨지지 않으므로 한 태스크로 묶는다.

**Files:**
- Delete: `src/main/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplier.java`
- Delete: `src/test/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplierTest.java`
- Modify: `src/main/java/com/crosscert/passkey/rpapp/config/PasskeyClientConfiguration.java:21-29` (apiKeySupplier 빈 + import/주석)
- Modify: `src/main/java/com/crosscert/passkey/rpapp/config/PasskeyProperties.java:6,17-23` (필드 2개 + Path import)
- Modify: `src/main/resources/application.yml:24-28` (설정/주석 5줄)
- Modify: `src/test/java/com/crosscert/passkey/rpapp/config/PasskeyClientWiringIT.java` (클래스 javadoc + properties)

**Interfaces:**
- Consumes: `PasskeyProperties#apiKey()` (`String`) — 유지되는 record accessor.
- Produces: 빈 `apiKeySupplier` → `Supplier<String>`. 기동 시 `props.apiKey()` 값을 캡처해 매 호출 동일 값 반환. `PasskeyClientConfiguration#passkeyClient`와 `PasskeyClientWiringIT`가 이 빈을 소비한다.

- [ ] **Step 1: `PasskeyClientWiringIT`를 새 동작 기준으로 먼저 고친다 (실패하는 테스트 역할)**

`PasskeyClientWiringIT.java`의 클래스 javadoc과 `@SpringBootTest` properties를 아래로 교체한다. 핫리로드/`ReloadableApiKeySupplier`/`api-key-file` 전제를 제거하고, env 키가 고정 공급됨을 검증하는 형태로 바꾼다. 두 `@Test` 본문은 그대로 둔다(빈 존재 + env 키 반환은 새 구현에서도 참).

기존 javadoc 블록(`/** ... */`, 클래스 선언 바로 위 전체)을 다음으로 교체:

```java
/**
 * API Key 빈 배선의 경량 컨텍스트 로드 테스트.
 *
 * <p>유일한 @SpringBootTest 인 {@link com.crosscert.passkey.rpapp.RpAppSmokeIT} 는
 * docker compose + passkey-app + admin-app 풀 인프라를 요구해 @Disabled 라,
 * {@link PasskeyClientConfiguration} 의 {@code apiKeySupplier}/{@code passkeyClient}
 * 빈 배선이 컴파일로만 검증되던 갭이 있었다. 본 IT 는 외부 인프라 없이 컨텍스트를
 * 띄워 두 빈의 존재 + apiKeySupplier 가 env(api-key)로 주입한 값을 그대로 공급함을 실증한다.
 *
 * <p>webEnvironment 가 NONE 이 아닌 RANDOM_PORT 인 이유: {@code WebSecurityConfig.chain}
 * 빈이 {@code HttpSecurity} 를 주입받는데, 이는 servlet 웹 컨텍스트에서만 자동 구성된다
 * (Spring Security 의 HttpSecurityConfiguration 은 @ConditionalOnWebApplication(SERVLET)).
 * NONE 이면 HttpSecurity 빈 부재로 컨텍스트 로드가 실패하므로 RpAppSmokeIT 와 동일하게
 * RANDOM_PORT 를 쓴다. 서블릿 컨테이너만 뜰 뿐 외부 호출은 없다(PasskeyClient.of 는
 * RestClient/JwksCache 만 구성, 기동 시 네트워크 I/O 없음).
 *
 * <p>properties: api-key 를 명시 값으로 세팅해 apiKeySupplier 가 그 값을 그대로 반환함을
 * 실제 Spring 바인딩을 통해 검증한다. base-url 등 나머지 필수 프로퍼티는
 * application.yml 기본값(localhost:8080 등)으로 충족된다.
 *
 * <p>rp.relay.secret 을 강한 값으로 명시하는 이유: test 프로필은 dev/local 이 아니므로
 * {@link RelayKeyGuard} 가 ApplicationReadyEvent 에서 데모 기본 키를 거부한다. 본 IT 와
 * 무관한 기존 가드이므로, 컨텍스트가 뜨도록 비-데모 키를 최소 주입한다.
 */
```

`@SpringBootTest` 어노테이션의 properties 배열에서 `"passkey.api-key-file=",` 줄을 삭제해 다음과 같이 만든다:

```java
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "passkey.api-key=pk_envFallbackKey",
                "rp.relay.secret=test-relay-secret-not-demo-0123456789"
        })
@ActiveProfiles("test")
class PasskeyClientWiringIT {
```

(메서드명 `supplierFallsBackToEnvWhenNoFileConfigured`는 의미가 약간 어긋나지만 동작 검증은 유효하다. 그대로 둬도 무방하나, 원하면 `supplierReturnsEnvApiKey`로 바꿔도 된다 — 바꿀 경우 메서드 선언부 한 곳만 수정.)

- [ ] **Step 2: 수정한 IT가 옛 구현 위에서 깨지는지 확인 (실패 확인)**

이 시점엔 코어(`ReloadableApiKeySupplier`)가 아직 살아있고 `application.yml`에 `api-key-file`도 남아있다. 방금 IT에서 `"passkey.api-key-file="` property를 제거했으므로, test 프로필 바인딩이 yml 기본값(빈 문자열)으로 떨어져도 동작 자체는 동일하다(파일 미설정 → env 폴백). 따라서 IT는 이 단계에서도 통과할 수 있다 — 이 변경은 신규 동작 추가가 아니라 제거이므로 "빨간 테스트"가 자연스럽지 않다.

Run: `./gradlew test --tests '*PasskeyClientWiringIT'`
Expected: PASS. (옛 구현에서도 env 키를 반환하므로 통과가 정상이다. 이 단계는 "수정한 IT가 기존 동작과 모순되지 않음"을 확인하는 회귀 기준점이다. Step 6에서 새 구현으로도 동일하게 통과해야 한다.)

- [ ] **Step 3: `PasskeyClientConfiguration`의 apiKeySupplier 빈을 고정값으로 교체**

`PasskeyClientConfiguration.java`에서:

(a) `import java.time.Duration;` 줄(11번째 줄)을 삭제한다 — 이 파일에서 Duration은 apiKeySupplier 외에 쓰이지 않는다(timeout들은 `props.connectTimeout()` 등으로 받아 SDK builder에 그대로 넘김).

(b) 클래스 javadoc(14-17줄)의 마지막 문장 "API Key 는 핫리로드 가능한 공급자로 주입한다."를 "API Key 는 기동 시 env(api-key)에서 읽어 고정 공급한다."로 바꾼다.

(c) apiKeySupplier 빈(21-29줄)을 다음으로 교체:

```java
    /**
     * SDK 가 매 요청 get() 을 호출하지만, RP 레퍼런스는 기동 시 env(api-key) 값을
     * 한 번 읽어 고정 공급한다. 키 교체는 재기동으로 반영한다.
     */
    @Bean
    public Supplier<String> apiKeySupplier(PasskeyProperties props) {
        String key = props.apiKey();
        return () -> key;
    }
```

- [ ] **Step 4: `PasskeyProperties`에서 핫리로드 필드 제거**

`PasskeyProperties.java`에서:

(a) `import java.nio.file.Path;` 줄(6번째 줄)을 삭제한다 — `apiKeyFile` 제거 후 Path 미사용.

(b) record 컴포넌트 `apiKeyFile`, `apiKeyReload`와 각각의 javadoc(17-23줄)을 삭제한다. 결과 record 헤더는 다음과 같다:

```java
@ConfigurationProperties(prefix = "passkey")
public record PasskeyProperties(
        URI baseUrl,
        String apiKey,
        String tenantId,
        /**
         * ID Token 의 {@code iss} claim 비교용 prefix. passkey-app 이 {@code <issuer-base>/<tenantId>}
         * 형태로 iss 를 발급하므로, 이 값은 passkey-app 의 issuer-base 설정과 정확히 일치해야 한다.
         * 운영에서는 자사 테넌트에 발급된 issuer-base 를 주입한다.
         */
        URI issuerBase,
        /** passkey-app 연결 타임아웃. 기본 3s. */
        Duration connectTimeout,
        /** passkey-app 응답 읽기 타임아웃. 기본 10s. */
        Duration readTimeout,
        /** ID Token 검증용 JWKS 캐시 유효 기간. 기본 5m. */
        Duration jwksCacheTtl
) {}
```

(`Duration` import는 connectTimeout/readTimeout/jwksCacheTtl이 쓰므로 유지.)

- [ ] **Step 5: `ReloadableApiKeySupplier` 본체와 테스트 파일 삭제, yml 설정 정리**

(a) 파일 삭제:

```bash
git rm src/main/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplier.java
git rm src/test/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplierTest.java
```

(b) `application.yml`에서 24-28줄(아래 5줄)을 삭제한다:

```yaml
  # 재기동 없는 키 교체용. 설정 시 이 파일을 핫리로드(api-key env 보다 우선).
  # 미설정이면 위 api-key 를 그대로 쓴다(기존 동작 보존). 파일 내용은 키 평문 한 줄.
  api-key-file:   ${PASSKEY_API_KEY_FILE:}
  # api-key-file mtime 폴링 주기. 기본 10s.
  api-key-reload: ${PASSKEY_API_KEY_RELOAD:10s}
```

삭제 후 `passkey:` 블록 상단은 다음처럼 되어야 한다:

```yaml
passkey:
  base-url:    ${PASSKEY_BASE_URL:http://localhost:8080}
  api-key:     ${PASSKEY_API_KEY:}
  tenant-id:   ${PASSKEY_TENANT_ID:}
```

- [ ] **Step 6: 전체 테스트 실행으로 컴파일 + 동작 검증 (통과 확인)**

Run: `./gradlew test`
Expected: PASS. 특히 `PasskeyClientWiringIT.dynamicApiKeyBeansAreWired`(빈 2개 존재)와 `supplierFallsBackToEnvWhenNoFileConfigured`(=`pk_envFallbackKey` 반환)가 green. `ReloadableApiKeySupplierTest`는 삭제되어 더 이상 실행되지 않는다.

만약 컴파일 에러가 나면(미사용 import, 누락된 참조) 해당 파일을 점검해 import/참조를 정리한 뒤 재실행한다.

- [ ] **Step 7: 코드 변경 커밋**

```bash
git add src/main/java/com/crosscert/passkey/rpapp/config/PasskeyClientConfiguration.java \
        src/main/java/com/crosscert/passkey/rpapp/config/PasskeyProperties.java \
        src/main/resources/application.yml \
        src/test/java/com/crosscert/passkey/rpapp/config/PasskeyClientWiringIT.java
git commit -m "$(cat <<'EOF'
refactor(config): API Key 파일 핫리로드 제거 — 기동 시 고정 env 키

ReloadableApiKeySupplier 와 파일 mtime 폴링/캐시 로직 삭제. apiKeySupplier
빈을 props.apiKey() 를 기동 시 캡처하는 () -> key 람다로 대체한다. SDK 계약
(Supplier<String>) 유지로 SDK jar 무변경. PasskeyProperties 의 apiKeyFile/
apiKeyReload 필드와 application.yml 의 api-key-file/api-key-reload 설정 제거.
PasskeyClientWiringIT 는 빈 존재 + env 키 공급 검증으로 정리.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

(`git rm`으로 스테이징된 두 삭제 파일은 이미 인덱스에 반영되어 있으므로 이 커밋에 함께 포함된다. 포함 여부가 의심되면 `git status`로 확인 후 필요한 경로를 추가한다.)

---

### Task 2: 문서에서 핫리로드 언급 정리

코드와 독립적으로 리뷰/리젝 가능한 문서 변경이므로 별도 태스크로 분리한다.

**Files:**
- Modify: `docs/rp-app-guide.md:41,68,79,80`
- Modify: `docs/sdk-java-guide.md:100-101,228`

**Interfaces:**
- Consumes: 없음 (문서).
- Produces: 없음.

- [ ] **Step 1: `docs/rp-app-guide.md` 정리**

(a) 41줄 — `api-key-file` 언급 제거:

기존:
```
`passkey.base-url`, `passkey.api-key`(또는 `api-key-file`), `passkey.tenant-id`, `passkey.issuer-base`
```
교체:
```
`passkey.base-url`, `passkey.api-key`, `passkey.tenant-id`, `passkey.issuer-base`
```

(b) 68줄 — "고객사가 손봐야 할 곳" 표 행:

기존:
```
| `passkey.api-key` / `api-key-file` | 발급받은 API Key | passkey-app 인증 |
```
교체:
```
| `passkey.api-key` | 발급받은 API Key | passkey-app 인증 |
```

(c) 79-80줄 — 설정 레퍼런스 표에서 두 행 전체 삭제:

```
| `passkey.api-key-file` | (빈 값) | 키 파일 핫리로드 경로(설정 시 `api-key` 보다 우선) |
| `passkey.api-key-reload` | `10s` | 키 파일 mtime 폴링 주기 |
```

- [ ] **Step 2: `docs/sdk-java-guide.md` 정리**

(a) 100-102줄 — "동적 API Key" 단락. SDK 일반 능력(매 요청 get 호출) 사실은 유지하되, RP 레퍼런스가 고정 키를 쓴다는 단서를 추가하고 "재기동 없이 교체" 표현을 SDK 일반론으로 한정한다:

기존:
```
**동적 API Key:** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에 호출된다. 따라서
Supplier 뒤편(파일/시크릿 매니저)에서 키를 교체하면 재기동 없이 다음 요청부터 반영된다. 반환값이
null/blank 면 그 요청은 `PasskeyConfigurationException` 으로 fail-fast.
```
교체:
```
**API Key Supplier:** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에 호출된다.
Supplier 구현에 따라 동적 교체도 가능하나, 본 패키지의 RP 레퍼런스는 기동 시 env(api-key)
값을 캡처해 고정 공급한다(키 교체는 재기동으로 반영). 반환값이 null/blank 면 그 요청은
`PasskeyConfigurationException` 으로 fail-fast.
```

(b) 227-228줄 — 참조 통합 예제의 설명에서 "핫리로드" 제거:

기존:
```
- `src/main/java/.../config/PasskeyClientConfiguration.java` — Spring `@Bean` 으로
  `PasskeyClient` 와 `RegistrationRelayCodec` 구성(동적 API Key Supplier 핫리로드 포함).
```
교체:
```
- `src/main/java/.../config/PasskeyClientConfiguration.java` — Spring `@Bean` 으로
  `PasskeyClient` 와 `RegistrationRelayCodec` 구성(고정 env API Key Supplier).
```

- [ ] **Step 3: 잔존 흔적 0건 검증**

Run:
```bash
grep -rin "reloadable\|api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|핫리로드\|mtime\|폴링" src/ docs/
```
Expected: 출력 없음(exit 1). 만약 무언가 잡히면 해당 위치를 마저 정리한다. (단, 이 plan 파일 자체와 spec 파일 `docs/superpowers/`는 변경 이력 기록이므로 검색 대상에서 제외 — 위 grep은 `docs/` 전체를 보므로, `docs/superpowers/` 경로가 잡히면 그것은 의도된 기록이니 무시한다.)

- [ ] **Step 4: 문서 변경 커밋**

```bash
git add docs/rp-app-guide.md docs/sdk-java-guide.md
git commit -m "$(cat <<'EOF'
docs: API Key 핫리로드 언급 제거 — 고정 env 키로 정정

rp-app-guide 의 api-key-file/api-key-reload 설정 행과 언급 삭제.
sdk-java-guide 는 SDK 일반 능력(매 요청 get)은 유지하되 RP 레퍼런스가
고정 env 키를 쓴다는 단서로 정정.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 검증 요약 (전체 완료 후)

1. `./gradlew test` — 전체 green.
2. `grep -rin "reloadable\|api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|핫리로드\|mtime\|폴링" src/ docs/` — `docs/superpowers/`(기록 문서) 외 0건.
3. `git log --oneline -3` — Task 1, Task 2 커밋 확인.

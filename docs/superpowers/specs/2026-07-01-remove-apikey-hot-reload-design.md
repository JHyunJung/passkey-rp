# API Key 핫리로드 기능 완전 삭제 — 설계

- 날짜: 2026-07-01
- 대상 프로젝트: passkey-rp (Spring Boot 3.5.x, Java 17, Gradle)
- 작업 성격: 기능 제거(feature removal) + 잔존 흔적 정리

## 1. 배경

RP 애플리케이션은 passkey-app 호출 인증에 쓰는 **API Key**를 두 가지 방식으로 공급해 왔다.

1. `passkey.api-key` (env) — 고정 키
2. `passkey.api-key-file` — 파일을 mtime 폴링으로 **핫리로드**해 재기동 없이 무중단 교체

이 중 (2) 핫리로드 메커니즘을 **완전히 제거**하고, API Key는 기동 시 env에서 한 번 읽어 고정하는 단순 방식만 남긴다.

SDK 계약(`Supplier<String>`)은 유지한다. SDK의 `RedactingRequestInterceptor`가 매 요청마다 `apiKeySupplier.get()`을 호출하므로, RP는 **항상 같은 값을 반환하는 Supplier**를 주입하면 된다. 따라서 SDK jar는 손대지 않는다.

## 2. 목표 / 비목표

### 목표
- 파일 기반 API Key 핫리로드 코드·설정·테스트·문서를 전부 제거한다.
- API Key는 `passkey.api-key`(env) 하나로만 공급한다(기동 시 값 고정).
- 기존 SDK 연동 계약을 깨지 않는다(`Supplier<String>` 유지).

### 비목표
- SDK jar 내부(`PasskeyClientConfig`, `RedactingRequestInterceptor`)는 변경하지 않는다.
- Relay secret(HMAC), JWKS 캐시, ID Token 검증 등 다른 토큰/키 계열은 건드리지 않는다.
- `SecretRedactor`의 `pk_` API Key 마스킹은 유지한다(키 값은 여전히 로그 노출 금지).

## 3. apiKeySupplier 빈의 최종 형태

```java
@Bean
Supplier<String> apiKeySupplier(PasskeyProperties props) {
    String key = props.apiKey();
    return () -> key;   // 기동 시 고정
}
```

- 빈 생성 시 `props.apiKey()`를 한 번 읽어 캡처한다.
- 이후 매 요청의 `get()`은 동일한 값을 반환한다.
- `ReloadableApiKeySupplier`, 파일 폴링, fail-safe 캐시 로직은 모두 제거된다.

## 4. 변경 목록

### 4.1 삭제 (파일 통째)

| 파일 | 비고 |
|---|---|
| `src/main/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplier.java` | 79줄 전체 |
| `src/test/java/com/crosscert/passkey/rpapp/config/ReloadableApiKeySupplierTest.java` | 핫리로드 전용 6개 테스트 |

### 4.2 수정

| 파일 | 변경 |
|---|---|
| `config/PasskeyClientConfiguration.java` | `apiKeySupplier` 빈을 §3 형태(`() -> key`)로 교체. `ReloadableApiKeySupplier` import 제거, reload 기본값(`Duration.ofSeconds(10)`) 처리 제거, 클래스/메서드 주석에서 "핫리로드/동적 키 소스/재기동 없이 교체" 표현 정리(고정 env 키로 서술). `Duration` import는 다른 빈(`registrationRelayCodec` 등)에서 쓰면 유지. |
| `config/PasskeyProperties.java` | record 컴포넌트 `apiKeyFile`, `apiKeyReload` + 관련 javadoc 삭제. `apiKey` 유지. 더 이상 쓰이지 않는 `import java.nio.file.Path` 제거(다른 필드가 쓰면 유지). `Duration`은 connectTimeout/readTimeout/jwksCacheTtl이 쓰므로 유지. |
| `src/main/resources/application.yml` | `passkey.api-key-file`, `passkey.api-key-reload` 설정 2줄 + 관련 주석 3줄(24~28줄 영역) 삭제. `api-key`는 유지. |
| `src/test/java/com/crosscert/passkey/rpapp/config/PasskeyClientWiringIT.java` | 클래스 javadoc에서 핫리로드/`ReloadableApiKeySupplier`/`api-key-file` 전제 서술 정리. `@SpringBootTest` properties에서 `"passkey.api-key-file="` 라인 제거. 두 `@Test`(`dynamicApiKeyBeansAreWired`, `supplierFallsBackToEnvWhenNoFileConfigured`)는 유지 — `apiKeySupplier` 빈은 그대로 존재하고 env 키(`pk_envFallbackKey`)를 반환하므로 검증이 여전히 유효하다. 다만 메서드명/주석의 "fallback" 뉘앙스를 "env 키 공급"으로 다듬을 수 있다(선택). |
| `docs/rp-app-guide.md` | 설정표에서 `passkey.api-key-file`(79줄), `passkey.api-key-reload`(80줄) 행 삭제. 68줄 "`passkey.api-key` / `api-key-file`" → "`passkey.api-key`"로, 41줄 "(또는 `api-key-file`)" 표현 제거. |
| `docs/sdk-java-guide.md` | 101줄은 SDK 일반 능력 서술(SDK가 매 요청 `get()`을 호출하므로 Supplier 구현에 따라 교체가 가능하다는 사실)이므로 SDK 관점 일반론으로 표현을 유지하되, "RP 레퍼런스는 고정 env 키를 쓴다"는 단서를 덧붙인다. 228줄 "동적 API Key Supplier 핫리로드 포함" → "API Key Supplier 구성"으로 정리. |

> 참고: `application-{dev,local,prod,qa}.yml`에는 `api-key-file`/`api-key-reload` 설정이 없어 수정 대상이 아니다.

### 4.3 유지 (변경 금지)
- SDK jar (`libs/sdk-java-1.0.0*.jar`) — `Supplier<String>` 계약 유지로 변경 불필요.
- `web/SecretRedactor.java`의 `pk_` 마스킹 패턴.
- Relay secret(`RelayProperties`, `RelayKeyGuard`, `RegistrationRelayCodec`), JWKS, ID Token 검증 계열.

## 5. 검증

1. `./gradlew test` 전체 통과. 특히 `PasskeyClientWiringIT`의 두 메서드가 green인지 확인(빈 존재 + env 키 반환).
2. 잔존 흔적 0건 확인:
   ```
   grep -ri "reloadable\|api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|핫리로드\|mtime\|폴링" src/ docs/
   ```
   결과가 비어 있어야 한다.
3. 기동 시 `PASSKEY_API_KEY`(env)로 API Key가 SDK에 정상 주입되는지 — `PasskeyClientWiringIT`의 빈 조립 검증으로 커버된다.

## 6. 리스크 / 주의

- **운영 영향**: 이 변경 이후 API Key 교체는 **재기동이 필요**해진다. 운영자가 파일 핫리로드에 의존하고 있었다면 운영 절차 변경이 필요하다(문서에 명시). 본 변경은 그 의존을 의도적으로 제거하는 것이 목표다.
- **빈 이름 유지**: 빈 이름 `apiKeySupplier`와 타입 `Supplier<String>`은 그대로 둔다 — `PasskeyClientConfiguration#passkeyClient`가 이 빈을 주입받으므로 이름/타입이 바뀌면 배선이 깨진다.
- **컴파일 정리**: 필드/클래스 삭제 후 미사용 import가 남지 않도록 정리한다(`Path` 등).

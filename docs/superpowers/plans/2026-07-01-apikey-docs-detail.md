# 핫리로드 제거 후 운영 절차·설정 주석 상세화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** API Key 파일 핫리로드 제거로 생긴 "키 교체 = env 갱신 후 재기동" 운영 함의를 코드 주석·설정·가이드 4곳에 일관되게 명시하고, `application.yml`의 `api-key`에 설명 주석을 추가한다.

**Architecture:** 코드 동작 변경은 없다. `application.yml` 주석, `PasskeyClientConfiguration`의 apiKeySupplier javadoc, `rp-app-guide.md` §7 보안 노트 1줄, `sdk-java-guide.md` §4 단락을 spec의 확정 문안으로 교체/추가한다. 네 곳 모두 "기동 시 1회 읽어 고정" / "교체는 env 갱신 후 재기동" 취지로 표현을 통일한다.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Gradle (편집 검증용 `./gradlew compileJava`), Markdown, YAML.

## Global Constraints

- 코드 동작 변경 금지 — 주석/문서만 편집한다.
- SDK가 매 요청 `apiKeySupplier.get()`을 호출한다는 계약 서술은 사실이므로 유지한다(RP가 그 위에서 "고정 공급"을 선택했다는 관계를 명확히).
- 새 문안이 과거 핫리로드 기능을 되살리는 것처럼 읽히면 안 된다 — `핫리로드` 단어는 반드시 부정 서술("제거됨 / 지원하지 않음") 맥락에서만 등장한다.
- Relay/JWKS/ID Token 등 다른 영역 문서는 건드리지 않는다. 새 섹션 신설 없이 기존 위치에 보강만 한다.
- 재기동-필요 메시지는 네 곳에서 일관된 표현으로 통일한다.

---

### Task 1: 운영 절차·설정 주석 4곳 보강

문서/주석만 만지는 응집된 변경이라 한 태스크로 묶는다. 4곳 편집 후 컴파일 검증과 grep 검증을 한 번에 수행한다.

**Files:**
- Modify: `src/main/resources/application.yml` (`api-key` 라인, 현재 23줄)
- Modify: `src/main/java/com/crosscert/passkey/rpapp/config/PasskeyClientConfiguration.java` (apiKeySupplier javadoc, 현재 20-23줄)
- Modify: `docs/rp-app-guide.md` (§7 보안 노트, 현재 99줄 뒤에 1줄 추가)
- Modify: `docs/sdk-java-guide.md` (§4 API Key Supplier 단락, 현재 100-103줄)

**Interfaces:**
- Consumes: 없음 (독립적 문서/주석 편집).
- Produces: 없음 (동작 변경 없음).

- [ ] **Step 1: `application.yml`의 `api-key`에 주석 추가**

파일 `src/main/resources/application.yml`에서 아래 정확한 라인을 찾는다(앞쪽 공백 정렬 포함):

```yaml
  api-key:     ${PASSKEY_API_KEY:}
```

바로 위에 주석 2줄을 삽입해 다음과 같이 만든다:

```yaml
  # passkey-app 발급 API Key(X-API-Key). 기동 시 1회 읽어 고정 사용하므로,
  # 키를 교체하려면 이 값을 바꾼 뒤 재기동한다(무중단 핫리로드는 지원하지 않음).
  api-key:     ${PASSKEY_API_KEY:}
```

(`base-url` 라인과 `api-key` 라인 사이에 주석이 들어간다. `base-url` 라인은 건드리지 않는다.)

- [ ] **Step 2: `PasskeyClientConfiguration`의 apiKeySupplier javadoc 교체**

파일 `src/main/java/com/crosscert/passkey/rpapp/config/PasskeyClientConfiguration.java`에서 아래 javadoc 블록(apiKeySupplier 빈 바로 위, `@Bean` 앞)을 찾는다:

```java
    /**
     * SDK 가 매 요청 get() 을 호출하지만, RP 레퍼런스는 기동 시 env(api-key) 값을
     * 한 번 읽어 고정 공급한다. 키 교체는 재기동으로 반영한다.
     */
```

다음으로 교체한다:

```java
    /**
     * SDK 는 매 요청 get() 을 호출하지만(런타임 키 교체를 허용하는 계약), RP 레퍼런스는
     * 기동 시 env(passkey.api-key)를 한 번 읽어 고정 공급한다. 따라서 키를 교체하려면
     * PASSKEY_API_KEY(또는 application.yml 의 api-key)를 바꾼 뒤 애플리케이션을 재기동한다.
     */
```

(빈 본문 `String key = props.apiKey(); return () -> key;`와 `@Bean` 어노테이션은 건드리지 않는다.)

- [ ] **Step 3: `rp-app-guide.md` §7 보안 노트에 운영 항목 1줄 추가**

파일 `docs/rp-app-guide.md`에서 §7 보안 노트 목록의 마지막 bullet을 찾는다:

```markdown
- **무상태/CSRF**: 서버 세션을 두지 않으므로 STATELESS + CSRF 비활성(`WebSecurityConfig`). 토큰 릴레이로 단계를 잇는다.
```

이 줄 **바로 뒤**(목록 맨 끝, `## 8. SDK 연동` 헤더 앞의 빈 줄 앞)에 아래 bullet 1줄을 추가한다:

```markdown
- **API Key 교체**: `passkey.api-key`(env `PASSKEY_API_KEY`)는 기동 시 1회 읽혀 고정된다. 키를 회전/폐기하려면 env 를 갱신하고 재기동한다(파일 기반 무중단 핫리로드는 제거됨).
```

편집 후 §7 목록은 기존 5개 bullet + 새 bullet = 6개가 되고, 기존 bullet 순서/내용은 그대로다.

- [ ] **Step 4: `sdk-java-guide.md` §4 단락을 두 문단으로 분리**

파일 `docs/sdk-java-guide.md` §4에서 아래 단락(현재 100-103줄)을 찾는다:

```markdown
**API Key Supplier:** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에 호출된다.
Supplier 구현에 따라 동적 교체도 가능하나, 본 패키지의 RP 레퍼런스는 기동 시 env(api-key)
값을 캡처해 고정 공급한다(키 교체는 재기동으로 반영). 반환값이 null/blank 면 그 요청은
`PasskeyConfigurationException` 으로 fail-fast.
```

다음 두 문단으로 교체한다:

```markdown
**API Key Supplier (SDK 계약):** `apiKeySupplier` 는 부팅 시 1회가 아니라 **매 요청 시점**에
호출된다. 반환값이 null/blank 면 그 요청은 `PasskeyConfigurationException` 으로 fail-fast.
Supplier 구현을 시크릿 매니저·파일 감시 등에 연결하면 재기동 없는 키 교체도 가능하다.

**RP 레퍼런스의 선택:** 본 패키지의 RP(`PasskeyClientConfiguration`)는 기동 시
env(`passkey.api-key`)를 한 번 캡처해 고정 공급한다. 키 교체는 env 갱신 후 재기동으로 반영한다
(운영 단순성을 위해 파일 핫리로드는 두지 않음). 무중단 교체가 필요하면 이 빈만 Supplier
구현으로 바꾸면 된다.
```

- [ ] **Step 5: 컴파일 검증 (주석 편집이 빌드를 깨지 않는지)**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (javadoc 주석만 바꿨으므로 컴파일에 영향 없어야 한다.)

- [ ] **Step 6: grep 검증 — 과거 기능 부활 방지 + 흔적 0건 유지**

Run:
```bash
grep -rin "api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|mtime\|폴링" src/ docs/ | grep -v "docs/superpowers/"
```
Expected: 출력 없음(exit 1). 이 키워드들은 과거 핫리로드 기능 잔재이며 되살아나면 안 된다.

이어서 `핫리로드` 등장 위치가 모두 부정 서술인지 확인:
```bash
grep -rin "핫리로드" src/ docs/ | grep -v "docs/superpowers/"
```
Expected: `application.yml`("무중단 핫리로드는 지원하지 않음"), `rp-app-guide.md`("파일 기반 무중단 핫리로드는 제거됨"), `sdk-java-guide.md`("파일 핫리로드는 두지 않음") 세 곳만 나오고, 모두 "지원 안 함/제거됨/두지 않음" 부정 맥락이어야 한다. (SDK 계약 문단의 "재기동 없는 키 교체도 가능하다"는 Supplier 일반론이며 핫리로드 단어를 쓰지 않으므로 무관하다.)

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/application.yml \
        src/main/java/com/crosscert/passkey/rpapp/config/PasskeyClientConfiguration.java \
        docs/rp-app-guide.md \
        docs/sdk-java-guide.md
git commit -m "$(cat <<'EOF'
docs: API Key 교체=재기동 운영 절차·설정 주석 상세화

핫리로드 제거로 생긴 "키 교체는 env 갱신 후 재기동" 함의를 4곳에 일관되게
명시. application.yml api-key 주석 추가, PasskeyClientConfiguration
apiKeySupplier javadoc 보강, rp-app-guide §7 보안노트 운영 항목 추가,
sdk-java-guide §4 를 SDK 계약/RP 선택 두 문단으로 분리. 동작 변경 없음.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 검증 요약 (완료 후)

1. `./gradlew compileJava` — BUILD SUCCESSFUL.
2. `grep -rin "api-key-file\|api-key-reload\|apiKeyFile\|apiKeyReload\|mtime\|폴링" src/ docs/ | grep -v "docs/superpowers/"` — 0건.
3. `핫리로드` 등장 3곳이 모두 부정 서술(제거/지원 안 함/두지 않음)인지 육안 확인.
4. 네 곳의 재기동-필요 메시지가 서로 모순 없이 일관되는지 육안 확인.

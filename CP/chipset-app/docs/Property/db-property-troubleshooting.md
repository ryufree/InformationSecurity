# DB Property 로딩 Bean 초기화 오류 — 진단 & 해결 가이드

---

> ## 🚨 먼저 시도할 것
>
> [DbPropertyApplicationListener.java](java/DbPropertyApplicationListener.java) 에 `mybatis.*` 필터를 적용하고 기동하세요.
> **현재 오류의 가장 유력한 원인**입니다.
>
> ```java
> // addFirst 직전에 추가
> dbProperties.keySet().removeIf(key -> key.startsWith("mybatis."));
> ```
>
> - 에러 사라짐 → `mybatis.*` 가 원인. [섹션 2](#2-db에-저장하면-안-되는-프로퍼티) 의 전체 필터 적용으로 마무리
> - 에러 유지 → [섹션 7 테스트 순서](#7-테스트-우선순위-요약) 대로 진행

---

> **증상 요약**
> - DB Property 로딩 비활성 → 정상 기동, BeanPostProcessor 정상 처리
> - DB Property 로딩 활성 → BeanPostProcessor 이전에 오류 발생
> - 오류 루트 원인: `BindingException: Invalid bound statement (not found): maru.v3.mr.internal.eml.EmlUploadRepo.selectdocType`
> - 연쇄 오류: `UnsatisfiedDependencyException` — DashBoardService, BizTripService, MeetingService 등
> - DB 값과 파일 값은 동일하게 등록된 상태
> - `EmlUploadRepo.xml`에 `selectdocType` 매핑 존재 확인됨 (T2 완료)

---

## 목차

1. [오류 구조 이해](#1-오류-구조-이해)
2. [DB에 저장하면 안 되는 프로퍼티](#2-db에-저장하면-안-되는-프로퍼티) ← **핵심**
3. [진단 테스트 (원인 파악용)](#3-진단-테스트-원인-파악용)
4. [수정 테스트 (해결책 검증용)](#4-수정-테스트-해결책-검증용)
5. [대안 설계](#5-대안-설계)
6. [테스트 우선순위 요약](#6-테스트-우선순위-요약)

---

## 1. 오류 구조 이해 — `Invalid bound statement` 원인 메커니즘

`EmlUploadRepo.xml`에 `selectdocType`이 **존재함에도** 이 오류가 발생한다는 것은:

```
MyBatis SqlSessionFactory가
EmlUploadRepo.xml 파일을 전혀 로딩하지 못했다는 뜻
```

XML 파일이 있어도 `SqlSessionFactory`가 그 파일을 찾지 못하는 이유는 **mapper XML 스캔 경로(`mybatis.mapper-locations`)가 DB 프로퍼티에 의해 덮어씌워지거나, MyBatis 관련 프레임워크 설정이 DB `addFirst`에 의해 변경되었기 때문**입니다.

---

## 2. DB에 저장하면 안 되는 프로퍼티

> **결론 먼저**: Spring Boot 프레임워크 프로퍼티(`spring.*`, `mybatis.*`, `server.*`, `logging.*`)는
> DB에 저장하면 안 됩니다. **애플리케이션 비즈니스 프로퍼티(`maru.*`)만 DB에 저장해야 합니다.**

---

### 카테고리별 정리

#### 🔴 절대 저장 금지 — MyBatis 관련 (현재 오류의 직접 원인)

| 프로퍼티 키 | 왜 안 되는가 |
|---|---|
| `mybatis.mapper-locations` | SqlSessionFactory의 XML 스캔 경로. DB `addFirst`로 덮이면 파일 기반 값과 동일해도 **타입·처리 방식 차이**로 XML 로딩 실패 가능 |
| `mybatis.config-location` | MyBatis XML 설정 파일 경로. 잘못 덮이면 전체 MyBatis 설정 무효화 |
| `mybatis.type-aliases-package` | 타입 별칭 스캔 패키지. 변경 시 resultType 매핑 전체 실패 |
| `mybatis.type-handlers-package` | 타입 핸들러 스캔 패키지 |
| `mybatis.executor-type` | SIMPLE / REUSE / BATCH — 실행 중 변경 불가 |
| `mybatis.configuration.*` | SqlSessionFactory 세부 설정 전체 (`default-statement-timeout`, `map-underscore-to-camel-case` 등) |

**왜 `mybatis.*`가 특히 위험한가:**

```
파일 PropertySource:
  mybatis.mapper-locations = "classpath*:/mapper/**/*.xml"  (String)

DB PropertySource (addFirst — 최우선):
  mybatis.mapper-locations = "classpath*:/mapper/**/*.xml"  (String — 값 동일)

문제: Spring Boot의 MybatisAutoConfiguration이 SqlSessionFactory를 생성할 때
      어느 PropertySource에서 값을 읽느냐에 따라 classpath 스캔 동작이 달라질 수 있음.
      특히 MapPropertySource vs FilePropertySource의 리소스 로딩 컨텍스트 차이.
```

또한 `mybatis.configuration.*` 하위 숫자형 프로퍼티는 `convertValue()`에 의해 `Long`으로 변환됩니다:

```
파일:  mybatis.configuration.default-statement-timeout = "30"   (String)
DB:    mybatis.configuration.default-statement-timeout = Long(30) (Long)

→ MybatisProperties.Configuration.defaultStatementTimeout 필드는 Integer
→ Long → Integer 변환 과정에서 Spring Binder가 예외를 던지거나
  silent fail로 null이 될 경우 SqlSessionFactory 설정 불완전 → XML 로딩 실패
```

---

#### 🔴 절대 저장 금지 — DataSource 부트스트랩 프로퍼티 (닭달걀 문제)

| 프로퍼티 키 | 왜 안 되는가 |
|---|---|
| `spring.datasource.url` | DB에 연결하기 위해 필요한 값. DB에서 읽어온 값이 이걸 덮으면 순환 구조 |
| `spring.datasource.username` | 위와 동일 |
| `spring.datasource.password` | 위와 동일. 암호화된 값(`${ENC(...)}`)이면 이중 복호화 시도 |
| `spring.datasource.driver-class-name` | JDBC 드라이버 클래스명. 변경 불가 |
| `spring.datasource.hikari.*` | HikariCP 풀 설정. 숫자형 → Long 변환으로 풀 초기화 오류 가능 |
| `spring.datasource.hikari.maximum-pool-size` | `Long(10)` → `int` 바인딩 오류 가능 |
| `spring.datasource.hikari.minimum-idle` | 위와 동일 |
| `spring.datasource.hikari.connection-timeout` | 위와 동일 |
| `maru.db-property.enabled` | DB 로딩 제어 플래그. DB에서 읽으면 의미 없음 (이미 로딩 완료 시점) |

---

#### 🔴 절대 저장 금지 — Spring Boot 자동 구성 제어

| 프로퍼티 키 | 왜 안 되는가 |
|---|---|
| `spring.autoconfigure.exclude` | 이 값이 DB에서 `addFirst`로 오면 특정 AutoConfiguration이 활성/비활성 전환. `@ConditionalOnProperty` 평가 결과 변경 가능 |
| `spring.main.allow-bean-definition-overriding` | 빈 정의 중복 허용 여부 — 기동 순서 전체에 영향 |
| `spring.main.lazy-initialization` | `false`(기본)와 `true`가 바뀌면 BeanPostProcessor 등록 순서 전체 변화 |
| `spring.main.web-application-type` | SERVLET / REACTIVE / NONE |
| `spring.profiles.active` | 활성 프로파일 — 기동 전에 결정됨, DB에서 읽어도 이미 늦음 |
| `spring.config.*` | 설정 파일 로딩 제어 |

---

#### 🟡 저장 주의 — 값에 플레이스홀더가 있는 경우

Java `.properties` 파일에서 `${...}` 형태로 다른 프로퍼티를 참조하는 값들:

```properties
# 파일에서 이런 식으로 쓰는 경우
spring.datasource.password=${DB_PASSWORD}
maru.upload.path=${user.home}/uploads
clients.search=${maru.search.host}:${maru.search.port}
```

Python 스크립트가 이 값을 그대로 저장하면 DB에:
```json
{"default": "${DB_PASSWORD}"}
```

DB에서 읽어온 `"${DB_PASSWORD}"`를 Spring이 `addFirst` PropertySource에서 발견하면,
`PropertySourcesPropertyResolver`가 이 값을 **다시 플레이스홀더로 해석**하려 합니다.
만약 `DB_PASSWORD`라는 키가 다른 PropertySource에 없으면:

```
IllegalArgumentException: Could not resolve placeholder 'DB_PASSWORD'
```

**확인 방법**: T1 로그에서 값이 `${...}` 형태인 항목 찾기

---

#### 🟡 저장 주의 — 서버/로깅 인프라

| 프로퍼티 키 | 이유 |
|---|---|
| `server.port` | `Long(8080)` 타입 변환 — 대부분 정상이나 일부 버전에서 오류 |
| `server.servlet.context-path` | 서블릿 컨텍스트 경로 — 기동 중 변경 불가 |
| `server.ssl.*` | SSL 설정 — 기동 전 결정됨 |
| `logging.level.*` | 로깅 레벨은 LoggingSystem이 별도 처리. DB PropertySource에서 읽지 않을 수 있음 |
| `logging.file.*` | 로그 파일 경로 |

---

#### ✅ DB 저장 가능 — 애플리케이션 비즈니스 프로퍼티

| 프로퍼티 예시 | 이유 |
|---|---|
| `maru.common.timeout` | 비즈니스 타임아웃. Long 변환도 `@Value`로 주입 시 정상 동작 |
| `maru.upload.max-file-size-mb` | 업로드 제한 값 |
| `maru.batch.*.cron` | 배치 크론 표현식 — String, 정상 |
| `maru.security.*` | 보안 관련 애플리케이션 설정 |
| `maru.batch.noti.email.*` | 이메일 발송 설정 |
| `clients.search` | 외부 서버 URL (환경별 다른 경우 유용) |

---

### 안전한 DB 저장 범위 요약

```
✅ DB 저장 OK          ❌ DB 저장 금지
─────────────────────  ──────────────────────────────
maru.*                 spring.datasource.*
clients.*              spring.datasource.hikari.*
(비즈니스 설정)         mybatis.*
                       spring.autoconfigure.*
                       spring.main.*
                       spring.profiles.*
                       server.*
                       logging.*
                       maru.db-property.enabled
                       값에 ${...} 포함된 모든 키
```

---

### 즉시 적용 가능한 필터링 코드

[DbPropertyApplicationListener.java](java/DbPropertyApplicationListener.java)에서
`addFirst` 직전에 아래 필터를 추가하면 현재 오류가 해결될 가능성이 높습니다:

```java
// DB에서 읽어선 안 되는 프레임워크 프로퍼티 필터링
private static final Set<String> BLOCKED_PREFIXES = Set.of(
    "spring.datasource.",         // DataSource 부트스트랩 (닭달걀)
    "spring.datasource.hikari.",  // HikariCP 풀 (숫자 타입 변환 문제)
    "mybatis.",                   // MyBatis 설정 (SqlSessionFactory 영향)
    "spring.autoconfigure.",      // AutoConfiguration 제어
    "spring.main.",               // SpringApplication 동작
    "spring.profiles.",           // 프로파일 제어
    "spring.config.",             // 설정 파일 로딩
    "server.",                    // 내장 서버 설정
    "logging.",                   // 로깅 설정
    "maru.db-property."           // DB 로딩 플래그 자체
);

private static final Set<String> BLOCKED_EXACT = Set.of(
    "spring.profiles.active",
    "spring.profiles.include"
);

// ... (기존 DB 로딩 로직) ...

Map<String, Object> dbProperties =
        DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

// ★ 프레임워크 프로퍼티 제거
dbProperties.keySet().removeIf(key ->
    BLOCKED_EXACT.contains(key) ||
    BLOCKED_PREFIXES.stream().anyMatch(key::startsWith));

// ★ ${...} 플레이스홀더 포함 값 제거 (이중 해석 방지)
dbProperties.values().removeIf(v ->
    v instanceof String && ((String) v).contains("${"));

if (!dbProperties.isEmpty()) {
    environment.getPropertySources()
            .addFirst(new MapPropertySource("dbProperties", dbProperties));
}
```

**필터링 결과 확인용 로그** (적용 후 기동 로그에서 확인):

```java
System.out.println("====== [DB-PROPERTY] 필터링 후 등록 키 목록 ======");
dbProperties.keySet().stream().sorted()
    .forEach(k -> System.out.println("  " + k));
System.out.printf("====== 총 %d개 등록 ======%n", dbProperties.size());
```

---

### 필터링 전후 기동 결과 비교

| 시나리오 | 기동 결과 | 판단 |
|---|---|---|
| DB 로딩 OFF (`maru.db-property.enabled=false`) | 정상 | 베이스라인 |
| DB 로딩 ON, 필터링 없음 | `Invalid bound statement` | 현재 상태 |
| DB 로딩 ON, `mybatis.*` 만 필터링 | 정상 기동 | `mybatis.*` 가 원인 확정 |
| DB 로딩 ON, `mybatis.*` 만 필터링 | 여전히 오류 | 다른 카테고리 추가 확인 필요 |
| DB 로딩 ON, 전체 BLOCKED_PREFIXES 필터링 | 정상 기동 | 필터링 전략 유효 확인 |

---

---

## 3. 오류 구조 이해 (기동 순서)

```
[Spring 기동]
    ↓
ApplicationEnvironmentPreparedEvent
    ↓ DbPropertyApplicationListener
    ↓   → DriverManager.getConnection()  ← Oracle JDBC 드라이버 초기화
    ↓   → SP_GET_PROPERTIES 호출
    ↓   → environment.addFirst("dbProperties")
    ↓
ApplicationContext 생성
    ↓
BeanFactory 생성
    ↓
BeanPostProcessor 등록 단계  ← ★ 이 시점에 에러 발생
    ↓ Spring이 BPP 의존성 빈들을 먼저 생성하려 시도
    ↓   → 어떤 Bean → EmlUploadRepo → selectdocType 매핑 없음
    ↓
BindingException  →  UnsatisfiedDependencyException (연쇄)
    ↓
ApplicationContext 초기화 실패
```

**핵심 질문 3가지**

| # | 질문 | 확인 방법 |
|---|---|---|
| Q1 | DB에서 어떤 키를 읽어오는가? | 진단 T1 |
| Q2 | `EmlUploadRepo.xml`에 `selectdocType`이 실제로 있는가? | 진단 T2 |
| Q3 | `convertValue()`의 타입 변환이 `@Conditional` 동작을 바꾸는가? | 진단 T3 |

---

## 4. 진단 테스트 (원인 파악용)

> 아래 테스트는 **코드를 최소한으로 건드리면서 원인을 좁혀가는** 순서로 정렬됩니다.
> 각 테스트 후 기동 로그를 확인하고, 결과에 따라 다음 단계로 이동합니다.

---

### T1. DB 로딩 프로퍼티 전체 출력

**목적**: DB에서 실제로 무엇을 읽어오는지, 파일에 없는 키가 있는지 확인

**방법**: `DbPropertyApplicationListener.java`의 `addFirst` 직전에 임시 로그 추가

```java
// DbPropertyApplicationListener.java
Map<String, Object> dbProperties =
        DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

// ★ 임시 진단 로그 — 확인 후 반드시 제거
System.out.println("====== [DB-PROPERTY] 로딩된 프로퍼티 목록 ======");
dbProperties.entrySet().stream()
    .sorted(Map.Entry.comparingByKey())
    .forEach(e -> System.out.printf("  %-60s = %-40s  [%s]%n",
        e.getKey(),
        e.getValue(),
        e.getValue() != null ? e.getValue().getClass().getSimpleName() : "null"));
System.out.printf("====== 총 %d개 ======%n", dbProperties.size());
```

**확인 포인트**

| 체크 항목 | 의미 |
|---|---|
| `mybatis.*` 키가 있는가 | MyBatis 설정을 DB가 덮어쓸 가능성 |
| `spring.datasource.*` 키가 있는가 | DataSource 설정 충돌 가능성 |
| 파일에 없는 키가 있는가 | DB에만 있는 키 → 조건부 빈 활성화 가능성 |
| `Boolean.TRUE / FALSE`로 변환된 키 | `@ConditionalOnProperty` 동작 변화 가능성 |
| `Long / Double`로 변환된 키 | Spring 바인딩 타입 불일치 가능성 |

**판단**: 로그 확인 후 T2 또는 T3으로 이동

---

### T2. `EmlUploadRepo.xml`의 `selectdocType` 존재 확인

**목적**: MyBatis XML에 해당 SQL이 실제로 정의되어 있는지 확인
(DB 로딩과 무관한 선행 버그일 수 있음)

**방법**: IDE에서 `EmlUploadRepo.xml` 검색

```xml
<!-- 아래 id가 있는지 확인 -->
<select id="selectdocType" ...>
    ...
</select>
```

또는 프로젝트에서 텍스트 검색:
```
검색어: selectdocType
검색 범위: src/main/resources/mapper/**/*.xml
```

**판단**

| 결과 | 의미 | 조치 |
|---|---|---|
| `selectdocType` XML에 없음 | 근본 원인 발견. DB 로딩과 무관한 버그 | XML에 SQL 추가 → 해결 |
| `selectdocType` XML에 있음 | XML은 정상. DB 로딩이 XML 스캔 경로를 바꿈 | T3으로 이동 |

---

### T3. `convertValue()` 비활성화 테스트

**목적**: 타입 변환(Boolean/Long/Double)이 `@Conditional` 평가나 Spring 바인딩에 영향을 주는지 확인

**방법**: `DbPropertyLoader.java`의 `convertValue()`를 String 반환으로 임시 변경

```java
// DbPropertyLoader.java — 임시 변경
private static Object convertValue(String value) {
    return value;   // ★ 타입 변환 전부 꺼서 파일 PropertySource와 동일하게 만듦
}
```

**기동 후 확인**

| 결과 | 의미 | 다음 단계 |
|---|---|---|
| 에러 사라짐 | 타입 변환이 원인 | T4로 원인 좁히기 |
| 에러 유지 | 타입 변환은 무관 | T5로 이동 |

---

### T4. `convertValue()` 타입별 분리 테스트

**목적**: T3에서 타입 변환이 원인으로 밝혀진 경우, 어떤 타입이 문제인지 특정

**방법**: Boolean만 꺼보기 → Long/Double만 꺼보기 순서로 테스트

```java
// 테스트 A: Boolean 변환만 비활성화
private static Object convertValue(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    // Boolean 변환 제거
    try { return Long.parseLong(trimmed); } catch (NumberFormatException ignored) {}
    try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) {}
    return value;
}

// 테스트 B: 숫자 변환만 비활성화
private static Object convertValue(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if ("true".equalsIgnoreCase(trimmed))  return Boolean.TRUE;
    if ("false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
    return value; // Long/Double 변환 제거
}
```

**판단**: 어느 타입을 끄면 에러가 사라지는지로 원인 특정

---

### T5. `addFirst` → `addLast` 변경 테스트

**목적**: PropertySource 우선순위 자체가 문제인지 확인
(DB 값이 최우선이 되는 구조 자체의 부작용 검증)

**방법**: `DbPropertyApplicationListener.java`에서 `addFirst` → `addLast` 변경

```java
// 기존
environment.getPropertySources()
        .addFirst(new MapPropertySource("dbProperties", dbProperties));

// 테스트용 변경 — DB 값이 파일보다 낮은 우선순위
environment.getPropertySources()
        .addLast(new MapPropertySource("dbProperties", dbProperties));
```

> **주의**: `addLast`는 파일이 DB보다 우선이 되므로 실제 운영에는 사용 불가.
> 테스트 목적으로만 사용.

**판단**

| 결과 | 의미 | 다음 단계 |
|---|---|---|
| 에러 사라짐 | DB가 최우선일 때 특정 프로퍼티가 충돌 | T1 로그로 충돌 키 특정 |
| 에러 유지 | 우선순위 문제가 아님 | T6으로 이동 |

---

### T6. DB 프로퍼티 키 범위 최소화 테스트

**목적**: 특정 키 범위가 문제를 일으키는지 이진 탐색으로 특정

**방법**: DB에서 읽어온 프로퍼티 중 절반만 적용해보기

```java
// DbPropertyApplicationListener.java — 임시 필터링
Map<String, Object> dbProperties =
        DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

// 테스트 A: maru.* 키만 허용 (spring.*, mybatis.* 등 제외)
dbProperties.keySet().retainIf(k -> k.startsWith("maru."));

// 테스트 B: mybatis.*, spring.* 만 제외
dbProperties.keySet().removeIf(k ->
    k.startsWith("mybatis.") || k.startsWith("spring."));
```

**판단**: 어느 키 범위를 제외하면 에러가 사라지는지로 원인 좁히기

---

### T7. Oracle JDBC 드라이버 사전 초기화 영향 확인

**목적**: `Class.forName()` + `DriverManager.getConnection()`의 Oracle 드라이버 초기화가
       이후 HikariCP 풀 생성에 영향을 주는지 확인

**방법**: `DbPropertyLoader.java`에서 Oracle 관련 시스템 프로퍼티를 초기화 전후 비교

```java
// DbPropertyLoader.loadFromJdbc() 시작 부분에 추가
System.out.println("[BEFORE] oracle.jdbc 시스템 프로퍼티:");
System.getProperties().entrySet().stream()
    .filter(e -> e.getKey().toString().startsWith("oracle"))
    .forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));

try {
    Class.forName(driver);
} catch (ClassNotFoundException e) {
    throw new RuntimeException("JDBC 드라이버 로딩 실패: " + driver, e);
}

// 드라이버 로드 후 변화 확인
System.out.println("[AFTER Class.forName] oracle.jdbc 시스템 프로퍼티:");
System.getProperties().entrySet().stream()
    .filter(e -> e.getKey().toString().startsWith("oracle"))
    .forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));
```

**판단**: `Class.forName` 전후로 시스템 프로퍼티가 변경되면 Oracle 드라이버의 전역 상태 오염 의심

---

### T8. 특정 프로파일 분리 테스트

**목적**: 특정 프로파일의 프로퍼티가 문제를 일으키는지 확인

**방법**: 프로파일을 하나씩 바꿔가며 기동

```bash
# 테스트 1: 프로파일 없이 기동 (default만)
java -jar app.jar

# 테스트 2: dev만
java -jar app.jar -Dspring.profiles.active=dev

# 테스트 3: batch만
java -jar app.jar -Dspring.profiles.active=batch

# 테스트 4: dev + batch
java -jar app.jar -Dspring.profiles.active=dev,batch
```

**판단**: 특정 프로파일 조합에서만 에러가 나면, 그 프로파일의 프로퍼티 중 하나가 원인

---

## 5. 수정 테스트 (해결책 검증용)

> 진단 결과에 따라 아래 수정 중 하나를 적용하고 기동 테스트를 반복합니다.

---

### F1. `convertValue()` — String 고정 (가장 안전한 수정)

**적용 조건**: T3에서 타입 변환이 원인으로 밝혀진 경우

**변경 파일**: [DbPropertyLoader.java](java/DbPropertyLoader.java)

```java
// 기존
private static Object convertValue(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if ("true".equalsIgnoreCase(trimmed))  return Boolean.TRUE;
    if ("false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
    try { return Long.parseLong(trimmed); }   catch (NumberFormatException ignored) {}
    try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) {}
    return value;
}

// 수정안 — 항상 String 반환
// Spring @Value, @ConfigurationProperties 바인딩 시 ConversionService가 타입 변환 처리
private static Object convertValue(String value) {
    return value;
}
```

**검증**: 기동 후 `@Value("${maru.common.timeout:0}")` 주입 값이 정상인지 확인

---

### F2. 인프라 핵심 키 필터링

**적용 조건**: T1에서 `mybatis.*`, `spring.datasource.*` 등이 DB에 존재하는 경우

**변경 파일**: [DbPropertyApplicationListener.java](java/DbPropertyApplicationListener.java)

```java
// addFirst 직전에 추가
private static final Set<String> BLOCKED_PREFIXES = Set.of(
    "mybatis.",
    "spring.datasource.",
    "spring.jpa.",
    "spring.autoconfigure.",
    "spring.main.",
    "server.",
    "maru.db-property."     // 닭달걀 방지 플래그는 DB에서 읽지 않음
);

// ...

Map<String, Object> dbProperties =
        DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

// 인프라 핵심 키 제거
dbProperties.keySet().removeIf(key ->
        BLOCKED_PREFIXES.stream().anyMatch(key::startsWith));

if (!dbProperties.isEmpty()) {
    environment.getPropertySources()
            .addFirst(new MapPropertySource("dbProperties", dbProperties));
}
```

**검증**: 필터링된 키 목록을 로그로 확인 후 기동 테스트

---

### F3. PropertySource 삽입 위치 조정 (addFirst → addAfter)

**적용 조건**: T5에서 `addFirst` 자체가 원인으로 밝혀진 경우

**변경 파일**: [DbPropertyApplicationListener.java](java/DbPropertyApplicationListener.java)

```java
// 기존: addFirst — DB가 JVM 인수보다도 우선
environment.getPropertySources()
        .addFirst(new MapPropertySource("dbProperties", dbProperties));

// 수정안: commandLineArgs 바로 다음 삽입
// → JVM --key=value 인수 > DB > 시스템 환경변수 > 파일 순서
MutablePropertySources sources = environment.getPropertySources();
if (sources.contains("commandLineArgs")) {
    sources.addAfter("commandLineArgs",
            new MapPropertySource("dbProperties", dbProperties));
} else {
    sources.addFirst(new MapPropertySource("dbProperties", dbProperties));
}
```

**우선순위 변화**

```
변경 전 (addFirst):
  0. dbProperties       ← DB 값 (JVM 인수보다 높음)
  1. commandLineArgs     ← --key=value
  2. systemProperties   ← -Dkey=value
  ...

변경 후 (addAfter commandLineArgs):
  0. commandLineArgs     ← --key=value (긴급 재정의 가능)
  1. dbProperties        ← DB 값
  2. systemProperties    ← -Dkey=value
  ...
```

---

### F4. EmlUploadRepo.xml에 누락된 SQL 추가

**적용 조건**: T2에서 `selectdocType` 이 XML에 없는 것으로 확인된 경우

```xml
<!-- EmlUploadRepo.xml 에 추가 -->
<select id="selectdocType" parameterType="..." resultType="...">
    SELECT ...
    FROM   ...
    WHERE  ...
</select>
```

> **주의**: DB 로딩 여부와 무관하게 이 메서드가 XML에 없으면 해당 기능이 런타임에 항상 실패합니다.
> DB 로딩이 생기면서 이 메서드가 **기동 시점에 검증되도록** 초기화 순서가 바뀐 것이므로
> XML 수정은 반드시 병행해야 합니다.

---

### F5. 문제 Bean에 `@Lazy` 추가

**적용 조건**: BeanPostProcessor 의존성 체인 상의 특정 Service가 원인인 경우

Spring이 BeanPostProcessor 등록 단계에서 일반 Bean을 당겨서 만드는 문제를 회피합니다.

```java
// BeanPostProcessor가 의존하는 Service
@Component
public class SomeBeanPostProcessor implements BeanPostProcessor {

    private final SomeDependencyService service;

    // @Lazy 추가 → 실제 사용 시점까지 프록시로 지연 초기화
    public SomeBeanPostProcessor(@Lazy SomeDependencyService service) {
        this.service = service;
    }
}
```

**검증**: 기동 로그에서 `BeanPostProcessorChecker` 경고 이후로 에러가 이동하는지 확인

---

## 6. 대안 설계

> DB Property 로딩 구조 자체를 다른 방식으로 바꾸는 접근입니다.
> 현재 구조에서 근본적인 문제가 반복된다면 검토하세요.

---

### 대안 A: `EnvironmentPostProcessor` 방식 (Spring 표준)

**현재 방식의 문제**: `ApplicationListener<ApplicationEnvironmentPreparedEvent>`는 직접 `SpringApplication`에 등록해야 하며, 순서 제어가 까다롭습니다.

**대안**: Spring Boot 공식 확장 포인트인 `EnvironmentPostProcessor`를 사용합니다.

```java
// DbPropertyEnvironmentPostProcessor.java
public class DbPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        // 현재 DbPropertyApplicationListener 의 onApplicationEvent 내용과 동일
        // ...
    }

    @Override
    public int getOrder() {
        // application.properties 로딩 후, 다른 후처리보다 나중에 실행
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
```

```
# src/main/resources/META-INF/spring.factories
org.springframework.boot.env.EnvironmentPostProcessor=\
  maru.config.DbPropertyEnvironmentPostProcessor
```

또는 Spring Boot 2.7+ / 3.x 에서는:
```
# src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor
maru.config.DbPropertyEnvironmentPostProcessor
```

**장점**:
- Spring Boot 공식 지원 메커니즘 → 순서(Order) 제어가 명확
- `Boot.java`를 수정할 필요 없음
- 다른 `EnvironmentPostProcessor`(예: `ConfigDataEnvironmentPostProcessor`)와의 실행 순서를 명시 가능

**단점**:
- `spring.factories` 또는 별도 설정 파일 관리 필요

---

### 대안 B: ApplicationContextInitializer 방식

```java
public class DbPropertyContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        // Environment 접근 시점이 다름 — 빈 정의 로드 직전
        ConfigurableEnvironment env = ctx.getEnvironment();
        // ... 기존 로딩 로직
    }
}
```

```java
// Boot.java
new SpringApplicationBuilder(Boot.class)
    .initializers(new DbPropertyContextInitializer())
    .run(arguments);
```

---

### 대안 C: `@RefreshScope` + Actuator 방식 (재배포 없는 갱신 지원)

DB 값을 기동 시에만 읽는 것이 아니라, **런타임에도 갱신**하고 싶다면 아래 구조를 검토합니다.

```
[기동 시]
  DB Property → Environment PropertySource (기존 방식)

[런타임 갱신 시]
  POST /actuator/refresh
  → DB Property 재조회
  → @RefreshScope 빈 재초기화
  → 새 값 적용 (재배포 없이)
```

**필요 의존성**:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

**적용 범위**: 런타임 갱신이 필요한 빈에만 `@RefreshScope` 추가

**단점**: Spring Cloud 의존성 추가, `@RefreshScope` 빈은 프록시 기반이므로 `@Autowired` 시 주의 필요

---

### 대안 D: PropertySource를 `@Configuration`에서 등록 (Application 기동 후 방식)

DB Property를 **BeanFactory 초기화 이후**에 등록하는 방식입니다.
Bean 초기화 순서 문제를 완전히 우회합니다.

```java
@Configuration
public class DbPropertyConfig implements EnvironmentAware, InitializingBean {

    private ConfigurableEnvironment environment;
    private final DataSource dataSource; // Spring이 관리하는 DataSource 사용

    // ...

    @Override
    public void afterPropertiesSet() {
        // 이 시점은 DataSource, SqlSessionFactory 등이 이미 완성된 이후
        Map<String, Object> dbProperties = loadFromDataSource(dataSource);
        environment.getPropertySources()
                .addFirst(new MapPropertySource("dbProperties", dbProperties));
    }
}
```

**장점**: BeanPostProcessor 단계 이전에 JDBC 연결을 여는 문제가 없음. Spring이 관리하는 DataSource(HikariCP 풀)를 그대로 재활용.

**단점**: 등록 시점이 늦어서 **`@Value`, `@ConfigurationProperties`에는 반영 안 됨**.
Bean 초기화 후에 직접 값을 읽는 코드(예: `environment.getProperty(key)`)에만 유효.

---

### 대안 E: DB Property를 `@Value` 대신 별도 서비스로 관리

프로퍼티 값을 Spring Environment에 주입하는 대신, **런타임에 조회**하는 서비스 레이어를 두는 방식입니다.

```java
@Service
public class DbPropertyService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    public DbPropertyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void load() {
        // DataSource 준비 완료 이후 안전하게 로딩
        jdbc.query("SELECT property_key, property_value FROM ...",
            rs -> cache.put(rs.getString(1), rs.getString(2)));
    }

    public String get(String key) { return cache.get(key); }
    public String get(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }
}
```

**장점**: Spring 기동 순서 문제 완전 회피, `@Value` 없이 명시적으로 값 조회

**단점**: 기존 `@Value` 사용 코드를 전부 `DbPropertyService.get()` 호출로 변경해야 함

---

## 7. 테스트 우선순위 요약

```
[완료] T2 — EmlUploadRepo.xml에 selectdocType 존재 확인 ✅
              → XML 자체 문제 아님. SqlSessionFactory가 XML을 못 찾는 것.

[Step 1] F2 즉시 적용 — mybatis.* 필터링 (10분) ← 가장 먼저 시도
    ↓ 에러 사라짐 → mybatis.* 가 원인 확정. 필터링 유지
    ↓ 에러 유지 ↓

[Step 2] F2 확장 — BLOCKED_PREFIXES 전체 필터링 적용 (5분)
    ↓ 에러 사라짐 → 필터링 전략 유효. T1 로그로 어느 카테고리가 문제인지 좁히기
    ↓ 에러 유지 ↓

[Step 3] T1 — DB 로딩 키 전체 출력 (10분)
    ↓ ${...} 포함 값 발견 → 해당 키 DB에서 제거 또는 플레이스홀더 필터링 추가
    ↓ 파일에 없는 키 발견 → 해당 키 DB에서 제거
    ↓ 모두 정상 보임 ↓

[Step 4] T3 — convertValue() String 고정 테스트 (5분)
    ↓ 에러 사라짐 → 타입 변환 문제. F1 적용 확정
    ↓ 에러 유지 ↓

[Step 5] T5 — addFirst → addLast 테스트 (5분)
    ↓ 에러 사라짐 → F3 적용 (addAfter commandLineArgs)
    ↓ 에러 유지 ↓

[Step 6] T8 — 프로파일 분리 기동 (10분)
    ↓ 특정 프로파일에서만 에러 → T6으로 키 범위 이진 탐색

[끝까지 해결 안 되면]
    → 대안 A (EnvironmentPostProcessor) 또는 대안 D (@Configuration 방식) 검토
```

---

## 참고: Connection Close는 문제가 아님

`DbPropertyLoader.loadFromJdbc()`는 `try-with-resources`로 `Connection`, `CallableStatement`, `ResultSet` 세 자원을 모두 정상 종료합니다. DB 연결 누수는 이 오류의 원인이 아닙니다.

```java
try (Connection conn = DriverManager.getConnection(url, username, password);
     CallableStatement cs = conn.prepareCall("{call maruadm.SP_GET_PROPERTIES(?, ?)}")) {

    try (ResultSet rs = (ResultSet) cs.getObject(2)) {
        // ...
    }   // ← ResultSet 자동 close
}       // ← CallableStatement, Connection 자동 close
```

Connection이 닫히지 않았다면 `UnsatisfiedDependencyException`이 아니라
Oracle 세션 한도 초과 오류(`ORA-00018: maximum number of sessions exceeded`)가 나타납니다.

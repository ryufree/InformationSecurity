# DB Properties — BeanPostProcessor 구현 문서

`com/dbproperty` 패키지는 애플리케이션 설정값을 DB에서 읽어 Spring Bean 에 주입하고,
배치 스케줄을 DB 값으로 동적으로 제어하는 두 개의 `BeanPostProcessor` 를 제공합니다.

---

## 1. 구성 요소

```
com/dbproperty/
├── annotation/
│   ├── DbValue.java                          # 필드 주입 어노테이션
│   └── JobSchedulerTarget.java               # 배치 메서드 어노테이션
├── dto/
│   └── PropertiesResult.java                 # Mapper 조회 결과 DTO
├── mapper/
│   └── PropertiesMapper.java                 # MyBatis Mapper 인터페이스
├── processor/
│   ├── ValueBeanPostProcessor.java           # @DbValue 처리기
│   └── JobSchedulerTargetBeanPostProcessor.java  # @JobSchedulerTarget 처리기
└── sql/
    └── PropertiesMapper.xml                  # SQL 정의
```

---

## 2. 어노테이션

### `@DbValue`

필드에 선언하면 Bean 초기화 전(`postProcessBeforeInitialization`)에 DB 값을 주입합니다.

```java
@DbValue(value = "app.timeout", defaultValue = "30")
private int timeout;

@DbValue(value = "app.title", defaultValue = "기본 제목")
private String title;
```

| 속성 | 설명 | 필수 |
|------|------|------|
| `value` | DB 조회 키 (`refrc1`) | ✅ |
| `defaultValue` | DB 에 값 없을 때 코드 레벨 fallback | ❌ (기본값 `""`) |

### `@JobSchedulerTarget`

메서드에 선언하면 Bean 초기화 후(`postProcessAfterInitialization`)에 스케줄러에 등록됩니다.

```java
@JobSchedulerTarget(
    enabled = "${batch.reminder.active}",
    cron    = "${batch.reminder.cron}"
)
public void sendReminderEmail() { ... }
```

| 속성 | 설명 | 기본값 |
|------|------|--------|
| `enabled` | DB 활성화 여부 키 (`${...}` 또는 plain key) | `"true"` |
| `cron` | DB cron 표현식 키 | `""` |

---

## 3. 값 우선순위 (`PropertiesResult.resolveValue`)

```
DB refrc2 (현재 적용값)
    ↓ 없으면
DB refrc3 (DB 기본값)
    ↓ 없으면
@DbValue(defaultValue = "...")  또는  enabled/cron 하드코딩 기본값
```

---

## 4. 지원 타입 (`ValueBeanPostProcessor`)

| Java 타입 | DB 값 예시 | 변환 결과 | blank/빈값 처리 |
|-----------|------------|-----------|-----------------|
| `String` | `"hello"` | `"hello"` | 빈 문자열 그대로 |
| `int` / `Integer` | `"30"` | `30` | `0` |
| `long` / `Long` | `"9999999999"` | `9999999999L` | `0L` |
| `boolean` / `Boolean` | `"true"` | `true` | `false` |

> **주의** `boolean` 은 `Boolean.parseBoolean()` 을 사용하므로 `"true"` (대소문자 무관) 만 `true` 로 변환됩니다.
> `"yes"`, `"1"`, `"on"` 등은 모두 `false` 입니다.

---

## 5. PropertiesMapper.xml — profile 조건 추가

### 수정 전 (profile 미적용)

```xml
WHERE
    comm_type_cd = 'MARU_PROPERTIES'
    AND del_yn   = 0
    AND refrc1   = #{key}
```

### 수정 후

```xml
WHERE
    comm_type_cd = 'MARU_PROPERTIES'
    AND del_yn   = 0
    AND refrc1   = #{key}
    AND profile  = #{profile}
```

`profile` 컬럼이 WHERE 절에 없으면 모든 프로파일의 행이 조회되어 잘못된 값이 반환될 수 있습니다.
`resolveActiveProfile()` 이 반환하는 프로파일(없으면 `"common"`)로 DB 행을 정확히 필터링합니다.

---

## 6. 테스트 케이스

테스트 파일 위치: `backend/src/test/java/maru/platform/config/`

### 6-1. `ValueBeanPostProcessorTest` — 16개

| 번호 | 분류 | 테스트 명 | 검증 내용 |
|------|------|-----------|-----------|
| p01 | ✅ Positive | `p01_String_DB_refrc2_주입` | String 필드에 DB refrc2 값이 주입됨 |
| p02 | ✅ Positive | `p02_int_DB_정수_변환` | int 필드 숫자 문자열 변환 |
| p03 | ✅ Positive | `p03_long_DB_Long_변환` | long 필드 대형 숫자 변환 |
| p04 | ✅ Positive | `p04_boolean_true_변환` | boolean `"true"` → `true` |
| p05 | ✅ Positive | `p05_DB_null_annotation_default_사용` | DB null → annotation `defaultValue` 사용 |
| p06 | ✅ Positive | `p06_refrc2_blank_refrc3_fallback` | refrc2 blank → refrc3 fallback |
| p07 | ✅ Positive | `p07_no_annotation_필드_무시` | `@DbValue` 없는 필드 → Mapper 미호출 |
| p08 | ✅ Positive | `p08_int_blank_DB값_0_반환` | int blank → 0 방어 처리 |
| p09 | ✅ Positive | `p09_long_blank_DB값_0L_반환` | long 빈 문자열 → 0L |
| p10 | ✅ Positive | `p10_active_profile_사용` | 활성 프로파일로 DB 조회 |
| p11 | ✅ Positive | `p11_no_profile_common_fallback` | 프로파일 없으면 `"common"` 사용 |
| n01 | ❌ Negative | `n01_int_숫자아닌값_예외` | int — 문자열 → `NumberFormatException` |
| n02 | ❌ Negative | `n02_long_숫자아닌값_예외` | long — 문자열 → `NumberFormatException` |
| n03 | ❌ Negative | `n03_int_부동소수점_예외` | int — `"3.14"` → `NumberFormatException` |
| n04 | ❌ Negative | `n04_DB_null_no_default_빈문자열_주입` | DB null + defaultValue 없음 → `""` 주입 |
| n05 | ❌ Negative | `n05_boolean_yes_문자열_false` | `"yes"` → `false` (Boolean.parseBoolean 특성) |

### 6-2. `JobSchedulerTargetBeanPostProcessorTest` — 10개

| 번호 | 분류 | 테스트 명 | 검증 내용 |
|------|------|-----------|-----------|
| p01 | ✅ Positive | `p01_enabled_true_배치_실행` | enabled `"true"` → 배치 메서드 실행 |
| p02 | ✅ Positive | `p02_enabled_false_배치_skip` | enabled `"false"` → 배치 메서드 skip |
| p03 | ✅ Positive | `p03_cron_DB_동적_조회` | DB cron 값으로 다음 실행 시간 계산 |
| p04 | ✅ Positive | `p04_DB_enabled_null_기본값_false_skip` | DB null → 기본값 `false` → skip |
| p05 | ✅ Positive | `p05_DB_cron_null_기본값_cron_사용` | DB null → 기본 cron `"0 0/5 * * * ?"` 적용 |
| p06 | ✅ Positive | `p06_no_profile_common으로_DB_조회` | 프로파일 없으면 `"common"` 으로 조회 |
| p07 | ✅ Positive | `p07_no_annotation_빈_스케줄_미등록` | `@JobSchedulerTarget` 없는 빈 → 스케줄러 미등록 |
| p08 | ✅ Positive | `p08_dollar_brace_key_추출` | `${batch.job.active}` → key 추출 후 plain key 로 조회 |
| n01 | ❌ Negative | `n01_method_예외_RuntimeException_전파` | 배치 메서드 예외 → `RuntimeException` 래핑 전파 |
| n02 | ❌ Negative | `n02_invalid_cron_표현식_예외` | 잘못된 cron → `IllegalArgumentException` |

---

## 7. 테스트 실행 방법

### 7-1. 전체 테스트 실행 (Maven)

```bash
# backend 디렉토리에서 실행
cd backend
mvn test
```

### 7-2. 특정 클래스만 실행

```bash
# ValueBeanPostProcessorTest 만 실행
mvn test -Dtest=ValueBeanPostProcessorTest

# JobSchedulerTargetBeanPostProcessorTest 만 실행
mvn test -Dtest=JobSchedulerTargetBeanPostProcessorTest

# 두 클래스 동시 실행
mvn test -Dtest="ValueBeanPostProcessorTest,JobSchedulerTargetBeanPostProcessorTest"
```

### 7-3. 특정 메서드만 실행

```bash
# 특정 테스트 메서드 하나만 실행
mvn test -Dtest="ValueBeanPostProcessorTest#p01_String_DB_refrc2_주입"

# 패턴으로 여러 메서드 실행 (Positive 만)
mvn test -Dtest="ValueBeanPostProcessorTest#p*"

# 패턴으로 여러 메서드 실행 (Negative 만)
mvn test -Dtest="ValueBeanPostProcessorTest#n*"
```

### 7-4. IntelliJ IDEA 에서 실행

| 방법 | 설명 |
|------|------|
| 클래스 전체 | 테스트 클래스 파일 열기 → 클래스명 옆 ▶ 클릭 → `Run` |
| 메서드 하나 | 테스트 메서드 옆 ▶ 클릭 → `Run` |
| 분류별 (Positive) | `@DisplayName` 으로 필터 또는 메서드명 패턴 `p*` 로 실행 |
| 분류별 (Negative) | 메서드명 패턴 `n*` 로 실행 |

### 7-5. 테스트 결과 확인

```bash
# Surefire 리포트 생성 후 확인
mvn surefire-report:report
# 결과 파일: backend/target/site/surefire-report.html
```

---

## 8. 테스트 설계 원칙

- **단위 테스트만** 사용 (`@ExtendWith(MockitoExtension.class)`) — Spring Context 불필요
- `PropertiesMapper`, `Environment`, `TaskScheduler` 는 모두 **Mockito Mock** 으로 대체
- `PropertiesResult` 는 `@NoArgsConstructor` 이므로 **ReflectionTestUtils** 로 필드 설정
- `JobSchedulerTargetBeanPostProcessor` 의 Runnable/Trigger 는 **ArgumentCaptor** 로 꺼내 즉시 실행
  → 실제 스케줄러 없이 배치 로직 검증 가능
- Mockito **STRICT_STUBS** (기본값) 적용 — 사용하지 않는 stub 은 테스트 실패 처리

### ObjectProvider 대응 — processor 수동 생성

프로덕션 코드가 `ObjectProvider<T>` 로 변경됨에 따라 테스트 setup 방식도 변경되었습니다.

**변경 전 (`@InjectMocks` 사용)**

```java
@Mock private PropertiesMapper propertiesMapper;
@Mock private Environment      environment;
@InjectMocks private DbValueBeanPostProcessor processor;
// Mockito 가 생성자를 자동 호출해서 주입
```

**변경 후 (`@BeforeEach` 수동 생성)**

```java
@Mock private PropertiesMapper                 propertiesMapper;
@Mock private ObjectProvider<PropertiesMapper> propertiesMapperProvider;
@Mock private Environment                      environment;

private DbValueBeanPostProcessor processor;

@BeforeEach
void setUp() {
    lenient().when(propertiesMapperProvider.getObject()).thenReturn(propertiesMapper);
    processor = new DbValueBeanPostProcessor(propertiesMapperProvider, environment);
}
```

`@InjectMocks` 는 `ObjectProvider<PropertiesMapper>` 타입을 `@Mock PropertiesMapper` 로
자동 매핑하지 못하므로 processor 를 직접 생성합니다.

**`lenient()` 를 사용하는 이유**

Mockito 기본 모드인 `STRICT_STUBS` 는 선언한 stub 이 테스트 내에서 한 번도 호출되지 않으면
테스트를 실패 처리합니다.

`p07_no_annotation_필드_무시` / `p07_no_annotation_빈_스케줄_미등록` 테스트는
어노테이션이 없는 빈을 처리하므로 `provider.getObject()` 가 전혀 호출되지 않습니다.
`@BeforeEach` 에 선언한 provider stub 이 이 테스트에서 미사용으로 판정되어 실패하게 됩니다.

`lenient()` 는 이 infrastructure 성격의 공통 provider stub 에만 미사용 허용 예외를 부여합니다.
비즈니스 로직 stub (`propertiesMapper.findByKey(...)` 등) 은 여전히 strict 하게 검증됩니다.

| stub 종류 | 선언 방식 | 미사용 시 |
|---|---|---|
| `propertiesMapperProvider.getObject()` | `lenient().when(...)` | 허용 (infrastructure) |
| `taskSchedulerProvider.getObject()` | `lenient().when(...)` | 허용 (infrastructure) |
| `propertiesMapper.findByKey(...)` | `given(...).willReturn(...)` | 실패 처리 (비즈니스 검증) |

---

## 9. Spring 기동 생애주기 — BeanPostProcessor 초기화 시점

`BeanPostProcessor` 는 Spring 컨테이너에서 **가장 먼저** 초기화되는 특수한 빈입니다.
이 특성을 이해해야 `ObjectProvider` 를 사용한 이유가 명확해집니다.

```
[서버 시작]
    │
    ▼
① JVM 시작
    │
    ▼
② application.properties / yml 읽기
    │                                      ← DB 연결 없음. @DbValue 사용 불가 구간
    ▼
③ PropertySourcesPlaceholderConfigurer 등록
    │                                      ← ${...} 플레이스홀더 리졸브 담당
    │                                         @FeignClient url="${clients.search}" 등
    │                                         이 시점에 처리됨
    ▼
④ BeanPostProcessor 빈 초기화            ← ★★★ 일반 빈보다 훨씬 먼저 초기화
    │
    │   DbValueBeanPostProcessor
    │   JobSchedulerTargetBeanPostProcessor
    │
    ▼
⑤ Tomcat 포트 바인딩 (server.port)        ← 이후 포트 변경 불가
    │
    ▼
⑥ Spring ApplicationContext 생성 (일반 빈)
    │
    ▼
⑦ DataSource(DB 연결) 초기화              ← ★ 여기서부터 DB 조회 가능
    │
    ▼
⑧ @Service, @Repository 빈 생성          ← ★★ BeanPostProcessor 가 실제로 동작하는 구간
    │
    │   각 빈이 생성될 때마다 자동으로 아래 순서 실행:
    │
    │   ① 빈 인스턴스 생성 (new)
    │   ② postProcessBeforeInitialization() 호출  ← DB에서 refrc2/refrc3 읽어 필드 주입
    │   ③ @PostConstruct 실행
    │   ④ InitializingBean.afterPropertiesSet() 실행
    │   ⑤ postProcessAfterInitialization() 호출   ← @JobSchedulerTarget 스케줄 등록
    │
    ▼
⑨ @Scheduled 등록 (cron 표현식 고정)
    │
    ▼
⑩ 서버 기동 완료
    │
    ▼
[요청 처리 중]
```

---

## 10. 트러블슈팅 — `@FeignClient` 플레이스홀더 오류와 `ObjectProvider` 적용

### 발생한 오류

두 `BeanPostProcessor` 를 도입한 이후 아래 오류가 발생했습니다.

```
org.springframework.beans.factory.BeanDefinitionStoreException:
  Invalid bean definition with name 'maru.platform.clients.CockpitClient' defined in null:
  Could not resolve placeholder 'clients.search' in value "http://${clients.search}"
```

`CockpitClient` 에는 아래와 같이 `@FeignClient` 가 선언되어 있었습니다.

```java
@FeignClient(value = "cockpit", url = "${clients.search}", fallback = CockpitClientFallback.class)
interface CockpitClient { ... }
```

`application.properties` 에 `clients.search` 가 정상적으로 정의되어 있었음에도
플레이스홀더를 찾지 못하는 오류가 발생했습니다.

---

### 원인 — BeanPostProcessor 조기 초기화의 Side Effect

`BeanPostProcessor` 는 ④ 단계에서 다른 어떤 일반 빈보다도 먼저 초기화됩니다.
이때 `PropertiesMapper` 를 **직접 의존성(`final` 필드)** 으로 가지고 있으면,
Spring 은 `DbValueBeanPostProcessor` 를 만들기 위해 `PropertiesMapper` 를 **강제로 조기 초기화**합니다.

```
④ BeanPostProcessor 초기화
    │
    └─► DbValueBeanPostProcessor 생성 시도
            │
            └─► PropertiesMapper 강제 조기 초기화
                    │
                    └─► SqlSessionFactory 조기 초기화
                            │
                            └─► DataSource 조기 초기화
                                    │
                                    └─► 인프라 빈 전체가 ③ 단계 이전에 로드됨
                                            │
                                            ↓ 부작용 (Side Effect)
                                        @FeignClient url="${clients.search}" 리졸브 시도
                                        → PropertySourcesPlaceholderConfigurer 아직 미실행
                                        → 플레이스홀더 못 찾음 ❌
```

정상 순서라면 `③ PropertySourcesPlaceholderConfigurer` 가 먼저 실행된 후
`@FeignClient` 플레이스홀더가 리졸브되어야 하지만,
`PropertiesMapper` 조기 초기화로 인해 이 순서가 역전되었습니다.

---

### 시도했던 방법 — `@Lazy` (효과 없음)

```java
// 시도 1: @RequiredArgsConstructor 와 함께 필드에 @Lazy
@Lazy
private final PropertiesMapper propertiesMapper;  // ❌ Lombok 이 @Lazy 를 생성자 파라미터에 전달하지 않음
```

`@RequiredArgsConstructor` 는 Lombok 이 생성자를 자동 생성하는데,
필드에 붙인 `@Lazy` 는 Lombok 이 생성자 파라미터에 전달하지 않습니다.
결과적으로 `@Lazy` 가 적용되지 않아 동일한 오류가 계속 발생했습니다.

생성자를 직접 작성해도 `@Lazy` 프록시가 `BeanPostProcessor` 단계에서
올바르게 동작하지 않는 경우가 있어 불안정합니다.

---

### 적용한 해결책 — `ObjectProvider<T>`

`ObjectProvider<T>` 는 Spring 이 `BeanPostProcessor` 순서 문제 해결을 위해
공식 권장하는 지연 조회(lazy lookup) 방식입니다.

| 항목 | `PropertiesMapper` 직접 주입 | `ObjectProvider<PropertiesMapper>` |
|------|-----------------------------|------------------------------------|
| 초기화 시점 | `BeanPostProcessor` 생성 시 즉시 | `getObject()` 첫 호출 시 |
| `@FeignClient` 영향 | ④ 단계에서 인프라 빈 조기 초기화 → 순서 역전 ❌ | 조기 초기화 없음 → 순서 정상 ✅ |
| `@Lazy` 와의 차이 | — | Lombok 생성자와 무관하게 안정적 동작 |

#### 변경 전

```java
@Component
@RequiredArgsConstructor
public class DbValueBeanPostProcessor implements BeanPostProcessor {

    private final PropertiesMapper propertiesMapper;   // ❌ 조기 초기화 유발
    private final Environment environment;
}
```

#### 변경 후

```java
@Component
@RequiredArgsConstructor
public class DbValueBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<PropertiesMapper> propertiesMapperProvider;  // ✅
    private final Environment environment;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // ...
        PropertiesResult result = propertiesMapperProvider.getObject()  // ← 실제 사용 시점에 초기화
                                      .findByKey(key, profile);
        // ...
    }
}
```

`JobSchedulerTargetBeanPostProcessor` 도 동일하게 `PropertiesMapper` 와 `TaskScheduler`
두 의존성 모두 `ObjectProvider<T>` 로 변경했습니다.

---

### 수정 후 기동 흐름

```
④ BeanPostProcessor 초기화
    │
    └─► DbValueBeanPostProcessor 생성
            │
            └─► ObjectProvider<PropertiesMapper> 는 프록시만 생성 (실제 Mapper 미초기화)
                    ✅ 인프라 빈 조기 초기화 없음

③ PropertySourcesPlaceholderConfigurer 정상 실행
    └─► @FeignClient url="${clients.search}" 정상 리졸브 ✅

⑦ DataSource 초기화
    └─► SqlSessionFactory → PropertiesMapper 정상 초기화

⑧ @Service 빈 생성 시
    └─► postProcessBeforeInitialization() 호출
            └─► propertiesMapperProvider.getObject() 호출
                    └─► 이 시점에 PropertiesMapper 첫 조회 → DB 조회 정상 실행 ✅
```

---

## 11. 부분 마이그레이션 — properties 파일과 DB 혼용 시 동작

properties 파일에 있는 값을 DB 로 전부 옮기지 않은 경우, 두 방식이 같은 애플리케이션 안에 공존합니다.
각 processor 가 어떻게 동작하는지 정확히 이해해야 의도치 않은 오동작을 방지할 수 있습니다.

---

### DbValueBeanPostProcessor

이 processor 는 `@DbValue` 어노테이션이 붙은 필드만 처리합니다.
`@Value` 어노테이션 필드는 완전히 무시하며, Spring 기본 메커니즘이 별도로 처리합니다.

```
필드에 @Value   → Spring PropertySourcesPlaceholderConfigurer 처리 → properties 파일에서 읽음
필드에 @DbValue → DbValueBeanPostProcessor 처리                   → DB 에서 읽음
```

두 어노테이션은 독립적으로 동작하므로 같은 클래스 안에서 혼용해도 충돌하지 않습니다.

```java
// ✅ 같은 클래스에서 혼용 — 정상 동작
@Service
public class SomeService {

    @Value("${app.name}")           // properties 파일에서 읽음
    private String appName;

    @DbValue(value = "app.timeout") // DB 에서 읽음
    private int timeout;
}
```

#### DB 에 키가 없을 때의 fallback

`@DbValue` 가 선언된 필드는 **DB 만 참조합니다.** DB 에 키가 없으면 properties 파일로 되돌아가지 않고
어노테이션의 `defaultValue` 를 사용합니다.

```
@DbValue 처리 흐름:
  DB 조회 결과 있음 → refrc2 > refrc3 > annotation.defaultValue()
  DB 조회 결과 없음 → annotation.defaultValue()   ← properties 파일 값은 참조하지 않음
```

```java
// properties 파일: app.timeout=60
@DbValue(value = "app.timeout", defaultValue = "30")
private int timeout;
// DB 에 app.timeout 키가 없으면 → timeout = 30  (properties 의 60 이 아님)
```

> `@Value` → `@DbValue` 로 코드를 바꿨는데 DB 에 키를 아직 추가하지 않은 경우,
> properties 파일 값이 아닌 어노테이션 `defaultValue` 가 적용됩니다.
> DB 에 키를 먼저 추가한 뒤 코드를 전환하거나, `defaultValue` 를 properties 의 값과 맞춰두어야 합니다.

---

### JobSchedulerTargetBeanPostProcessor

이 processor 는 `@JobSchedulerTarget` 어노테이션이 붙은 메서드만 처리합니다.
`@Scheduled` 메서드는 완전히 무시하며, Spring `ScheduledAnnotationBeanPostProcessor` 가 별도로 처리합니다.

```
메서드에 @Scheduled          → Spring ScheduledAnnotationBeanPostProcessor 처리 → properties cron 사용 (기동 시 고정)
메서드에 @JobSchedulerTarget → JobSchedulerTargetBeanPostProcessor 처리        → 실행마다 DB 조회 (동적)
```

#### DB 에 키가 없을 때의 fallback

`@JobSchedulerTarget` 은 실행마다 DB 를 조회합니다. DB 에 키가 없으면 아래 기본값이 적용됩니다.

| 속성 | DB 키 없을 때 기본값 | 결과 |
|------|---------------------|------|
| `enabled` | `"false"` | 배치가 항상 skip — **무음 장애 위험** |
| `cron` | `"0 0/5 * * * ?"` | 5분마다 실행 시도 (enabled=false 이면 실제 실행 안 됨) |

> `@Scheduled` → `@JobSchedulerTarget` 으로 코드를 바꿨는데 DB 에 키를 아직 추가하지 않은 경우,
> `enabled` 기본값이 `"false"` 이므로 배치가 조용히 중단됩니다. 오류 없이 skip 되므로 발견이 어렵습니다.

#### 절대 금지 — 두 어노테이션 동시 선언

```java
// ❌ 이중 등록 — 배치가 2번 실행됨
@Scheduled(cron = "${batch.job.cron}")
@JobSchedulerTarget(enabled = "${batch.job.active}", cron = "${batch.job.cron}")
public void myBatch() { ... }
```

`Spring ScheduledAnnotationBeanPostProcessor` 와 `JobSchedulerTargetBeanPostProcessor` 가
각각 독립적으로 스케줄을 등록하므로 동일한 메서드가 2번 실행됩니다.
`@Scheduled` 를 `@JobSchedulerTarget` 으로 전환할 때 반드시 `@Scheduled` 를 제거해야 합니다.

---

### 마이그레이션 전환 상태별 체크리스트

| 전환 상태 | 코드 어노테이션 | DB 키 필요 | 위험 사항 |
|---|---|---|---|
| 미전환 (properties 그대로) | `@Value("${key}")` | 불필요 | 없음 |
| 미전환 (배치 그대로) | `@Scheduled(cron = "${key}")` | 불필요 | 없음 |
| 전환 완료 (필드) | `@DbValue(value = "key")` | **필수** | DB 키 없으면 `defaultValue` 사용 (properties 무시) |
| 전환 완료 (배치) | `@JobSchedulerTarget(enabled = "key")` | **필수** | DB 키 없으면 `enabled=false` → 배치 무음 중단 |
| 전환 실수 | `@Scheduled` + `@JobSchedulerTarget` 동시 선언 | — | 배치 이중 실행 버그 |

**안전한 전환 순서**

```
1. DB 테이블에 키/값 먼저 추가
2. 코드에서 @Value → @DbValue 또는 @Scheduled → @JobSchedulerTarget 으로 변경
3. @Scheduled 제거 확인 (JobSchedulerTarget 전환 시)
4. 배포 후 배치 실행 로그 확인
```

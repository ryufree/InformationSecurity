# @DbValue — DB 기반 프로퍼티 주입 설계

## 개요

Spring의 `@Value`를 대체하여, `application.properties` 대신 DB 테이블에서
프로퍼티를 읽어 Bean 필드에 주입하는 커스텀 어노테이션 시스템.

---

## DB 구조

### 조회 대상 테이블

```sql
maru_pl_comm_cd  (comm_type_cd = 'MARU_PROPERTIES')
```

### 컬럼 역할

| 컬럼           | 역할                                      | 예시                             |
|----------------|-------------------------------------------|----------------------------------|
| `refrc1`       | property key                              | `maru.batch.monitor.Mail.active` |
| `refrc2`       | 실제 적용값 (현재 설정된 값)              | `true`                           |
| `refrc3`       | default value (null 또는 빈 값일 수 있음) | `true`                           |
| `comm_type_cd` | 고정값                                    | `MARU_PROPERTIES`                |

### 값 적용 우선순위

```
refrc2 (DB 현재값)  →  refrc3 (DB 기본값)  →  @DbValue.defaultValue() (코드 fallback)
```

### 조회 SQL

```sql
SELECT refrc1, refrc2, refrc3
FROM maru_pl_comm_cd
WHERE comm_type_cd = 'MARU_PROPERTIES'
  AND del_yn = 0
  AND refrc1 = #{key}
```

---

## 파일 위치

### platform/dbproperties 하위 배치 — 가능하며 권장

```
src/main/java/
└── maru/
    └── platform/
        └── dbproperties/                         ← 기능 단위로 묶음
            ├── annotation/
            │   └── DbValue.java
            ├── config/
            │   └── DbValueBeanPostProcessor.java
            ├── dto/
            │   └── PropertiesResult.java
            └── mapper/
                └── PropertiesMapper.java
```

> **패키지명 주의:** Java 관례상 패키지는 소문자를 사용합니다.
> `DBProperties` (X) → `dbproperties` (O)
>
> 패키지명: `maru.platform.dbproperties.annotation`, `maru.platform.dbproperties.config` …

### 왜 `maru.platform` 하위인가

`plm`, `vrix` 등 모든 하위 모듈이 공통으로 참조하는 최상위 공통 레이어이기 때문.
`dbproperties`라는 하위 폴더로 묶으면 기능 단위 응집도가 높아지고 관리가 쉬워짐.

---

## 다른 패키지에서 참조하는 방법

### 1. import만 하면 된다 — 별도 설정 불필요

Spring Boot의 컴포넌트 스캔은 `@SpringBootApplication` 위치를 기준으로
하위 패키지를 **자동으로 전부 스캔**합니다.

`DbValueBeanPostProcessor`에 `@Component`가 붙어 있으므로,
별도 등록 없이 Spring이 자동으로 찾아서 동작시킵니다.

```java
// plm 패키지의 서비스에서 사용하는 예
package maru.plm.batch;

import maru.platform.dbproperties.annotation.DbValue;  // ← import 한 줄만 추가

@Service
public class BatchMailService {

    @DbValue(value = "maru.batch.monitor.Mail.active", defaultValue = "false")
    private boolean mailActive;

    @DbValue(value = "maru.batch.monitor.Mail.host")
    private String mailHost;
}
```

### 2. 컴포넌트 스캔 범위 확인

#### 컴포넌트 스캔이란

Spring Boot는 `@Component`, `@Service`, `@Repository`, `@Mapper` 등이 붙은 클래스를
자동으로 찾아 Bean으로 등록합니다. 이것을 **컴포넌트 스캔**이라고 합니다.

**핵심 규칙: 스캔은 `@SpringBootApplication`이 있는 클래스의 패키지를 기준으로,
그 하위 패키지만 탐색합니다.**

#### 정상 동작하는 경우

```
maru/                              ← @SpringBootApplication 위치
└── MaruPlatformApplication.java   ← package maru;

├── platform/
│   └── dbproperties/
│       └── config/
│           └── DbValueBeanPostProcessor.java  ← @Component
│
├── plm/
│   └── BatchService.java          ← @Service
│
└── vrix/
    └── SomeService.java           ← @Service
```

`package maru`에 진입점이 있으면 `maru.platform`, `maru.plm`, `maru.vrix` 전부 자동 스캔됩니다.

#### 문제가 생기는 경우

```
com/
└── example/
    └── MaruPlatformApplication.java  ← package com.example;

maru/                                 ← 스캔 안 됨!
└── platform/
    └── dbproperties/
        └── DbValueBeanPostProcessor.java  ← @Component 붙어있어도 무시됨
```

`com.example` 기준으로 스캔하므로 `maru.*` 패키지는 찾지 못합니다.
결과: `DbValueBeanPostProcessor`가 Bean으로 등록되지 않아 `@DbValue`가 전혀 동작하지 않음.

#### 해결: `scanBasePackages` 명시

```java
package com.example;

@SpringBootApplication(scanBasePackages = {"maru", "com.example"})
//                                          ↑ maru 전체   ↑ 기존 패키지도 유지
public class MaruPlatformApplication { ... }
```

#### 진입점 패키지별 동작 여부

| 진입점 package  | 동작 여부            | 이유                                          |
|-----------------|----------------------|-----------------------------------------------|
| `maru`          | 정상                 | `maru.**` 전체 자동 스캔                      |
| `maru.platform` | **주의** (일부 누락) | `maru.plm`, `maru.vrix`는 형제라 스캔 안 됨   |
| `com.maru`      | 동작 안 함           | 루트가 `com`이라 `maru.*`는 전혀 스캔 안 됨   |

> **`maru`가 루트이면 하위 구조는 무엇이든 상관없습니다.**
>
> ```
> maru/            ← @SpringBootApplication (package maru;)
> ├── com/...      → maru.com.** → 스캔됨
> ├── pkg/...      → maru.pkg.** → 스캔됨
> ├── dlm/...      → maru.dlm.** → 스캔됨
> └── platform/
>     └── dbproperties/...  → maru.platform.dbproperties.** → 스캔됨
> ```
>
> `maru.com`과 `com.maru`는 완전히 다른 패키지입니다.
> 루트(`maru`)가 같으면 깊이와 구조에 상관없이 전부 스캔됩니다.

> `package maru.platform`이면 `maru.plm`은 형제 패키지라 스캔되지 않으므로,
> 이 경우 `scanBasePackages = {"maru"}`를 명시해야 합니다.

#### 실무에서 확인하는 방법

진입점 파일(Application.java)의 첫 줄 `package` 선언을 확인합니다.

```java
package maru;           // → 문제 없음, maru.** 전체 자동 스캔
package maru.platform;  // → maru.plm, maru.vrix는 스캔 안 됨 → scanBasePackages 필요
package com.maru;       // → maru.** 전체 스캔 안 됨 → scanBasePackages 필요
```

---

## DbValueBeanPostProcessor가 DB를 읽는 시점

```
[서버 시작]
    │
    ▼
① JVM 시작
    │
    ▼
② application.properties 읽기          ← DB 연결 없음. @DbValue 사용 불가 구간
    │
    ▼
③ Tomcat 포트 바인딩 (server.port)      ← 이후 포트 변경 불가
    │
    ▼
④ Spring ApplicationContext 생성 시작
    │
    ▼
⑤ DataSource(DB 연결) 초기화           ← ★ 여기서부터 DB 조회 가능
    │
    ▼
⑥ @Service, @Repository 빈 생성       ← ★★ DbValueBeanPostProcessor가 동작하는 구간
    │
    │   각 빈이 생성될 때마다 자동으로 아래 순서 실행:
    │
    │   ① 빈 인스턴스 생성 (new)
    │   ② postProcessBeforeInitialization() 호출  ← DB에서 refrc2/refrc3 읽어 필드 주입
    │   ③ @PostConstruct 실행
    │   ④ InitializingBean.afterPropertiesSet() 실행
    │   ⑤ postProcessAfterInitialization() 호출
    │
    ▼
⑦ @Scheduled 등록 (cron 표현식 고정)
    │
    ▼
⑧ 서버 기동 완료
    │
    ▼
[요청 처리 중]
```

> **결론:** `@DbValue` 주입은 ⑥ 단계, 즉 DataSource 초기화(⑤) 이후이므로
> DB 조회가 안전하게 가능합니다.

---

## 누가 자동으로 호출하는가

개발자가 직접 호출할 필요가 없습니다. Spring이 자동으로 처리합니다.

```
Spring의 AbstractAutowireCapableBeanFactory
    │
    │  빈을 하나 만들 때마다 등록된 모든 BeanPostProcessor를 순서대로 호출
    │
    └─► DbValueBeanPostProcessor.postProcessBeforeInitialization(bean, beanName)
            │
            ├─ @DbValue 붙은 필드 탐색 (ReflectionUtils)
            ├─ PropertiesMapper.findByKey(refrc1) 로 DB 조회
            └─ 조회 결과를 필드 타입에 맞게 변환 후 주입
```

**자동 동작 조건 (체크리스트):**

| 조건 | 내용 |
|------|------|
| `@Component` | `DbValueBeanPostProcessor`에 붙어 있어야 함 |
| 컴포넌트 스캔 범위 | `maru.platform.dbproperties` 가 스캔 대상이어야 함 |
| `PropertiesMapper` | MyBatis Mapper가 정상 등록되어 있어야 함 |
| SQL XML 위치 | `PropertiesMapper.xml`이 MyBatis `mapperLocations` 경로에 있어야 함 |

---

## 사용법

```java
// 기존 (@Value 방식)
@Value("${maru.upload.max-file-size-mb:100}")
private int maxFileSizeMb;

// 변경 후 (@DbValue 방식)
@DbValue(value = "maru.upload.max-file-size-mb", defaultValue = "100")
private int maxFileSizeMb;

// refrc3이 DB에 있으면 defaultValue 생략 가능
@DbValue(value = "maru.batch.monitor.Mail.active")
private boolean mailActive;
```

### `defaultValue` 생략 가능 여부

| 타입           | refrc3 없을 때 생략 시 동작   | 권장          |
|----------------|-------------------------------|---------------|
| `String`       | 빈 문자열 `""`                | 생략 가능     |
| `boolean`      | `false`                       | 생략 가능     |
| `int` / `long` | `0` (의도치 않은 값 위험)     | **명시 권장** |

- DB `refrc3`에 기본값이 항상 있다면 어노테이션 `defaultValue`는 최후 안전망
- `int`/`long`은 `0`이 의미 있는 값일 수 있으므로 명시 권장

---

## 코드 파일 목록

| 파일 | 경로 (docs 기준) |
|------|-----------------|
| 어노테이션 | `code/annotation/DbValue.java` |
| BeanPostProcessor | `code/config/DbValueBeanPostProcessor.java` |
| DTO | `code/dto/PropertiesResult.java` |
| Mapper 인터페이스 | `code/mapper/PropertiesMapper.java` |
| SQL (MyBatis XML) | `code/sql/PropertiesMapper.xml` |

---

## @JobSchedulerTarget — DB 기반 배치 스케줄러 제어

### 개요

`@DbValue`가 **필드**에 DB 값을 주입하는 것처럼,
`@JobSchedulerTarget`은 **배치 메서드**의 실행 여부(enabled)와 스케줄(cron)을
DB에서 읽어 제어하는 커스텀 어노테이션 시스템.

```
@DbValue           → 필드 주입     → 기동 시 DB 조회 후 주입
@JobSchedulerTarget → 메서드 실행  → 실행마다 DB 조회 (동적 반영)
```

### 이름 변경이 없는 이유

`@DbValue`는 Spring 내장 `@Value`와 이름이 겹쳐서 충돌을 피하기 위해 이름을 바꿨습니다.
`@JobSchedulerTarget`은 Spring / Java 표준에 동일한 이름의 어노테이션이 존재하지 않아
이름 변경 없이 그대로 사용합니다.

| | @Value → @DbValue | @JobSchedulerTarget |
|---|---|---|
| 충돌 대상 | Spring `@Value` (`org.springframework.beans.factory.annotation.Value`) | 없음 |
| 이름 변경 | 필요 | 불필요 |

### @DbValue 와의 차이

| 구분 | @DbValue | @JobSchedulerTarget |
|------|----------|---------------------|
| 적용 대상 | 필드 | 메서드 |
| DB 조회 시점 | 기동 시 1회 | **실행마다** |
| 서버 재시작 없이 변경 | ❌ (주입 후 고정) | ✅ (실행마다 최신값) |
| 용도 | 설정값 주입 | 배치 ON/OFF, cron 동적 변경 |

### 사용법

```java
// 기존 — 기동 시 고정, DB 변경 후 재시작 필요
@JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
@Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
public void meetingEmailReminder_BEFORE() { ... }

// 변경 후 — 실행마다 DB 조회, 재시작 불필요
@JobSchedulerTarget(
    enabled = "${maru.batch.mh.meeting.reminder.active}",
    cron    = "${maru.batch.mh.meeting.reminder.cron}"
)
public void meetingEmailReminder() {
    // 순수 배치 로직만 작성
    // enabled 체크, cron 관리는 JobSchedulerTargetProcessor 가 대신함
}
```

> `@Scheduled` 어노테이션은 제거합니다.
> `JobSchedulerTargetProcessor` 가 cron 스케줄까지 대신 등록합니다.

### DB 구조 (SYS_CONFIG 테이블)

| PROFILE | PROP_KEY | PROP_VALUE | DATA_TYPE | EDITABLE_YN |
|---------|----------|-----------|-----------|-------------|
| `batch1` | `maru.batch.mh.meeting.reminder.active` | `true` | `BOOLEAN` | `Y` |
| `batch1` | `maru.batch.mh.meeting.reminder.cron` | `0 0/5 * * * ?` | `CRON` | `Y` |

`EDITABLE_YN = 'Y'` → 런타임 변경 즉시 반영 (서버 재시작 불필요)

### 동작 원리

```
기동 시:
  JobSchedulerTargetProcessor (BeanPostProcessor)
    → @JobSchedulerTarget 붙은 메서드 스캔
    → enabled/cron 의 "${...}" 에서 DB key 추출
    → TaskScheduler 에 태스크 등록 (enabled 체크 없이 무조건)

실행마다:
  ① enabledKey → SysConfigService.getBoolean() → DB 조회
     false 이면 skip, true 이면 실행
  ② cronKey    → SysConfigService.getString()  → DB 조회
     최신 cron 표현식으로 다음 실행 시간 결정
```

### 파일 위치

```
src/main/java/
└── maru/
    └── platform/
        ├── dbproperties/                         ← 기존 @DbValue 구조
        │   ├── annotation/DbValue.java
        │   ├── config/DbValueBeanPostProcessor.java
        │   ├── dto/PropertiesResult.java
        │   └── mapper/PropertiesMapper.java
        │
        └── scheduler/                            ← @JobSchedulerTarget 구조 (신규)
            ├── annotation/JobSchedulerTarget.java
            └── processor/JobSchedulerTargetProcessor.java
```

### JobSchedulerTargetProcessor 핵심 구조

```java
@Component
public class JobSchedulerTargetProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);  // AOP 프록시 대응

        for (Method method : targetClass.getMethods()) {
            JobSchedulerTarget ann = method.getAnnotation(JobSchedulerTarget.class);
            if (ann == null) continue;

            String enabledKey = extractKey(ann.enabled()); // "${...}" → key
            String cronKey    = extractKey(ann.cron());    // "${...}" → key

            taskScheduler.schedule(
                () -> {
                    // ★ @DbValue 와 동일한 구조: 실행마다 DB 조회
                    boolean active = sysConfigService.getBoolean(enabledKey, false);
                    if (!active) return;
                    method.invoke(bean);
                },
                triggerContext -> {
                    // ★ cron 도 실행마다 DB 조회 → 동적 스케줄 반영
                    String cron = sysConfigService.getString(cronKey, "0 0/5 * * * ?");
                    return new CronTrigger(cron).nextExecution(triggerContext);
                }
            );
        }
        return bean;
    }
}
```

### 코드 파일 목록

| 파일 | 경로 (예제 기준) |
|------|-----------------|
| 어노테이션 | `example/annotation/JobSchedulerTarget.java` |
| BeanPostProcessor | `example/processor/JobSchedulerTargetProcessor.java` |
| 패턴 예제 | `example/pattern/Pat2_FeatureFlag.java` |
| DB 스키마 | `resources/sql/SysConfig_schema.sql` |
| **단위 테스트** | `test/.../processor/JobSchedulerTargetProcessorTest.java` |

---

### 테스트 방법

#### 테스트 구조 (단위 테스트)

실제 스케줄러 없이 `ArgumentCaptor`로 `Runnable`/`Trigger`를 꺼내
즉시 실행함으로써 DB 조회 결과에 따른 동작을 검증합니다.

```
SysConfigService (Mock)  →  getBoolean() 반환값을 true/false 로 제어
TaskScheduler    (Mock)  →  등록된 Runnable 을 ArgumentCaptor 로 포착
                             → run() 직접 호출하여 배치 실행 시뮬레이션
```

#### 테스트 케이스 3가지

**① enabled = true → 배치 실행 확인**

```java
@Test
@DisplayName("enabled=true → 배치 메서드 실행됨")
void enabled_true_배치_실행() {
    TestBatch bean = new TestBatch();
    given(sysConfigService.getBoolean(ENABLED_KEY, false)).willReturn(true);  // DB: true

    processor.postProcessAfterInitialization(bean, "testBatch");
    verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
    runnableCaptor.getValue().run();  // ← 실제 스케줄러 없이 즉시 실행

    assertThat(bean.executed).isTrue();  // 메서드가 호출됨
}
```

**② enabled = false → skip 확인**

```java
@Test
@DisplayName("enabled=false → 배치 메서드 skip")
void enabled_false_배치_스킵() {
    TestBatch bean = new TestBatch();
    given(sysConfigService.getBoolean(ENABLED_KEY, false)).willReturn(false); // DB: false

    processor.postProcessAfterInitialization(bean, "testBatch");
    verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
    runnableCaptor.getValue().run();

    assertThat(bean.executed).isFalse();  // 메서드가 호출 안됨
}
```

**③ cron → DB 값으로 다음 실행 시간 결정 확인**

```java
@Test
@DisplayName("cron — DB에서 읽은 값으로 다음 실행 시간 결정")
void cron_DB에서_동적_조회() {
    String customCron = "0 0 9 * * ?";  // DB 에서 매일 09:00 으로 변경된 상황
    given(sysConfigService.getString(CRON_KEY, DEFAULT_CRON)).willReturn(customCron);

    processor.postProcessAfterInitialization(new TestBatch(), "testBatch");
    verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());

    Instant nextTime = triggerCaptor.getValue().nextExecution(mock(TriggerContext.class));
    assertThat(nextTime).isNotNull();  // cron 파싱 성공, 다음 실행 시간 계산됨
}
```

#### 테스트 실행 명령

```bash
# 전체 테스트
./gradlew test

# 해당 클래스만
./gradlew test --tests "com.chipset.example.processor.JobSchedulerTargetProcessorTest"

# 특정 메서드만
./gradlew test --tests "com.chipset.example.processor.JobSchedulerTargetProcessorTest.enabled_true_배치_실행"
```

#### 테스트 결과 위치

```
backend/build/reports/tests/test/index.html
```

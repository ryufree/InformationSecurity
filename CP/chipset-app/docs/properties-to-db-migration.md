# Properties → DB 이전 가이드

> **이 문서의 목적**  
> `application.properties` 파일에 흩어져 있는 설정값들을 Oracle DB 테이블(`SYS_CONFIG`)로  
> 옮기는 작업의 전체 개념, 코드 설명, 우선순위를 정리한다.

---

## 목차

1. [왜 DB로 옮기는가?](#1-왜-db로-옮기는가)
2. [핵심 개념 이해](#2-핵심-개념-이해)
3. [생성된 파일 목록](#3-생성된-파일-목록)
4. [SYS_CONFIG 테이블 설계](#4-sysconfigs-테이블-설계)
5. [이전 가능 vs 이전 불가 판단 기준](#5-이전-가능-vs-이전-불가-판단-기준)
6. [이전 우선순위 로드맵](#6-이전-우선순위-로드맵)
7. [Pattern 1: @Value → DB 직접 호출](#7-pattern-1-value--db-직접-호출)
8. [Pattern 2: Feature Flag (ON/OFF 스위치)](#8-pattern-2-feature-flag-onoff-스위치)
9. [Pattern 3: DB → Spring Environment 등록](#9-pattern-3-db--spring-environment-등록)
10. [Pattern 4: 동적 스케줄러 (cron을 DB에서)](#10-pattern-4-동적-스케줄러-cron을-db에서)
11. [핵심 클래스 설명](#11-핵심-클래스-설명)
12. [실제 적용 순서 체크리스트](#12-실제-적용-순서-체크리스트)

---

## 1. 왜 DB로 옮기는가?

### 현재 상황 (문제점)

```
application-dev.properties    ← 개발 서버 설정
application-live1.properties  ← 운영 서버1 설정
application-live2.properties  ← 운영 서버2 설정
application-rc.properties     ← RC 서버 설정
application-batch.properties  ← 배치 서버 설정
```

**문제 시나리오:**
> 운영 중에 "미팅 알림 배치를 잠깐 꺼야 해" → 파일 수정 → 서버 재시작 → 서비스 중단

### DB 이전 후 (개선)

```
SYS_CONFIG 테이블에서 관리
→ DB 값 UPDATE 한 줄이면 즉시 반영
→ 서버 재시작 없음
→ 관리자 화면에서 비개발자도 수정 가능
```

---

## 2. 핵심 개념 이해

### Spring Boot 기동 순서 (이것이 이전 가능 여부를 결정한다)

```
[서버 시작]
    │
    ▼
① JVM 시작
    │
    ▼
② application.properties 읽기          ← ★ 이 시점에 server.port 등을 읽음
    │                                      DB 연결이 아직 없으므로 DB 조회 불가
    ▼
③ Tomcat 포트 바인딩 (server.port=8443)  ← 이후 포트 변경 불가
    │
    ▼
④ Spring ApplicationContext 생성 시작
    │
    ▼
⑤ DataSource(DB 연결) 초기화            ← ★ 여기서부터 DB 조회 가능
    │
    ▼
⑥ @Service, @Repository 빈 생성
    │
    ▼
⑦ @Scheduled 등록 (cron 표현식 고정)    ← 이후 cron 변경 불가 (기본 방식)
    │
    ▼
⑧ 서버 기동 완료
    │
    ▼
[요청 처리 중] ← ★ 여기서는 DB 자유롭게 읽고 쓸 수 있음
```

**결론:**
- **② 이전** 에 필요한 값 → DB 이전 **불가** (`server.port` 등)
- **⑧ 이후** 에 사용하는 값 → DB 이전 **가능** (비즈니스 로직 설정 등)

---

## 3. 생성된 파일 목록

```
backend/
├── docs/
│   └── properties-to-db-migration.md        ← 지금 보고 있는 이 문서
│
├── src/main/resources/
│   ├── sql/
│   │   └── SysConfig_schema.sql             ← DB 테이블 생성 + 샘플 데이터
│   └── mapper/
│       └── SysConfigMapper.xml              ← SQL 쿼리 모음 (MyBatis)
│
└── src/main/java/com/chipset/example/
    ├── model/
    │   └── SysConfig.java                   ← 테이블 한 행(Row)을 담는 클래스
    ├── mapper/
    │   └── SysConfigMapper.java             ← DB 조회 인터페이스 선언
    ├── service/
    │   └── SysConfigService.java            ← 핵심! 설정값 읽기/쓰기 서비스
    └── pattern/
        ├── Pat1_DirectServiceCall.java      ← 패턴1: @Value 교체
        ├── Pat2_FeatureFlag.java            ← 패턴2: ON/OFF 스위치
        ├── Pat3_DbPropertySource.java       ← 패턴3: Spring 환경변수 등록
        └── Pat4_DynamicScheduler.java       ← 패턴4: 동적 cron 스케줄러
```

---

## 4. SYS_CONFIG 테이블 설계

### 테이블 구조

```sql
CREATE TABLE SYS_CONFIG (
    CONFIG_ID   NUMBER        -- 고유 번호 (자동증가)
    PROFILE     VARCHAR2(50)  -- 'dev' / 'live1' / 'batch1' / 'common'
    PROP_KEY    VARCHAR2(200) -- 설정 키 (예: 'maru.batch.mh.meeting.reminder.active')
    PROP_VALUE  VARCHAR2(2000)-- 설정 값 (예: 'true')
    DATA_TYPE   VARCHAR2(20)  -- 값의 타입: STRING / BOOLEAN / INTEGER / CRON
    EDITABLE_YN CHAR(1)       -- Y=재시작없이 변경가능, N=재시작필요
    RESTART_YN  CHAR(1)       -- Y=서버기동시에만 적용됨(DB이전불가)
    DESCRIPTION VARCHAR2(500) -- 설명
    USE_YN      CHAR(1)       -- Y=사용중, N=비활성
)
```

### 실제 데이터 예시

| PROFILE | PROP_KEY | PROP_VALUE | EDITABLE_YN | RESTART_YN | 의미 |
|---------|----------|------------|-------------|------------|------|
| `dev` | `server.port` | `8443` | `N` | `Y` | ❌ 이전불가 |
| `live1` | `server.port` | `443` | `N` | `Y` | ❌ 이전불가 |
| `live1` | `server.tomcat.max-threads` | `3000` | `N` | `Y` | ❌ 이전불가 |
| `batch1` | `maru.batch.mh.meeting.reminder.active` | `true` | `Y` | `N` | ✅ 이전가능 |
| `batch1` | `maru.batch.mh.meeting.reminder.cron` | `0 0/5 * * * ?` | `Y` | `N` | ✅ 이전가능(주의) |
| `common` | `maru.email.sender` | `noreply@example.com` | `Y` | `N` | ✅ 이전가능 |

### PROFILE = 'common' 이란?

```
common 은 모든 환경에서 공통으로 사용하는 기본값.
dev/live1 등 특정 프로파일에 값이 없으면 common 값을 사용.

조회 우선순위: 현재 프로파일(dev/live1 등) > common
```

---

## 5. 이전 가능 vs 이전 불가 판단 기준

### ❌ 이전 불가 (RESTART_YN = 'Y')

이 설정들은 **서버가 시작하는 순간** Spring/Tomcat이 읽어버린다.  
그 시점에는 아직 DB 연결이 없으므로 DB에서 읽는 것이 **구조적으로 불가능**하다.

```properties
# application-dev.properties
server.port=8443               ← Tomcat이 이 포트를 열어버림. 이후 변경 불가
server.port.http=8001          ← HTTP 커넥터 설정. 동일
security.require-ssl=true      ← Spring Security 초기화 시 고정

# application-live1.properties
server.port=443
server.port.http=80
server.tomcat.max-threads=3000 ← Tomcat 스레드풀 크기. 기동 시 고정
```

**왜 불가능한가? 쉬운 비유:**
> 자동차가 출발(서버 기동)하기 전에 목적지(포트 번호)를 정해야 한다.  
> 출발한 후에 목적지를 바꾸면 이미 달리고 있는 자동차가 방향을 바꿀 수 없다.

**해결책:** 이 값들은 properties 파일에 그대로 두되, `SYS_CONFIG` 테이블에도  
**기록 목적으로만** 저장해두어 어떤 환경에서 어떤 포트를 쓰는지 한눈에 확인할 수 있게 한다.

---

### ✅ 이전 가능 (RESTART_YN = 'N')

이 설정들은 **서버가 완전히 기동된 후** 비즈니스 로직에서 읽는다.  
서비스가 실행되는 중에 DB를 자유롭게 읽을 수 있으므로 이전 가능하다.

```properties
# application-batch1.properties
maru.batch.mh.meeting.reminder.active=true   ← 배치 실행 시 읽으면 됨 ✅
maru.batch.mh.meeting.reminder.cron=0 0/5 * * * ?  ← @Scheduled 주의 필요

# 기타 비즈니스 설정들
maru.upload.max-file-size-mb=100   ← 업로드 처리 시 읽으면 됨 ✅
maru.email.sender=noreply@...      ← 이메일 발송 시 읽으면 됨 ✅
```

### ⚠️ cron 표현식의 특수한 경우

```java
// 현재 코드 (문제)
@Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
public void meetingEmailReminder() { ... }
```

`@Scheduled`의 cron 값은 **서버 기동 시** 딱 한 번 고정된다.  
즉, DB에서 cron을 바꿔도 서버를 재시작하지 않으면 반영이 안 된다.

→ **Pattern 4**로 코드를 재작성하면 해결 가능 (다음 섹션 참고)

---

## 6. 이전 우선순위 로드맵

```
작업 난이도: 낮음 ────────────────────────────────────── 높음
              │                                          │
              ▼                                          ▼

[1순위] boolean 플래그      [2순위] String/Integer    [3순위] cron 표현식    [불가]
        (active, enabled)           값들                (@Scheduled 재작성)  server.*
                                                                             security.*
```

### 1순위: boolean 플래그 ★ 지금 당장 가능, 코드 변경 최소

**대상 설정:**
```
maru.batch.mh.meeting.reminder.active=true
maru.feature.new-ui-enabled=false
```

**변경 전 코드:**
```java
// MhBatchJobScheduler.java
@JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
public BatchResult meetingEmailReminder() {
    // 항상 실행됨 (properties에서 active=true라고 고정)
}
```

**변경 후 코드:**
```java
// MhBatchJobScheduler.java
public BatchResult meetingEmailReminder() {
    boolean active = sysConfigService.getBoolean(
        "maru.batch.mh.meeting.reminder.active", true);

    if (!active) return; // DB에서 false로 바꾸면 즉시 중단

    // 실제 배치 로직
}
```

**효과:** DB에서 `active`를 `false`로 바꾸면 다음 실행부터 즉시 중단. 재시작 불필요.

---

### 2순위: String / Integer 비즈니스 값 ★★ 1순위 완료 후 진행

**대상 설정:**
```
maru.upload.max-file-size-mb=100
maru.email.sender=noreply@example.com
maru.meeting.join-url=https://meet.example.com
maru.batch.email.retry-count=3
```

**변경 전 코드:**
```java
// MeetingHubUploadService.java (현재)
@Value("${maru.upload.max-file-size-mb}")
private int maxFileSizeMb;   // 서버 기동 시 100으로 고정

public void upload(byte[] file) {
    if (file.length > maxFileSizeMb * 1024 * 1024) { ... }
}
```

**변경 후 코드:**
```java
// MeetingHubUploadService.java (이전 후)
// @Value 필드 삭제

public void upload(byte[] file) {
    int maxMb = sysConfigService.getInt("maru.upload.max-file-size-mb", 100);
    // 이제 DB에서 100→200으로 바꾸면 다음 업로드부터 즉시 반영
    if (file.length > maxMb * 1024 * 1024) { ... }
}
```

---

### 3순위: @Value 필드 전체 교체 ★★★ 2순위 완료 후 진행

Pattern 3 (DbPropertySource)을 사용하면 `@Value` 어노테이션을 그대로 두고  
DB에서 값을 읽을 수 있다. 단, 빈 초기화 순서 문제로 제약이 있다. (패턴 설명 참고)

---

### 4순위: cron 동적화 ★★★★ 코드 재작성 필요

`@Scheduled(cron = "${...}")` 를 `SchedulingConfigurer` 방식으로 변경.  
Pattern 4 참고.

---

### 이전 안 함 (properties 파일 유지)

```
server.port
server.port.http
server.tomcat.max-threads
security.require-ssl
```

이 값들은 그대로 `application-{profile}.properties`에 유지.  
`SYS_CONFIG`에는 **참고 기록**으로만 저장.

---

## 7. Pattern 1: @Value → DB 직접 호출

**파일:** `Pat1_DirectServiceCall.java`

### 개념 그림

```
[이전 전]
application.properties ──→ @Value 주입 ──→ 필드에 저장 ──→ 서비스 사용
                            (기동 시 1회)    (변경 불가)

[이전 후]
SYS_CONFIG 테이블 ──→ SysConfigService.getInt() ──→ 메서드 실행 시마다 최신값
                        (메서드 호출마다 DB 조회)
```

### Before / After 비교

```java
// ════════════════════════════════════════
// BEFORE: properties 파일 기반 @Value
// ════════════════════════════════════════
@Service
public class MeetingHubUploadService {

    @Value("${server.port}")              // ← 이전 불가 (서버 포트)
    private String localPort;

    @Value("${maru.upload.max-file-size-mb}")  // ← 이전 가능
    private int maxFileSizeMb;            // 서버 기동 시 100으로 고정됨

    public void upload(byte[] file) {
        // maxFileSizeMb는 항상 100 (DB에서 바꿔도 재시작 전까지 100)
        if (file.length > maxFileSizeMb * 1024 * 1024) {
            throw new Exception("파일 초과");
        }
    }
}


// ════════════════════════════════════════
// AFTER: DB 조회 방식
// ════════════════════════════════════════
@Service
public class MeetingHubUploadService {

    @Autowired
    private SysConfigService sysConfigService;

    @Value("${server.port:9090}")         // ← 이전 불가이므로 @Value 유지
    private String localPort;

    // @Value 필드 삭제 → 메서드에서 직접 조회
    public void upload(byte[] file) {
        // 매번 DB에서 읽음 → DB에서 값 바꾸면 다음 업로드부터 즉시 반영
        int maxMb = sysConfigService.getInt("maru.upload.max-file-size-mb", 100);

        if (file.length > maxMb * 1024 * 1024) {
            throw new Exception("파일 초과: 최대 " + maxMb + "MB");
        }
    }
}
```

### 주의사항

- **성능**: 매 요청마다 DB를 조회한다. 변경 빈도가 낮은 값은 캐시(예: `@Cacheable`) 적용 권장.
- **server.port 같은 이전 불가 값**: `@Value` 필드를 그대로 유지한다.

---

## 8. Pattern 2: Feature Flag (ON/OFF 스위치)

**파일:** `Pat2_FeatureFlag.java`

### 개념: 긴급 킬스위치(Kill-Switch)

```
DB에서 active = false 로 UPDATE
           │
           ▼
다음 배치 실행 시 → if (!active) return; → 즉시 중단
           │
           ▼
재시작 없이 배치 비활성화 완료 ✅
```

### 배치 활성화 플래그 적용

```java
// ════════════════════════════════════════
// BEFORE: @JobSchedulerTarget 방식
// ════════════════════════════════════════
@Profile({SpringProfile.BATCH_1})
public class MhBatchJobScheduler {

    // enabled 값이 기동 시 'true'로 고정됨
    // → 배치를 끄려면 properties 수정 후 재시작 필요
    @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
    @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
    public BatchResult meetingEmailReminder() {
        // 항상 실행됨
        sendReminderEmails();
    }
}


// ════════════════════════════════════════
// AFTER: DB boolean 플래그 방식
// ════════════════════════════════════════
@Profile({SpringProfile.BATCH_1})
public class MhBatchJobScheduler {

    @Autowired
    private SysConfigService sysConfigService;

    @Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")  // cron은 일단 유지
    public BatchResult meetingEmailReminder() {

        // ★ 핵심: 실행할 때마다 DB에서 최신 active 값을 읽는다
        boolean active = sysConfigService.getBoolean(
            "maru.batch.mh.meeting.reminder.active", true);

        if (!active) {
            // DB에서 active=false 로 바꾸면 다음 실행부터 여기서 종료됨
            log.info("배치 비활성화 상태, 스킵");
            return BatchResult.skipped();
        }

        // 실제 배치 로직 실행
        sendReminderEmails();
    }
}
```

### 신규 UI 피처 플래그 예시

```java
public String getHomeView(String userId) {
    // DB에서 false → true 로 바꾸면 신규 UI 즉시 활성화
    boolean newUiEnabled = sysConfigService.getBoolean(
        "maru.feature.new-ui-enabled", false);

    return newUiEnabled ? "new-home" : "legacy-home";
}
```

### 이메일 재시도 횟수 동적 조회

```java
public boolean sendWithRetry(String to, String body) {
    // DB에서 3 → 5로 바꾸면 다음 발송부터 5회 재시도
    int retryCount = sysConfigService.getInt("maru.batch.email.retry-count", 3);

    for (int i = 0; i <= retryCount; i++) {
        if (sendEmail(to, body)) return true;
    }
    return false;
}
```

---

## 9. Pattern 3: DB → Spring Environment 등록

**파일:** `Pat3_DbPropertySource.java`

### 개념

```
[일반적인 @Value 동작]
application.properties → Spring Environment → @Value 주입 (기동 시 1회)

[Pattern 3 추가 후]
application.properties }
SYS_CONFIG 테이블      } → Spring Environment → Environment.getProperty("key") 로 조회 가능
```

### 코드 설명

```java
@Configuration
public class Pat3_DbPropertySource {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private ConfigurableEnvironment environment;

    // 서버 완전 기동 후(@PostConstruct) DB 값을 Spring Environment에 추가
    @PostConstruct
    public void registerDbPropertySource() {
        Map<String, String> dbProps = sysConfigService.loadAsMap();
        // ...
        // application.properties보다 높은 우선순위로 등록
        environment.getPropertySources().addFirst(dbSource);
    }
}
```

### 한계점 (중요!)

```
❌ @Value 필드에는 반영되지 않음

왜냐하면 @Value 주입은 빈(Bean) 초기화 시 1회 실행되는데,
Pat3는 모든 빈이 다 만들어진 후(@PostConstruct)에 등록되기 때문.

✅ 이런 경우에는 사용 가능:
  - environment.getProperty("key") 직접 호출
  - 테스트 코드에서 설정값 오버라이드
  - 관리자 API에서 "현재 적용된 설정 확인"
```

---

## 10. Pattern 4: 동적 스케줄러 (cron을 DB에서)

**파일:** `Pat4_DynamicScheduler.java`

### 문제: @Scheduled의 한계

```java
// 현재 코드 - cron이 기동 시 고정됨
@Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")
// "0 0/5 * * * ?" 이 서버 기동 시 딱 한 번 읽힘
// DB에서 값을 바꿔도 서버 재시작 전까지 반영 안됨
public void meetingEmailReminder() { ... }
```

**cron 표현식 읽는 법:**
```
0 0/5 * * * ?
│  │  │ │ │ └─ 요일 (?)
│  │  │ │ └─── 월 (*)
│  │  │ └───── 일 (*)
│  │  └─────── 시간 (*)
│  └────────── 분 (매 5분마다)
└──────────── 초 (0초)

= 매 5분마다 실행
```

### 해결: SchedulingConfigurer 방식

```java
// ════════════════════════════════════════
// AFTER: 동적 cron 방식 (Pat4_DynamicScheduler)
// ════════════════════════════════════════
@Configuration
@EnableScheduling
public class Pat4_DynamicScheduler implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {

        registrar.addTriggerTask(
            // ① 실행할 작업
            this::meetingEmailReminder,

            // ② 다음 실행 시간 결정 (매 실행 완료 후 호출됨)
            triggerContext -> {
                // ★ 매번 DB에서 최신 cron 읽음
                String cron = sysConfigService.getString(
                    "maru.batch.mh.meeting.reminder.cron",
                    "0 0/5 * * * ?");

                return new CronTrigger(cron).nextExecution(triggerContext);
            }
        );
    }
}
```

### 동작 흐름

```
[서버 기동]
    │
    ▼
configureTasks() 호출 → 태스크 등록

[매 실행 사이클]
    │
    ├─→ ① 배치 실행 (meetingEmailReminder)
    │
    └─→ ② Trigger 호출: "다음 실행은 언제?"
              │
              ▼
         DB에서 cron 값 읽기
              │
              ▼
         새 CronTrigger 생성 → 다음 실행 시간 계산
              │
              ▼
         [다음 실행 대기]
```

**결과:** DB에서 `0 0/5 * * * ?` → `0 0/10 * * * ?`로 바꾸면  
**현재 실행이 끝난 후** 다음 실행부터 10분 간격으로 자동 변경.

### cron 변경 API 예시

```java
// REST API에서 호출
@RestController
public class ConfigController {

    @Autowired
    private Pat4_DynamicScheduler scheduler;

    @PutMapping("/admin/batch/cron")
    public String updateCron(@RequestParam String cron) {
        scheduler.updateCron(cron);  // 유효성 검사 + DB 업데이트
        return "변경 완료: " + cron;
    }
}
```

---

## 11. 핵심 클래스 설명

### SysConfig.java — 테이블 한 행(Row)을 담는 그릇

```java
// DB의 SYS_CONFIG 테이블 한 행이 이 클래스 하나에 매핑됨
SysConfig config = ...;

config.getPropKey();    // "maru.batch.mh.meeting.reminder.active"
config.getPropValue();  // "true" (문자열로 저장됨)

// 편의 변환 메서드
config.asBoolean();     // true  (String "true" → boolean)
config.asInt(100);      // 100   (String "100" → int)
config.asString("기본값"); // "noreply@example.com"
```

---

### SysConfigMapper.java + SysConfigMapper.xml — DB 조회 담당

```
SysConfigMapper.java   ← 메서드 선언 (인터페이스)
SysConfigMapper.xml    ← 실제 SQL (MyBatis)

예시:
selectByProfileAndKey("batch1", "maru.batch.mh.meeting.reminder.active")
→ SELECT ... WHERE PROFILE='batch1' AND PROP_KEY='maru.batch...'
→ SysConfig 객체 반환
```

**common 자동 폴백(fallback) 쿼리:**

```sql
-- SysConfigMapper.xml 핵심 쿼리
SELECT * FROM SYS_CONFIG
WHERE PROFILE IN ('common', 'batch1')  -- common + 현재 프로파일 동시 조회
AND USE_YN = 'Y'
ORDER BY CASE PROFILE WHEN 'common' THEN 0 ELSE 1 END  -- common 먼저, batch1이 override
```

---

### SysConfigService.java — 실제로 코드에서 사용하는 서비스

이 클래스만 알면 된다. 다른 클래스는 이것이 내부적으로 사용한다.

```java
@Autowired
private SysConfigService sysConfigService;

// String 값 가져오기
String sender = sysConfigService.getString(
    "maru.email.sender",          // DB에서 찾을 키
    "default@example.com");       // DB에 없으면 이 기본값 사용

// boolean 값 가져오기
boolean active = sysConfigService.getBoolean(
    "maru.batch.mh.meeting.reminder.active",
    true);                        // 기본값 true

// int 값 가져오기
int maxMb = sysConfigService.getInt(
    "maru.upload.max-file-size-mb",
    100);                         // 기본값 100MB

// 런타임에 값 변경
sysConfigService.updateValue(
    "maru.batch.email.retry-count",
    "5");                         // 3 → 5로 변경
```

**공통 fallback 로직:**
```
1. 현재 프로파일(예: batch1)에서 먼저 찾는다
2. 없으면 common에서 찾는다
3. 거기도 없으면 코드에서 지정한 defaultValue를 사용한다
```

---

## 12. 실제 적용 순서 체크리스트

### Step 1: DB 테이블 생성 (DBA 또는 직접)

```sql
-- DBeaver에서 실행
@SysConfig_schema.sql
```

- [ ] `SQ_SYS_CONFIG` 시퀀스 생성 확인
- [ ] `SYS_CONFIG` 테이블 생성 확인
- [ ] 샘플 데이터 13건 INSERT 확인

---

### Step 2: 1순위 - boolean 플래그 이전

```java
// 이전 대상 클래스 찾기:
// grep -r "maru.batch.mh.meeting.reminder.active" --include="*.java"

// 변경:
// Before: @JobSchedulerTarget(enabled = "${maru.batch.mh.meeting.reminder.active}")
// After: boolean active = sysConfigService.getBoolean("maru.batch...", true);
```

- [ ] `SysConfigService` `@Autowired` 주입
- [ ] `@JobSchedulerTarget(enabled = "${...}")` 제거
- [ ] 메서드 내부에 `getBoolean()` + `if (!active) return;` 추가
- [ ] 테스트: DB에서 `active=false`로 변경 후 배치가 실제로 스킵되는지 확인

---

### Step 3: 2순위 - String/Integer 값 이전

```java
// 이전 대상: @Value 어노테이션 사용 중인 비즈니스 설정값들
// grep -r "@Value" --include="*.java" | grep "maru\."
```

- [ ] `@Value("${maru.upload.max-file-size-mb}")` → `sysConfigService.getInt(...)` 교체
- [ ] `@Value("${maru.email.sender}")` → `sysConfigService.getString(...)` 교체
- [ ] `@Value("${maru.meeting.join-url}")` → `sysConfigService.getString(...)` 교체
- [ ] 테스트: DB 값 변경 후 즉시 반영되는지 확인

---

### Step 4: 4순위 - cron 동적화 (선택사항)

- [ ] `@Scheduled(cron = "${maru.batch.mh.meeting.reminder.cron}")` 제거
- [ ] `SchedulingConfigurer` 구현 (Pat4_DynamicScheduler 참고)
- [ ] 테스트: DB에서 cron 변경 후 다음 실행부터 반영되는지 확인

---

### 이전 안 하는 것들 (확인만)

- [x] `server.port` → properties 파일 유지 (`SYS_CONFIG`에 기록만)
- [x] `server.port.http` → 동일
- [x] `server.tomcat.max-threads` → 동일
- [x] `security.require-ssl` → 동일

---

## 요약

```
properties 파일의 설정값
        │
        ├─ 서버 기동 시 필요한 값 (server.*, security.*)
        │       │
        │       └─ ❌ 이전 불가 → properties 파일 유지
        │              (SYS_CONFIG에 기록만)
        │
        └─ 비즈니스 로직에서 사용하는 값 (maru.*)
                │
                ├─ boolean 플래그 (active, enabled)
                │       └─ ✅ 1순위 이전 (Pattern 2)
                │
                ├─ String/Integer 값 (URL, 크기, 개수 등)
                │       └─ ✅ 2순위 이전 (Pattern 1)
                │
                └─ cron 표현식
                        └─ ✅ 4순위 이전 (Pattern 4, 코드 재작성)
```

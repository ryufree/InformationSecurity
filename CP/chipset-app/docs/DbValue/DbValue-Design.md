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

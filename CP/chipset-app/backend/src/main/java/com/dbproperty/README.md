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

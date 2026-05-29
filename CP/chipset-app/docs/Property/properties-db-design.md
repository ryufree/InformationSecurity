# Properties DB화 설계 문서

> **작성 목적**: Spring `application.properties` 파일을 Oracle DB로 이관하는 설계 방식, 구현 코드, 운영 방법을 팀원들과 공유합니다.

---

## 1. 배경 및 목적

### 기존 파일 방식의 문제점

```
src/main/resources/
├── application.properties          ← 공통 설정
├── application-dev.properties      ← DEV 환경
├── application-batch.properties    ← BATCH 환경
├── application-live1.properties    ← LIVE1 환경
├── application-rc.properties       ← RC 환경
└── application-staging.properties  ← STAGING 환경
```

| 문제 | 설명 |
|---|---|
| 변경 시 재배포 필요 | 설정값 하나를 바꾸려면 빌드/배포 전체를 다시 해야 함 |
| 파일 분산 관리 | 환경이 늘어날수록 파일 수가 증가하고 관리가 어려워짐 |
| 동일 키 추적 어려움 | 어느 환경에 어떤 값이 들어있는지 한눈에 파악 불가 |
| 히스토리 관리 불가 | 언제 누가 어떤 값을 바꿨는지 추적하기 어려움 |

### DB화 목적

- **재배포 없이** 설정값 변경 가능
- **모든 환경의 설정값을 한 곳**에서 조회/관리
- **변경 이력 추적** 가능 (DB 감사 로그 활용)
- Spring `@Value`와 유사한 편의성 유지

---

## 2. 기존 Java 코드 분석 (구버전 DbPropertyLoader)

### 구버전 코드의 문제점

아래는 개선 전 `DbPropertyLoader.java` 의 주요 문제점입니다.

| # | 문제 | 상세 |
|---|---|---|
| 1 | **스키마 불일치** | `upper(cd) in (?)`, `eng_cd_nm` 읽기 → 신규 JSON 스키마(`comm_type_cd`, `refrc1`)와 맞지 않음 |
| 2 | **JSON 미파싱** | `refrc1` 는 `{"dev":"value"}` JSON인데 구버전은 컬럼 값을 그대로 읽음 |
| 3 | **동적 SQL 취약** | `decode(cd` + 문자열 이어붙이기 → 가독성 최악, 특수문자 프로파일명에서 오동작 가능 |
| 4 | **default 자동 포함 없음** | Spring 기본 동작(default 항상 로딩)을 Java에서 수동 구현해야 함 |
| 5 | **타입 변환 불완전** | Boolean 만 변환, 숫자(Long/Double) 미변환 → `@Value` 주입 시 타입 오류 가능 |
| 6 | **잘못된 import** | `import org.apache.kafka.common.metrics.Sensor` — 완전히 무관한 클래스 |
| 7 | **actv_yn 미검사** | 비활성 레코드(`actv_yn=0`)도 로딩될 수 있음 |
| 8 | **for 루프 안 Connection** | SQL 빌딩 루프 안에 `try(Connection ...)` 블록이 있어 프로파일 수만큼 커넥션 생성 위험 |

### 구버전 코드의 장점

| # | 장점 |
|---|---|
| 1 | 외부 의존성 없음 — 순수 JDBC |
| 2 | 저장 프로시저 없이 동작 가능 |
| 3 | 코드가 짧아 초기 이해가 쉬움 |

---

## 3. 테이블 설계

### 기존 테이블 활용: `maru_pl_comm_cd`

신규 테이블을 만들지 않고 기존 공통 코드 테이블을 활용합니다.

```sql
CREATE TABLE "maruadm"."maru_pl_comm_cd" (
  "cd_id"        VARCHAR2(35 CHAR)   NOT NULL,  -- 고유 ID (랜덤 16자리)
  "cd"           VARCHAR2(32 CHAR)   NOT NULL,  -- ← 카테고리: DEFAULT / BATCH / COMMON
  "comm_type_cd" VARCHAR2(100 CHAR),             -- 'maru_properties' 고정
  "kor_cd_nm"    VARCHAR2(500 CHAR),             -- ← Property KEY
  "refrc1"       VARCHAR2(4000 CHAR),            -- ← Property VALUE (JSON)
  "del_yn"       VARCHAR2(1 CHAR),               -- 'N' = 유효
  "actv_yn"      NUMBER,                         -- 1 = 활성
  ...
)
```

### cd 컬럼 카테고리 규칙

| cd 값 | 대상 프로파일 | 설명 |
|---|---|---|
| `DEFAULT` | `default` | 공통 기본값. 모든 환경에서 항상 로딩 |
| `BATCH` | `batch`, `batch1`, `batch2` 등 | 배치 전용 설정 |
| `COMMON` | `dev`, `live1`, `rc`, `staging` 및 여러 프로파일 공유 키 | 일반 환경 설정 |

> **단독 키**(하나의 프로파일에만 존재): 해당 프로파일명으로 카테고리 결정  
> **공통 키**(여러 프로파일에 걸침): 항상 `COMMON`

### 핵심 설계: `refrc1` 컬럼에 JSON 저장

```
kor_cd_nm : maru.batch.noti.email.cron
refrc1    : {"batch":"0 0 5 * * ?","dev":"0 0 5 * * ?","live1":"0 0 6 * * ?"}
```

---

## 4. 설계 대안 비교

### 안 A: 환경별로 ROW를 따로 INSERT

```sql
INSERT ... ('maru.batch.noti.email.cron', 'dev',   '0 0 5 * * ?')
INSERT ... ('maru.batch.noti.email.cron', 'batch', '0 0 5 * * ?')
INSERT ... ('maru.batch.noti.email.cron', 'live1', '0 0 6 * * ?')
```

❌ Key 100개 × 환경 5개 = ROW 500개. 조회 복잡도 증가.

### 안 B: `refrc1~refrc5` 컬럼에 분산 저장

```sql
-- refrc1=dev값, refrc2=batch값, refrc3=live1값 ...
INSERT ... ('key', 'dev-val', 'batch-val', 'live1-val', ...)
```

❌ 환경이 5개를 초과하면 `ALTER TABLE ADD COLUMN` 필요.

### 안 C: `refrc1` 하나에 JSON 저장 ✅ 채택

```sql
INSERT ... ('key', '{"batch":"0 0 5 * * ?","dev":"0 0 5 * * ?","live1":"0 0 6 * * ?"}')
```

✅ Key당 **1 ROW** — 조회 단순  
✅ 환경이 늘어도 **스키마 변경 불필요**  
✅ `JSON_VALUE` 로 특정 환경 값만 정확하게 추출  
✅ 전체 환경 값을 한눈에 파악

---

## 5. 신규 설계: Oracle 저장 프로시저 방식

### 왜 저장 프로시저인가?

| 항목 | SELECT 직접 실행 | **SP_GET_PROPERTIES (채택)** |
|---|---|---|
| 프로파일 우선순위 로직 | Java 에서 동적 SQL 빌딩 | **DB 프로시저 내부에서 처리** |
| default 자동 포함 | Java 에서 수동 추가 | **프로시저가 항상 prepend** |
| JSON 파싱 | Java 에서 별도 처리 | **`JSON_VALUE`로 DB 내 처리** |
| Java 코드 복잡도 | 높음 (SQL 문자열 조립) | **낮음 (CallableStatement만)** |
| 로직 변경 시 | 재빌드/재배포 필요 | **프로시저만 교체** |

### 단점

| 항목 | 설명 |
|---|---|
| Oracle 12c+ 필요 | `JSON_VALUE` 함수 지원 버전 |
| 프로시저 관리 | DB 배포 파이프라인에 SQL 포함 필요 |
| 디버깅 | 로직이 DB 내부에 있어 Java 디버거로 추적 불가 |

---

## 6. SP_GET_PROPERTIES 상세

### 파일 위치
`docs/Property/sql/SP_GET_PROPERTIES.sql`

### 시그니처

```sql
CREATE OR REPLACE PROCEDURE maruadm.SP_GET_PROPERTIES(
    p_profile_list  IN  VARCHAR2,   -- ex) 'dev,batch'
    p_cursor        OUT SYS_REFCURSOR
)
```

### 동작 흐름

```
1. p_profile_list = 'dev,batch'
         ↓
2. v_full_list = 'default,dev,batch'   (default 자동 prepend)
         ↓
3. REGEXP_SUBSTR 로 콤마 분리 → 행 전개
   profile_nm: 'default', 'dev', 'batch'
         ↓
4. 각 행에 대해 JSON_VALUE(refrc1, '$.profile_nm') 추출
         ↓
5. 우선순위 계산: INSTR(',default,dev,batch,', ',profile_nm,')
   ',default,' → 1   (최저)
   ',dev,'     → 9
   ',batch,'   → 14  (최고)
         ↓
6. ROW_NUMBER() OVER (PARTITION BY kor_cd_nm ORDER BY priority DESC) = 1
   → key당 최고 우선순위 값 1개만 반환
         ↓
7. SYS_REFCURSOR 로 (property_key, property_value) 반환
```

### 우선순위 계산 원리 (콤마 감싸기)

```
-- 일반 INSTR 의 문제: 'batch' 검색 시 'batch1' 안에서도 일치
INSTR('default,batch,batch1', 'batch') = 9   ← batch1 에도 매칭

-- 콤마 감싸기로 해결
INSTR(',default,batch,batch1,', ',batch,')  = 9   ✅ batch 정확히 9
INSTR(',default,batch,batch1,', ',batch1,') = 15  ✅ batch1 정확히 15
```

### 활성 프로파일별 동작 예시

| 기동 옵션 | v_full_list | batch 키 포함 | default 키 포함 |
|---|---|---|---|
| `(없음)` | `default` | ❌ | ✅ |
| `-Dspring.profiles.active=dev` | `default,dev` | ❌ | ✅ |
| `-Dspring.profiles.active=dev,batch` | `default,dev,batch` | ✅ (batch 값 우선) | ✅ |
| `-Dspring.profiles.active=batch1` | `default,batch1` | ✅ | ✅ |

---

## 7. 데이터 구조 상세

### INSERT 구조 (properties_analyzer_v4.py 생성)

#### 단독 키 — default 전용 (cd = 'DEFAULT')

```sql
INSERT INTO "maruadm"."maru_pl_comm_cd"
  ("cd_id","cd","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn")
VALUES
  ('xxxx','DEFAULT','maru_properties',1,1,
   'maru.common.timeout',
   '{"default": "30"}',
   'N',1);
```

#### 단독 키 — batch 전용 (cd = 'BATCH')

```sql
INSERT INTO "maruadm"."maru_pl_comm_cd"
  ("cd_id","cd","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn")
VALUES
  ('xxxx','BATCH','maru_properties',1,1,
   'maru.batch.sa.consumer.cron',
   '{"batch": "0 0 5 * * ?"}',
   'N',1);
```

#### 공통 키 — 여러 프로파일 (cd = 'COMMON')

```sql
INSERT INTO "maruadm"."maru_pl_comm_cd"
  ("cd_id","cd","comm_type_cd","hirc_level","ord","kor_cd_nm","refrc1","del_yn","actv_yn")
VALUES
  ('xxxx','COMMON','maru_properties',1,1,
   'maru.batch.noti.email.cron',
   '{"batch":"0 0 5 * * ?","dev":"0 0 5 * * ?","live1":"0 0 6 * * ?","rc":"0 0 7 * * ?","staging":"0 0 8 * * ?"}',
   'N',1);
```

---

## 8. Java 구현

### Boot.java (변경 없음)

```java
@EnableFeignClients
@EnableCircuitBreaker
@SpringBootApplication(scanBasePackages = {"maru"})
public class Boot {
    public static void main(final String[] arguments) {
        new SpringApplicationBuilder(Boot.class)
            .listeners(new DbPropertyApplicationListener())
            .web(true)
            .run(arguments);
    }
}
```

### DbPropertyApplicationListener.java (신규)

```java
public class DbPropertyApplicationListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // 소문자 변환 — 프로시저 내에서 LOWER() 처리하지만 Java 에서도 보장
        List<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        String url      = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        String driver   = environment.getProperty("spring.datasource.driver-class-name");

        if (url == null || username == null || password == null || driver == null) {
            return; // datasource 미설정 → DB 로딩 스킵
        }

        Map<String, Object> dbProperties =
                DbPropertyLoader.loadFromJdbc(url, username, password, driver, activeProfiles);

        if (!dbProperties.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("dbProperties", dbProperties));
        }
    }
}
```

### DbPropertyLoader.java (신규 — 프로시저 기반)

```java
public class DbPropertyLoader {

    private DbPropertyLoader() {}

    public static Map<String, Object> loadFromJdbc(
            String url, String username, String password, String driver,
            List<String> activeProfiles) {

        Map<String, Object> result = new HashMap<>();

        // activeProfiles 가 비어있어도 프로시저를 호출해야 한다.
        // 프로시저는 빈 문자열을 받으면 'default' 프로파일만 로딩하므로
        // application.properties(default 프로파일) 값이 DB에 있으면 정상 로딩된다.
        String profileList = (activeProfiles == null || activeProfiles.isEmpty())
                ? ""
                : activeProfiles.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.joining(","));

        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC 드라이버 로딩 실패: " + driver, e);
        }

        try (Connection conn = DriverManager.getConnection(url, username, password);
             CallableStatement cs = conn.prepareCall("{call maruadm.SP_GET_PROPERTIES(?, ?)}")) {

            cs.setString(1, profileList);
            cs.registerOutParameter(2, OracleTypes.CURSOR);
            cs.execute();

            try (ResultSet rs = (ResultSet) cs.getObject(2)) {
                while (rs.next()) {
                    String key   = rs.getString("property_key");
                    String value = rs.getString("property_value");
                    if (key != null && !key.isBlank()) {
                        result.put(key, convertValue(value));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "DB Property 로딩 실패 [profiles=" + profileList + "]", e);
        }
        return result;
    }

    // String → Boolean → Long → Double → String 순서로 타입 변환
    private static Object convertValue(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed))  return Boolean.TRUE;
        if ("false".equalsIgnoreCase(trimmed)) return Boolean.FALSE;
        try { return Long.parseLong(trimmed); }   catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(trimmed); } catch (NumberFormatException ignored) {}
        return value;
    }
}
```

### 구버전 vs 신버전 비교

| 항목 | 구버전 | 신버전 |
|---|---|---|
| 스키마 | OLD (`cd`, `eng_cd_nm`) | NEW (`comm_type_cd`, `refrc1` JSON) |
| JSON 파싱 | ❌ 없음 | ✅ DB 내 JSON_VALUE |
| default 자동 포함 | ❌ 없음 | ✅ 프로시저가 prepend |
| 프로파일 우선순위 | `decode()` 동적 SQL | ✅ INSTR 콤마 감싸기 |
| 타입 변환 | Boolean 만 | ✅ Boolean / Long / Double / String |
| 커넥션 관리 | 루프 내 생성 위험 | ✅ try-with-resources 단일 커넥션 |
| actv_yn 검사 | ❌ 없음 | ✅ `actv_yn = 1` 체크 |
| 잘못된 import | kafka.Sensor | ✅ 제거됨 |

---

## 9. Properties 파일과 DB의 관계 (우선순위)

### 두 소스를 모두 읽는다

Spring Boot는 시작 시 아래 두 가지 소스를 **모두** 읽습니다.

| 소스 | 담당 | 시점 |
|---|---|---|
| `application*.properties` 파일 | Spring Boot 자동 처리 | `ApplicationEnvironmentPreparedEvent` 전 |
| DB (`maru_pl_comm_cd`) | `DbPropertyApplicationListener` | `ApplicationEnvironmentPreparedEvent` 시점 |

파일 기반 PropertySource 는 Spring Boot 가 자동으로 등록하며, 삭제되거나 무시되지 않습니다.  
DB PropertySource 는 우리 리스너가 추가로 등록합니다.

---

### Spring PropertySource 우선순위 체인

`environment.getPropertySources().addFirst(...)` 를 사용하기 때문에 DB 값이 **모든 소스 중 가장 앞(최우선)** 에 삽입됩니다.

```
┌─────────────────────────────────────────────────────────────┐
│  Spring PropertySource 우선순위 (위 → 아래 = 높음 → 낮음)      │
├─────────────────────────────────────────────────────────────┤
│  0. dbProperties          ← addFirst() — DB 값 (최우선)      │
│  1. commandLineArgs       ← --key=value JVM 인수             │
│  2. systemProperties      ← -Dkey=value JVM 시스템 속성       │
│  3. systemEnvironment     ← OS 환경 변수                      │
│  4. application-{profile}.properties  ← 프로파일별 파일        │
│  5. application.properties            ← 공통 파일 (최후순위)   │
└─────────────────────────────────────────────────────────────┘
```

`${key}` 를 해석할 때 Spring 은 위에서부터 순서대로 탐색하고, **처음 발견한 값을 사용**합니다.

---

### 케이스별 동작

#### Case 1: 같은 키가 파일과 DB 양쪽에 존재

```
application.properties : maru.common.timeout = 30
DB (refrc1 JSON)       : maru.common.timeout = 60   ← default 프로파일
```

**결과: DB 값 `60` 이 적용됨**

DB PropertySource 가 position 0 이므로 파일 값을 덮어씁니다.  
파일의 `30` 은 fallback 으로만 존재하며 실제로는 사용되지 않습니다.

---

#### Case 2: 키가 파일에만 존재 (DB에 없음)

```
application-dev.properties : clients.search = http://dev-server:8080
DB                         : (해당 키 없음)
```

**결과: 파일 값 `http://dev-server:8080` 이 적용됨**

DB 에서 찾지 못하면 Spring 이 다음 순위 소스(파일)에서 값을 찾습니다.  
→ 파일이 **자동 fallback** 역할을 합니다.

---

#### Case 3: 키가 DB에만 존재 (파일에 없음)

```
DB : maru.batch.sa.consumer.cron = 0 0 5 * * ?
파일 : (해당 키 없음)
```

**결과: DB 값 `0 0 5 * * ?` 이 적용됨**

---

#### Case 4: 활성 프로파일 없음 (순수 default)

```
-Dspring.profiles.active 미설정
→ activeProfiles = []
→ profileList = ""
→ 프로시저 v_full_list = 'default'
→ refrc1 JSON 에서 $.default 값만 로딩
```

**결과: `{"default": "30"}` 형태로 저장된 DB 값이 로딩됨**

---

#### Case 5: JVM 인수로 긴급 재정의 시도

```bash
java -jar app.jar --maru.common.timeout=999
```

**결과: DB 값이 우선 (긴급 재정의 불가)**

`addFirst()` 로 DB 가 commandLineArgs 보다 높은 위치에 있으므로, DB 에 해당 키가 있으면 JVM 인수도 무시됩니다.

> **운영 주의**: 장애 대응 등 긴급하게 JVM 인수로 값을 재정의해야 한다면, DB 의 해당 키를 임시로 `actv_yn = 0` (비활성) 또는 `del_yn = 'Y'` 처리하거나, 아래 `addAfter` 방식으로 변경해야 합니다.

---

### (선택) JVM 인수를 DB보다 우선하는 방식

긴급 운영 대응을 위해 `--key=value` JVM 인수가 DB 보다 높아야 한다면, `addFirst` 대신 commandLineArgs 다음 위치에 삽입합니다.

```java
MutablePropertySources sources = environment.getPropertySources();
if (sources.contains("commandLineArgs")) {
    // commandLineArgs 다음 위치 → DB 가 파일보다 우선이지만 JVM 인수보다는 낮음
    sources.addAfter("commandLineArgs",
            new MapPropertySource("dbProperties", dbProperties));
} else {
    sources.addFirst(new MapPropertySource("dbProperties", dbProperties));
}
```

이 방식의 우선순위:

```
┌─────────────────────────────────────────────────────────────┐
│  1. commandLineArgs       ← --key=value (최우선, 긴급 재정의)  │
│  2. dbProperties          ← DB 값                            │
│  3. systemProperties      ← -Dkey=value                      │
│  4. systemEnvironment     ← OS 환경 변수                      │
│  5. application-{profile}.properties                         │
│  6. application.properties            ← 파일 (최후순위)        │
└─────────────────────────────────────────────────────────────┘
```

---

### 이관 전략 권장

완전히 DB로 이관하기 전, **단계적 전환** 을 권장합니다.

```
Phase 1: 파일 유지 + DB 병행
  → DB 에 값 없으면 파일 fallback
  → DB 가 먼저 적용되어 점진적 덮어쓰기 확인 가능

Phase 2: 전체 키 DB 이관 완료 확인 후
  → 파일에서 DB로 이관된 키 제거
  → @FeignClient 등 파일 필수 키만 파일에 유지
```

---

## 10. 데이터 입력 도구

### Python 스크립트: `properties_analyzer_v4.py`

```bash
# 사용법
python3 properties_analyzer_v4.py /path/to/properties/folder

# 현재 폴더 실행
python3 properties_analyzer_v4.py .
```

**생성 파일**:

| 파일 | 내용 |
|---|---|
| `properties_analysis.csv` | 전체 키 현황 (cd 카테고리, 환경별 값 비교) |
| `properties_insert.sql` | DB INSERT SQL (cd 컬럼 포함, 바로 실행 가능) |
| `properties_select.sql` | 조회 쿼리 모음 (단일/다중 프로파일) |

**cd 컬럼 자동 분류 로직**:

```python
def get_cd_category(profile: str) -> str:
    if profile == 'default':        return 'DEFAULT'
    if re.match(r'^batch', profile): return 'BATCH'
    return 'COMMON'
```

---

## 10. 전체 흐름 요약

```
[기존 .properties 파일]
        ↓
[properties_analyzer_v4.py 실행]
  → cd 컬럼 자동 분류 (DEFAULT / BATCH / COMMON)
  → refrc1 에 JSON 으로 프로파일 값 저장
        ↓
[properties_insert.sql Oracle 실행]
        ↓
[SP_GET_PROPERTIES.sql Oracle 실행 — 프로시저 등록]
        ↓
[Spring Boot 기동]
  -Dspring.profiles.active=dev,batch
        ↓
[DbPropertyApplicationListener.onApplicationEvent]
  → activeProfiles = ["dev", "batch"]
        ↓
[DbPropertyLoader.loadFromJdbc]
  → CallableStatement: SP_GET_PROPERTIES('dev,batch', cursor)
        ↓
[SP_GET_PROPERTIES 내부]
  → v_full_list = 'default,dev,batch'
  → JSON_VALUE(refrc1, '$.profile') 추출
  → INSTR 우선순위: batch > dev > default
  → SYS_REFCURSOR 반환
        ↓
[DbPropertyLoader]
  → Map<String, Object> 빌드
  → convertValue: Boolean / Long / Double / String
        ↓
[environment.getPropertySources().addFirst("dbProperties")]
  → DB 값이 파일 값보다 최우선 적용
        ↓
[@Value, @ConfigurationProperties 정상 주입]
```

---

## 11. 주의 사항

### `@FeignClient` 등 Spring 내부 어노테이션

```java
// ❌ DB 값 주입 불가 — properties 파일에 유지 필요
@FeignClient(value = "cockpit", url = "${clients.search}")
```

Spring이 직접 처리하는 어노테이션은 `Environment` 초기화 이전에 동작하므로 DB 값 주입 불가. 해당 값은 `.properties` 파일에 유지.

### `BeanPostProcessor` 사이드 이펙트

```java
// @DbValue BeanPostProcessor 가 사용하는 경우 @Lazy 필수
public ValueBeanPostProcessor(@Lazy PropertiesMap propertiesMap) {
    this.propertiesMap = propertiesMap;
}
```

### Oracle 버전 요건

- `JSON_VALUE` 함수: **Oracle 12c Release 1 (12.1) 이상** 필요
- 미만 버전은 `REGEXP_SUBSTR` 기반 JSON 파서로 대체 필요

### cron 값의 공백 처리

cron 표현식(`0 0 5 * * ?`)은 중간에 공백 포함.  
Python 파서는 `=` 기준 **첫 번째만 분리**하므로 정상 처리됨.  
JSON 저장 후 `JSON_VALUE` 로 추출할 때도 공백 보존.

### 프로파일명 대소문자

- Spring 프로파일은 대소문자 구분 (`Dev` ≠ `dev`)
- `DbPropertyLoader` 에서 소문자로 정규화 후 프로시저 전달
- `refrc1` JSON 키도 소문자로 통일 (`{"dev": "..."}`, `{"batch": "..."}`)

---

## 12. 파일 구조

```
docs/Property/
├── properties_analyzer_v4.py        ← properties 파일 → INSERT SQL 변환기
├── properties-db-design.md          ← 이 문서
├── Properties/                      ← 원본 .properties 파일
│   ├── application.properties
│   ├── application-dev.properties
│   ├── application-batch.properties
│   └── ...
├── Output/                          ← 생성 산출물
│   ├── properties_insert.sql        ← DB INSERT (cd 컬럼 포함)
│   └── properties_select.sql        ← 조회 쿼리 참고용
├── sql/
│   └── SP_GET_PROPERTIES.sql        ← Oracle 저장 프로시저
└── java/
    ├── DbPropertyLoader.java         ← 신규 구현 (프로시저 호출)
    └── DbPropertyApplicationListener.java  ← 신규 구현
```

---

## 13. 담당자 및 문의

| 항목 | 내용 |
|---|---|
| 설계 | MARU 플랫폼 개발팀 |
| 관련 테이블 | `maruadm.maru_pl_comm_cd` |
| 관련 프로시저 | `maruadm.SP_GET_PROPERTIES` |
| 관련 클래스 | `DbPropertyLoader`, `DbPropertyApplicationListener` |
| Oracle 최소 버전 | 12c Release 1 (JSON_VALUE 지원) |

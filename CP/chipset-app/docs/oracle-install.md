# Oracle 21c XE 설치 가이드 (Windows)

> 작성일: 2026-04-22  
> 대상 OS: Windows 10 / 11 (64-bit)  
> 설치 파일: `OracleXE213_Win64.zip` (1.8 GB)

---

## 0. 사전 요구사항

| 항목 | 최소 사양 |
|------|-----------|
| OS | Windows 10 / 11 64-bit |
| RAM | 2 GB 이상 (권장 4 GB) |
| 디스크 | 10 GB 이상 여유 공간 |
| 권한 | 로컬 관리자(Administrator) 계정 |

> **주의:** 기존에 Oracle이 설치되어 있다면 포트 충돌 발생 가능. 기존 버전 완전 제거 후 설치 권장.

---

## 1. 설치 파일 준비

### 다운로드
```
https://www.oracle.com/database/technologies/xe-downloads.html
→ Oracle Database 21c Express Edition for Windows (64-bit)
→ OracleXE213_Win64.zip (약 1.8 GB)
```

### 압축 해제
```
OracleXE213_Win64.zip 우클릭 → 압축 풀기
→ OracleXE213_Win64\ 폴더 생성됨
```

---

## 2. 설치 진행

### Step 1. setup.exe 실행
```
OracleXE213_Win64\setup.exe 우클릭
→ "관리자 권한으로 실행"
```

### Step 2. 설치 마법사

| 단계 | 설정값 |
|------|--------|
| 언어 선택 | Korean 또는 English |
| 라이선스 동의 | 동의 체크 |
| 설치 경로 | `C:\app\username\product\21c\` (기본값 권장) |
| Oracle Base | `C:\app\username\` |
| **비밀번호 설정** | **SYS / SYSTEM 공통 비밀번호 입력** |

> **비밀번호 규칙**: 대문자 + 소문자 + 숫자 + 특수문자 조합  
> 예시: `Oracle123!`  
> **반드시 메모해두세요. 나중에 변경이 번거롭습니다.**

### Step 3. 설치 완료 확인

설치 완료 후 화면에 표시되는 정보:
```
Oracle Database 21c Express Edition 설치 완료

Database connection info:
  Multitenant container database:  localhost:1521
  Pluggable database:              localhost:1521/XEPDB1
  EM Express URL:                  https://localhost:5500/em
  
SYS/SYSTEM 공통 비밀번호: (설치 시 입력한 값)
```

---

## 3. 설치 후 서비스 확인

### Windows 서비스 확인
```
Win + R → services.msc → 엔터

확인할 서비스:
  ✅ OracleServiceXE          → 상태: 실행 중
  ✅ OracleOraDB21Home1TNSListener → 상태: 실행 중
```

서비스가 중지 상태면:
```cmd
# 관리자 cmd에서 실행
net start OracleServiceXE
net start OracleOraDB21Home1TNSListener
```

### 리스너 상태 확인
```cmd
lsnrctl status
```

정상 출력 예시:
```
LSNRCTL for 64-bit Windows: ...
Connecting to (DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=1521)))
STATUS of the LISTENER
...
Services Summary...
Service "XE" has 1 instance(s).
  Instance "xe", status READY, has 1 handler(s) for this service...
Service "XEPDB1" has 1 instance(s).
  Instance "xe", status READY, has 1 handler(s) for this service...
The command completed successfully
```

---

## 4. chipset 유저 생성

Oracle 설치 후 chipset 전용 유저를 만들어야 합니다.

### 4-1. DBeaver에서 SYS 계정으로 접속

| 항목 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `1521` |
| Database | `XEPDB1` |
| Username | `SYS` |
| Password | 설치 시 입력한 비밀번호 |
| **Role** | **SYSDBA** ← 반드시 선택 |

### 4-2. chipset 유저 생성 SQL

DBeaver SQL 편집기에서 실행:

```sql
-- 1. XEPDB1 PDB로 컨테이너 전환 (SYS로 접속 시 필요)
ALTER SESSION SET CONTAINER = XEPDB1;

-- 2. chipset 유저 생성
CREATE USER chipset IDENTIFIED BY chipset123;

-- 3. 권한 부여
GRANT CONNECT, RESOURCE TO chipset;
GRANT DBA TO chipset;
GRANT UNLIMITED TABLESPACE TO chipset;

-- 4. 생성 확인
SELECT USERNAME, ACCOUNT_STATUS FROM DBA_USERS WHERE USERNAME = 'CHIPSET';
```

예상 결과:
```
USERNAME   ACCOUNT_STATUS
---------  ---------------
CHIPSET    OPEN
```

---

## 5. Chipset-App DB 스키마 생성

### 5-1. chipset 유저로 DBeaver 재접속

| 항목 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `1521` |
| Database | `XEPDB1` |
| Username | `chipset` |
| Password | `chipset123` |
| Role | Normal |

### 5-2. DDL 실행

DBeaver에서 SQL 파일 열기:
```
chipset-app/backend/src/main/resources/schema_oracle.sql
```

> **주의:** `schema.sql`은 H2용입니다. Oracle에서는 아래 DDL을 사용하세요.

Oracle용 DDL (chipset 유저로 실행):

```sql
-- ── 시퀀스 ──
CREATE SEQUENCE SQ_CHIPSET_UPLOAD     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_ROW        START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CELL       START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_UPLOAD_H   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL_H START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_ROW_H      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CELL_H     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_RAWDATA_ROW        START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_RAWDATA_ROW_H      START WITH 1 INCREMENT BY 1 NOCACHE;

-- ── 메인: 업로드 ──
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ  NUMBER        NOT NULL,
    FILE_NM     VARCHAR2(255) NOT NULL,
    FILE_TYPE   VARCHAR2(20)  DEFAULT 'SERVER' NOT NULL,
    UPLOAD_DT   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ROW_COUNT   NUMBER        DEFAULT 0,
    COL_COUNT   NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);

-- ── 메인: 칩 컬럼 ──
CREATE TABLE CHIPSET_CHIP_COL (
    COL_SEQ     NUMBER        NOT NULL,
    UPLOAD_SEQ  NUMBER        NOT NULL,
    VENDOR      VARCHAR2(100) NOT NULL,
    COL_IDX     NUMBER        NOT NULL,
    CHIP_NM     VARCHAR2(200) NOT NULL,
    CHIP_DT     VARCHAR2(50),
    SORT_ORDER  NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_CHIP_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_CHIP_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);

-- ── 메인: 스펙 행 ──
CREATE TABLE CHIPSET_ROW (
    ROW_SEQ     NUMBER        NOT NULL,
    UPLOAD_SEQ  NUMBER        NOT NULL,
    DIMM        VARCHAR2(100),
    PRODUCT     VARCHAR2(200),
    VER         VARCHAR2(50),
    DENSITY     VARCHAR2(50),
    ORG         VARCHAR2(50),
    SPEED       VARCHAR2(100),
    SORT_ORDER  NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_ROW PRIMARY KEY (ROW_SEQ),
    CONSTRAINT FK_ROW_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);

-- ── 메인: 셀 값 ──
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ    NUMBER        NOT NULL,
    ROW_SEQ     NUMBER        NOT NULL,
    COL_SEQ     NUMBER        NOT NULL,
    CELL_VALUE  VARCHAR2(200),
    BG_COLOR    VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL PRIMARY KEY (CELL_SEQ),
    CONSTRAINT FK_CELL_ROW FOREIGN KEY (ROW_SEQ)
        REFERENCES CHIPSET_ROW (ROW_SEQ),
    CONSTRAINT FK_CELL_COL FOREIGN KEY (COL_SEQ)
        REFERENCES CHIPSET_CHIP_COL (COL_SEQ)
);

-- ── 메인: Raw Data ──
CREATE TABLE RAWDATA_ROW (
    RAWDATA_ROW_SEQ NUMBER        NOT NULL,
    UPLOAD_SEQ      NUMBER        NOT NULL,
    COMPANY         VARCHAR2(100),
    SEG             VARCHAR2(100),
    CHIPSET         VARCHAR2(200),
    SOC_CS          VARCHAR2(200),
    PART_NUMBER     VARCHAR2(200),
    DRAM_PROCESS    VARCHAR2(100),
    FLASH_PROCESS   VARCHAR2(100),
    DENSITY         VARCHAR2(50),
    MLC_TLC         VARCHAR2(50),
    PKG             VARCHAR2(200),
    VAL1_DATE       VARCHAR2(20),
    VAL1_ENG        VARCHAR2(50),
    VAL1_STATUS     VARCHAR2(50),
    VAL1_REMARK     VARCHAR2(500),
    VAL2_DATE       VARCHAR2(20),
    VAL2_ENG        VARCHAR2(50),
    VAL2_STATUS     VARCHAR2(50),
    VAL2_REMARK     VARCHAR2(500),
    VAL3_DATE       VARCHAR2(20),
    VAL3_ENG        VARCHAR2(50),
    SORT_ORDER      NUMBER        DEFAULT 0,
    CONSTRAINT PK_RAWDATA_ROW PRIMARY KEY (RAWDATA_ROW_SEQ)
);

-- ── 히스토리: 업로드 ──
CREATE TABLE CHIPSET_UPLOAD_H (
    UPLOAD_H_SEQ NUMBER        NOT NULL,
    UPLOAD_SEQ   NUMBER        NOT NULL,
    FILE_NM      VARCHAR2(255),
    FILE_TYPE    VARCHAR2(20),
    UPLOAD_DT    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    ROW_COUNT    NUMBER        DEFAULT 0,
    COL_COUNT    NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_UPLOAD_H PRIMARY KEY (UPLOAD_H_SEQ)
);

-- ── 히스토리: 칩 컬럼 ──
CREATE TABLE CHIPSET_CHIP_COL_H (
    COL_H_SEQ   NUMBER        NOT NULL,
    COL_SEQ     NUMBER        NOT NULL,
    UPLOAD_SEQ  NUMBER        NOT NULL,
    VENDOR      VARCHAR2(100),
    COL_IDX     NUMBER,
    CHIP_NM     VARCHAR2(200),
    CHIP_DT     VARCHAR2(50),
    SORT_ORDER  NUMBER,
    CONSTRAINT PK_CHIPSET_CHIP_COL_H PRIMARY KEY (COL_H_SEQ)
);

-- ── 히스토리: 스펙 행 ──
CREATE TABLE CHIPSET_ROW_H (
    ROW_H_SEQ   NUMBER        NOT NULL,
    ROW_SEQ     NUMBER        NOT NULL,
    UPLOAD_SEQ  NUMBER        NOT NULL,
    DIMM        VARCHAR2(100),
    PRODUCT     VARCHAR2(200),
    VER         VARCHAR2(50),
    DENSITY     VARCHAR2(50),
    ORG         VARCHAR2(50),
    SPEED       VARCHAR2(100),
    SORT_ORDER  NUMBER,
    CONSTRAINT PK_CHIPSET_ROW_H PRIMARY KEY (ROW_H_SEQ)
);

-- ── 히스토리: 셀 값 ──
CREATE TABLE CHIPSET_CELL_H (
    CELL_H_SEQ  NUMBER        NOT NULL,
    CELL_SEQ    NUMBER        NOT NULL,
    ROW_SEQ     NUMBER        NOT NULL,
    COL_SEQ     NUMBER        NOT NULL,
    UPLOAD_SEQ  NUMBER        NOT NULL,
    CELL_VALUE  VARCHAR2(200),
    BG_COLOR    VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL_H PRIMARY KEY (CELL_H_SEQ)
);

-- ── 히스토리: Raw Data ──
CREATE TABLE RAWDATA_ROW_H (
    RAWDATA_ROW_H_SEQ NUMBER        NOT NULL,
    RAWDATA_ROW_SEQ   NUMBER        NOT NULL,
    UPLOAD_SEQ        NUMBER        NOT NULL,
    COMPANY         VARCHAR2(100),
    SEG             VARCHAR2(100),
    CHIPSET         VARCHAR2(200),
    SOC_CS          VARCHAR2(200),
    PART_NUMBER     VARCHAR2(200),
    DRAM_PROCESS    VARCHAR2(100),
    FLASH_PROCESS   VARCHAR2(100),
    DENSITY         VARCHAR2(50),
    MLC_TLC         VARCHAR2(50),
    PKG             VARCHAR2(200),
    VAL1_DATE       VARCHAR2(20),  VAL1_ENG    VARCHAR2(50),
    VAL1_STATUS     VARCHAR2(50),  VAL1_REMARK VARCHAR2(500),
    VAL2_DATE       VARCHAR2(20),  VAL2_ENG    VARCHAR2(50),
    VAL2_STATUS     VARCHAR2(50),  VAL2_REMARK VARCHAR2(500),
    VAL3_DATE       VARCHAR2(20),  VAL3_ENG    VARCHAR2(50),
    SORT_ORDER      NUMBER,
    CONSTRAINT PK_RAWDATA_ROW_H PRIMARY KEY (RAWDATA_ROW_H_SEQ)
);

-- ── 인덱스 ──
CREATE INDEX IDX_CHIP_COL_UPLOAD   ON CHIPSET_CHIP_COL   (UPLOAD_SEQ);
CREATE INDEX IDX_ROW_UPLOAD        ON CHIPSET_ROW        (UPLOAD_SEQ);
CREATE INDEX IDX_CELL_ROW          ON CHIPSET_CELL       (ROW_SEQ);
CREATE INDEX IDX_CELL_COL          ON CHIPSET_CELL       (COL_SEQ);
CREATE INDEX IDX_RAWDATA_UPLOAD    ON RAWDATA_ROW        (UPLOAD_SEQ);
CREATE INDEX IDX_UPLOAD_H_SEQ      ON CHIPSET_UPLOAD_H  (UPLOAD_SEQ);
CREATE INDEX IDX_RAWDATA_H_UPLOAD  ON RAWDATA_ROW_H     (UPLOAD_SEQ);
```

### 5-3. 생성 확인

```sql
-- 테이블 목록 확인
SELECT TABLE_NAME FROM USER_TABLES ORDER BY TABLE_NAME;

-- 시퀀스 목록 확인
SELECT SEQUENCE_NAME FROM USER_SEQUENCES ORDER BY SEQUENCE_NAME;
```

정상 생성 시 테이블 10개, 시퀀스 10개가 표시됩니다.

---

## 6. application.yml Oracle 전환

`backend/src/main/resources/application.yml` 수정:

```yaml
spring:
  # ── H2 설정 주석 처리 ──
  # datasource:
  #   driver-class-name: org.h2.Driver
  #   url: jdbc:h2:mem:chipsetdb;MODE=Oracle;...
  # h2:
  #   console:
  #     enabled: true
  # sql:
  #   init:
  #     mode: always

  # ── Oracle 설정 활성화 ──
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@localhost:1521/XEPDB1
    username: chipset
    password: chipset123
  sql:
    init:
      mode: never   # Oracle은 DBeaver에서 DDL 직접 실행했으므로 never
```

---

## 7. 백엔드 재시작 후 확인

```bash
cd D:\Gitkraken\InformationSecurity\CP\chipset-app\backend
.\gradlew.bat bootRun
```

정상 시작 로그:
```
HikariPool-1 - Starting...
HikariPool-1 - Added connection oracle.jdbc.driver.T4CConnection
Started ChipsetApplication in X.XXX seconds
```

---

## 8. 자주 발생하는 오류

| 오류 코드 | 원인 | 해결 방법 |
|-----------|------|-----------|
| `ORA-12541` | TNS 리스너 없음 (서비스 미실행) | `net start OracleOraDB21Home1TNSListener` |
| `ORA-12505` | SID 잘못됨 | Database를 `XE` 대신 `XEPDB1`로 변경 |
| `ORA-01017` | 유저명/비밀번호 오류 | 대소문자 확인, SYS 계정은 Role=SYSDBA 필수 |
| `ORA-65096` | CDB에서 유저 생성 시도 | `ALTER SESSION SET CONTAINER=XEPDB1` 후 재시도 |
| `ORA-28040` | 클라이언트-서버 버전 불일치 | ojdbc 드라이버 버전 확인 (ojdbc11 권장) |
| `Listener refused connection` | 포트 1521 방화벽 차단 | Windows 방화벽에서 1521 포트 인바운드 허용 |

---

## 9. 포트 방화벽 허용 (필요 시)

```
Windows Defender 방화벽 고급 설정
→ 인바운드 규칙 → 새 규칙
→ 포트 → TCP → 특정 로컬 포트: 1521
→ 연결 허용 → 이름: Oracle 1521
```

---

## 10. Oracle 서비스 자동 시작 설정

기본값은 수동 시작. 자동으로 바꾸려면:

```
services.msc → OracleServiceXE → 속성
→ 시작 유형: 자동
(OracleOraDB21Home1TNSListener 동일하게 설정)
```

> PC 부팅 시 자동으로 Oracle이 시작됩니다. 불필요할 경우 수동 유지 권장 (부팅 속도 개선).

---

## 11. 앱 사용 순서 (Vue 프론트엔드)

### 사전 조건

| 항목 | 확인 |
|------|------|
| Oracle 서비스 실행 중 | `services.msc` → OracleServiceXE / TNSListener 상태 확인 |
| 백엔드 서버 실행 중 | `.\gradlew.bat bootRun` → `Started ChipsetApplication` 로그 확인 |
| 프론트엔드 실행 중 | `npm run dev` → `http://localhost:5173` |

### 사용 순서

```
1. 탭 선택     화면 상단 탭에서 파일 타입 선택
               Server / Client / Mobile / Raw Data

2. XLSX 선택   "↑ XLSX 선택" 버튼 클릭 → 파일 선택
               파일명이 버튼 옆에 표시됨

3. DB 저장     "→ DB 저장" 버튼 클릭
               (파일 선택 후 자동으로 버튼 표시됨)
               → 백엔드로 업로드 → Oracle DB에 INSERT

4. 결과 확인   그리드에 데이터 자동 출력
```

### 버튼 설명

| 버튼 | 동작 |
|------|------|
| `↑ XLSX 선택` | 업로드할 Excel 파일 선택 |
| `→ DB 저장` | 선택한 파일을 DB에 저장 (파일 선택 후 표시) |
| `DB 불러오기` | 파일 없이 기존 DB 데이터 조회 |
| `히스토리` 드롭다운 | 이전 업로드 목록 선택하여 과거 데이터 조회 |

### 주의사항

- 탭과 다른 타입의 파일 선택 시 `⚠ XXX 형식으로 감지됨` 경고 표시 → 그래도 저장 가능
- `서버 오류: Network Error` → 백엔드 서버(8080)가 실행 중인지 확인
- SYS 계정으로 DBeaver 접속 시 Role을 반드시 **SYSDBA**로 설정

# Chipset DB 설계 문서

> 작성일: 2026-04-23 (개정)  
> 대상 시스템: chipset-app (Vue 3 + Spring Boot + MyBatis + Oracle/H2)  
> 목적: 4가지 Excel 파일을 DB 테이블에 저장하는 구조와 테이블 관계 설명

---

## 변경 이력

| 버전 | 일자 | 주요 변경 |
|------|------|----------|
| v1.0 | 2026-04-22 | 최초 작성 |
| v1.1 | 2026-04-23 | ① CHIPSET_CHIP_COL → CHIPSET_CELL_COL 개명, ② CHIPSET_CELL에 UPLOAD_SEQ 추가, ③ CHIPSET_ROW 동적 컬럼(col1..col10) + CHIPSET_SPEC_COL 신규 추가 |

---

## 1. 개요

이 시스템은 4종류의 Chipset Validation Excel 파일을 업로드받아 DB에 저장하고,
웹 화면에서 조회/히스토리 비교를 제공합니다.

### 1-1. 파일 타입 분류

| 파일명 | 타입 코드 | 구조 | 설명 |
|--------|-----------|------|------|
| `Server.xlsx` | `SERVER` | Matrix형 | 서버 칩셋 검증 매트릭스 |
| `Client.xlsx` | `CLIENT` | Matrix형 | 클라이언트 칩셋 검증 매트릭스 |
| `Mobile.xlsx` | `MOBILE` | Matrix형 | 모바일 칩셋 검증 매트릭스 |
| `Raw_Data.xlsx` | `RAW_DATA` | Tracking형 | 검증 이력 추적 시트 |

**핵심 구분:**
- **Matrix형** (SERVER / CLIENT / MOBILE): 좌측 고정 스펙 컬럼 + 우측 칩셋 동적 컬럼 구조
- **Tracking형** (RAW_DATA): 행마다 검증 이력을 기록하는 단순 테이블 구조

---

## 2. 테이블 전체 목록

### 2-1. 메인 테이블 (현재 최신 데이터)

| 테이블명 | 역할 | 대응 파일 타입 |
|----------|------|---------------|
| `CHIPSET_UPLOAD` | 업로드 메타정보 (파일명, 타입, 일시) | 전체 |
| `CHIPSET_CELL_COL` | 셀 컬럼 정의 (벤더, 칩명, 출시일) — ~~구 CHIPSET_CHIP_COL~~ | SERVER / CLIENT / MOBILE |
| `CHIPSET_SPEC_COL` | 스펙 컬럼 메타 (좌측 고정 컬럼 헤더명 동적 저장) — **신규** | SERVER / CLIENT / MOBILE |
| `CHIPSET_ROW` | 스펙 행 (col1 ~ col10 동적 컬럼) | SERVER / CLIENT / MOBILE |
| `CHIPSET_CELL` | 셀 값 (검증일자, 배경색, upload_seq 포함) | SERVER / CLIENT / MOBILE |
| `RAWDATA_ROW` | Raw Data 행 (검증 이력 전체) | RAW_DATA |

### 2-2. 히스토리 테이블 (누적 이력)

| 테이블명 | 역할 |
|----------|------|
| `CHIPSET_UPLOAD_H` | 업로드 이력 누적 |
| `CHIPSET_CELL_COL_H` | 셀 컬럼 이력 누적 — ~~구 CHIPSET_CHIP_COL_H~~ |
| `CHIPSET_SPEC_COL_H` | 스펙 컬럼 메타 이력 누적 — **신규** |
| `CHIPSET_ROW_H` | 스펙 행 이력 누적 |
| `CHIPSET_CELL_H` | 셀 값 이력 누적 |
| `RAWDATA_ROW_H` | Raw Data 행 이력 누적 |

> **메인 vs 히스토리**: 메인 테이블은 파일타입별로 최신 1건만 유지(업로드 시 기존 삭제).  
> 히스토리 테이블은 업로드할 때마다 누적 저장하여 과거 버전 조회를 지원합니다.

---

## 3. 설계 결정 사항 (v1.1 변경 이유)

### 3-1. CHIPSET_CHIP_COL → CHIPSET_CELL_COL 개명

| 항목 | 설명 |
|------|------|
| **변경 전** | `CHIPSET_CHIP_COL` |
| **변경 후** | `CHIPSET_CELL_COL` |
| **이유** | 이 테이블은 `CHIPSET_CELL`의 열(column) 정의를 담는 테이블이다. `CHIP_COL`은 "칩 컬럼"처럼 읽혀 기능이 모호하지만, `CELL_COL`은 "셀의 컬럼 정의"임을 명확히 표현한다. |
| **영향** | 시퀀스 `SQ_CHIPSET_CHIP_COL` → `SQ_CHIPSET_CELL_COL`, Java 클래스 `ChipsetChipCol` → `ChipsetCellCol`, MyBatis 매퍼 메서드명 일괄 변경 |

### 3-2. CHIPSET_CELL에 UPLOAD_SEQ 추가

| 항목 | 설명 |
|------|------|
| **변경 전** | `CHIPSET_CELL`에 `UPLOAD_SEQ` 없음 |
| **변경 후** | `CHIPSET_CELL`에 `UPLOAD_SEQ NUMBER NOT NULL` 추가 |
| **이유** | ① 히스토리 누적 시 `CHIPSET_CELL_H`는 `UPLOAD_SEQ`가 있었으나 메인 테이블에는 없어 일관성 부재. ② 타입별 삭제 시 `ROW_SEQ IN (SELECT ...)` 이중 서브쿼리 필요 → `UPLOAD_SEQ IN (SELECT ...)` 단일 서브쿼리로 단순화. ③ 직접 upload 단위 집계/분석 가능 |
| **삭제 쿼리 개선** | `DELETE CHIPSET_CELL WHERE UPLOAD_SEQ IN (SELECT UPLOAD_SEQ FROM CHIPSET_UPLOAD WHERE FILE_TYPE = ?)` |

### 3-3. CHIPSET_ROW 동적 컬럼 + CHIPSET_SPEC_COL 신규 추가

| 항목 | 설명 |
|------|------|
| **변경 전** | `CHIPSET_ROW`에 `DIMM, PRODUCT, VER, DENSITY, ORG, SPEED` (6개 고정, 타입별 의미 하드코딩) |
| **변경 후** | `CHIPSET_ROW`에 `COL1 ~ COL10` (최대 10개 동적), 헤더명은 `CHIPSET_SPEC_COL`에 별도 저장 |
| **이유** | ① 타입별(SERVER/MOBILE)로 컬럼 의미가 달라 프론트에서 하드코딩 필요 (`SPEC_LABELS_SERVER`, `SPEC_LABELS_MOBILE`). ② 향후 컬럼이 추가될 경우 DDL 변경 + 코드 변경 병행 필요. ③ `CHIPSET_SPEC_COL`에 Excel 실제 헤더명을 저장하면 UI가 DB 조회로 동적 렌더링 가능, 코드 수정 불필요 |
| **최대 컬럼 수** | 10개 고정 (현재 Server/Client=6, Mobile=5 사용 중) |

---

## 4. 테이블 관계도 (ER Diagram)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            CHIPSET_UPLOAD                                    │
│  PK: UPLOAD_SEQ  │  FILE_NM  │  FILE_TYPE  │  UPLOAD_DT  │  ROW/COL_COUNT  │
└───┬──────────────────────────────────────────────────────────────────────────┘
    │ 1
    ├──────────────────┬─────────────────────────┬──────────────────────────┐
    │ N                │ N                        │ N                        │ N (RAW_DATA)
    ▼                  ▼                          ▼                          ▼
┌──────────────┐  ┌──────────────────┐  ┌────────────────────────┐  ┌─────────────────┐
│CHIPSET_CELL  │  │ CHIPSET_SPEC_COL │  │   CHIPSET_ROW          │  │  RAWDATA_ROW    │
│     _COL     │  │ PK: SPEC_COL_SEQ │  │ PK: ROW_SEQ            │  │ PK: RAWDATA_    │
│PK: COL_SEQ   │  │ FK: UPLOAD_SEQ   │  │ FK: UPLOAD_SEQ         │  │     ROW_SEQ     │
│FK: UPLOAD_SEQ│  │ COL_IDX (1-10)   │  │ COL1 ~ COL10           │  │ FK: UPLOAD_SEQ  │
│VENDOR        │  │ COL_NM (헤더명)  │  │ SORT_ORDER             │  │ COMPANY, SEG    │
│CHIP_NM       │  └──────────────────┘  └──────────┬─────────────┘  │ CHIPSET, SOC_CS │
│CHIP_DT       │                                    │ 1 → N          │ PART_NUMBER     │
│SORT_ORDER    │◄──────────────────────────────────┐│                │ ...             │
└──────┬───────┘  COL_SEQ 참조                     ││                └─────────────────┘
       │ 1 → N                             ┌───────▼▼────────┐
       └──────────────────────────────────►│  CHIPSET_CELL   │
                                           │ PK: CELL_SEQ    │
                                           │ FK: ROW_SEQ     │
                                           │ FK: COL_SEQ     │
                                           │ FK: UPLOAD_SEQ  │ ← 신규 추가
                                           │ CELL_VALUE      │
                                           │ BG_COLOR        │
                                           └─────────────────┘

※ 히스토리 테이블(*_H)은 위 메인 테이블과 동일한 구조 + H_SEQ PK를 추가로 보유
※ CHIPSET_SPEC_COL은 CHIPSET_ROW.col1..col10 의 헤더명을 index(1-based) 기준으로 저장
```

### 4-1. 관계 요약

| 관계 | 설명 |
|------|------|
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_CELL_COL` | 업로드 1건에 셀 컬럼(칩셋) N개 |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_SPEC_COL` | 업로드 1건에 스펙 컬럼 메타 N개 (최대 10) |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_ROW` | 업로드 1건에 스펙 행 N개 |
| `CHIPSET_ROW` 1 → N `CHIPSET_CELL` | 스펙 행 1개에 셀 N개 (칩셋 수만큼) |
| `CHIPSET_CELL_COL` 1 → N `CHIPSET_CELL` | 셀 컬럼 1개에 셀 N개 (행 수만큼) |
| `CHIPSET_UPLOAD` 1 → N `RAWDATA_ROW` | 업로드 1건에 Raw Data 행 N개 |

---

## 5. 각 테이블 상세 스키마

### 5-1. CHIPSET_UPLOAD (업로드 메타)

```sql
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    FILE_NM     VARCHAR2(255) NOT NULL,   -- 원본 파일명
    FILE_TYPE   VARCHAR2(20)  NOT NULL,   -- 'SERVER' | 'CLIENT' | 'MOBILE' | 'RAW_DATA'
    UPLOAD_DT   TIMESTAMP     NOT NULL,   -- 업로드 일시
    ROW_COUNT   NUMBER        DEFAULT 0,
    COL_COUNT   NUMBER        DEFAULT 0,  -- 셀 컬럼 수 (RAW_DATA는 0)
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);
```

---

### 5-2. CHIPSET_CELL_COL (셀 컬럼 정의) — 구 CHIPSET_CHIP_COL

```sql
CREATE TABLE CHIPSET_CELL_COL (
    COL_SEQ     NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    VENDOR      VARCHAR2(100) NOT NULL,   -- 벤더명 (예: Intel, AMD, Qualcomm)
    COL_IDX     NUMBER        NOT NULL,   -- Excel 원본 컬럼 인덱스 (0-based)
    CHIP_NM     VARCHAR2(200) NOT NULL,   -- 칩셋 이름 (예: SPR-SP(4800))
    CHIP_DT     VARCHAR2(50),             -- 출시일 (예: 01'23)
    SORT_ORDER  NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_CELL_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_CELL_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

> **개명 이유**: `CHIPSET_CHIP_COL`보다 `CHIPSET_CELL_COL`이 "CHIPSET_CELL의 열 정의"임을 명확히 표현

---

### 5-3. CHIPSET_SPEC_COL (스펙 컬럼 메타) — 신규

```sql
CREATE TABLE CHIPSET_SPEC_COL (
    SPEC_COL_SEQ NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ   NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    COL_IDX      NUMBER        NOT NULL,   -- 1-based (1=col1, 2=col2, ... 최대 10=col10)
    COL_NM       VARCHAR2(100) NOT NULL,   -- Excel 원본 컬럼 헤더명
    SORT_ORDER   NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_SPEC_COL PRIMARY KEY (SPEC_COL_SEQ),
    CONSTRAINT FK_SPEC_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

**COL_IDX와 CHIPSET_ROW 컬럼 대응:**

| COL_IDX | CHIPSET_ROW 컬럼 | Server/Client 예시 | Mobile 예시 |
|---------|-----------------|-------------------|-------------|
| 1 | COL1 | DIMM | PKG |
| 2 | COL2 | Product (Ver.) | Density |
| 3 | COL3 | Ver. | Product |
| 4 | COL4 | Density | P/N |
| 5 | COL5 | Org | Code Name |
| 6 | COL6 | Speed | (없음) |
| 7-10 | COL7-COL10 | (예비) | (예비) |

> **UI 연동**: `MatrixResponse.specCols` 로 프론트엔드에 전달. Vue 컴포넌트는 하드코딩 없이 이 값을 읽어 컬럼 헤더 렌더링

---

### 5-4. CHIPSET_ROW (스펙 행) — 동적 컬럼으로 변경

```sql
CREATE TABLE CHIPSET_ROW (
    ROW_SEQ     NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    COL1        VARCHAR2(200),            -- 스펙값 1 (헤더명은 CHIPSET_SPEC_COL 참조)
    COL2        VARCHAR2(200),            -- 스펙값 2
    COL3        VARCHAR2(200),
    COL4        VARCHAR2(200),
    COL5        VARCHAR2(200),
    COL6        VARCHAR2(200),
    COL7        VARCHAR2(200),            -- 예비 (향후 컬럼 추가 시 사용)
    COL8        VARCHAR2(200),
    COL9        VARCHAR2(200),
    COL10       VARCHAR2(200),            -- 최대 10개 고정
    SORT_ORDER  NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_ROW PRIMARY KEY (ROW_SEQ),
    CONSTRAINT FK_ROW_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

> **구 설계 문제점**: `DIMM, PRODUCT, VER, DENSITY, ORG, SPEED` 고정 컬럼은 Mobile 타입과 의미가 달라 프론트엔드 코드에 `SPEC_LABELS_SERVER`, `SPEC_LABELS_MOBILE` 상수를 하드코딩해야 했음.  
> **현 설계**: `COL1..COL10` + `CHIPSET_SPEC_COL`로 완전 동적화. 컬럼이 늘어나도 코드 변경 없이 수용 가능.

---

### 5-5. CHIPSET_CELL (셀 값) — UPLOAD_SEQ 추가

```sql
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ    NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    ROW_SEQ     NUMBER        NOT NULL,   -- FK → CHIPSET_ROW
    COL_SEQ     NUMBER        NOT NULL,   -- FK → CHIPSET_CELL_COL
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD (신규 추가)
    CELL_VALUE  VARCHAR2(200),            -- 셀 텍스트 (검증일자 등)
    BG_COLOR    VARCHAR2(10),             -- 배경색 HEX (예: #00B050)
    CONSTRAINT PK_CHIPSET_CELL PRIMARY KEY (CELL_SEQ),
    CONSTRAINT FK_CELL_ROW FOREIGN KEY (ROW_SEQ) REFERENCES CHIPSET_ROW (ROW_SEQ),
    CONSTRAINT FK_CELL_COL FOREIGN KEY (COL_SEQ) REFERENCES CHIPSET_CELL_COL (COL_SEQ)
);
```

> **UPLOAD_SEQ 추가 이유**: ① 히스토리와 일관성 (`CHIPSET_CELL_H`는 기존에도 `UPLOAD_SEQ` 보유). ② 타입별 DELETE 시 이중 서브쿼리 제거 (`WHERE ROW_SEQ IN (SELECT ... FROM CHIPSET_ROW WHERE UPLOAD_SEQ IN (...))` → `WHERE UPLOAD_SEQ IN (...)`).

---

### 5-6. RAWDATA_ROW (Raw Data 행) — 변경 없음

```sql
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
```

---

## 6. Excel → DB 매핑 상세

### 6-1. Server.xlsx / Client.xlsx 구조와 매핑

**Excel 시트 구조:**

```
행 0 │ DIMM │Prod│ Ver│Dens│ Org│Spd│◄── Intel(병합) ──►│◄── AMD(병합) ──►│
행 1 │ (빈) │    │    │    │    │   │ SPR-SP │ EMR-SP │...│ GENOA │ TURIN │...│
행 2 │ (빈) │    │    │    │    │   │  01'23 │  12'23 │...│ 12'23 │ 08'24 │...│
행 3 │RDIMM │1A P│ WC │16GB│2RX4│5600│  ██  │        │   │  ██   │       │   │
...
```

**매핑 규칙:**

| Excel 위치 | 저장 테이블 | 저장 컬럼 | 비고 |
|------------|------------|-----------|------|
| 행0, 컬6+ 병합셀 텍스트 | `CHIPSET_CELL_COL` | `VENDOR` | "Intel", "AMD" |
| 행1, 컬6+ 각 셀 | `CHIPSET_CELL_COL` | `CHIP_NM` | "SPR-SP(4800)" |
| 행2, 컬6+ 각 셀 | `CHIPSET_CELL_COL` | `CHIP_DT` | "01'23" |
| 행0, 컬0 헤더명 (DIMM) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=1) | **신규** |
| 행0, 컬1 헤더명 (Product) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=2) | **신규** |
| 행0, 컬2 헤더명 (Ver.) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=3) | **신규** |
| 행0, 컬3 헤더명 (Density) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=4) | **신규** |
| 행0, 컬4 헤더명 (Org) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=5) | **신규** |
| 행0, 컬5 헤더명 (Speed) | `CHIPSET_SPEC_COL` | `COL_NM` (COL_IDX=6) | **신규** |
| 행3+, 컬0 값 | `CHIPSET_ROW` | `COL1` | 구 DIMM |
| 행3+, 컬1 값 | `CHIPSET_ROW` | `COL2` | 구 PRODUCT |
| 행3+, 컬2 값 | `CHIPSET_ROW` | `COL3` | 구 VER |
| 행3+, 컬3 값 | `CHIPSET_ROW` | `COL4` | 구 DENSITY |
| 행3+, 컬4 값 | `CHIPSET_ROW` | `COL5` | 구 ORG |
| 행3+, 컬5 값 | `CHIPSET_ROW` | `COL6` | 구 SPEED |
| 행3+, 컬6+ 셀값 | `CHIPSET_CELL` | `CELL_VALUE` | |
| 행3+, 컬6+ 배경색 | `CHIPSET_CELL` | `BG_COLOR` | |

---

### 6-2. Mobile.xlsx 구조와 매핑

**Excel 시트 구조:**

```
행 0 │ PKG│Dens│Prod│ P/N│CdNm│◄──────── Qualcomm(병합) ─────────────────►│
행 1 │ (빈) × 5  │ SM8650 │ SM8550 │ SM7675 │ ...│
행 2 │ (빈) × 5  │ 2023.Q4│ 2023.Q1│ 2024.Q2│ ...│
행 3 │LP5X│16GB│ A  │KMQ │ VP │  ██   │       │  ██   │   │
```

**CHIPSET_SPEC_COL 저장 (Mobile 예시):**

| COL_IDX | COL_NM (Excel 헤더) | CHIPSET_ROW 컬럼 |
|---------|-------------------|-----------------|
| 1 | PKG | COL1 |
| 2 | Density | COL2 |
| 3 | Product | COL3 |
| 4 | P/N | COL4 |
| 5 | Code Name | COL5 |

---

## 7. 업로드 처리 흐름

### 7-1. Matrix형 (SERVER / CLIENT / MOBILE) 업로드 흐름

```
[클라이언트]                [ChipsetController]           [ChipsetExcelParser]
     │── POST /upload ──────►│── parse() ─────────────────►│
     │                       │                              │── FileType 감지
     │                       │◄── ParseResult ─────────────│
     │                       │   (cellColDefs,              │
     │                       │    specColDefs,   ← 신규     │
     │                       │    rows)                     │
     │                 [ChipsetService]
     │                       │ ① DELETE CHIPSET_CELL    (UPLOAD_SEQ 직접 사용)
     │                       │ ② DELETE CHIPSET_ROW
     │                       │ ③ DELETE CHIPSET_CELL_COL
     │                       │ ④ DELETE CHIPSET_SPEC_COL  ← 신규
     │                       │ ⑤ DELETE CHIPSET_UPLOAD
     │                       │
     │                       │ ⑥ INSERT CHIPSET_UPLOAD → uploadSeq 채번
     │                       │ ⑦ INSERT CHIPSET_SPEC_COL  ← 신규 (헤더명 저장)
     │                       │ ⑧ INSERT CHIPSET_CELL_COL  (구 CHIP_COL)
     │                       │ ⑨ INSERT CHIPSET_ROW (col1..col10)
     │                       │ ⑩ INSERT CHIPSET_CELL (uploadSeq 포함)  ← 변경
     │                       │
     │                       │ ⑪ 히스토리 누적 (_H 테이블)
     │◄── UploadResult ───────│
```

---

## 8. 데이터 조회 구조

### 8-1. Matrix 조회 (GET /api/chipset/matrix?type=SERVER)

```json
{
  "uploadSeq": 5,
  "uploadDt":  "2026-04-22T09:30:00",
  "fileType":  "SERVER",
  "vendors":   ["INTEL", "AMD"],
  "chipCols":  [
    { "colSeq":1, "vendor":"INTEL", "chipNm":"SPR-SP(4800)", "chipDt":"01'23" },
    ...
  ],
  "specCols": [
    { "specColSeq":1, "colIdx":1, "colNm":"DIMM" },
    { "specColSeq":2, "colIdx":2, "colNm":"Product (Ver.)" },
    { "specColSeq":3, "colIdx":3, "colNm":"Ver." },
    { "specColSeq":4, "colIdx":4, "colNm":"Density" },
    { "specColSeq":5, "colIdx":5, "colNm":"Org" },
    { "specColSeq":6, "colIdx":6, "colNm":"Speed" }
  ],
  "rows": [
    {
      "rowSeq":1,
      "col1":"RDIMM", "col2":"1A P-DIE", "col3":"WC",
      "col4":"16GB",  "col5":"2RX4",     "col6":"5600",
      "col7":null, "col8":null, "col9":null, "col10":null,
      "cells": [
        { "colSeq":1, "cellValue":"10 '23", "bgColor":"#00B050" },
        ...
      ]
    }
  ]
}
```

### 8-2. Vue 프론트엔드 렌더링 매핑

```
specCols → 좌측 고정 컬럼 헤더 (동적, 하드코딩 제거)
  └─ colIdx 순서로 정렬 → spec.key = "col" + colIdx
  └─ spec.label = colNm (Excel 원본 헤더명)
  └─ row["col" + colIdx] 로 실제 데이터 접근

chipCols → 칩셋 컬럼 헤더 (SPR-SP, EMR-SP, ...)
rows     → 데이터 행
  └─ cells → col.colSeq와 일치하는 cell을 찾아 값/색상 표시
```

---

## 9. 히스토리 관리 전략

### 9-1. 메인 테이블: 덮어쓰기 방식

```
새 Server.xlsx 업로드 시:
  DELETE CHIPSET_CELL    (UPLOAD_SEQ 직접 참조, 단일 서브쿼리)
  DELETE CHIPSET_ROW
  DELETE CHIPSET_CELL_COL
  DELETE CHIPSET_SPEC_COL  ← 신규
  DELETE CHIPSET_UPLOAD
  ↓
  새 데이터 INSERT
```

### 9-2. 히스토리 테이블: 누적 방식

```
메인 테이블 삭제/INSERT 후 추가로:
  INSERT CHIPSET_UPLOAD_H
  INSERT CHIPSET_SPEC_COL_H  ← 신규
  INSERT CHIPSET_CELL_COL_H  (구 CHIP_COL_H)
  INSERT CHIPSET_ROW_H
  INSERT CHIPSET_CELL_H (cell.uploadSeq로 바로 저장)  ← 단순화
```

---

## 10. 시퀀스 목록

| 시퀀스명 | 용도 |
|----------|------|
| `SQ_CHIPSET_UPLOAD` | CHIPSET_UPLOAD.UPLOAD_SEQ |
| `SQ_CHIPSET_CELL_COL` | CHIPSET_CELL_COL.COL_SEQ — ~~구 SQ_CHIPSET_CHIP_COL~~ |
| `SQ_CHIPSET_SPEC_COL` | CHIPSET_SPEC_COL.SPEC_COL_SEQ — **신규** |
| `SQ_CHIPSET_ROW` | CHIPSET_ROW.ROW_SEQ |
| `SQ_CHIPSET_CELL` | CHIPSET_CELL.CELL_SEQ |
| `SQ_CHIPSET_UPLOAD_H` | CHIPSET_UPLOAD_H.UPLOAD_H_SEQ |
| `SQ_CHIPSET_CELL_COL_H` | CHIPSET_CELL_COL_H.COL_H_SEQ — ~~구 SQ_CHIPSET_CHIP_COL_H~~ |
| `SQ_CHIPSET_SPEC_COL_H` | CHIPSET_SPEC_COL_H.SPEC_COL_H_SEQ — **신규** |
| `SQ_CHIPSET_ROW_H` | CHIPSET_ROW_H.ROW_H_SEQ |
| `SQ_CHIPSET_CELL_H` | CHIPSET_CELL_H.CELL_H_SEQ |
| `SQ_RAWDATA_ROW` | RAWDATA_ROW.RAWDATA_ROW_SEQ |
| `SQ_RAWDATA_ROW_H` | RAWDATA_ROW_H.RAWDATA_ROW_H_SEQ |

---

## 11. 데이터 예시 (Server.xlsx 1행 업로드 시, v1.1)

Excel 입력:

```
벤더행:  [DIMM│Product│Ver.│Density│Org│Speed] | Intel(병합 3칸)
칩명행:  [빈칸×6]                              | SPR  | EMR  | GNR
날짜행:  [빈칸×6]                              |01'23 |12'23 |01'25
데이터:  RDIMM | 1A P-DIE | WC | 16GB | 2RX4 | 5600 | 10'23(녹색) | | 01'24(파랑)
```

DB INSERT 결과:

```sql
-- CHIPSET_UPLOAD
(UPLOAD_SEQ=1, FILE_NM='Server.xlsx', FILE_TYPE='SERVER', ROW_COUNT=1, COL_COUNT=3)

-- CHIPSET_SPEC_COL (신규)
(SPEC_COL_SEQ=1, UPLOAD_SEQ=1, COL_IDX=1, COL_NM='DIMM',    SORT_ORDER=0)
(SPEC_COL_SEQ=2, UPLOAD_SEQ=1, COL_IDX=2, COL_NM='Product', SORT_ORDER=1)
(SPEC_COL_SEQ=3, UPLOAD_SEQ=1, COL_IDX=3, COL_NM='Ver.',    SORT_ORDER=2)
(SPEC_COL_SEQ=4, UPLOAD_SEQ=1, COL_IDX=4, COL_NM='Density', SORT_ORDER=3)
(SPEC_COL_SEQ=5, UPLOAD_SEQ=1, COL_IDX=5, COL_NM='Org',     SORT_ORDER=4)
(SPEC_COL_SEQ=6, UPLOAD_SEQ=1, COL_IDX=6, COL_NM='Speed',   SORT_ORDER=5)

-- CHIPSET_CELL_COL (구 CHIPSET_CHIP_COL)
(COL_SEQ=1, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=6, CHIP_NM='SPR', CHIP_DT='01''23', SORT_ORDER=0)
(COL_SEQ=2, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=7, CHIP_NM='EMR', CHIP_DT='12''23', SORT_ORDER=1)
(COL_SEQ=3, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=8, CHIP_NM='GNR', CHIP_DT='01''25', SORT_ORDER=2)

-- CHIPSET_ROW (구 dimm/product/ver/density/org/speed → col1..col6)
(ROW_SEQ=1, UPLOAD_SEQ=1,
 COL1='RDIMM', COL2='1A P-DIE', COL3='WC', COL4='16GB', COL5='2RX4', COL6='5600',
 COL7=NULL, COL8=NULL, COL9=NULL, COL10=NULL, SORT_ORDER=0)

-- CHIPSET_CELL (UPLOAD_SEQ 추가)
(CELL_SEQ=1, ROW_SEQ=1, COL_SEQ=1, UPLOAD_SEQ=1, CELL_VALUE='10''23', BG_COLOR='#00B050')
(CELL_SEQ=2, ROW_SEQ=1, COL_SEQ=2, UPLOAD_SEQ=1, CELL_VALUE='',       BG_COLOR=NULL)
(CELL_SEQ=3, ROW_SEQ=1, COL_SEQ=3, UPLOAD_SEQ=1, CELL_VALUE='01''24', BG_COLOR='#4472C4')
```

---

## 12. Oracle 배포용 DDL

Oracle에 배포할 때는 아래 DDL을 DBeaver 또는 SQL*Plus에서 실행하세요.  
H2와 달리 `IF NOT EXISTS` 구문을 지원하지 않으므로 테이블/시퀀스가 없는 상태에서 실행해야 합니다.

> 전체 DDL은 `backend/src/main/resources/schema.sql` 참조  
> (H2 호환 형식이나 Oracle에서도 동일하게 동작)

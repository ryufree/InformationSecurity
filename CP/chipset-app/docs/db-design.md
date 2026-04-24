# Chipset DB 설계 문서

> 작성일: 2026-04-25 (개정)
> 대상 시스템: chipset-app (Vue 3 + Spring Boot + MyBatis + Oracle/H2)
> 목적: 4가지 Excel 파일을 DB 테이블에 저장하는 구조와 테이블 관계 설명

---

## 변경 이력

| 버전 | 일자 | 주요 변경 |
|------|------|----------|
| v1.0 | 2026-04-22 | 최초 작성 |
| v1.1 | 2026-04-23 | CHIPSET_CHIP_COL → CHIPSET_CELL_COL 개명, CHIPSET_CELL에 UPLOAD_SEQ 추가, CHIPSET_ROW 동적 컬럼(COL1~COL10) + CHIPSET_SPEC_COL 신규 추가 |
| v1.2 | 2026-04-25 | CHIPSET_CELL_COL → CHIPSET_CHIP_COL 재개명, CHIPSET_ROW·RAWDATA_ROW 제거, CHIPSET_RAWDATA_COL 신규, 모든 셀을 CHIPSET_CELL로 통합(COL_TYPE 구분자), ROW_IDX 도입 |

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

- **Matrix형** (SERVER / CLIENT / MOBILE): 좌측 고정 스펙 컬럼 + 우측 칩셋 동적 컬럼
- **Tracking형** (RAW_DATA): 행마다 검증 이력을 기록하는 단순 테이블

### 1-2. v1.2 핵심 설계 원칙

모든 셀 데이터(스펙 셀·칩셋 셀·RawData 셀)를 `CHIPSET_CELL` 하나로 통합합니다.

| 역할 | 테이블 |
|------|--------|
| Server/Client/Mobile 왼쪽 컬럼 헤더 | `CHIPSET_SPEC_COL` |
| Server/Client/Mobile 오른쪽 칩셋 컬럼 헤더 | `CHIPSET_CHIP_COL` |
| RawData 컬럼 헤더 | `CHIPSET_RAWDATA_COL` |
| **모든 셀 실제 데이터** | `CHIPSET_CELL` (COL_TYPE 구분자로 참조 테이블 식별) |

**장점:**
- 모든 셀에 `BG_COLOR` 저장 가능 (구 설계에서는 칩셋 셀만 가능)
- 새 컬럼 타입 추가 시 `CHIPSET_CELL` DDL 변경 불필요 (`COL_TYPE` 값만 추가)
- 테이블 수 감소 → 로직 단순화

---

## 2. 테이블 전체 목록

### 2-1. 메인 테이블 (최신 데이터 — 파일타입별 1건 유지)

| 테이블명 | 역할 | 대응 파일 타입 |
|----------|------|---------------|
| `CHIPSET_UPLOAD` | 업로드 메타정보 (파일명, 타입, 일시) | 전체 |
| `CHIPSET_SPEC_COL` | Server/Client/Mobile **왼쪽** 컬럼 헤더 (DIMM, Product…) | SERVER / CLIENT / MOBILE |
| `CHIPSET_CHIP_COL` | Server/Client/Mobile **오른쪽** 칩셋 컬럼 헤더 (벤더, 칩명, 출시일) | SERVER / CLIENT / MOBILE |
| `CHIPSET_RAWDATA_COL` | RawData 컬럼 헤더 (Company, Seg, Chipset…) | RAW_DATA |
| `CHIPSET_CELL` | **모든 셀 데이터** (COL_TYPE으로 셀 종류 구분) | 전체 |

### 2-2. 히스토리 테이블 (누적 이력)

| 테이블명 | 역할 |
|----------|------|
| `CHIPSET_UPLOAD_H` | 업로드 이력 누적 |
| `CHIPSET_SPEC_COL_H` | 스펙 컬럼 헤더 이력 |
| `CHIPSET_CHIP_COL_H` | 칩셋 컬럼 헤더 이력 |
| `CHIPSET_RAWDATA_COL_H` | RawData 컬럼 헤더 이력 |
| `CHIPSET_CELL_H` | 셀 데이터 이력 |

> **제거된 테이블 (v1.2):** `CHIPSET_ROW`, `CHIPSET_ROW_H`, `RAWDATA_ROW`, `RAWDATA_ROW_H`
> 이들의 데이터는 모두 `CHIPSET_CELL`로 통합됨

---

## 3. 설계 결정 사항 (v1.2)

### 3-1. CHIPSET_ROW 제거 → CHIPSET_CELL 통합

| 항목 | v1.1 (변경 전) | v1.2 (변경 후) |
|------|---------------|---------------|
| 스펙 행 저장 | `CHIPSET_ROW` (COL1~COL10) | `CHIPSET_CELL` (COL_TYPE='SPEC', ROW_IDX) |
| 행 식별 | `ROW_SEQ` (전역 시퀀스 PK) | `ROW_IDX` (업로드 내 0-based 행 번호) |
| 스펙 셀 배경색 | 저장 불가 | `BG_COLOR` 저장 가능 |

### 3-2. RAWDATA_ROW 제거 → CHIPSET_RAWDATA_COL + CHIPSET_CELL 통합

| 항목 | v1.1 (변경 전) | v1.2 (변경 후) |
|------|---------------|---------------|
| 컬럼 헤더 | 고정 컬럼명 (COMPANY, SEG… 하드코딩) | `CHIPSET_RAWDATA_COL.COL_NM` (동적) |
| 셀 데이터 | `RAWDATA_ROW` 1행 = 1레코드 | `CHIPSET_CELL` (COL_TYPE='RAWDATA') |
| 컬럼 추가 대응 | DDL 변경 필요 | `CHIPSET_RAWDATA_COL` 행 추가만으로 대응 |

### 3-3. CHIPSET_CELL: COL_TYPE + COL_SEQ 구분자 방식

```
CHIPSET_CELL.COL_TYPE = 'SPEC'    → CHIPSET_SPEC_COL.COL_SEQ 참조
CHIPSET_CELL.COL_TYPE = 'CHIP'    → CHIPSET_CHIP_COL.COL_SEQ 참조
CHIPSET_CELL.COL_TYPE = 'RAWDATA' → CHIPSET_RAWDATA_COL.COL_SEQ 참조
```

- DB 레벨 FK 제약 없음 (3개 테이블 중 하나를 참조하는 다형 참조이므로)
- 무결성은 애플리케이션(Service 레이어)에서 보장
- 새 컬럼 타입 추가 시 `CHIPSET_CELL` DDL 변경 없이 새 COL_TYPE 값만 추가

### 3-4. CHIPSET_CELL_COL → CHIPSET_CHIP_COL 재개명

| 항목 | 설명 |
|------|------|
| v1.0 | `CHIPSET_CHIP_COL` |
| v1.1 | `CHIPSET_CELL_COL` (CHIPSET_CELL의 컬럼 정의임을 표현하려 했으나 모호) |
| v1.2 | `CHIPSET_CHIP_COL` (칩셋 컬럼 헤더임을 직관적으로 표현) |

---

## 4. 테이블 관계도 (ER Diagram)

```
┌──────────────────────────────────────────────────────────────────────────┐
│                          CHIPSET_UPLOAD                                   │
│  PK: UPLOAD_SEQ │ FILE_NM │ FILE_TYPE │ UPLOAD_DT │ ROW_COUNT│COL_COUNT  │
└───┬──────────────────────────────────────────────────────────────────────┘
    │ 1
    ├───────────────┬──────────────────┬─────────────────┐
    │ N             │ N                │ N               │ N
    ▼               ▼                  ▼                 ▼
┌───────────────┐ ┌────────────────┐ ┌───────────────┐ ┌─────────────────────┐
│CHIPSET_       │ │CHIPSET_        │ │CHIPSET_       │ │CHIPSET_CELL         │
│SPEC_COL       │ │CHIP_COL        │ │RAWDATA_COL    │ │PK: CELL_SEQ         │
│PK: COL_SEQ    │ │PK: COL_SEQ     │ │PK: COL_SEQ    │ │FK: UPLOAD_SEQ       │
│FK: UPLOAD_SEQ │ │FK: UPLOAD_SEQ  │ │FK: UPLOAD_SEQ │ │    ROW_IDX (행번호) │
│COL_IDX        │ │VENDOR          │ │COL_IDX        │ │    COL_TYPE         │
│COL_NM         │ │CHIP_NM         │ │COL_NM         │ │    COL_SEQ ─────────┤
└───────────────┘ │CHIP_DT         │ └───────────────┘ │    CELL_VALUE       │
                  └────────────────┘                   │    BG_COLOR         │
                                                        └─────────────────────┘
        COL_TYPE='SPEC'    → CHIPSET_SPEC_COL.COL_SEQ
        COL_TYPE='CHIP'    → CHIPSET_CHIP_COL.COL_SEQ
        COL_TYPE='RAWDATA' → CHIPSET_RAWDATA_COL.COL_SEQ

※ 히스토리 테이블(*_H)은 메인 테이블과 동일한 구조 + 별도 H_SEQ PK 추가
```

### 4-1. 관계 요약

| 관계 | 설명 |
|------|------|
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_SPEC_COL` | 업로드 1건에 스펙 컬럼 헤더 N개 |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_CHIP_COL` | 업로드 1건에 칩셋 컬럼 헤더 N개 |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_RAWDATA_COL` | 업로드 1건에 RawData 컬럼 헤더 N개 |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_CELL` | 업로드 1건에 셀 N개 (전체) |
| `CHIPSET_SPEC_COL` 1 → N `CHIPSET_CELL` | 스펙 컬럼 1개에 스펙 셀 N개 (행 수만큼) |
| `CHIPSET_CHIP_COL` 1 → N `CHIPSET_CELL` | 칩셋 컬럼 1개에 칩셋 셀 N개 (행 수만큼) |
| `CHIPSET_RAWDATA_COL` 1 → N `CHIPSET_CELL` | RawData 컬럼 1개에 RawData 셀 N개 (행 수만큼) |

---

## 5. 각 테이블 상세 스키마

### 5-1. CHIPSET_UPLOAD (업로드 메타)

```sql
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- PK (SQ_CHIPSET_UPLOAD)
    FILE_NM     VARCHAR2(255) NOT NULL,   -- 원본 파일명
    FILE_TYPE   VARCHAR2(20)  NOT NULL,   -- 'SERVER'|'CLIENT'|'MOBILE'|'RAW_DATA'
    UPLOAD_DT   TIMESTAMP     NOT NULL,   -- 업로드 일시
    ROW_COUNT   NUMBER        DEFAULT 0,  -- 데이터 행 수
    COL_COUNT   NUMBER        DEFAULT 0,  -- 칩셋 컬럼 수 (RAW_DATA는 0)
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);
```

---

### 5-2. CHIPSET_SPEC_COL (Server/Client/Mobile 왼쪽 스펙 컬럼 헤더)

```sql
CREATE TABLE CHIPSET_SPEC_COL (
    COL_SEQ    NUMBER        NOT NULL,   -- PK (SQ_CHIPSET_SPEC_COL)
    UPLOAD_SEQ NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    COL_IDX    NUMBER        NOT NULL,   -- 1-based (1=첫 번째 스펙 컬럼, 최대 10)
    COL_NM     VARCHAR2(100) NOT NULL,   -- Excel 헤더명
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_SPEC_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_SPEC_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

**COL_IDX 예시:**

| COL_IDX | Server/Client 헤더 | Mobile 헤더 |
|---------|-------------------|-------------|
| 1 | DIMM | PKG |
| 2 | Product (Ver.) | Density |
| 3 | Ver. | Product |
| 4 | Density | P/N |
| 5 | Org | Code Name |
| 6 | Speed | (없음) |
| 7~10 | (예비) | (예비) |

---

### 5-3. CHIPSET_CHIP_COL (Server/Client/Mobile 오른쪽 칩셋 컬럼 헤더)

```sql
CREATE TABLE CHIPSET_CHIP_COL (
    COL_SEQ    NUMBER        NOT NULL,   -- PK (SQ_CHIPSET_CHIP_COL)
    UPLOAD_SEQ NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    VENDOR     VARCHAR2(100) NOT NULL,   -- 벤더명 (예: Intel, AMD, Qualcomm)
    COL_IDX    NUMBER        NOT NULL,   -- Excel 원본 컬럼 인덱스 (0-based)
    CHIP_NM    VARCHAR2(200) NOT NULL,   -- 칩셋명 (예: SPR-SP(4800), SM8650)
    CHIP_DT    VARCHAR2(50),             -- 출시일 (예: 01'23, 2023.Q4)
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_CHIP_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_CHIP_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

---

### 5-4. CHIPSET_RAWDATA_COL (RawData 컬럼 헤더)

```sql
CREATE TABLE CHIPSET_RAWDATA_COL (
    COL_SEQ    NUMBER        NOT NULL,   -- PK (SQ_CHIPSET_RAWDATA_COL)
    UPLOAD_SEQ NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    COL_IDX    NUMBER        NOT NULL,   -- Excel 원본 컬럼 인덱스 (0-based)
    COL_NM     VARCHAR2(200) NOT NULL,   -- 컬럼 헤더명
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_RAWDATA_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_RAWDATA_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);
```

**Raw_Data.xlsx 컬럼 저장 예시:**

| COL_IDX | COL_NM |
|---------|--------|
| 0 | Company |
| 1 | Seg |
| 2 | Chipset |
| 3 | SoC CS |
| 4 | Part Number |
| 5 | DRAM Process |
| 6 | Flash Process |
| 7 | Density |
| 8 | MLC/TLC |
| 9 | PKG |
| 10 | Date (1차) |
| 11 | Eng (1차) |
| 12 | Status (1차) |
| 13 | Remark (1차) |
| 14~17 | Date/Eng/Status/Remark (2차) |
| 18~19 | Date/Eng (3차) |

---

### 5-5. CHIPSET_CELL (모든 셀 데이터)

```sql
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ   NUMBER        NOT NULL,   -- PK (SQ_CHIPSET_CELL)
    UPLOAD_SEQ NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    ROW_IDX    NUMBER        NOT NULL,   -- 업로드 내 0-based 행 번호
    COL_TYPE   VARCHAR2(20)  NOT NULL,   -- 'SPEC' | 'CHIP' | 'RAWDATA'
    COL_SEQ    NUMBER        NOT NULL,   -- 해당 COL_TYPE 테이블의 COL_SEQ 값
    CELL_VALUE VARCHAR2(200),            -- 셀 텍스트
    BG_COLOR   VARCHAR2(10),             -- 배경색 HEX (예: #00B050)
    CONSTRAINT PK_CHIPSET_CELL PRIMARY KEY (CELL_SEQ),
    CONSTRAINT FK_CELL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ),
    CONSTRAINT CHK_CELL_COL_TYPE CHECK (COL_TYPE IN ('SPEC', 'CHIP', 'RAWDATA'))
);
```

**셀 타입별 저장 패턴:**

| COL_TYPE | COL_SEQ 참조 대상 | 사용 주체 | 설명 |
|----------|-----------------|---------|------|
| `'SPEC'` | `CHIPSET_SPEC_COL.COL_SEQ` | Server/Client/Mobile | 왼쪽 스펙 값 (DIMM=RDIMM 등) |
| `'CHIP'` | `CHIPSET_CHIP_COL.COL_SEQ` | Server/Client/Mobile | 오른쪽 검증값 + 배경색 |
| `'RAWDATA'` | `CHIPSET_RAWDATA_COL.COL_SEQ` | RawData | 추적 이력 모든 셀 |

> **ROW_IDX**: 동일한 `UPLOAD_SEQ + ROW_IDX`를 가진 셀들이 하나의 Excel 행을 구성
> **COL_SEQ 무결성**: DB FK 없음 — Service 레이어에서 올바른 COL_TYPE/COL_SEQ 보장

---

## 6. Excel → DB 매핑 상세

### 6-1. Server.xlsx / Client.xlsx 매핑

```
행 0 │ DIMM │Prod│ Ver│Dens│ Org│Spd│◄── Intel(병합) ──►│◄── AMD(병합) ──►│
행 1 │ (빈) │    │    │    │    │   │ SPR-SP │ EMR-SP │...│ GENOA │ TURIN │...│
행 2 │ (빈) │    │    │    │    │   │  01'23 │  12'23 │...│ 12'23 │ 08'24 │...│
행 3 │RDIMM │1A P│ WC │16GB│2RX4│5600│  ██  │        │   │  ██   │       │   │
```

| Excel 위치 | 저장 테이블 | 저장 컬럼 |
|------------|------------|-----------|
| 행0 컬6+ 병합셀 | `CHIPSET_CHIP_COL` | `VENDOR` ("Intel", "AMD") |
| 행1 컬6+ 각 셀 | `CHIPSET_CHIP_COL` | `CHIP_NM` ("SPR-SP(4800)") |
| 행2 컬6+ 각 셀 | `CHIPSET_CHIP_COL` | `CHIP_DT` ("01'23") |
| 행0 컬0 헤더 | `CHIPSET_SPEC_COL` | `COL_NM`="DIMM", COL_IDX=1 |
| 행0 컬1~5 헤더 | `CHIPSET_SPEC_COL` | `COL_NM`, COL_IDX=2~6 |
| 행3+ 컬0~5 값 | `CHIPSET_CELL` | COL_TYPE='SPEC', COL_SEQ=SPEC_COL.COL_SEQ |
| 행3+ 컬6+ 셀값 | `CHIPSET_CELL` | COL_TYPE='CHIP', `CELL_VALUE` |
| 행3+ 컬6+ 배경색 | `CHIPSET_CELL` | COL_TYPE='CHIP', `BG_COLOR` |

---

### 6-2. Mobile.xlsx 매핑

Mobile도 동일한 패턴. `CHIPSET_SPEC_COL` 저장:

| COL_IDX | COL_NM |
|---------|--------|
| 1 | PKG |
| 2 | Density |
| 3 | Product |
| 4 | P/N |
| 5 | Code Name |

---

### 6-3. Raw_Data.xlsx 매핑

| Excel 위치 | 저장 테이블 | 저장 컬럼 |
|------------|------------|-----------|
| 헤더행 각 열 | `CHIPSET_RAWDATA_COL` | `COL_NM` (COL_IDX=0~N) |
| 데이터행 각 셀 | `CHIPSET_CELL` | COL_TYPE='RAWDATA', `CELL_VALUE` |
| 데이터행 각 배경색 | `CHIPSET_CELL` | COL_TYPE='RAWDATA', `BG_COLOR` |

---

## 7. 업로드 처리 흐름

### 7-1. Matrix형 (SERVER / CLIENT / MOBILE)

```
[ChipsetService]
  ① DELETE CHIPSET_CELL        WHERE UPLOAD_SEQ IN (해당 FILE_TYPE)
  ② DELETE CHIPSET_CHIP_COL    WHERE UPLOAD_SEQ IN (해당 FILE_TYPE)
  ③ DELETE CHIPSET_SPEC_COL    WHERE UPLOAD_SEQ IN (해당 FILE_TYPE)
  ④ DELETE CHIPSET_UPLOAD      WHERE FILE_TYPE = ?

  ⑤ INSERT CHIPSET_UPLOAD      → uploadSeq 채번
  ⑥ INSERT CHIPSET_SPEC_COL    (헤더명 저장, COL_IDX별)
  ⑦ INSERT CHIPSET_CHIP_COL    (벤더·칩명·출시일)
  ⑧ INSERT CHIPSET_CELL        (COL_TYPE='SPEC', 스펙 셀)
  ⑨ INSERT CHIPSET_CELL        (COL_TYPE='CHIP', 칩셋 셀)
  ⑩ INSERT *_H 히스토리 누적
```

### 7-2. Tracking형 (RAW_DATA)

```
[ChipsetService]
  ① DELETE CHIPSET_CELL        WHERE UPLOAD_SEQ IN (RAW_DATA)
  ② DELETE CHIPSET_RAWDATA_COL WHERE UPLOAD_SEQ IN (RAW_DATA)
  ③ DELETE CHIPSET_UPLOAD      WHERE FILE_TYPE = 'RAW_DATA'

  ④ INSERT CHIPSET_UPLOAD      → uploadSeq 채번
  ⑤ INSERT CHIPSET_RAWDATA_COL (헤더명 저장, COL_IDX별)
  ⑥ INSERT CHIPSET_CELL        (COL_TYPE='RAWDATA', ROW_IDX로 행 구분)
  ⑦ INSERT *_H 히스토리 누적
```

---

## 8. 데이터 조회 구조

### 8-1. Matrix 조회 응답 구조 (GET /api/chipset/matrix?type=SERVER)

```json
{
  "uploadSeq": 5,
  "fileType":  "SERVER",
  "specCols": [
    { "colSeq": 1, "colIdx": 1, "colNm": "DIMM" },
    { "colSeq": 2, "colIdx": 2, "colNm": "Product (Ver.)" }
  ],
  "chipCols": [
    { "colSeq": 1, "vendor": "INTEL", "chipNm": "SPR-SP(4800)", "chipDt": "01'23" }
  ],
  "rows": [
    {
      "rowIdx": 0,
      "specCells": [
        { "colSeq": 1, "cellValue": "RDIMM",    "bgColor": null },
        { "colSeq": 2, "cellValue": "1A P-DIE", "bgColor": null }
      ],
      "chipCells": [
        { "colSeq": 1, "cellValue": "10'23", "bgColor": "#00B050" }
      ]
    }
  ]
}
```

### 8-2. CHIPSET_CELL 조회 SQL 예시

```sql
-- Server 업로드의 스펙 셀 조회
SELECT c.ROW_IDX, sc.COL_NM, sc.COL_IDX, c.CELL_VALUE, c.BG_COLOR
FROM   CHIPSET_CELL c
JOIN   CHIPSET_SPEC_COL sc ON c.COL_SEQ = sc.COL_SEQ
WHERE  c.UPLOAD_SEQ = :uploadSeq
AND    c.COL_TYPE   = 'SPEC'
ORDER  BY c.ROW_IDX, sc.COL_IDX;

-- Server 업로드의 칩셋 셀 조회
SELECT c.ROW_IDX, cc.VENDOR, cc.CHIP_NM, c.CELL_VALUE, c.BG_COLOR
FROM   CHIPSET_CELL c
JOIN   CHIPSET_CHIP_COL cc ON c.COL_SEQ = cc.COL_SEQ
WHERE  c.UPLOAD_SEQ = :uploadSeq
AND    c.COL_TYPE   = 'CHIP'
ORDER  BY c.ROW_IDX, cc.SORT_ORDER;

-- RawData 업로드의 셀 조회
SELECT c.ROW_IDX, rc.COL_NM, rc.COL_IDX, c.CELL_VALUE, c.BG_COLOR
FROM   CHIPSET_CELL c
JOIN   CHIPSET_RAWDATA_COL rc ON c.COL_SEQ = rc.COL_SEQ
WHERE  c.UPLOAD_SEQ = :uploadSeq
AND    c.COL_TYPE   = 'RAWDATA'
ORDER  BY c.ROW_IDX, rc.COL_IDX;
```

---

## 9. 히스토리 관리 전략

### 9-1. 메인 테이블: 덮어쓰기 방식

```
새 Server.xlsx 업로드 시:
  DELETE CHIPSET_CELL, CHIPSET_CHIP_COL, CHIPSET_SPEC_COL, CHIPSET_UPLOAD (SERVER)
  ↓ 새 데이터 INSERT
```

### 9-2. 히스토리 테이블: 누적 방식

```
메인 INSERT 후 추가로:
  INSERT CHIPSET_UPLOAD_H
  INSERT CHIPSET_SPEC_COL_H    (Matrix형 업로드 시)
  INSERT CHIPSET_CHIP_COL_H    (Matrix형 업로드 시)
  INSERT CHIPSET_RAWDATA_COL_H (RAW_DATA 업로드 시)
  INSERT CHIPSET_CELL_H
```

---

## 10. 시퀀스 목록

| 시퀀스명 | 채번 대상 |
|----------|----------|
| `SQ_CHIPSET_UPLOAD` | CHIPSET_UPLOAD.UPLOAD_SEQ |
| `SQ_CHIPSET_SPEC_COL` | CHIPSET_SPEC_COL.COL_SEQ |
| `SQ_CHIPSET_CHIP_COL` | CHIPSET_CHIP_COL.COL_SEQ |
| `SQ_CHIPSET_RAWDATA_COL` | CHIPSET_RAWDATA_COL.COL_SEQ |
| `SQ_CHIPSET_CELL` | CHIPSET_CELL.CELL_SEQ |
| `SQ_CHIPSET_UPLOAD_H` | CHIPSET_UPLOAD_H.UPLOAD_H_SEQ |
| `SQ_CHIPSET_SPEC_COL_H` | CHIPSET_SPEC_COL_H.SPEC_COL_H_SEQ |
| `SQ_CHIPSET_CHIP_COL_H` | CHIPSET_CHIP_COL_H.CHIP_COL_H_SEQ |
| `SQ_CHIPSET_RAWDATA_COL_H` | CHIPSET_RAWDATA_COL_H.RAWDATA_COL_H_SEQ |
| `SQ_CHIPSET_CELL_H` | CHIPSET_CELL_H.CELL_H_SEQ |

> **제거된 시퀀스:** `SQ_CHIPSET_ROW`, `SQ_CHIPSET_ROW_H`, `SQ_RAWDATA_ROW`, `SQ_RAWDATA_ROW_H`, `SQ_CHIPSET_CELL_COL`, `SQ_CHIPSET_CELL_COL_H`

---

## 11. 데이터 예시 (Server.xlsx 1행 업로드 시, v1.2)

Excel 입력:
```
벤더행:  [DIMM│Product│Ver.│Density│Org│Speed] │ Intel(병합 3칸)
칩명행:  [빈칸 × 6]                            │ SPR  │ EMR  │ GNR
날짜행:  [빈칸 × 6]                            │01'23 │12'23 │01'25
데이터:  RDIMM │ 1A P-DIE │ WC │ 16GB │ 2RX4 │ 5600 │ 10'23(녹색) │ (빈) │ 01'24(파랑)
```

DB INSERT 결과:

```sql
-- CHIPSET_UPLOAD
(UPLOAD_SEQ=1, FILE_NM='Server.xlsx', FILE_TYPE='SERVER', ROW_COUNT=1, COL_COUNT=3)

-- CHIPSET_SPEC_COL
(COL_SEQ=1, UPLOAD_SEQ=1, COL_IDX=1, COL_NM='DIMM',    SORT_ORDER=0)
(COL_SEQ=2, UPLOAD_SEQ=1, COL_IDX=2, COL_NM='Product', SORT_ORDER=1)
(COL_SEQ=3, UPLOAD_SEQ=1, COL_IDX=3, COL_NM='Ver.',    SORT_ORDER=2)
(COL_SEQ=4, UPLOAD_SEQ=1, COL_IDX=4, COL_NM='Density', SORT_ORDER=3)
(COL_SEQ=5, UPLOAD_SEQ=1, COL_IDX=5, COL_NM='Org',     SORT_ORDER=4)
(COL_SEQ=6, UPLOAD_SEQ=1, COL_IDX=6, COL_NM='Speed',   SORT_ORDER=5)

-- CHIPSET_CHIP_COL
(COL_SEQ=1, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=6, CHIP_NM='SPR', CHIP_DT='01''23', SORT_ORDER=0)
(COL_SEQ=2, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=7, CHIP_NM='EMR', CHIP_DT='12''23', SORT_ORDER=1)
(COL_SEQ=3, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=8, CHIP_NM='GNR', CHIP_DT='01''25', SORT_ORDER=2)

-- CHIPSET_CELL (스펙 셀: COL_TYPE='SPEC')
(CELL_SEQ=1,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=1, CELL_VALUE='RDIMM',    BG_COLOR=NULL)
(CELL_SEQ=2,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=2, CELL_VALUE='1A P-DIE', BG_COLOR=NULL)
(CELL_SEQ=3,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=3, CELL_VALUE='WC',       BG_COLOR=NULL)
(CELL_SEQ=4,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=4, CELL_VALUE='16GB',     BG_COLOR=NULL)
(CELL_SEQ=5,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=5, CELL_VALUE='2RX4',     BG_COLOR=NULL)
(CELL_SEQ=6,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='SPEC', COL_SEQ=6, CELL_VALUE='5600',     BG_COLOR=NULL)

-- CHIPSET_CELL (칩셋 셀: COL_TYPE='CHIP')
(CELL_SEQ=7,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='CHIP', COL_SEQ=1, CELL_VALUE='10''23', BG_COLOR='#00B050')
(CELL_SEQ=8,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='CHIP', COL_SEQ=2, CELL_VALUE='',       BG_COLOR=NULL)
(CELL_SEQ=9,  UPLOAD_SEQ=1, ROW_IDX=0, COL_TYPE='CHIP', COL_SEQ=3, CELL_VALUE='01''24', BG_COLOR='#4472C4')
```

---

## 12. Oracle 배포용 DDL

Oracle에 배포할 때는 `backend/src/main/resources/schema.sql`을 DBeaver 또는 SQL*Plus에서 실행하세요.
DROP 먼저 실행하려면 `backend/src/main/resources/schema-drop.sql`을 먼저 실행합니다.

> H2와 달리 `IF NOT EXISTS` 구문을 지원하지 않으므로 테이블/시퀀스가 없는 상태에서 실행해야 합니다.

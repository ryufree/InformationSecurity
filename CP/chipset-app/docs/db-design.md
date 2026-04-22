# Chipset DB 설계 문서

> 작성일: 2026-04-22  
> 대상 시스템: chipset-app (Vue 3 + Spring Boot + MyBatis + Oracle/H2)  
> 목적: 4가지 Excel 파일을 DB 테이블에 저장하는 구조와 테이블 관계 설명

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
| `CHIPSET_CHIP_COL` | 칩셋 컬럼 정의 (벤더, 칩명, 출시일) | SERVER / CLIENT / MOBILE |
| `CHIPSET_ROW` | 스펙 행 (DIMM, Product 등) | SERVER / CLIENT / MOBILE |
| `CHIPSET_CELL` | 셀 값 (검증일자, 배경색) | SERVER / CLIENT / MOBILE |
| `RAWDATA_ROW` | Raw Data 행 (검증 이력 전체) | RAW_DATA |

### 2-2. 히스토리 테이블 (누적 이력)

| 테이블명 | 역할 |
|----------|------|
| `CHIPSET_UPLOAD_H` | 업로드 이력 누적 |
| `CHIPSET_CHIP_COL_H` | 칩셋 컬럼 이력 누적 |
| `CHIPSET_ROW_H` | 스펙 행 이력 누적 |
| `CHIPSET_CELL_H` | 셀 값 이력 누적 |
| `RAWDATA_ROW_H` | Raw Data 행 이력 누적 |

> **메인 vs 히스토리**: 메인 테이블은 파일타입별로 최신 1건만 유지(업로드 시 기존 삭제).
> 히스토리 테이블은 업로드할 때마다 누적 저장하여 과거 버전 조회를 지원합니다.

---

## 3. 테이블 관계도 (ER Diagram)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CHIPSET_UPLOAD                                  │
│  PK: UPLOAD_SEQ  │  FILE_NM  │  FILE_TYPE  │  UPLOAD_DT  │  ROW/COL_COUNT │
└────────┬────────────────────────────────────────────────────────────────┘
         │ 1
         │
   ┌─────┴─────────────────────────────────────────┐
   │                                               │
   │ N (FILE_TYPE = SERVER/CLIENT/MOBILE)          │ N (FILE_TYPE = RAW_DATA)
   │                                               │
   ▼                                               ▼
┌──────────────────┐                    ┌──────────────────────────┐
│ CHIPSET_CHIP_COL │                    │      RAWDATA_ROW         │
│ PK: COL_SEQ      │                    │ PK: RAWDATA_ROW_SEQ      │
│ FK: UPLOAD_SEQ   │                    │ FK: UPLOAD_SEQ           │
│ VENDOR           │                    │ COMPANY, SEG, CHIPSET    │
│ CHIP_NM          │                    │ SOC_CS, PART_NUMBER      │
│ CHIP_DT          │                    │ DRAM_PROCESS             │
│ SORT_ORDER       │                    │ FLASH_PROCESS            │
└────────┬─────────┘                    │ DENSITY, MLC_TLC, PKG    │
         │ 1                            │ VAL1_DATE/ENG/STATUS/REM │
         │                              │ VAL2_DATE/ENG/STATUS/REM │
         │ N (COL_SEQ 참조)             │ VAL3_DATE/ENG            │
         ▼                              │ SORT_ORDER               │
┌──────────────────┐                    └──────────────────────────┘
│   CHIPSET_CELL   │ ◄──────────┐
│ PK: CELL_SEQ     │            │ N (ROW_SEQ 참조)
│ FK: ROW_SEQ      │            │
│ FK: COL_SEQ      │   ┌────────┴─────────┐
│ CELL_VALUE       │   │   CHIPSET_ROW    │
│ BG_COLOR         │   │ PK: ROW_SEQ      │
└──────────────────┘   │ FK: UPLOAD_SEQ   │
                       │ DIMM             │
                       │ PRODUCT          │
                       │ VER              │
                       │ DENSITY          │
                       │ ORG              │
                       │ SPEED            │
                       │ SORT_ORDER       │
                       └──────────────────┘

※ 히스토리 테이블(*_H)은 위 메인 테이블과 동일한 구조 + H_SEQ PK를 추가로 보유
```

### 3-1. 관계 요약

| 관계 | 설명 |
|------|------|
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_CHIP_COL` | 업로드 1건에 칩셋 컬럼 N개 |
| `CHIPSET_UPLOAD` 1 → N `CHIPSET_ROW` | 업로드 1건에 스펙 행 N개 |
| `CHIPSET_ROW` 1 → N `CHIPSET_CELL` | 스펙 행 1개에 셀 N개 (칩셋 수만큼) |
| `CHIPSET_CHIP_COL` 1 → N `CHIPSET_CELL` | 칩셋 컬럼 1개에 셀 N개 (행 수만큼) |
| `CHIPSET_UPLOAD` 1 → N `RAWDATA_ROW` | 업로드 1건에 Raw Data 행 N개 |

---

## 4. 각 테이블 상세 스키마

### 4-1. CHIPSET_UPLOAD (업로드 메타)

```sql
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    FILE_NM     VARCHAR2(255) NOT NULL,   -- 원본 파일명 (예: Server.xlsx)
    FILE_TYPE   VARCHAR2(20)  NOT NULL,   -- 'SERVER' | 'CLIENT' | 'MOBILE' | 'RAW_DATA'
    UPLOAD_DT   TIMESTAMP     NOT NULL,   -- 업로드 일시 (CURRENT_TIMESTAMP)
    ROW_COUNT   NUMBER        DEFAULT 0,  -- 파싱된 데이터 행 수
    COL_COUNT   NUMBER        DEFAULT 0,  -- 칩셋 컬럼 수 (RAW_DATA는 0)
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);
```

**FILE_TYPE 역할**: 타입별로 메인 테이블을 독립 관리하는 핵심 키.
업로드 시 동일 FILE_TYPE의 기존 데이터를 모두 삭제 후 새로 INSERT합니다.

---

### 4-2. CHIPSET_CHIP_COL (칩셋 컬럼 정의)

```sql
CREATE TABLE CHIPSET_CHIP_COL (
    COL_SEQ     NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    VENDOR      VARCHAR2(100) NOT NULL,   -- 벤더명 (예: Intel, AMD, Qualcomm)
    COL_IDX     NUMBER        NOT NULL,   -- Excel 원본 컬럼 인덱스 (0-based)
    CHIP_NM     VARCHAR2(200) NOT NULL,   -- 칩셋 이름 (예: SPR, EMR, GNR)
    CHIP_DT     VARCHAR2(50),             -- 출시일 (예: 2023.Q1)
    SORT_ORDER  NUMBER        DEFAULT 0,  -- 화면 표시 순서
    CONSTRAINT PK_CHIPSET_CHIP_COL PRIMARY KEY (COL_SEQ)
);
```

**COL_IDX 역할**: Excel 파싱 시 실제 컬럼 번호를 보존합니다.
CHIPSET_CELL과 조인할 때 COL_SEQ로 연결되지만,
파싱 단계에서는 COL_IDX로 매핑합니다.

---

### 4-3. CHIPSET_ROW (스펙 행)

```sql
CREATE TABLE CHIPSET_ROW (
    ROW_SEQ     NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ  NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    DIMM        VARCHAR2(100),            -- Server/Client: DIMM 타입 | Mobile: PKG
    PRODUCT     VARCHAR2(200),            -- 제품명 (공통)
    VER         VARCHAR2(50),             -- Server/Client: 버전 | Mobile: Code Name
    DENSITY     VARCHAR2(50),             -- 용량 (공통)
    ORG         VARCHAR2(50),             -- Server/Client: Org | Mobile: P/N
    SPEED       VARCHAR2(100),            -- Server/Client: Speed | Mobile: "" (없음)
    SORT_ORDER  NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_ROW PRIMARY KEY (ROW_SEQ)
);
```

**Mobile 컬럼 재사용 전략**: Mobile.xlsx의 좌측 스펙 컬럼이 Server/Client와 다르지만,
의미적으로 대응 가능하므로 기존 컬럼을 재사용합니다.

| CHIPSET_ROW 컬럼 | Server/Client 의미 | Mobile 의미 |
|------------------|--------------------|-------------|
| DIMM | DIMM 타입 (DDR5 등) | PKG (LP5X 등) |
| PRODUCT | 제품명 | 제품명 |
| VER | 버전 | Code Name (VP/VL/VH) |
| DENSITY | 용량 | 용량 |
| ORG | Organization | P/N (Part Number) |
| SPEED | 속도 | (없음, 빈 문자열) |

---

### 4-4. CHIPSET_CELL (셀 값)

```sql
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ    NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    ROW_SEQ     NUMBER        NOT NULL,   -- FK → CHIPSET_ROW
    COL_SEQ     NUMBER        NOT NULL,   -- FK → CHIPSET_CHIP_COL
    CELL_VALUE  VARCHAR2(200),            -- 셀 텍스트 (검증일자 등)
    BG_COLOR    VARCHAR2(10),             -- 배경색 HEX (예: #00B050, null=없음)
    CONSTRAINT PK_CHIPSET_CELL PRIMARY KEY (CELL_SEQ)
);
```

**CELL_VALUE 예시**: `"2024.03.15"`, `"VP"`, `"VH"`, `""` (미검증)  
**BG_COLOR 역할**: Excel 셀의 배경색을 HEX로 저장. 화면에서 그대로 렌더링합니다.
흰색(#FFFFFF), 검정(#000000)은 null로 저장 (의미 없는 기본색 제외).

---

### 4-5. RAWDATA_ROW (Raw Data 행)

```sql
CREATE TABLE RAWDATA_ROW (
    RAWDATA_ROW_SEQ NUMBER        NOT NULL,   -- PK, 시퀀스 자동 채번
    UPLOAD_SEQ      NUMBER        NOT NULL,   -- FK → CHIPSET_UPLOAD
    -- Target AP 섹션 (Excel 컬럼 B-D)
    COMPANY         VARCHAR2(100),            -- 회사명
    SEG             VARCHAR2(100),            -- 세그먼트
    CHIPSET         VARCHAR2(200),            -- 칩셋명
    -- Sorting KEY 섹션 (Excel 컬럼 E-K)
    SOC_CS          VARCHAR2(200),            -- SoC CS
    PART_NUMBER     VARCHAR2(200),            -- Part Number
    DRAM_PROCESS    VARCHAR2(100),            -- DRAM 공정
    FLASH_PROCESS   VARCHAR2(100),            -- Flash 공정
    DENSITY         VARCHAR2(50),             -- 용량
    MLC_TLC         VARCHAR2(50),             -- MLC/TLC 구분
    PKG             VARCHAR2(200),            -- 패키지
    -- Validation Status 섹션 (1차, Excel 컬럼 L-O)
    VAL1_DATE       VARCHAR2(20),             -- 검증일자
    VAL1_ENG        VARCHAR2(50),             -- 담당 엔지니어
    VAL1_STATUS     VARCHAR2(50),             -- Pass/Fail/In Progress
    VAL1_REMARK     VARCHAR2(500),            -- 비고
    -- Validation Status 섹션 (2차, Excel 컬럼 P-S)
    VAL2_DATE       VARCHAR2(20),
    VAL2_ENG        VARCHAR2(50),
    VAL2_STATUS     VARCHAR2(50),
    VAL2_REMARK     VARCHAR2(500),
    -- Validation Status 섹션 (3차, Excel 컬럼 T-U)
    VAL3_DATE       VARCHAR2(20),
    VAL3_ENG        VARCHAR2(50),
    SORT_ORDER      NUMBER        DEFAULT 0,
    CONSTRAINT PK_RAWDATA_ROW PRIMARY KEY (RAWDATA_ROW_SEQ)
);
```

---

## 5. Excel → DB 매핑 상세

### 5-1. Server.xlsx / Client.xlsx 구조와 매핑

**Excel 시트 구조:**

```
행 0 │ (빈칸×6) │◄─────── Intel ────────►│◄──────── AMD ─────────►│
행 1 │ (빈칸×6) │  SPR  │  EMR  │  GNR  │  GENOA │ BERGAMO│  TUR  │
행 2 │ (빈칸×6) │2022.Q4│2023.Q4│2024.H1│2022.H2 │2023.H1 │2024.Q3│
행 3 │ DIMM │Prod│ Ver│Dens│ Org│Spd│  2024 │       │       │        │
행 4 │ DDR5 │ A  │ B  │16G │x4  │4800│  ██   │       │       │        │
...
```

**매핑 규칙:**

| Excel 위치 | 저장 테이블 | 저장 컬럼 |
|------------|------------|-----------|
| 행0, 컬6 이후 병합셀 텍스트 | `CHIPSET_CHIP_COL` | `VENDOR` |
| 행1, 컬6 이후 각 셀 텍스트 | `CHIPSET_CHIP_COL` | `CHIP_NM` |
| 행2, 컬6 이후 각 셀 텍스트 | `CHIPSET_CHIP_COL` | `CHIP_DT` |
| 행3 이후, 컬0 (DIMM) | `CHIPSET_ROW` | `DIMM` |
| 행3 이후, 컬1 (Product) | `CHIPSET_ROW` | `PRODUCT` |
| 행3 이후, 컬2 (Ver.) | `CHIPSET_ROW` | `VER` |
| 행3 이후, 컬3 (Density) | `CHIPSET_ROW` | `DENSITY` |
| 행3 이후, 컬4 (Org) | `CHIPSET_ROW` | `ORG` |
| 행3 이후, 컬5 (Speed) | `CHIPSET_ROW` | `SPEED` |
| 행3 이후, 컬6 이후 셀값 | `CHIPSET_CELL` | `CELL_VALUE` |
| 행3 이후, 컬6 이후 배경색 | `CHIPSET_CELL` | `BG_COLOR` |

---

### 5-2. Mobile.xlsx 구조와 매핑

**Excel 시트 구조:**

```
행 0 │ (빈칸×5) │◄──────────────── Qualcomm ──────────────────────►│
행 1 │ (빈칸×5) │  SM8650 │ SM8550 │ SM7675 │ SM7450 │  SM6475 │...│
행 2 │ (빈칸×5) │ 2023.Q4 │2023.Q1 │ 2024.Q2│2022.Q4 │ 2023.Q3 │...│
행 3 │ PKG│Dens│Prod│ P/N│CdNm│  VP   │  VL   │  VH   │        │   │
행 4 │LP5X│16GB│ A  │KMQ │ VP │  ██   │       │       │        │   │
...
```

**매핑 규칙 (Server/Client와 다른 부분만 표기):**

| Excel 위치 | 저장 테이블 | 저장 컬럼 | 비고 |
|------------|------------|-----------|------|
| 행3 이후, 컬0 (PKG) | `CHIPSET_ROW` | `DIMM` | 컬럼 재사용 |
| 행3 이후, 컬1 (Density) | `CHIPSET_ROW` | `DENSITY` | 동일 |
| 행3 이후, 컬2 (Product) | `CHIPSET_ROW` | `PRODUCT` | 동일 |
| 행3 이후, 컬3 (P/N) | `CHIPSET_ROW` | `ORG` | 컬럼 재사용 |
| 행3 이후, 컬4 (Code Name) | `CHIPSET_ROW` | `VER` | 컬럼 재사용 |
| SPEED 컬럼 | `CHIPSET_ROW` | `SPEED` | `""` (없음) |

---

### 5-3. Raw_Data.xlsx 구조와 매핑

**Excel 시트 구조:**

```
행 0 │(빈)│◄── Target AP ──►│◄──────── Sorting KEY ────────►│◄─── Validation Status ───►│
행 1 │(빈)│Comp│Seg │Chipset │SoC │P/N │DRAM│Flash│Dens│MLC│PKG│Date│Eng│Status│Remark│...│
행 2 │(빈)│ A  │Mob │SM8650  │QCM │KMQ │7nm │ 7nm │16G │TLC│BGA│2024│홍│Pass  │OK    │...│
...
```

**매핑 규칙:**

| Excel 컬럼 인덱스 | 헤더명 | 저장 테이블 | 저장 컬럼 |
|------------------|--------|------------|-----------|
| 1 | Company | `RAWDATA_ROW` | `COMPANY` |
| 2 | Seg | `RAWDATA_ROW` | `SEG` |
| 3 | Chipset | `RAWDATA_ROW` | `CHIPSET` |
| 4 | SoC CS | `RAWDATA_ROW` | `SOC_CS` |
| 5 | Part Number | `RAWDATA_ROW` | `PART_NUMBER` |
| 6 | DRAM Process | `RAWDATA_ROW` | `DRAM_PROCESS` |
| 7 | Flash Process | `RAWDATA_ROW` | `FLASH_PROCESS` |
| 8 | Density | `RAWDATA_ROW` | `DENSITY` |
| 9 | MLC/TLC | `RAWDATA_ROW` | `MLC_TLC` |
| 10 | PKG | `RAWDATA_ROW` | `PKG` |
| 11 | Date (1차) | `RAWDATA_ROW` | `VAL1_DATE` |
| 12 | Eng. (1차) | `RAWDATA_ROW` | `VAL1_ENG` |
| 13 | Status (1차) | `RAWDATA_ROW` | `VAL1_STATUS` |
| 14 | Remark (1차) | `RAWDATA_ROW` | `VAL1_REMARK` |
| 15 | Date (2차) | `RAWDATA_ROW` | `VAL2_DATE` |
| 16 | Eng. (2차) | `RAWDATA_ROW` | `VAL2_ENG` |
| 17 | Status (2차) | `RAWDATA_ROW` | `VAL2_STATUS` |
| 18 | Remark (2차) | `RAWDATA_ROW` | `VAL2_REMARK` |
| 19 | Date (3차) | `RAWDATA_ROW` | `VAL3_DATE` |
| 20 | Eng. (3차) | `RAWDATA_ROW` | `VAL3_ENG` |

---

## 6. 업로드 처리 흐름

### 6-1. Matrix형 (SERVER / CLIENT / MOBILE) 업로드 흐름

```
[클라이언트]                [서버: ChipsetController]         [ChipsetExcelParser]
     │                              │                                  │
     │── POST /api/chipset/upload ─►│                                  │
     │         (file: .xlsx)        │── parse(file, filename) ────────►│
     │                              │                                  │── 파일 타입 자동 감지
     │                              │                                  │   (헤더 키워드 스캔)
     │                              │◄─ ParseResult(fileType, ────────│
     │                              │   chipColDefs, rows)             │
     │                              │
     │                        [ChipsetService]
     │                              │
     │                              │ ① 기존 데이터 삭제 (FILE_TYPE 기준)
     │                              │   DELETE CHIPSET_CELL (해당 타입)
     │                              │   DELETE CHIPSET_ROW  (해당 타입)
     │                              │   DELETE CHIPSET_CHIP_COL (해당 타입)
     │                              │   DELETE CHIPSET_UPLOAD (해당 타입)
     │                              │
     │                              │ ② CHIPSET_UPLOAD INSERT
     │                              │   → uploadSeq 채번
     │                              │
     │                              │ ③ CHIPSET_CHIP_COL INSERT (칩셋 컬럼별)
     │                              │   → colSeq 채번, colIdx→colSeq 매핑 보존
     │                              │
     │                              │ ④ CHIPSET_ROW INSERT (스펙 행별)
     │                              │   → rowSeq 채번
     │                              │
     │                              │ ⑤ CHIPSET_CELL INSERT (행×칩셋 교차점)
     │                              │   cellValue + bgColor
     │                              │
     │                              │ ⑥ 히스토리 누적 (_H 테이블에 동일 데이터 INSERT)
     │                              │
     │◄─ UploadResult(success) ─────│
```

### 6-2. Tracking형 (RAW_DATA) 업로드 흐름

```
     │── POST /api/chipset/upload ─►│
     │                              │── parse() ─► 파일 타입: RAW_DATA 감지
     │                              │
     │                        [ChipsetService]
     │                              │
     │                              │ ① 기존 데이터 삭제
     │                              │   DELETE RAWDATA_ROW (해당 타입)
     │                              │   DELETE CHIPSET_UPLOAD (해당 타입)
     │                              │
     │                              │ ② CHIPSET_UPLOAD INSERT (ROW_COUNT, COL_COUNT=0)
     │                              │
     │                              │ ③ RAWDATA_ROW INSERT (행별 전체 컬럼)
     │                              │
     │                              │ ④ 히스토리 누적 (RAWDATA_ROW_H INSERT)
     │                              │
     │◄─ UploadResult(success) ─────│
```

---

## 7. 파일 타입 자동 감지 로직

`ChipsetExcelParser.FileType.detect()` 메서드가 시트 내용을 분석합니다.

```
detect(filename, sheet) 실행 순서:

1단계: 상위 4행 스캔 → "TARGET AP" 또는 "SORTING KEY" 발견
       → RAW_DATA 반환 (최우선)

2단계: 상위 4행 컬럼 0-1번 스캔 → "PKG" 발견
       → MOBILE 반환

3단계: 파일명 패턴 확인
       - 파일명에 "CLIENT" 포함 → CLIENT
       - 파일명에 "MOBILE" 포함 → MOBILE
       - 파일명에 "RAW"    포함 → RAW_DATA

4단계: 위 조건 미해당 → SERVER (기본값)
```

**감지 우선순위 이유:**
- RAW_DATA는 구조 자체가 완전히 다르므로 최우선 감지
- Mobile은 PKG 컬럼이 0번 위치에 있어 구분 가능
- Server vs Client는 구조가 동일하므로 파일명으로 구분
- 파일명 미포함 시 Server로 기본 처리

---

## 8. 데이터 조회 구조

### 8-1. Matrix 조회 (GET /api/chipset/matrix?type=SERVER)

```
응답 구조 (MatrixResponse):
{
  "uploadSeq": 5,
  "uploadDt":  "2026-04-22T09:30:00",
  "fileType":  "SERVER",
  "vendors":   ["INTEL", "AMD"],             ← 고유 벤더 목록 (화면 헤더용)
  "chipCols":  [                             ← CHIPSET_CHIP_COL 전체
    { "colSeq":1, "vendor":"INTEL", "chipNm":"SPR", "chipDt":"2022.Q4", ... },
    { "colSeq":2, "vendor":"INTEL", "chipNm":"EMR", "chipDt":"2023.Q4", ... },
    ...
  ],
  "rows": [                                  ← CHIPSET_ROW + CHIPSET_CELL 조인
    {
      "rowSeq":1, "dimm":"DDR5", "product":"A", ...,
      "cells": [
        { "cellSeq":1, "rowSeq":1, "colSeq":1, "cellValue":"2024.03", "bgColor":"#00B050" },
        { "cellSeq":2, "rowSeq":1, "colSeq":2, "cellValue":"",        "bgColor":null },
        ...
      ]
    },
    ...
  ]
}
```

### 8-2. 화면 렌더링 매핑

```
vendors  → 벤더 그룹 헤더 행 (colspan = 해당 벤더의 chipCols 수)
chipCols → 칩셋 이름/날짜 헤더 행
rows     → 데이터 행
  └─ cells → row.cells에서 col.colSeq와 일치하는 cell을 찾아 셀값/색상 표시
```

---

## 9. 히스토리 관리 전략

### 9-1. 메인 테이블: 덮어쓰기 방식

```
새 Server.xlsx 업로드 시:
  ┌─ 기존 SERVER 데이터 전체 삭제 ─┐
  │  DELETE CHIPSET_CELL            │ (SERVER와 연결된 것만)
  │  DELETE CHIPSET_ROW             │
  │  DELETE CHIPSET_CHIP_COL        │
  │  DELETE CHIPSET_UPLOAD          │
  └─────────────────────────────────┘
  ↓
  새 데이터 INSERT
```

→ 메인 테이블에는 항상 파일타입별 최신 1건만 존재합니다.  
→ 4가지 타입은 서로 독립적이어서 Server 업로드가 Client 데이터에 영향 없습니다.

### 9-2. 히스토리 테이블: 누적 방식

```
새 Server.xlsx 업로드 시:
  메인 테이블 삭제/INSERT 후 추가로:
  ├─ CHIPSET_UPLOAD_H   에 INSERT (삭제 없이 누적)
  ├─ CHIPSET_CHIP_COL_H 에 INSERT
  ├─ CHIPSET_ROW_H      에 INSERT
  └─ CHIPSET_CELL_H     에 INSERT (uploadSeq 함께 저장)
```

→ 과거 버전은 `GET /api/chipset/history?type=SERVER` 로 목록 조회  
→ 특정 버전은 `GET /api/chipset/history/{uploadSeq}` 로 상세 조회

---

## 10. 시퀀스 목록

| 시퀀스명 | 용도 |
|----------|------|
| `SQ_CHIPSET_UPLOAD` | CHIPSET_UPLOAD.UPLOAD_SEQ 채번 |
| `SQ_CHIPSET_CHIP_COL` | CHIPSET_CHIP_COL.COL_SEQ 채번 |
| `SQ_CHIPSET_ROW` | CHIPSET_ROW.ROW_SEQ 채번 |
| `SQ_CHIPSET_CELL` | CHIPSET_CELL.CELL_SEQ 채번 |
| `SQ_CHIPSET_UPLOAD_H` | CHIPSET_UPLOAD_H.UPLOAD_H_SEQ 채번 |
| `SQ_CHIPSET_CHIP_COL_H` | CHIPSET_CHIP_COL_H.COL_H_SEQ 채번 |
| `SQ_CHIPSET_ROW_H` | CHIPSET_ROW_H.ROW_H_SEQ 채번 |
| `SQ_CHIPSET_CELL_H` | CHIPSET_CELL_H.CELL_H_SEQ 채번 |
| `SQ_RAWDATA_ROW` | RAWDATA_ROW.RAWDATA_ROW_SEQ 채번 |
| `SQ_RAWDATA_ROW_H` | RAWDATA_ROW_H.RAWDATA_ROW_H_SEQ 채번 |

---

## 11. 데이터 예시 (Server.xlsx 1행 업로드 시)

Excel 입력:

```
벤더행:  [빈칸×6] | Intel(병합 3칸) |
칩명행:  [빈칸×6] | SPR  | EMR  | GNR  |
날짜행:  [빈칸×6] |22.Q4 |23.Q4 |24.H1 |
데이터:  DDR5 | Samsung | v1.0 | 16GB | x4 | 4800 | 2024.03(녹색) | | 2024.11(노랑) |
```

DB INSERT 결과:

```sql
-- CHIPSET_UPLOAD
(UPLOAD_SEQ=1, FILE_NM='Server.xlsx', FILE_TYPE='SERVER', ROW_COUNT=1, COL_COUNT=3)

-- CHIPSET_CHIP_COL
(COL_SEQ=1, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=6, CHIP_NM='SPR', CHIP_DT='22.Q4', SORT_ORDER=0)
(COL_SEQ=2, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=7, CHIP_NM='EMR', CHIP_DT='23.Q4', SORT_ORDER=1)
(COL_SEQ=3, UPLOAD_SEQ=1, VENDOR='INTEL', COL_IDX=8, CHIP_NM='GNR', CHIP_DT='24.H1', SORT_ORDER=2)

-- CHIPSET_ROW
(ROW_SEQ=1, UPLOAD_SEQ=1, DIMM='DDR5', PRODUCT='Samsung', VER='v1.0',
            DENSITY='16GB', ORG='x4', SPEED='4800', SORT_ORDER=0)

-- CHIPSET_CELL
(CELL_SEQ=1, ROW_SEQ=1, COL_SEQ=1, CELL_VALUE='2024.03', BG_COLOR='#00B050')  ← SPR: 녹색
(CELL_SEQ=2, ROW_SEQ=1, COL_SEQ=2, CELL_VALUE='',        BG_COLOR=null)        ← EMR: 없음
(CELL_SEQ=3, ROW_SEQ=1, COL_SEQ=3, CELL_VALUE='2024.11', BG_COLOR='#FFFF00')  ← GNR: 노랑
```

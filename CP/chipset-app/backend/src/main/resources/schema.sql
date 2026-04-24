-- ============================================================
-- Chipset App DB Schema (Oracle DDL)
-- v1.2 : 2026-04-25
-- 변경: CHIPSET_ROW·RAWDATA_ROW 제거, CHIPSET_CELL로 통합
--       CHIPSET_RAWDATA_COL 신규, COL_TYPE 구분자 방식 채택
--       CHIPSET_CELL_COL → CHIPSET_CHIP_COL 개명
-- ============================================================

-- ── 시퀀스 ──────────────────────────────────────────────────────────────────

-- 메인 테이블용
CREATE SEQUENCE SQ_CHIPSET_UPLOAD        START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_SPEC_COL      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_RAWDATA_COL   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_CELL          START WITH 1 INCREMENT BY 1;

-- 히스토리 테이블용
CREATE SEQUENCE SQ_CHIPSET_UPLOAD_H      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_SPEC_COL_H    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL_H    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_RAWDATA_COL_H START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SQ_CHIPSET_CELL_H        START WITH 1 INCREMENT BY 1;


-- ── 메인: 업로드 메타 ────────────────────────────────────────────────────────
-- FILE_TYPE: 'SERVER' | 'CLIENT' | 'MOBILE' | 'RAW_DATA'
-- 파일타입별 최신 1건만 유지 (업로드 시 기존 삭제 후 재INSERT)
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ  NUMBER        NOT NULL,
    FILE_NM     VARCHAR2(255) NOT NULL,
    FILE_TYPE   VARCHAR2(20)  DEFAULT 'SERVER' NOT NULL,
    UPLOAD_DT   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ROW_COUNT   NUMBER        DEFAULT 0,
    COL_COUNT   NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);


-- ── 메인: Server/Client/Mobile 왼쪽 스펙 컬럼 헤더 ─────────────────────────
-- 역할: Matrix형 Excel의 좌측 고정 컬럼 헤더명 저장 (예: DIMM, Product, PKG)
-- COL_IDX: 1-based (1=첫 번째 스펙 컬럼, 최대 10)
CREATE TABLE CHIPSET_SPEC_COL (
    COL_SEQ    NUMBER        NOT NULL,
    UPLOAD_SEQ NUMBER        NOT NULL,
    COL_IDX    NUMBER        NOT NULL,
    COL_NM     VARCHAR2(100) NOT NULL,
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_SPEC_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_SPEC_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);


-- ── 메인: Server/Client/Mobile 오른쪽 칩셋 컬럼 헤더 ──────────────────────
-- 역할: Matrix형 Excel의 우측 칩셋 컬럼 헤더 저장 (벤더, 칩명, 출시일)
-- 구 테이블명: CHIPSET_CELL_COL → CHIPSET_CHIP_COL 으로 개명 (v1.2)
CREATE TABLE CHIPSET_CHIP_COL (
    COL_SEQ    NUMBER        NOT NULL,
    UPLOAD_SEQ NUMBER        NOT NULL,
    VENDOR     VARCHAR2(100) NOT NULL,
    COL_IDX    NUMBER        NOT NULL,
    CHIP_NM    VARCHAR2(200) NOT NULL,
    CHIP_DT    VARCHAR2(50),
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_CHIP_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_CHIP_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);


-- ── 메인: RawData 컬럼 헤더 ────────────────────────────────────────────────
-- 역할: Tracking형 Excel(Raw_Data.xlsx)의 컬럼 헤더명 저장
--       (예: Company, Seg, Chipset, SoC CS, Part Number, ...)
-- COL_IDX: 0-based Excel 컬럼 인덱스
CREATE TABLE CHIPSET_RAWDATA_COL (
    COL_SEQ    NUMBER        NOT NULL,
    UPLOAD_SEQ NUMBER        NOT NULL,
    COL_IDX    NUMBER        NOT NULL,
    COL_NM     VARCHAR2(200) NOT NULL,
    SORT_ORDER NUMBER        DEFAULT 0,
    CONSTRAINT PK_CHIPSET_RAWDATA_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_RAWDATA_COL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);


-- ── 메인: 모든 셀 데이터 (Server/Client/Mobile/RawData 공통) ────────────────
-- 역할: 스펙 셀, 칩셋 셀, RawData 셀을 하나의 테이블에 통합 저장
-- ROW_IDX : 업로드 내 0-based 행 번호 (구 CHIPSET_ROW.ROW_SEQ 대체)
-- COL_TYPE: 셀이 속한 컬럼 테이블 구분자
--           'SPEC'    → CHIPSET_SPEC_COL.COL_SEQ 참조
--           'CHIP'    → CHIPSET_CHIP_COL.COL_SEQ 참조
--           'RAWDATA' → CHIPSET_RAWDATA_COL.COL_SEQ 참조
-- COL_SEQ : COL_TYPE에 해당하는 테이블의 COL_SEQ 값
--           (무결성은 애플리케이션 레이어에서 보장, DB FK 미설정)
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ   NUMBER        NOT NULL,
    UPLOAD_SEQ NUMBER        NOT NULL,
    ROW_IDX    NUMBER        NOT NULL,
    COL_TYPE   VARCHAR2(20)  NOT NULL,
    COL_SEQ    NUMBER        NOT NULL,
    CELL_VALUE VARCHAR2(200),
    BG_COLOR   VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL PRIMARY KEY (CELL_SEQ),
    CONSTRAINT FK_CELL_UPLOAD FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ),
    CONSTRAINT CHK_CELL_COL_TYPE CHECK (COL_TYPE IN ('SPEC', 'CHIP', 'RAWDATA'))
);


-- ── 히스토리: 업로드 ────────────────────────────────────────────────────────
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


-- ── 히스토리: 스펙 컬럼 헤더 ────────────────────────────────────────────────
CREATE TABLE CHIPSET_SPEC_COL_H (
    SPEC_COL_H_SEQ NUMBER        NOT NULL,
    COL_SEQ        NUMBER        NOT NULL,
    UPLOAD_SEQ     NUMBER        NOT NULL,
    COL_IDX        NUMBER,
    COL_NM         VARCHAR2(100),
    SORT_ORDER     NUMBER,
    CONSTRAINT PK_CHIPSET_SPEC_COL_H PRIMARY KEY (SPEC_COL_H_SEQ)
);


-- ── 히스토리: 칩셋 컬럼 헤더 ────────────────────────────────────────────────
-- 구 테이블명: CHIPSET_CELL_COL_H → CHIPSET_CHIP_COL_H (v1.2)
CREATE TABLE CHIPSET_CHIP_COL_H (
    CHIP_COL_H_SEQ NUMBER        NOT NULL,
    COL_SEQ        NUMBER        NOT NULL,
    UPLOAD_SEQ     NUMBER        NOT NULL,
    VENDOR         VARCHAR2(100),
    COL_IDX        NUMBER,
    CHIP_NM        VARCHAR2(200),
    CHIP_DT        VARCHAR2(50),
    SORT_ORDER     NUMBER,
    CONSTRAINT PK_CHIPSET_CHIP_COL_H PRIMARY KEY (CHIP_COL_H_SEQ)
);


-- ── 히스토리: RawData 컬럼 헤더 ─────────────────────────────────────────────
CREATE TABLE CHIPSET_RAWDATA_COL_H (
    RAWDATA_COL_H_SEQ NUMBER        NOT NULL,
    COL_SEQ           NUMBER        NOT NULL,
    UPLOAD_SEQ        NUMBER        NOT NULL,
    COL_IDX           NUMBER,
    COL_NM            VARCHAR2(200),
    SORT_ORDER        NUMBER,
    CONSTRAINT PK_CHIPSET_RAWDATA_COL_H PRIMARY KEY (RAWDATA_COL_H_SEQ)
);


-- ── 히스토리: 셀 값 ─────────────────────────────────────────────────────────
CREATE TABLE CHIPSET_CELL_H (
    CELL_H_SEQ NUMBER        NOT NULL,
    CELL_SEQ   NUMBER        NOT NULL,
    UPLOAD_SEQ NUMBER        NOT NULL,
    ROW_IDX    NUMBER,
    COL_TYPE   VARCHAR2(20),
    COL_SEQ    NUMBER,
    CELL_VALUE VARCHAR2(200),
    BG_COLOR   VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL_H PRIMARY KEY (CELL_H_SEQ)
);

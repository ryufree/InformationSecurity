# Chipset Validation Matrix — 시스템 설계 문서 v2

> 작성일: 2026-04-22 (v2 업데이트)  
> 범위: Vue 3 (chipset-app) + Spring Boot/MyBatis/Gradle (chipset-app/backend) + Oracle 21c XE  
> 예제 파일: `src/data/ChipsetValidation_Fixed.xlsx`  
> Git Repo: `D:\GitKraken\InformationSecurity\CP\chipset-app`

---

## 현재 진행 상태 (2026-04-22 기준)

> 새 컴퓨터에서 이어 작업할 때 이 표를 먼저 확인하세요.

### ✅ 완료된 파일 목록

#### Vue 프론트엔드 (`chipset-app/src/`)

| 파일 | 설명 |
|------|------|
| `src/App.vue` | 홈 ↔ 컴포넌트 전환 라우터, apps 배열에 2개 등록 |
| `src/main.js` | Tachyons CSS import 완료 (`import 'tachyons/css/tachyons.min.css'`) |
| `src/components/HomeScreen.vue` | 런처 화면 — 컴포넌트 카드 목록 |
| `src/components/ChipsetValidation.vue` | 로컬 Excel 직접 파싱 (DB 없음) |
| `src/components/ChipsetUploadView.vue` | **★ DB 연동 화면** — SERVER/CLIENT/MOBILE/RAW_DATA 탭, 업로드/조회/히스토리 |
| `docs/design.md` | 이 설계 문서 |

#### Java 백엔드 (`chipset-app/backend/`)

| 파일 | 설명 |
|------|------|
| `backend/build.gradle.kts` | Gradle 빌드 설정 (H2, POI, MyBatis, Lombok) |
| `backend/settings.gradle.kts` | 프로젝트명: chipset-backend |
| `backend/src/main/resources/application.yml` | H2 기본, Oracle 전환 주석 포함 |
| `backend/src/main/resources/schema.sql` | H2용 DDL (4파일타입 + RAW_DATA 테이블, 히스토리 포함) |
| `backend/src/main/resources/mapper/ChipsetMapper.xml` | MyBatis SQL (메인+히스토리, 파일타입별 DELETE/SELECT) |
| `backend/src/main/java/com/chipset/ChipsetApplication.java` | Spring Boot 진입점 |
| `backend/src/main/java/com/chipset/config/WebConfig.java` | CORS (5173 허용) |
| `backend/src/main/java/com/chipset/controller/ChipsetController.java` | REST API 6개 (upload, matrix, rawdata, history, history/{seq}, rawdata/history/{seq}) |
| `backend/src/main/java/com/chipset/service/ChipsetService.java` | 업로드/조회 비즈니스 로직 (4가지 파일타입 대응) |
| `backend/src/main/java/com/chipset/mapper/ChipsetMapper.java` | MyBatis 인터페이스 |
| `backend/src/main/java/com/chipset/util/ChipsetExcelParser.java` | POI 파서 — SERVER/CLIENT/MOBILE/RAW_DATA 자동 감지 |
| `backend/src/main/java/com/chipset/model/ChipsetUpload.java` | 업로드 메타 모델 (FILE_TYPE 포함) |
| `backend/src/main/java/com/chipset/model/ChipsetChipCol.java` | 칩 컬럼 모델 |
| `backend/src/main/java/com/chipset/model/ChipsetRow.java` | 스펙 행 모델 (Mobile: dimm=PKG, org=P/N, ver=CodeName 재사용) |
| `backend/src/main/java/com/chipset/model/ChipsetCell.java` | 셀 값 모델 |
| `backend/src/main/java/com/chipset/model/UploadResult.java` | 업로드 응답 모델 (fileType 포함) |
| `backend/src/main/java/com/chipset/model/MatrixResponse.java` | 매트릭스 조회 응답 모델 |
| `backend/src/main/java/com/chipset/model/RawDataRow.java` | Raw_Data 행 모델 (val1~3 세트) |
| `backend/src/main/java/com/chipset/model/RawDataResponse.java` | Raw_Data 조회 응답 모델 |

#### npm 패키지 (`chipset-app/`)

| 패키지 | 용도 | 설치 상태 |
|--------|------|-----------|
| `vue` | 프레임워크 | ✅ |
| `vite` | 빌드 도구 | ✅ |
| `xlsx` | 로컬 Excel 파싱 | ✅ |
| `axios` | HTTP 클라이언트 | ✅ |
| `tachyons` | CSS 유틸리티 | ✅ |

---

### ⬜ 남은 작업

| 순서 | 항목 | 비고 |
|------|------|------|
| ~~1~~ | ~~`src/main.js`에 tachyons import 추가~~ | ✅ 완료 |
| ~~2~~ | ~~멀티 파일타입 지원 (14번 섹션 분석 → 구현)~~ | ✅ 완료 (SERVER/CLIENT/MOBILE/RAW_DATA) |
| 3 | IntelliJ에서 `backend/` Gradle import 후 백엔드 실행 확인 | `http://localhost:8080/h2-console` |
| 4 | 통합 테스트 (4가지 Excel 각각 업로드 → H2 → 화면 출력) | Server/Client/Mobile/Raw_Data.xlsx 사용 |
| 5 | Oracle 21c XE 설치 (시간 될 때) | 이 문서 4번 섹션 |
| 6 | Oracle DDL 실행 후 application.yml 전환 | 아래 "Oracle 전환" 참고 |

---

## 새 컴퓨터에서 시작하기

### Step 0. 필수 소프트웨어 설치 확인

```
□ Git
□ Node.js 18+        https://nodejs.org
□ JDK 17             https://adoptium.net  (Eclipse Temurin 17 LTS 권장)
□ IntelliJ IDEA      https://www.jetbrains.com/idea/  (Community 무료)
□ DBeaver Community  https://dbeaver.io    (Oracle 연결 시 필요)
□ Oracle 21c XE      이 문서 4번 섹션 참고  (나중에 설치해도 됨)
```

설치 확인:
```bash
java -version    # openjdk version "17.x.x" 이상
node -v          # v18 이상
npm -v           # 9 이상
git --version    # 아무 버전
```

---

### Step 1. 프로젝트 준비 및 Vue 실행

```bash
# 저장소가 이미 로컬에 있으면 pull만
cd D:\GitKraken\InformationSecurity\CP\chipset-app

git pull

# Vue 의존성 설치 (처음 한 번만)
npm install
```

`src/main.js`에 tachyons import가 없으면 추가:
```js
// src/main.js 맨 위에 추가
import 'tachyons/css/tachyons.min.css'
```

Vue 개발 서버 실행:
```bash
npm run dev
# → http://localhost:5173
```

HomeScreen에서 두 카드 확인:
- `ChipsetValidation.vue` → DB 없이 로컬 Excel 파싱
- `ChipsetUploadView.vue` → DB 연동 (백엔드 필요)

---

### Step 2. 백엔드 실행 (H2 인메모리 DB)

> Oracle 없이도 H2로 바로 실행 가능합니다.

**IntelliJ에서 열기 (권장):**
```
1. IntelliJ IDEA 실행
2. File → Open → D:\GitKraken\InformationSecurity\CP\chipset-app\backend 선택
3. "Open as Gradle project" 선택 → 의존성 자동 다운로드 (최초 수분 소요)
4. ChipsetApplication.java 열기 → main() 옆 ▶ 클릭 → Run
```

**또는 Gradle CLI (Gradle 전역 설치 필요):**
```bash
cd D:\GitKraken\InformationSecurity\CP\chipset-app\backend

# Gradle wrapper 생성 (최초 1회, Gradle이 전역 설치된 경우)
gradle wrapper

# 실행
gradlew.bat bootRun       # Windows
./gradlew bootRun         # Mac/Linux
```

**서버 시작 확인:**
```
콘솔에 아래 메시지가 나오면 정상:
  Started ChipsetApplication in X.XXX seconds
  Tomcat started on port(s): 8080
```

**schema.sql 자동 실행 확인:**
```
H2 콘솔: http://localhost:8080/h2-console
  JDBC URL:  jdbc:h2:mem:chipsetdb
  User Name: sa
  Password:  (비워둠)
  → Connect → 테이블 목록에서 CHIPSET_UPLOAD 등 8개 테이블 확인
```

---

### Step 3. 통합 테스트

```
1. Vue: http://localhost:5173 → ChipsetUploadView.vue 카드 클릭
2. "↑ XLSX 선택" → src/data/ChipsetValidation_Fixed.xlsx 선택
3. "→ DB 저장" 클릭
4. 성공 메시지 확인 (예: "업로드 완료 (행: 120, 칩 컬럼: 15)")
5. 매트릭스 테이블 렌더링 확인
6. H2 콘솔에서 데이터 직접 확인:
   SELECT * FROM CHIPSET_ROW;
   SELECT * FROM CHIPSET_UPLOAD_H;
```

---

### Step 4. Oracle 전환 (Oracle 설치 후)

Oracle 설치 및 유저 생성은 이 문서 **4번 섹션** 참고.

DBeaver로 chipset 유저 접속 후 **이 문서 8번 섹션 DDL** 실행.

`backend/src/main/resources/application.yml` 수정:

```yaml
spring:
  # ── H2 비활성화 (아래 블록 전체 주석 처리) ──
  # datasource:
  #   driver-class-name: org.h2.Driver
  #   url: jdbc:h2:mem:chipsetdb;MODE=Oracle;...
  #   username: sa
  #   password:
  # h2:
  #   console:
  #     enabled: true
  # sql:
  #   init:
  #     mode: always

  # ── Oracle 활성화 (아래 블록 주석 해제) ──
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@localhost:1521/XEPDB1
    username: chipset
    password: chipset123
  sql:
    init:
      mode: never     # Oracle은 DBeaver에서 DDL 직접 실행
```

`build.gradle.kts`에서 Oracle JDBC 주석 해제:
```kotlin
// runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.11.0.0")
↓
runtimeOnly("com.oracle.database.jdbc:ojdbc11:21.11.0.0")
```

백엔드 재시작하면 Oracle로 자동 전환됩니다.

---

### 포트 및 접속 정보 요약

| 서비스 | URL / 접속 정보 | 비고 |
|--------|----------------|------|
| Vue 프론트엔드 | `http://localhost:5173` | `npm run dev` |
| Spring Boot API | `http://localhost:8080` | IntelliJ 또는 gradlew |
| H2 웹 콘솔 | `http://localhost:8080/h2-console` | 개발 중 DB 확인 |
| H2 JDBC URL | `jdbc:h2:mem:chipsetdb` | User: sa / PW: 없음 |
| Oracle DB | `localhost:1521/XEPDB1` | 나중에 설치 |
| Oracle 유저 | `chipset` / `chipset123` | 나중에 생성 |
| 예제 Excel | `src/data/ChipsetValidation_Fixed.xlsx` | 테스트용 |

---

### REST API 빠른 테스트 (curl / 브라우저)

```bash
# 매트릭스 조회 (데이터 있을 때)
curl http://localhost:8080/api/chipset/matrix

# 히스토리 목록
curl http://localhost:8080/api/chipset/history

# 특정 히스토리 버전
curl http://localhost:8080/api/chipset/history/1

# 업로드 (파일 경로는 실제 경로로)
curl -X POST http://localhost:8080/api/chipset/upload \
     -F "file=@D:/GitKraken/InformationSecurity/CP/chipset-app/src/data/ChipsetValidation_Fixed.xlsx"
```

---

### 알려진 주의사항

| 항목 | 내용 |
|------|------|
| H2 인메모리 | 서버 재시작 시 데이터 초기화됨 (테스트용). Oracle 전환 후 영구 저장 |
| FETCH FIRST | `selectLatestUpload`의 `FETCH FIRST 1 ROWS ONLY` — H2 Oracle mode, Oracle 12c+ 모두 지원 |
| 벤더 동적 감지 | Excel 병합 셀로 벤더 탐지. 병합 없는 파일은 헤더 행 스캔으로 폴백 |
| 메인 테이블 덮어쓰기 | 업로드마다 메인 테이블 전체 삭제 후 재삽입. 히스토리 테이블에는 누적 보관 |
| CORS | `WebConfig.java`에서 `http://localhost:5173` 허용. 포트 변경 시 수정 필요 |

# 또는 IntelliJ에서: ChipsetApplication.java → main() → Run
```

서버 시작 확인:
```
Started ChipsetApplication in X.XXX seconds
Tomcat started on port(s): 8080
```

---

### Step 7. 전체 동작 확인

```
1. Oracle 서비스 실행 중  (services.msc → OracleServiceXE)
2. 백엔드 실행 중         (localhost:8080)
3. Vue 개발 서버 실행 중  (localhost:5173)

브라우저 → http://localhost:5173
→ HomeScreen에서 "ChipsetUploadView.vue" 카드 클릭
→ UPLOAD XLSX → ChipsetValidation_Fixed.xlsx 선택
→ "SEND TO DB" 클릭
→ 업로드 완료 메시지 확인
→ 매트릭스 테이블 렌더링 확인
```

---

### 포트 및 접속 정보 요약

| 서비스 | URL / 접속 정보 |
|--------|----------------|
| Vue 프론트엔드 | `http://localhost:5173` |
| Spring Boot API | `http://localhost:8080` |
| Oracle DB | `localhost:1521/XEPDB1` |
| DB 유저 | `chipset` / `chipset123` |
| 예제 Excel | `src/data/ChipsetValidation_Fixed.xlsx` |

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [확정 사항 정리](#2-확정-사항-정리)
3. [기술 스택](#3-기술-스택)
4. [Oracle 21c XE 로컬 환경 구축 (단계별 가이드)](#4-oracle-21c-xe-로컬-환경-구축)
5. [시스템 아키텍처](#5-시스템-아키텍처)
6. [프로젝트 디렉토리 구조](#6-프로젝트-디렉토리-구조)
7. [Excel 파일 구조 분석](#7-excel-파일-구조-분석)
8. [DB 스키마 설계 (메인 + 히스토리)](#8-db-스키마-설계)
9. [API 설계](#9-api-설계)
10. [Java 핵심 코드](#10-java-핵심-코드)
11. [MyBatis Mapper XML](#11-mybatis-mapper-xml)
12. [Vue 프론트엔드](#12-vue-프론트엔드)
13. [제공 코드 버그 목록](#13-제공-코드-버그-목록)
14. [멀티 파일 타입 설계 분석](#14-멀티-파일-타입-설계-분석)

---

## 1. 프로젝트 개요

```
[Vue 3 + Tachyons]
  XLSX 파일 선택 → 업로드
        ↓  multipart/form-data  POST /api/chipset/upload
[Spring Boot REST (backend/)]
  Apache POI 스트리밍 파싱
        ↓
[Oracle 21c XE  localhost:1521/XEPDB1]
  메인 테이블 (최신 상태, 덮어쓰기)
  히스토리 테이블 (누적 보관)
        ↓  GET /api/chipset/matrix
[Vue 3 + Tachyons Grid]
  동적 벤더 그룹 렌더링
```

---

## 2. 확정 사항 정리

| 항목 | 결정 내용 |
|------|-----------|
| Oracle 접속 | **Service Name 방식** (`XEPDB1`) 사용. 설치 가이드 4번 참고 |
| 인증 | 없음 (테스트 목적) |
| 다중 업로드 | 메인 테이블은 **덮어쓰기** (DELETE → INSERT), 히스토리 테이블은 **누적 INSERT** |
| 벤더 구조 | **동적** — Intel/AMD 고정 아님. 향후 추가 벤더 자동 지원 |
| axios | 설치 완료 |
| 백엔드 위치 | `chipset-app/backend/` (동일 repo, 하위 폴더) |

---

## 3. 기술 스택

| 영역 | 기술 | 버전 |
|------|------|------|
| Frontend | Vue 3 + Vite | 3.x / 8.x |
| CSS | Tachyons | 4.x |
| HTTP Client | axios | 1.x |
| Backend | Spring Boot | 3.2.x |
| Build | Gradle (Kotlin DSL) | 8.x |
| ORM | MyBatis Spring Boot Starter | 3.0.x |
| Excel 파싱 | Apache POI (XSSF Streaming) | 5.2.x |
| File Upload | Apache Commons FileUpload2 | 2.0.x |
| DB | Oracle Database 21c XE | 21.x |
| JDBC | ojdbc11 | 21.x |
| Java | OpenJDK | 17 |

---

## 4. Oracle 21c XE 로컬 환경 구축

### 4-1. SID vs Service Name 개념 정리

Oracle 21c XE를 설치하면 두 가지 접속 방식이 생깁니다.

```
Oracle 21c XE 설치 후 구조:
┌─────────────────────────────────────┐
│  CDB (Container DB)                  │
│  SID = XE                            │  ← 레거시 방식, 루트 컨테이너
│  ┌───────────────────────────────┐   │
│  │  PDB (Pluggable DB)           │   │
│  │  Service Name = XEPDB1        │   │  ← 권장 방식, 개발용
│  └───────────────────────────────┘   │
└─────────────────────────────────────┘
```

| 구분 | SID 방식 | Service Name 방식 (권장) |
|------|----------|--------------------------|
| 접속 대상 | CDB$ROOT (컨테이너 루트) | XEPDB1 (플러거블 DB) |
| JDBC URL | `jdbc:oracle:thin:@localhost:1521:XE` | `jdbc:oracle:thin:@localhost:1521/XEPDB1` |
| 구분자 | `:` (콜론) | `/` (슬래시) |
| 사용 권장 | X (레거시) | O (신규 개발 표준) |

**→ 이 프로젝트는 Service Name = `XEPDB1` 방식을 사용합니다.**

---

### 4-2. Oracle 21c XE 다운로드 및 설치

**Step 1. 다운로드**

```
URL: https://www.oracle.com/database/technologies/xe-downloads.html
파일: OracleXE213_Win64.zip  (약 1.6 GB)
※ Oracle 계정 필요 (무료 가입)
```

**Step 2. 설치 실행**

```
1. OracleXE213_Win64.zip 압축 해제
2. setup.exe 실행 (관리자 권한)
3. 설치 경로: C:\app\{사용자명}\product\21c\dbhome  (기본값 그대로)
4. 비밀번호 설정 화면:
   - SYS 비밀번호: Oracle21c!  (특수문자 포함 8자 이상)
   - SYSTEM 비밀번호: Oracle21c!  (동일하게)
   - PDBADMIN 비밀번호: Oracle21c!  (동일하게)
   ※ 모두 같은 비밀번호로 설정해도 무방 (테스트 환경)
5. 포트: 1521 (기본값 그대로)
6. Install 클릭 → 완료 대기 (5~10분)
```

**Step 3. 설치 확인**

```cmd
# 방법 A: Windows 서비스 확인
Win+R → services.msc → 아래 두 서비스 상태 확인
  ✅ OracleServiceXE      → 실행 중
  ✅ OracleXETNSListener  → 실행 중

# 방법 B: CMD에서 리스너 상태 확인
lsnrctl status
# 출력에 "XEPDB1" 서비스가 보이면 정상
```

---

### 4-3. 개발용 사용자 생성

Oracle sqlplus로 접속하여 `chipset` 유저를 생성합니다.

**sqlplus 위치 찾기:**
```cmd
# 일반적으로 아래 경로에 있음
C:\app\{사용자명}\product\21c\dbhome\bin\sqlplus.exe

# PATH 추가 (선택, 한 번만):
# 시스템 환경변수 PATH에 위 bin 경로 추가
```

**XEPDB1에 접속 후 유저 생성:**
```sql
-- CMD에서 실행
sqlplus sys/Oracle21c!@localhost:1521/XEPDB1 as sysdba

-- sqlplus 프롬프트에서:
CREATE USER chipset IDENTIFIED BY chipset123;
GRANT CONNECT, RESOURCE TO chipset;
GRANT CREATE SESSION TO chipset;
GRANT UNLIMITED TABLESPACE TO chipset;
EXIT;
```

> **트러블슈팅**: `ORA-12514: TNS:listener does not currently know of service requested` 오류 시  
> → `lsnrctl stop` 후 `lsnrctl start` 재시작

---

### 4-4. DBeaver 연결 설정

**신규 연결 생성:**

```
1. DBeaver 실행 → 데이터베이스 → 새 연결 → Oracle
2. 아래와 같이 입력:

   ┌──────────────────────────────────────────┐
   │ Connection Type: [ Service Name ▼ ]       │  ← 반드시 Service Name 선택
   │ Host:            localhost                │
   │ Port:            1521                     │
   │ Database:        XEPDB1                   │  ← 서비스명 입력
   │ Username:        chipset                  │
   │ Password:        chipset123               │
   └──────────────────────────────────────────┘

3. "Test Connection" 클릭 → "Connected" 확인
4. 완료
```

> **주의**: Connection Type이 기본 `SID`로 되어 있으면 반드시 `Service Name`으로 변경해야 함

---

### 4-5. 테이블 DDL 실행

DBeaver에서 `chipset` 유저로 연결 후 8번 섹션의 DDL 스크립트를 실행합니다.

```
DBeaver → chipset 연결 → SQL 편집기 → DDL 붙여넣기 → Ctrl+Enter (또는 전체 실행)
```

---

### 4-6. Docker 대안 (Oracle 설치 없이 빠르게)

```bash
docker run -d \
  --name oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=Oracle21c! \
  gvenzl/oracle-xe:21-slim

# 준비 완료까지 약 2분 대기
docker logs -f oracle-xe
# "DATABASE IS READY TO USE!" 메시지 확인 후 접속

# DBeaver 접속 정보:
# Host: localhost, Port: 1521, Service Name: XEPDB1
# User: system, Password: Oracle21c!
```

---

## 5. 시스템 아키텍처

```
┌──────────────────────────────────────────────────────────────────┐
│  chipset-app/  (Git Repo Root)                                    │
│                                                                    │
│  ┌─────────────────────────────┐  ┌──────────────────────────┐   │
│  │  frontend  (src/)            │  │  backend/                │   │
│  │  Vue 3 + Vite + Tachyons    │  │  Spring Boot 3 + Gradle  │   │
│  │  port: 5173                 │  │  port: 8080              │   │
│  │                             │  │                          │   │
│  │  ChipsetUploadView.vue      │  │  ChipsetController       │   │
│  │  ① 파일 선택               │  │  ChipsetService          │   │
│  │  ② POST /api/chipset/upload │→│  ChipsetExcelParser      │   │
│  │  ③ GET  /api/chipset/matrix │←│  ChipsetMapper           │   │
│  │  ④ Tachyons Grid 렌더링     │  │  MyBatis XML             │   │
│  └─────────────────────────────┘  └───────────┬──────────────┘   │
└───────────────────────────────────────────────┼──────────────────┘
                                                │ JDBC (ojdbc11)
                                                │ localhost:1521/XEPDB1
                              ┌─────────────────▼───────────────────┐
                              │  Oracle 21c XE                       │
                              │                                      │
                              │  메인 테이블 (최신 상태)              │
                              │  CHIPSET_UPLOAD                      │
                              │  CHIPSET_CHIP_COL                    │
                              │  CHIPSET_ROW                         │
                              │  CHIPSET_CELL                        │
                              │                                      │
                              │  히스토리 테이블 (누적)               │
                              │  CHIPSET_UPLOAD_H                    │
                              │  CHIPSET_CHIP_COL_H                  │
                              │  CHIPSET_ROW_H                       │
                              │  CHIPSET_CELL_H                      │
                              └─────────────────────────────────────┘
```

---

## 6. 프로젝트 디렉토리 구조

```
chipset-app/                          ← Git Repo Root
│
├── backend/                          ← ★ NEW: Spring Boot 프로젝트
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew
│   ├── gradlew.bat
│   └── src/
│       └── main/
│           ├── java/com/chipset/
│           │   ├── ChipsetApplication.java
│           │   ├── config/
│           │   │   └── WebConfig.java
│           │   ├── controller/
│           │   │   └── ChipsetController.java
│           │   ├── service/
│           │   │   └── ChipsetService.java
│           │   ├── mapper/
│           │   │   └── ChipsetMapper.java
│           │   ├── model/
│           │   │   ├── ChipsetUpload.java
│           │   │   ├── ChipsetChipCol.java
│           │   │   ├── ChipsetRow.java
│           │   │   ├── ChipsetCell.java
│           │   │   ├── MatrixResponse.java
│           │   │   └── UploadResult.java
│           │   └── util/
│           │       └── ChipsetExcelParser.java
│           └── resources/
│               ├── application.yml
│               └── mapper/
│                   └── ChipsetMapper.xml
│
├── src/                              ← Vue 3 소스 (기존)
│   ├── components/
│   │   ├── HomeScreen.vue
│   │   ├── ChipsetValidation.vue
│   │   └── ChipsetUploadView.vue     ← ★ NEW
│   ├── docs/
│   │   └── design.md
│   └── main.js
│
├── index.html
├── package.json                      ← Vue 의존성
├── vite.config.js
└── .gitignore
```

---

## 7. Excel 파일 구조 분석

`ChipsetValidation_Fixed.xlsx` 기준 (동적 벤더 구조):

```
Row 0: [DIMM][Product(Ver.)][Ver.][Density][Org][Speed]  ← 스펙 헤더 (병합)
       [Intel ←───colspan───→][AMD ←─colspan─→][기타벤더...]  ← 벤더 그룹
Row 1:  ↑ 병합 계속              [chip1][chip2] [chip1]...   ← 칩 이름 행
Row 2:  ↑ 병합 계속              [24'1] [24'3]  [25'2]...   ← 출시일 행
Row 3+: [DDR5][SKH A-die][A][16Gb][x8][5600][OK][ ][25'1]   ← 데이터 행
```

**파싱 규칙**
- 스펙 고정 컬럼: col 0~5
- `ws['!merges']`로 벤더 그룹 범위 자동 탐지 (어떤 벤더명도 지원)
- 스펙 컬럼 이후의 모든 병합 셀 헤더 = 벤더 그룹으로 처리
- `headerRowIdx + 1` = 칩 이름 행, `+2` = 출시일 행, `+3~` = 데이터

---

## 8. DB 스키마 설계

### 설계 원칙

| 테이블 유형 | 동작 | 용도 |
|-------------|------|------|
| 메인 (`CHIPSET_*`) | 업로드 시 DELETE → INSERT (덮어쓰기) | 현재 최신 상태 조회 |
| 히스토리 (`CHIPSET_*_H`) | 업로드 시 INSERT만 (삭제 없음) | 전체 이력 누적 보관 |

### DDL

```sql
-- ══════════════════════════════════════════════════
-- 시퀀스
-- ══════════════════════════════════════════════════
CREATE SEQUENCE SQ_CHIPSET_UPLOAD     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_ROW        START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CELL       START WITH 1 INCREMENT BY 1 NOCACHE;

-- 히스토리용 별도 시퀀스
CREATE SEQUENCE SQ_CHIPSET_UPLOAD_H   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CHIP_COL_H START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_ROW_H      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_CHIPSET_CELL_H     START WITH 1 INCREMENT BY 1 NOCACHE;

-- ══════════════════════════════════════════════════
-- 메인 테이블 (최신 상태 — 업로드마다 덮어쓰기)
-- ══════════════════════════════════════════════════

-- 업로드 메타
CREATE TABLE CHIPSET_UPLOAD (
    UPLOAD_SEQ      NUMBER          NOT NULL,
    FILE_NM         VARCHAR2(255)   NOT NULL,
    UPLOAD_DT       DATE            DEFAULT SYSDATE NOT NULL,
    ROW_COUNT       NUMBER          DEFAULT 0,
    COL_COUNT       NUMBER          DEFAULT 0,
    CONSTRAINT PK_CHIPSET_UPLOAD PRIMARY KEY (UPLOAD_SEQ)
);

-- 벤더별 칩 컬럼 정의 (Intel, AMD, 기타 동적 지원)
CREATE TABLE CHIPSET_CHIP_COL (
    COL_SEQ         NUMBER          NOT NULL,
    UPLOAD_SEQ      NUMBER          NOT NULL,
    VENDOR          VARCHAR2(100)   NOT NULL,   -- 동적: 'INTEL', 'AMD', 기타 어떤 벤더명도 가능
    COL_IDX         NUMBER          NOT NULL,   -- 원본 Excel 컬럼 인덱스
    CHIP_NM         VARCHAR2(200)   NOT NULL,
    CHIP_DT         VARCHAR2(50),
    SORT_ORDER      NUMBER          DEFAULT 0,
    CONSTRAINT PK_CHIPSET_CHIP_COL PRIMARY KEY (COL_SEQ),
    CONSTRAINT FK_CHIP_COL_UPLOAD   FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);

-- 스펙 행 (DIMM, Product, Ver., Density, Org, Speed)
CREATE TABLE CHIPSET_ROW (
    ROW_SEQ         NUMBER          NOT NULL,
    UPLOAD_SEQ      NUMBER          NOT NULL,
    DIMM            VARCHAR2(100),
    PRODUCT         VARCHAR2(200),
    VER             VARCHAR2(50),
    DENSITY         VARCHAR2(50),
    ORG             VARCHAR2(50),
    SPEED           VARCHAR2(100),
    SORT_ORDER      NUMBER          DEFAULT 0,
    CONSTRAINT PK_CHIPSET_ROW PRIMARY KEY (ROW_SEQ),
    CONSTRAINT FK_ROW_UPLOAD        FOREIGN KEY (UPLOAD_SEQ)
        REFERENCES CHIPSET_UPLOAD (UPLOAD_SEQ)
);

-- 셀 값 (행 × 칩 컬럼 교차)
CREATE TABLE CHIPSET_CELL (
    CELL_SEQ        NUMBER          NOT NULL,
    ROW_SEQ         NUMBER          NOT NULL,
    COL_SEQ         NUMBER          NOT NULL,
    CELL_VALUE      VARCHAR2(200),
    BG_COLOR        VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL  PRIMARY KEY (CELL_SEQ),
    CONSTRAINT FK_CELL_ROW      FOREIGN KEY (ROW_SEQ)
        REFERENCES CHIPSET_ROW (ROW_SEQ),
    CONSTRAINT FK_CELL_COL      FOREIGN KEY (COL_SEQ)
        REFERENCES CHIPSET_CHIP_COL (COL_SEQ)
);

-- ══════════════════════════════════════════════════
-- 히스토리 테이블 (누적 — INSERT만, DELETE 없음)
-- FK 없음 (성능 + 메인 데이터 삭제 영향 방지)
-- ══════════════════════════════════════════════════

CREATE TABLE CHIPSET_UPLOAD_H (
    UPLOAD_H_SEQ    NUMBER          NOT NULL,
    UPLOAD_SEQ      NUMBER          NOT NULL,   -- 메인 UPLOAD_SEQ 동일값 참조
    FILE_NM         VARCHAR2(255),
    UPLOAD_DT       DATE            DEFAULT SYSDATE,
    ROW_COUNT       NUMBER          DEFAULT 0,
    COL_COUNT       NUMBER          DEFAULT 0,
    CONSTRAINT PK_CHIPSET_UPLOAD_H PRIMARY KEY (UPLOAD_H_SEQ)
);

CREATE TABLE CHIPSET_CHIP_COL_H (
    COL_H_SEQ       NUMBER          NOT NULL,
    COL_SEQ         NUMBER          NOT NULL,   -- 메인 COL_SEQ 참조
    UPLOAD_SEQ      NUMBER          NOT NULL,
    VENDOR          VARCHAR2(100),
    COL_IDX         NUMBER,
    CHIP_NM         VARCHAR2(200),
    CHIP_DT         VARCHAR2(50),
    SORT_ORDER      NUMBER,
    CONSTRAINT PK_CHIPSET_CHIP_COL_H PRIMARY KEY (COL_H_SEQ)
);

CREATE TABLE CHIPSET_ROW_H (
    ROW_H_SEQ       NUMBER          NOT NULL,
    ROW_SEQ         NUMBER          NOT NULL,   -- 메인 ROW_SEQ 참조
    UPLOAD_SEQ      NUMBER          NOT NULL,
    DIMM            VARCHAR2(100),
    PRODUCT         VARCHAR2(200),
    VER             VARCHAR2(50),
    DENSITY         VARCHAR2(50),
    ORG             VARCHAR2(50),
    SPEED           VARCHAR2(100),
    SORT_ORDER      NUMBER,
    CONSTRAINT PK_CHIPSET_ROW_H PRIMARY KEY (ROW_H_SEQ)
);

CREATE TABLE CHIPSET_CELL_H (
    CELL_H_SEQ      NUMBER          NOT NULL,
    CELL_SEQ        NUMBER          NOT NULL,   -- 메인 CELL_SEQ 참조
    ROW_SEQ         NUMBER          NOT NULL,
    COL_SEQ         NUMBER          NOT NULL,
    UPLOAD_SEQ      NUMBER          NOT NULL,
    CELL_VALUE      VARCHAR2(200),
    BG_COLOR        VARCHAR2(10),
    CONSTRAINT PK_CHIPSET_CELL_H PRIMARY KEY (CELL_H_SEQ)
);

-- ══════════════════════════════════════════════════
-- 인덱스 (조회 성능)
-- ══════════════════════════════════════════════════
CREATE INDEX IDX_CHIP_COL_UPLOAD  ON CHIPSET_CHIP_COL (UPLOAD_SEQ);
CREATE INDEX IDX_ROW_UPLOAD       ON CHIPSET_ROW       (UPLOAD_SEQ);
CREATE INDEX IDX_CELL_ROW         ON CHIPSET_CELL      (ROW_SEQ);
CREATE INDEX IDX_CELL_H_UPLOAD    ON CHIPSET_CELL_H    (UPLOAD_SEQ);
CREATE INDEX IDX_ROW_H_UPLOAD     ON CHIPSET_ROW_H     (UPLOAD_SEQ);
```

### 업로드 트랜잭션 흐름

```
BEGIN TRANSACTION
  1. SQ_CHIPSET_UPLOAD.NEXTVAL → uploadSeq 확보
  
  ── 메인 테이블 덮어쓰기 ──────────────────────────────
  2. DELETE CHIPSET_CELL   (전체)
  3. DELETE CHIPSET_ROW    (전체)
  4. DELETE CHIPSET_CHIP_COL (전체)
  5. DELETE CHIPSET_UPLOAD   (전체)
  6. INSERT CHIPSET_UPLOAD   (새 데이터)
  7. INSERT CHIPSET_CHIP_COL (새 데이터)
  8. INSERT CHIPSET_ROW      (새 데이터)
  9. INSERT CHIPSET_CELL     (새 데이터)
  
  ── 히스토리 테이블 누적 ──────────────────────────────
  10. INSERT CHIPSET_UPLOAD_H
  11. INSERT CHIPSET_CHIP_COL_H
  12. INSERT CHIPSET_ROW_H
  13. INSERT CHIPSET_CELL_H
COMMIT
```

---

## 9. API 설계

| Method | URL | 설명 |
|--------|-----|------|
| `POST`   | `/api/chipset/upload`          | XLSX 업로드 & DB 저장 |
| `GET`    | `/api/chipset/matrix`          | 현재 메인 테이블 매트릭스 조회 |
| `GET`    | `/api/chipset/history`         | 히스토리 업로드 목록 조회 |
| `GET`    | `/api/chipset/history/{uploadSeq}` | 특정 히스토리 버전 매트릭스 조회 |

### 응답 모델

**POST /upload → UploadResult**
```json
{
  "success": true,
  "uploadSeq": 5,
  "rowCount": 120,
  "colCount": 15,
  "message": "업로드 완료"
}
```

**GET /matrix → MatrixResponse**
```json
{
  "uploadSeq": 5,
  "uploadDt": "2026-04-22T10:30:00",
  "vendors": ["INTEL", "AMD"],
  "chipCols": [
    { "colSeq": 1, "vendor": "INTEL", "chipNm": "RPL-H", "chipDt": "24'1", "sortOrder": 0 },
    { "colSeq": 2, "vendor": "AMD",   "chipNm": "PHX",   "chipDt": "24'3", "sortOrder": 1 }
  ],
  "rows": [
    {
      "rowSeq": 1,
      "dimm": "DDR5", "product": "SK Hynix", "ver": "A-die",
      "density": "16Gb", "org": "x8", "speed": "5600",
      "cells": [
        { "colSeq": 1, "cellValue": "25'1", "bgColor": null },
        { "colSeq": 2, "cellValue": "",     "bgColor": null }
      ]
    }
  ]
}
```

---

## 10. Java 핵심 코드

### build.gradle.kts (`backend/build.gradle.kts`)

```kotlin
plugins {
    id("org.springframework.boot") version "3.2.4"
    id("io.spring.dependency-management") version "1.1.4"
    java
}

group = "com.chipset"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")

    // Oracle JDBC
    implementation("com.oracle.database.jdbc:ojdbc11:21.11.0.0")

    // Apache POI (XLSX 스트리밍)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Commons FileUpload 2.x (Jakarta EE 호환)
    implementation("org.apache.commons:commons-fileupload2-jakarta-servlet6:2.0.0-M2")
    implementation("commons-io:commons-io:2.15.1")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

### settings.gradle.kts

```kotlin
rootProject.name = "chipset-backend"
```

### application.yml (`backend/src/main/resources/application.yml`)

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@localhost:1521/XEPDB1   # Service Name 방식 (슬래시 사용)
    username: chipset
    password: chipset123
  servlet:
    multipart:
      enabled: false    # Commons FileUpload 직접 사용 시 Spring multipart 비활성화

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

### WebConfig.java

```java
package com.chipset.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

### 모델 클래스

```java
// ChipsetUpload.java
package com.chipset.model;

import lombok.Data;
import java.util.Date;

@Data
public class ChipsetUpload {
    private Long   uploadSeq;
    private String fileNm;
    private Date   uploadDt;
    private int    rowCount;
    private int    colCount;
}
```

```java
// ChipsetChipCol.java
package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetChipCol {
    private Long    colSeq;
    private Long    uploadSeq;
    private String  vendor;       // 동적: "INTEL", "AMD", 기타 벤더명
    private Integer colIdx;
    private String  chipNm;
    private String  chipDt;
    private Integer sortOrder;
}
```

```java
// ChipsetRow.java
package com.chipset.model;

import lombok.Data;
import java.util.List;

@Data
public class ChipsetRow {
    private Long         rowSeq;
    private Long         uploadSeq;
    private String       dimm;
    private String       product;
    private String       ver;
    private String       density;
    private String       org;
    private String       speed;
    private Integer      sortOrder;
    private List<ChipsetCell> cells;   // 조회 시 N+1 방지용 컬렉션
}
```

```java
// ChipsetCell.java
package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetCell {
    private Long   cellSeq;
    private Long   rowSeq;
    private Long   colSeq;
    private String cellValue;
    private String bgColor;
}
```

```java
// UploadResult.java
package com.chipset.model;

import lombok.Data;

@Data
public class UploadResult {
    private boolean success;
    private Long    uploadSeq;
    private int     rowCount;
    private int     colCount;
    private String  message;

    public static UploadResult success(Long uploadSeq, int rowCount, int colCount) {
        UploadResult r = new UploadResult();
        r.success   = true;
        r.uploadSeq = uploadSeq;
        r.rowCount  = rowCount;
        r.colCount  = colCount;
        r.message   = "업로드 완료 (행: " + rowCount + ", 칩: " + colCount + ")";
        return r;
    }

    public static UploadResult error(String message) {
        UploadResult r = new UploadResult();
        r.success = false;
        r.message = message;
        return r;
    }
}
```

```java
// MatrixResponse.java
package com.chipset.model;

import lombok.Data;
import java.util.*;

@Data
public class MatrixResponse {
    private Long              uploadSeq;
    private Date              uploadDt;
    private List<String>      vendors;     // 중복 제거된 벤더 목록 (순서 유지)
    private List<ChipsetChipCol> chipCols;
    private List<ChipsetRow>  rows;
}
```

---

### ChipsetExcelParser.java

동적 벤더 지원 파서 — 병합 셀을 스캔하여 어떤 벤더명도 자동 인식합니다.

```java
package com.chipset.util;

import com.chipset.model.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.eventusermodel.*;
import org.apache.poi.xssf.model.StylesTable;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.*;
import java.util.*;

public class ChipsetExcelParser {

    private static final int SPEC_COL_COUNT = 6;  // DIMM ~ Speed 고정 6칸

    public static class ParseResult {
        public final List<ChipsetChipCol> chipCols = new ArrayList<>();
        public final List<ChipsetRow>     rows     = new ArrayList<>();
    }

    /**
     * XLSX 파일을 스트리밍 방식으로 파싱.
     * Intel/AMD 외 임의 벤더도 자동 감지합니다.
     */
    public static ParseResult parse(File file) throws Exception {
        // 1단계: 헤더 행(상위 5행)을 일반 읽기로 파싱하여 칩 컬럼 구조 파악
        List<List<String>> topRows = readTopRows(file, 5);
        ParseResult result = new ParseResult();
        buildChipCols(topRows, result);

        // colIdx → chipCol 매핑 (2단계에서 사용)
        Map<Integer, ChipsetChipCol> colByIdx = new HashMap<>();
        for (ChipsetChipCol col : result.chipCols) {
            colByIdx.put(col.getColIdx(), col);
        }

        // 2단계: 데이터 행 파싱 (3행 이후)
        parseDataRows(file, result, colByIdx, 3);
        return result;
    }

    // ── 상위 N행 읽기 ──────────────────────────────────────────────
    private static List<List<String>> readTopRows(File file, int maxRows) throws Exception {
        List<List<String>> topRows = new ArrayList<>();
        try (OPCPackage opc = OPCPackage.open(new FileInputStream(file))) {
            XSSFReader xssfReader  = new XSSFReader(opc);
            StylesTable styles     = xssfReader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(opc);
            Iterator<InputStream> sheets = xssfReader.getSheetsData();
            if (!sheets.hasNext()) return topRows;

            try (InputStream stream = sheets.next()) {
                InputSource inputSource = new InputSource(stream);
                XMLReader reader = XMLReaderFactory.createXMLReader(
                        "org.apache.xerces.parsers.SAXParser");

                reader.setContentHandler(
                    new XSSFSheetXMLHandler(styles, strings, new DataFormatter(), false) {
                        // 행 완료 콜백 오버라이드는 SheetContentsHandler를 통해 처리
                        // → 별도 SheetContentsHandler 구현 사용
                    }
                );

                // SheetContentsHandler 기반으로 직접 파싱
                topRows.addAll(parseSheetRows(stream, styles, strings, 0, maxRows));
            }
        }
        return topRows;
    }

    // ── Sheet 행 파싱 공통 메서드 ──────────────────────────────────
    private static List<List<String>> parseSheetRows(
            InputStream stream,
            StylesTable styles,
            ReadOnlySharedStringsTable strings,
            int fromRow, int toRow) throws Exception {

        List<List<String>> result = new ArrayList<>();
        List<String> currentRow  = new ArrayList<>();
        int[]        currentIdx  = { -1 };

        XSSFSheetXMLHandler.SheetContentsHandler handler =
                new XSSFSheetXMLHandler.SheetContentsHandler() {
            @Override
            public void startRow(int rowNum) {
                if (rowNum >= toRow) return;
                currentIdx[0] = rowNum;
                currentRow.clear();
            }
            @Override
            public void endRow(int rowNum) {
                if (rowNum >= fromRow && rowNum < toRow) {
                    result.add(new ArrayList<>(currentRow));
                }
            }
            @Override
            public void cell(String cellRef, String formattedValue,
                             XSSFComment comment) {
                // cellRef = "A1", "B3" 등. 컬럼 인덱스 계산
                int colIdx = colRefToIndex(cellRef);
                while (currentRow.size() <= colIdx) currentRow.add("");
                currentRow.set(colIdx, formattedValue != null ? formattedValue : "");
            }
            @Override
            public void headerFooter(String text, boolean isHeader, String tagName) {}
        };

        InputSource inputSource = new InputSource(stream);
        XMLReader reader = XMLReaderFactory.createXMLReader(
                "org.apache.xerces.parsers.SAXParser");
        reader.setContentHandler(
                new XSSFSheetXMLHandler(styles, null, strings, handler,
                        new DataFormatter(), false));
        reader.parse(inputSource);
        return result;
    }

    // ── 칩 컬럼 구조 파악 (동적 벤더) ──────────────────────────────
    private static void buildChipCols(List<List<String>> topRows, ParseResult result) {
        if (topRows.size() < 3) return;

        // 헤더 행 찾기 (DIMM 또는 PRODUCT 포함)
        int headerRowIdx = 0;
        for (int r = 0; r < topRows.size(); r++) {
            List<String> row = topRows.get(r);
            boolean found = row.stream().anyMatch(c -> c != null && (
                    "DIMM".equalsIgnoreCase(c.trim()) ||
                    c.toUpperCase().contains("PRODUCT")));
            if (found) { headerRowIdx = r; break; }
        }

        List<String> groupRow = topRows.get(headerRowIdx);
        List<String> chipRow  = get(topRows, headerRowIdx + 1);
        List<String> dateRow  = get(topRows, headerRowIdx + 2);

        // 스펙 컬럼(0~5) 이후에서 비어있지 않은 값 = 벤더명 (동적)
        // 벤더 범위: 현재 벤더 시작 ~ 다음 벤더 시작 전
        List<int[]> vendorRanges = new ArrayList<>(); // [시작col, 끝col, 벤더명idx]
        List<String> vendorNames = new ArrayList<>();

        for (int c = SPEC_COL_COUNT; c < groupRow.size(); c++) {
            String v = groupRow.get(c);
            if (v != null && !v.trim().isEmpty()) {
                // 이전 벤더 범위 종료
                if (!vendorRanges.isEmpty()) {
                    int[] prev = vendorRanges.get(vendorRanges.size() - 1);
                    prev[1] = c - 1;
                }
                vendorRanges.add(new int[]{c, groupRow.size() - 1, vendorNames.size()});
                vendorNames.add(v.trim().toUpperCase());
            }
        }

        int sortOrder = 0;
        for (int vi = 0; vi < vendorRanges.size(); vi++) {
            int[] range  = vendorRanges.get(vi);
            String vendor = vendorNames.get(vi);

            for (int c = range[0]; c <= range[1] && c < chipRow.size(); c++) {
                String chipNm = chipRow.get(c);
                if (chipNm == null || chipNm.trim().isEmpty()) continue;

                ChipsetChipCol col = new ChipsetChipCol();
                col.setVendor(vendor);
                col.setColIdx(c);
                col.setChipNm(chipNm.trim());
                col.setChipDt(c < dateRow.size() ? dateRow.get(c) : "");
                col.setSortOrder(sortOrder++);
                result.chipCols.add(col);
            }
        }
    }

    // ── 데이터 행 파싱 ──────────────────────────────────────────────
    private static void parseDataRows(File file, ParseResult result,
                                      Map<Integer, ChipsetChipCol> colByIdx,
                                      int dataStartRow) throws Exception {
        int[] sortOrder = {0};

        try (OPCPackage opc = OPCPackage.open(new FileInputStream(file))) {
            XSSFReader xssfReader  = new XSSFReader(opc);
            StylesTable styles     = xssfReader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(opc);
            Iterator<InputStream> sheets = xssfReader.getSheetsData();
            if (!sheets.hasNext()) return;

            try (InputStream stream = sheets.next()) {
                List<String>   currentCells = new ArrayList<>();
                int[]          currentRow   = {-1};
                ChipsetRow[]   pending      = {null};

                XSSFSheetXMLHandler.SheetContentsHandler handler =
                        new XSSFSheetXMLHandler.SheetContentsHandler() {
                    @Override
                    public void startRow(int rowNum) {
                        currentRow[0] = rowNum;
                        currentCells.clear();
                    }
                    @Override
                    public void endRow(int rowNum) {
                        if (rowNum < dataStartRow) return;
                        if (currentCells.stream().allMatch(c -> c == null || c.isEmpty())) return;

                        ChipsetRow row = new ChipsetRow();
                        row.setSortOrder(sortOrder[0]++);
                        row.setDimm(   getCell(currentCells, 0));
                        row.setProduct(getCell(currentCells, 1));
                        row.setVer(    getCell(currentCells, 2));
                        row.setDensity(getCell(currentCells, 3));
                        row.setOrg(    getCell(currentCells, 4));
                        row.setSpeed(  getCell(currentCells, 5));
                        row.setCells(new ArrayList<>());

                        for (int c = SPEC_COL_COUNT; c < currentCells.size(); c++) {
                            ChipsetChipCol chipCol = colByIdx.get(c);
                            if (chipCol == null) continue;
                            String val = currentCells.get(c);
                            // 빈 셀도 저장 (조회 시 colSeq 기준으로 매핑하기 위해)
                            ChipsetCell cell = new ChipsetCell();
                            cell.setCellValue(val != null ? val : "");
                            // colSeq는 INSERT 후 설정되므로 chipCol 임시 저장
                            cell.setColSeq(chipCol.getColSeq()); // INSERT 전이면 0, 서비스에서 재할당
                            row.getCells().add(cell);
                        }
                        result.rows.add(row);
                    }
                    @Override
                    public void cell(String cellRef, String formattedValue,
                                     XSSFComment comment) {
                        int colIdx = colRefToIndex(cellRef);
                        while (currentCells.size() <= colIdx) currentCells.add("");
                        currentCells.set(colIdx, formattedValue != null ? formattedValue : "");
                    }
                    @Override
                    public void headerFooter(String text, boolean isHeader, String tagName) {}
                };

                InputSource inputSource = new InputSource(stream);
                XMLReader reader = XMLReaderFactory.createXMLReader(
                        "org.apache.xerces.parsers.SAXParser");
                reader.setContentHandler(
                        new XSSFSheetXMLHandler(styles, null, strings, handler,
                                new DataFormatter(), false));
                reader.parse(inputSource);
            }
        }
    }

    // ── 유틸 ────────────────────────────────────────────────────────

    /** "A" → 0, "B" → 1, "AA" → 26, 셀 참조에서 컬럼 인덱스 추출 */
    private static int colRefToIndex(String cellRef) {
        int col = 0;
        for (char c : cellRef.toCharArray()) {
            if (!Character.isLetter(c)) break;
            col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return col - 1;
    }

    private static List<String> get(List<List<String>> rows, int idx) {
        return idx < rows.size() ? rows.get(idx) : List.of();
    }

    private static String getCell(List<String> cells, int idx) {
        return idx < cells.size() ? (cells.get(idx) != null ? cells.get(idx) : "") : "";
    }
}
```

---

### ChipsetService.java (메인 덮어쓰기 + 히스토리 누적)

```java
package com.chipset.service;

import com.chipset.mapper.ChipsetMapper;
import com.chipset.model.*;
import com.chipset.util.ChipsetExcelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChipsetService {

    private final ChipsetMapper chipsetMapper;

    @Transactional(rollbackFor = Exception.class)
    public UploadResult upload(FileItemInput fileItemInput) throws Exception {
        File tempFile = null;
        try {
            tempFile = writeTemporaryFile(fileItemInput);

            // 1. Excel 파싱
            ChipsetExcelParser.ParseResult parsed = ChipsetExcelParser.parse(tempFile);
            if (parsed.chipCols.isEmpty()) {
                return UploadResult.error("Excel에서 칩 컬럼을 찾을 수 없습니다.");
            }
            if (parsed.rows.isEmpty()) {
                return UploadResult.error("데이터 행이 없습니다.");
            }

            // 2. 새 UPLOAD_SEQ 확보
            Long uploadSeq = chipsetMapper.nextUploadSeq();

            // ── 메인 테이블: DELETE → INSERT (덮어쓰기) ──────────────
            chipsetMapper.deleteAllCells();
            chipsetMapper.deleteAllRows();
            chipsetMapper.deleteAllChipCols();
            chipsetMapper.deleteAllUploads();

            ChipsetUpload upload = new ChipsetUpload();
            upload.setUploadSeq(uploadSeq);
            upload.setFileNm(fileItemInput.getName());
            upload.setRowCount(parsed.rows.size());
            upload.setColCount(parsed.chipCols.size());
            chipsetMapper.insertUpload(upload);

            for (ChipsetChipCol col : parsed.chipCols) {
                col.setUploadSeq(uploadSeq);
                col.setColSeq(chipsetMapper.nextChipColSeq());
                chipsetMapper.insertChipCol(col);
            }

            // colIdx → colSeq 매핑 (셀 INSERT 시 필요)
            Map<Integer, Long> colIdxToSeq = parsed.chipCols.stream()
                    .collect(Collectors.toMap(ChipsetChipCol::getColIdx, ChipsetChipCol::getColSeq));

            for (ChipsetRow row : parsed.rows) {
                row.setUploadSeq(uploadSeq);
                row.setRowSeq(chipsetMapper.nextRowSeq());
                chipsetMapper.insertRow(row);

                for (ChipsetCell cell : row.getCells()) {
                    cell.setRowSeq(row.getRowSeq());
                    cell.setCellSeq(chipsetMapper.nextCellSeq());
                    chipsetMapper.insertCell(cell);
                }
            }

            // ── 히스토리 테이블: INSERT 누적 ──────────────────────────
            chipsetMapper.insertUploadH(upload);

            for (ChipsetChipCol col : parsed.chipCols) {
                chipsetMapper.insertChipColH(col);
            }

            for (ChipsetRow row : parsed.rows) {
                chipsetMapper.insertRowH(row);
                for (ChipsetCell cell : row.getCells()) {
                    cell.setColSeq(colIdxToSeq.getOrDefault(
                            findColIdx(parsed.chipCols, cell.getColSeq()), 0L));
                    chipsetMapper.insertCellH(cell, uploadSeq);
                }
            }

            log.info("업로드 완료 uploadSeq={} rows={} cols={}",
                    uploadSeq, parsed.rows.size(), parsed.chipCols.size());
            return UploadResult.success(uploadSeq, parsed.rows.size(), parsed.chipCols.size());

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public MatrixResponse getMatrix() {
        List<ChipsetChipCol> chipCols = chipsetMapper.selectChipCols();
        List<ChipsetRow> rows         = chipsetMapper.selectRows();

        // 벤더 목록 (순서 유지, 중복 제거)
        List<String> vendors = chipCols.stream()
                .map(ChipsetChipCol::getVendor)
                .distinct()
                .collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        ChipsetUpload latest = chipsetMapper.selectLatestUpload();
        if (latest != null) {
            resp.setUploadSeq(latest.getUploadSeq());
            resp.setUploadDt(latest.getUploadDt());
        }
        resp.setVendors(vendors);
        resp.setChipCols(chipCols);
        resp.setRows(rows);
        return resp;
    }

    public List<ChipsetUpload> getHistory() {
        return chipsetMapper.selectHistory();
    }

    public MatrixResponse getHistoryMatrix(Long uploadSeq) {
        List<ChipsetChipCol> chipCols = chipsetMapper.selectChipColsH(uploadSeq);
        List<ChipsetRow>     rows     = chipsetMapper.selectRowsH(uploadSeq);

        List<String> vendors = chipCols.stream()
                .map(ChipsetChipCol::getVendor)
                .distinct()
                .collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setVendors(vendors);
        resp.setChipCols(chipCols);
        resp.setRows(rows);
        return resp;
    }

    private int findColIdx(List<ChipsetChipCol> cols, Long colSeq) {
        return cols.stream()
                .filter(c -> c.getColSeq().equals(colSeq))
                .mapToInt(ChipsetChipCol::getColIdx)
                .findFirst().orElse(-1);
    }

    private File writeTemporaryFile(FileItemInput fileItemInput) throws IOException {
        String name = fileItemInput.getName();   // ← getFileName() 아님 (FileItemInput API)
        File tempFile = File.createTempFile(
                "chipset-" + name + "-" + UUID.randomUUID(), ".tmp");

        try (InputStream inputStream      = fileItemInput.getInputStream();
             ReadableByteChannel inputCh  = Channels.newChannel(inputStream);
             FileOutputStream outputStream = new FileOutputStream(tempFile);
             FileChannel outputCh         = outputStream.getChannel()) {
            outputCh.transferFrom(inputCh, 0, Long.MAX_VALUE);
        }
        return tempFile;
    }
}
```

---

### ChipsetController.java

```java
package com.chipset.controller;

import com.chipset.model.*;
import com.chipset.service.ChipsetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chipset")
@RequiredArgsConstructor
public class ChipsetController {

    private final ChipsetService chipsetService;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResult> upload(HttpServletRequest request) throws Exception {
        if (!JakartaServletFileUpload.isMultipartContent(request)) {
            return ResponseEntity.badRequest()
                    .body(UploadResult.error("multipart 요청이 아닙니다."));
        }
        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        FileItemInputIterator iterator  = upload.getItemIterator(request);
        if (!iterator.hasNext()) {
            return ResponseEntity.badRequest()
                    .body(UploadResult.error("파일이 없습니다."));
        }
        return ResponseEntity.ok(chipsetService.upload(iterator.next()));
    }

    @GetMapping("/matrix")
    public ResponseEntity<MatrixResponse> getMatrix() {
        return ResponseEntity.ok(chipsetService.getMatrix());
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChipsetUpload>> getHistory() {
        return ResponseEntity.ok(chipsetService.getHistory());
    }

    @GetMapping("/history/{uploadSeq}")
    public ResponseEntity<MatrixResponse> getHistoryMatrix(
            @PathVariable Long uploadSeq) {
        return ResponseEntity.ok(chipsetService.getHistoryMatrix(uploadSeq));
    }
}
```

---

### ChipsetMapper.java

```java
package com.chipset.mapper;

import com.chipset.model.*;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChipsetMapper {

    // ── 시퀀스 ──────────────────────────────────────────────
    @Select("SELECT SQ_CHIPSET_UPLOAD.NEXTVAL FROM DUAL")
    Long nextUploadSeq();

    @Select("SELECT SQ_CHIPSET_CHIP_COL.NEXTVAL FROM DUAL")
    Long nextChipColSeq();

    @Select("SELECT SQ_CHIPSET_ROW.NEXTVAL FROM DUAL")
    Long nextRowSeq();

    @Select("SELECT SQ_CHIPSET_CELL.NEXTVAL FROM DUAL")
    Long nextCellSeq();

    // ── 메인: 전체 삭제 ─────────────────────────────────────
    @Delete("DELETE FROM CHIPSET_CELL")
    void deleteAllCells();

    @Delete("DELETE FROM CHIPSET_ROW")
    void deleteAllRows();

    @Delete("DELETE FROM CHIPSET_CHIP_COL")
    void deleteAllChipCols();

    @Delete("DELETE FROM CHIPSET_UPLOAD")
    void deleteAllUploads();

    // ── 메인: INSERT ─────────────────────────────────────────
    void insertUpload(ChipsetUpload upload);
    void insertChipCol(ChipsetChipCol col);
    void insertRow(ChipsetRow row);
    void insertCell(ChipsetCell cell);

    // ── 메인: SELECT ─────────────────────────────────────────
    ChipsetUpload       selectLatestUpload();
    List<ChipsetChipCol> selectChipCols();
    List<ChipsetRow>     selectRows();

    // ── 히스토리: INSERT ─────────────────────────────────────
    void insertUploadH(ChipsetUpload upload);
    void insertChipColH(ChipsetChipCol col);
    void insertRowH(ChipsetRow row);
    void insertCellH(@Param("cell") ChipsetCell cell, @Param("uploadSeq") Long uploadSeq);

    // ── 히스토리: SELECT ─────────────────────────────────────
    List<ChipsetUpload>  selectHistory();
    List<ChipsetChipCol> selectChipColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetRow>     selectRowsH(@Param("uploadSeq") Long uploadSeq);
}
```

---

## 11. MyBatis Mapper XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.chipset.mapper.ChipsetMapper">

  <!-- ══ 메인: INSERT ════════════════════════════════════════════ -->

  <insert id="insertUpload" parameterType="com.chipset.model.ChipsetUpload">
    INSERT INTO CHIPSET_UPLOAD (UPLOAD_SEQ, FILE_NM, UPLOAD_DT, ROW_COUNT, COL_COUNT)
    VALUES (#{uploadSeq}, #{fileNm}, SYSDATE, #{rowCount}, #{colCount})
  </insert>

  <insert id="insertChipCol" parameterType="com.chipset.model.ChipsetChipCol">
    INSERT INTO CHIPSET_CHIP_COL
      (COL_SEQ, UPLOAD_SEQ, VENDOR, COL_IDX, CHIP_NM, CHIP_DT, SORT_ORDER)
    VALUES
      (#{colSeq}, #{uploadSeq}, #{vendor}, #{colIdx}, #{chipNm}, #{chipDt}, #{sortOrder})
  </insert>

  <insert id="insertRow" parameterType="com.chipset.model.ChipsetRow">
    INSERT INTO CHIPSET_ROW
      (ROW_SEQ, UPLOAD_SEQ, DIMM, PRODUCT, VER, DENSITY, ORG, SPEED, SORT_ORDER)
    VALUES
      (#{rowSeq}, #{uploadSeq}, #{dimm}, #{product}, #{ver}, #{density}, #{org}, #{speed}, #{sortOrder})
  </insert>

  <insert id="insertCell" parameterType="com.chipset.model.ChipsetCell">
    INSERT INTO CHIPSET_CELL (CELL_SEQ, ROW_SEQ, COL_SEQ, CELL_VALUE, BG_COLOR)
    VALUES (#{cellSeq}, #{rowSeq}, #{colSeq}, #{cellValue}, #{bgColor})
  </insert>

  <!-- ══ 메인: SELECT ════════════════════════════════════════════ -->

  <select id="selectLatestUpload" resultType="com.chipset.model.ChipsetUpload">
    SELECT UPLOAD_SEQ, FILE_NM, UPLOAD_DT, ROW_COUNT, COL_COUNT
    FROM CHIPSET_UPLOAD
    ORDER BY UPLOAD_DT DESC
    FETCH FIRST 1 ROWS ONLY
    ORDER BY UPLOAD_DT DESC
  </select>

  <select id="selectChipCols" resultType="com.chipset.model.ChipsetChipCol">
    SELECT COL_SEQ, UPLOAD_SEQ, VENDOR, COL_IDX, CHIP_NM, CHIP_DT, SORT_ORDER
    FROM CHIPSET_CHIP_COL
    ORDER BY SORT_ORDER
  </select>

  <resultMap id="rowResultMap" type="com.chipset.model.ChipsetRow">
    <id     property="rowSeq"    column="ROW_SEQ"/>
    <result property="uploadSeq" column="UPLOAD_SEQ"/>
    <result property="dimm"      column="DIMM"/>
    <result property="product"   column="PRODUCT"/>
    <result property="ver"       column="VER"/>
    <result property="density"   column="DENSITY"/>
    <result property="org"       column="ORG"/>
    <result property="speed"     column="SPEED"/>
    <result property="sortOrder" column="SORT_ORDER"/>
    <collection property="cells"
                ofType="com.chipset.model.ChipsetCell"
                select="selectCellsByRowSeq"
                column="ROW_SEQ"/>
  </resultMap>

  <select id="selectRows" resultMap="rowResultMap">
    SELECT ROW_SEQ, UPLOAD_SEQ, DIMM, PRODUCT, VER, DENSITY, ORG, SPEED, SORT_ORDER
    FROM CHIPSET_ROW
    ORDER BY SORT_ORDER
  </select>

  <select id="selectCellsByRowSeq" resultType="com.chipset.model.ChipsetCell">
    SELECT CELL_SEQ, ROW_SEQ, COL_SEQ, CELL_VALUE, BG_COLOR
    FROM CHIPSET_CELL
    WHERE ROW_SEQ = #{rowSeq}
  </select>

  <!-- ══ 히스토리: INSERT ════════════════════════════════════════ -->

  <insert id="insertUploadH" parameterType="com.chipset.model.ChipsetUpload">
    INSERT INTO CHIPSET_UPLOAD_H
      (UPLOAD_H_SEQ, UPLOAD_SEQ, FILE_NM, UPLOAD_DT, ROW_COUNT, COL_COUNT)
    VALUES
      (SQ_CHIPSET_UPLOAD_H.NEXTVAL, #{uploadSeq}, #{fileNm}, SYSDATE, #{rowCount}, #{colCount})
  </insert>

  <insert id="insertChipColH" parameterType="com.chipset.model.ChipsetChipCol">
    INSERT INTO CHIPSET_CHIP_COL_H
      (COL_H_SEQ, COL_SEQ, UPLOAD_SEQ, VENDOR, COL_IDX, CHIP_NM, CHIP_DT, SORT_ORDER)
    VALUES
      (SQ_CHIPSET_CHIP_COL_H.NEXTVAL, #{colSeq}, #{uploadSeq},
       #{vendor}, #{colIdx}, #{chipNm}, #{chipDt}, #{sortOrder})
  </insert>

  <insert id="insertRowH" parameterType="com.chipset.model.ChipsetRow">
    INSERT INTO CHIPSET_ROW_H
      (ROW_H_SEQ, ROW_SEQ, UPLOAD_SEQ, DIMM, PRODUCT, VER, DENSITY, ORG, SPEED, SORT_ORDER)
    VALUES
      (SQ_CHIPSET_ROW_H.NEXTVAL, #{rowSeq}, #{uploadSeq},
       #{dimm}, #{product}, #{ver}, #{density}, #{org}, #{speed}, #{sortOrder})
  </insert>

  <insert id="insertCellH">
    INSERT INTO CHIPSET_CELL_H
      (CELL_H_SEQ, CELL_SEQ, ROW_SEQ, COL_SEQ, UPLOAD_SEQ, CELL_VALUE, BG_COLOR)
    VALUES
      (SQ_CHIPSET_CELL_H.NEXTVAL, #{cell.cellSeq}, #{cell.rowSeq}, #{cell.colSeq},
       #{uploadSeq}, #{cell.cellValue}, #{cell.bgColor})
  </insert>

  <!-- ══ 히스토리: SELECT ════════════════════════════════════════ -->

  <select id="selectHistory" resultType="com.chipset.model.ChipsetUpload">
    SELECT UPLOAD_SEQ, FILE_NM, UPLOAD_DT, ROW_COUNT, COL_COUNT
    FROM CHIPSET_UPLOAD_H
    ORDER BY UPLOAD_DT DESC
  </select>

  <select id="selectChipColsH" resultType="com.chipset.model.ChipsetChipCol">
    SELECT COL_SEQ, UPLOAD_SEQ, VENDOR, COL_IDX, CHIP_NM, CHIP_DT, SORT_ORDER
    FROM CHIPSET_CHIP_COL_H
    WHERE UPLOAD_SEQ = #{uploadSeq}
    ORDER BY SORT_ORDER
  </select>

  <resultMap id="rowHResultMap" type="com.chipset.model.ChipsetRow">
    <id     property="rowSeq"    column="ROW_SEQ"/>
    <result property="uploadSeq" column="UPLOAD_SEQ"/>
    <result property="dimm"      column="DIMM"/>
    <result property="product"   column="PRODUCT"/>
    <result property="ver"       column="VER"/>
    <result property="density"   column="DENSITY"/>
    <result property="org"       column="ORG"/>
    <result property="speed"     column="SPEED"/>
    <result property="sortOrder" column="SORT_ORDER"/>
    <collection property="cells"
                ofType="com.chipset.model.ChipsetCell"
                select="selectCellsH"
                column="{rowSeq=ROW_SEQ, uploadSeq=UPLOAD_SEQ}"/>
  </resultMap>

  <select id="selectRowsH" resultMap="rowHResultMap">
    SELECT ROW_SEQ, UPLOAD_SEQ, DIMM, PRODUCT, VER, DENSITY, ORG, SPEED, SORT_ORDER
    FROM CHIPSET_ROW_H
    WHERE UPLOAD_SEQ = #{uploadSeq}
    ORDER BY SORT_ORDER
  </select>

  <select id="selectCellsH" resultType="com.chipset.model.ChipsetCell">
    SELECT CELL_SEQ, ROW_SEQ, COL_SEQ, CELL_VALUE, BG_COLOR
    FROM CHIPSET_CELL_H
    WHERE ROW_SEQ = #{rowSeq}
      AND UPLOAD_SEQ = #{uploadSeq}
  </select>

</mapper>
```

---

## 12. Vue 프론트엔드

### Tachyons 설치 확인 및 import

```bash
# 설치 (axios는 이미 설치됨)
npm install tachyons
```

`src/main.js`에 추가:
```js
import 'tachyons/css/tachyons.min.css'
```

### ChipsetUploadView.vue

동적 벤더 지원 — vendors 배열 기준으로 그룹 헤더를 자동 생성합니다.  
벤더 색상은 index 기반으로 자동 배정됩니다.

```vue
<template>
  <div class="bg-near-black min-vh-100 pa4 f7 white"
       style="font-family: 'JetBrains Mono', monospace">

    <!-- 헤더 -->
    <div class="flex items-center mb4 pb3" style="border-bottom: 1px solid #1e293b">
      <span class="blue f7 fw7 tracked ba b--blue pa1 br1 mr3"
            style="font-size:10px; letter-spacing:.15em">CHIPSET DB</span>
      <h1 class="f5 fw7 white ma0 tracked">Compatibility Matrix</h1>
    </div>

    <!-- 업로드 영역 -->
    <div class="flex items-center mb4 flex-wrap" style="gap:12px">
      <label class="pointer ba b--blue pa2 br2 blue f7 fw7 tracked"
             style="cursor:pointer; letter-spacing:.1em">
        <input type="file" accept=".xlsx,.xls" class="dn" @change="onFileChange" />
        ↑ UPLOAD XLSX
      </label>

      <span v-if="fileName" class="silver f7">{{ fileName }}</span>

      <button
        v-if="selectedFile"
        class="ba b--green pa2 br2 f7 fw7 tracked"
        :class="uploading ? 'moon-gray b--gray' : 'green'"
        :disabled="uploading"
        @click="doUpload"
        style="background:transparent; cursor:pointer; letter-spacing:.1em"
      >
        {{ uploading ? 'UPLOADING...' : '→ SEND TO DB' }}
      </button>

      <button
        v-if="!matrix && !uploading"
        class="ba b--silver pa2 br2 f7 fw6 silver"
        style="background:transparent; cursor:pointer"
        @click="loadMatrix"
      >
        DB 데이터 불러오기
      </button>
    </div>

    <!-- 결과 메시지 -->
    <p v-if="uploadMsg" class="mb4 f7"
       :class="uploadSuccess ? 'light-green' : 'light-red'">
      {{ uploadMsg }}
    </p>

    <!-- 매트릭스 테이블 -->
    <div v-if="matrix && matrix.rows.length" class="overflow-x-auto">

      <!-- 행 1: 벤더 그룹 헤더 -->
      <div class="flex" style="border-bottom:1px solid #1e293b">
        <div v-for="lbl in specLabels" :key="lbl"
             class="tc fw7 f7 pa2 flex-none"
             style="min-width:80px; background:#0f1729; color:#60a5fa;
                    border-right:1px solid #1a2035">
          {{ lbl }}
        </div>
        <template v-for="(vendor, vi) in matrix.vendors" :key="vendor">
          <div class="tc fw7 f7 pa2 flex-none"
               :style="vendorGroupStyle(vi, colsByVendor(vendor).length)"
               :style="{ minWidth: (colsByVendor(vendor).length * 90) + 'px',
                         ...vendorGroupStyle(vi) }">
            {{ vendor }}
          </div>
        </template>
      </div>

      <!-- 행 2: 칩 이름 + 출시일 -->
      <div class="flex" style="border-bottom:2px solid #1e293b">
        <div v-for="lbl in specLabels" :key="'sh'+lbl"
             class="tc f7 pa2 flex-none"
             style="min-width:80px; background:#0f1729; color:#60a5fa;
                    border-right:1px solid #1a2035">
        </div>
        <div v-for="col in matrix.chipCols" :key="col.colSeq"
             class="tc pa2 flex-none"
             :style="{ minWidth:'90px', ...chipColStyle(col) }">
          <div class="fw7" style="font-size:10px">{{ col.chipNm }}</div>
          <div style="font-size:9px; opacity:.6">{{ col.chipDt }}</div>
        </div>
      </div>

      <!-- 데이터 행 -->
      <div v-for="row in matrix.rows" :key="row.rowSeq"
           class="flex" style="border-bottom:1px solid #1a1c24">
        <!-- 스펙 셀 -->
        <div v-for="(val, si) in specValues(row)" :key="si"
             class="tc pa2 flex-none"
             style="min-width:80px; background:#0d1220; color:#e2e8f0;
                    border-right:1px solid #1a2035; font-size:11px">
          {{ val }}
        </div>
        <!-- 칩 셀 -->
        <div v-for="col in matrix.chipCols" :key="col.colSeq"
             class="tc pa2 flex-none"
             :style="chipCellStyle(row, col)">
          {{ cellValue(row, col) }}
        </div>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div v-else-if="matrix && !matrix.rows.length" class="tc mt5 silver">
      데이터가 없습니다. Excel을 업로드하세요.
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import axios from 'axios'

const API = 'http://localhost:8080/api/chipset'

const SPEC_LABELS = ['DIMM', 'Product', 'Ver.', 'Density', 'Org', 'Speed']
const specLabels  = SPEC_LABELS

// 벤더별 색상 팔레트 (동적 — 인덱스 순서대로 배정)
const VENDOR_COLORS = [
  { bg: '#0d1f38', text: '#93c5fd', border: '#1e3a5f' },  // 파랑 (Intel 계열)
  { bg: '#1a0f2e', text: '#c4b5fd', border: '#2d1a4a' },  // 보라 (AMD 계열)
  { bg: '#0f2318', text: '#6ee7b7', border: '#1a4a2e' },  // 초록
  { bg: '#2a1500', text: '#fbbf24', border: '#4a2a00' },  // 주황
  { bg: '#1a0a0a', text: '#f87171', border: '#4a1a1a' },  // 빨강
]

const selectedFile  = ref(null)
const fileName      = ref('')
const uploading     = ref(false)
const uploadMsg     = ref('')
const uploadSuccess = ref(false)
const matrix        = ref(null)

function onFileChange(e) {
  selectedFile.value = e.target.files[0] ?? null
  fileName.value     = selectedFile.value?.name ?? ''
  uploadMsg.value    = ''
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value    = true
  uploadMsg.value    = ''
  uploadSuccess.value = false
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const { data } = await axios.post(`${API}/upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    uploadSuccess.value = data.success
    uploadMsg.value     = data.message
    if (data.success) await loadMatrix()
  } catch (e) {
    uploadSuccess.value = false
    uploadMsg.value = '서버 오류: ' + (e.response?.data?.message ?? e.message)
  } finally {
    uploading.value = false
  }
}

async function loadMatrix() {
  try {
    const { data } = await axios.get(`${API}/matrix`)
    matrix.value = data
  } catch (e) {
    uploadMsg.value = '데이터 로드 실패: ' + e.message
  }
}

// ── 헬퍼 ──────────────────────────────────────────────────────
function colsByVendor(vendor) {
  return matrix.value?.chipCols.filter(c => c.vendor === vendor) ?? []
}

function vendorIndex(vendor) {
  return matrix.value?.vendors.indexOf(vendor) ?? 0
}

function vendorColor(vi) {
  return VENDOR_COLORS[vi % VENDOR_COLORS.length]
}

function vendorGroupStyle(vi) {
  const c = vendorColor(vi)
  return {
    background: c.bg,
    color: c.text,
    borderRight: `2px solid ${c.border}`,
    fontWeight: '700',
    letterSpacing: '.12em',
    fontSize: '10px',
  }
}

function chipColStyle(col) {
  const vi = vendorIndex(col.vendor)
  const c  = vendorColor(vi)
  return {
    background: c.bg,
    color: c.text,
    borderRight: `1px solid ${c.border}`,
    fontSize: '10px',
  }
}

function specValues(row) {
  return [row.dimm, row.product, row.ver, row.density, row.org, row.speed]
}

function cellValue(row, col) {
  return row.cells?.find(c => c.colSeq === col.colSeq)?.cellValue ?? ''
}

function chipCellStyle(row, col) {
  const cell = row.cells?.find(c => c.colSeq === col.colSeq)
  const vi   = vendorIndex(col.vendor)
  const base = {
    minWidth: '90px',
    fontSize: '11px',
    borderRight: '1px solid #1a1c24',
    background: '#111318',
  }
  if (!cellValue(row, col)) {
    return { ...base, background: '#222428', color: 'transparent' }
  }
  if (cell?.bgColor) {
    return { ...base, background: cell.bgColor, color: '#000' }
  }
  return { ...base, color: vendorColor(vi).text }
}
</script>
```

### App.vue apps 배열에 추가

```js
import ChipsetUploadView from './components/ChipsetUploadView.vue'

const apps = [
  {
    id: 'chipset-validation',
    name: 'ChipsetValidation.vue',
    description: '로컬 Excel 직접 파싱 (Vue only)',
    component: ChipsetValidation,
  },
  {
    id: 'chipset-db',
    name: 'ChipsetUploadView.vue',
    description: 'Upload → Oracle DB → Grid 출력',
    component: ChipsetUploadView,
  },
]
```

---

## 13. 제공 코드 버그 목록

### PlanApi.java

| 위치 | 원본 | 수정 | 원인 |
|------|------|------|------|
| throws 절 | `throws Excepion` | `throws Exception` | 오타 (`i` 누락) |
| 예외 클래스 | `NullArgumentException()` | `IllegalArgumentException("파일 없음")` | 표준 Java에 없는 클래스 |

### PlanService.java

| 위치 | 원본 | 수정 | 원인 |
|------|------|------|------|
| 제네릭 | `ArrayList<string>` | `ArrayList<String>` | Java는 대소문자 구분 |
| 메서드명 | `fileItemStream.getFiledName()` | `fileItemStream.getName()` | 오타 (`Filed` → `File`) |
| null 체크 | `Objects.isNull(plan.getDepaDt().getTime())` | `plan.getDepaDt() == null` | `getTime()` 반환형 `long`은 null 불가 |
| 중괄호 불일치 | for 루프 닫는 `}` 누락 | 누락된 `}` 추가 | 괄호 미스매치 |
| try-catch | catch 블록 비어있음 | `log.error(...)` 추가 | 예외 무시 anti-pattern |

### LargeExcelHelper.java

| 위치 | 원본 | 수정 | 원인 |
|------|------|------|------|
| 중괄호 | field 루프 `}` 없이 method 루프 시작 | field 루프 닫고 method 루프 시작 | 구조적 오류, 컴파일 불가 |
| setter 명 | `setParametertype(...)` | `setParameterType(...)` | 오타 (소문자 `t`), getter/setter 불일치 |

### ca-sop-plan-upload.xml

| 위치 | 원본 | 수정 | 원인 |
|------|------|------|------|
| SQL 키워드 | `form maru_ca_...` | `FROM maru_ca_...` | 오타 (`form` → `FROM`) |

---

## 14. 멀티 파일 타입 설계 분석

> 분석일: 2026-04-22  
> 대상 파일: `data/Server.xlsx`, `data/Client.xlsx`, `data/Mobile.xlsx`, `data/Raw_Data.xlsx`

---

### 14-1. 파일 구조 비교

| 항목 | Server.xlsx | Client.xlsx | Mobile.xlsx | Raw_Data.xlsx |
|------|-------------|-------------|-------------|---------------|
| **분류** | Server 검증 매트릭스 | Client 검증 매트릭스 | 모바일 검증 매트릭스 | 검증 추적 시트 |
| **고정 좌측 컬럼** | DIMM, Product(Ver.), Ver., Density, Org, Speed | 동일 | PKG, Density, Product, P/N, Code Name(Ver.) | Company, Seg, Chipset, SoC CS, Part Number, DRAM PROCES, Flash Process, Density, MLC/TLC, PKG |
| **벤더 그룹** | Intel, AMD (서버 칩셋) | Intel, AMD (클라이언트 칩셋) | Qualcomm (SM-series) | 없음 |
| **칩셋 헤더 구조** | 칩명 행 + 출시일 행 | 동일 | 동일 | 없음 (Target AP / Sorting KEY / Validation Status 섹션) |
| **셀 데이터 성격** | 검증일자 + 배경색 | 동일 | VP/VL/VH 코드 + 배경색 | 날짜, 담당자, Pass/Fail, Remark |
| **형태 동일 여부** | ✅ Server ↔ Client 동일 구조 | ✅ Server와 동일 | ⚠️ 좌측 컬럼 상이 | ❌ 완전히 다른 구조 |

**결론: 4개 파일 중 2가지 패턴**
- **패턴 A (Matrix형)**: Server.xlsx = Client.xlsx = Mobile.xlsx — 칩셋 매트릭스 구조 (좌측 스펙 + 우측 칩셋 열)
- **패턴 B (Tracking형)**: Raw_Data.xlsx — 섹션별 그룹 헤더 + 검증 이력 행

---

### 14-2. 현재 DB 스키마 적합성 평가

현재 스키마는 `Server.xlsx` 한 가지만을 기준으로 설계되어 있습니다.

| 파일 | 현재 스키마 지원 여부 | 문제점 |
|------|----------------------|--------|
| **Server.xlsx** | ✅ 완전 지원 | 설계 기준 파일 |
| **Client.xlsx** | ✅ 완전 지원 | Server와 동일 구조 |
| **Mobile.xlsx** | ⚠️ 부분 지원 | `CHIPSET_ROW`의 컬럼(DIMM/ORG/SPEED)이 Mobile 컬럼(PKG/P/N/CODE_NAME)과 불일치 |
| **Raw_Data.xlsx** | ❌ 미지원 | 칩셋 매트릭스 구조 자체가 없어 현재 스키마에 저장 불가 |

**Mobile.xlsx 불일치 상세:**

```
현재 CHIPSET_ROW 컬럼     Mobile.xlsx 실제 컬럼
─────────────────────     ──────────────────────
DIMM          ←→  PKG            (의미 다름)
PRODUCT       ←→  Product        (OK)
VER           ←→  Code Name      (의미 다름)
DENSITY       ←→  Density        (OK)
ORG           ←→  P/N            (의미 다름)
SPEED         ←→  (없음)         (Mobile엔 Speed 없음)
```

**핵심 문제: 메인 테이블 덮어쓰기 방식**

현재 업로드 시 `CHIPSET_*` 테이블 전체를 DELETE 후 INSERT합니다.  
→ Server를 올린 후 Client를 올리면 Server 데이터가 삭제됩니다.  
→ 4가지 파일 타입이 서로 독립적으로 관리되어야 합니다.

---

### 14-3. 파일 자동 감지 규칙

헤더 행의 키워드 패턴으로 파일 타입을 자동 판별할 수 있습니다.

| 파일 타입 | 감지 조건 | 신뢰도 |
|-----------|-----------|--------|
| **SERVER** | 고정 컬럼에 "DIMM" + "Org" + "Speed" 존재 AND 칩셋에 "SPR", "EMR", "GNR", "SRF" 등 서버 코드명 | 높음 |
| **CLIENT** | 고정 컬럼에 "DIMM" + "Org" + "Speed" 존재 AND 칩셋에 "MTL", "RPL", "ADL", "LNL" 등 클라이언트 코드명 | 높음 |
| **SERVER/CLIENT 공통** | 고정 컬럼에 "DIMM" + "Org" + "Speed" 존재 (칩셋명 무관) | 중간 → 파일명으로 2차 판별 |
| **MOBILE** | 고정 컬럼에 "PKG" + "P/N" + "Code Name" 존재 OR Qualcomm 벤더 감지 | 높음 |
| **RAW_DATA** | Row 1에 "Target AP" + "Sorting KEY" + "Validation Status" 섹션 헤더 존재 | 높음 |

**자동 감지 우선순위:**
```
1. Row 1의 병합 셀 텍스트 확인 → "Target AP" 있으면 RAW_DATA
2. 고정 컬럼 헤더 확인 → "PKG" + "P/N" 있으면 MOBILE
3. 고정 컬럼 헤더 확인 → "DIMM" + "Org" 있으면 SERVER 또는 CLIENT
   └─ 칩셋명 또는 파일명으로 SERVER vs CLIENT 구분
4. 감지 실패 시 → 사용자에게 타입 선택 요청
```

**Server vs Client 구분 불확실 시 파일명 기준:**

| 파일명 패턴 | 타입 |
|------------|------|
| `*Server*`, `*server*` | SERVER |
| `*Client*`, `*client*` | CLIENT |
| `*Mobile*`, `*mobile*` | MOBILE |
| `*Raw*`, `*raw*`, `*RawData*` | RAW_DATA |

---

### 14-4. 권장 DB 스키마 변경

#### (1) CHIPSET_UPLOAD에 FILE_TYPE 컬럼 추가

```sql
ALTER TABLE CHIPSET_UPLOAD ADD (
    FILE_TYPE  VARCHAR2(20)  DEFAULT 'SERVER'  -- 'SERVER','CLIENT','MOBILE','RAW_DATA'
);
ALTER TABLE CHIPSET_UPLOAD_H ADD (
    FILE_TYPE  VARCHAR2(20)
);
```

#### (2) Mobile용 ROW 컬럼 추가 (CHIPSET_ROW 확장)

```sql
ALTER TABLE CHIPSET_ROW ADD (
    PKG         VARCHAR2(200),   -- Mobile: PKG 스펙 (LP5X 496b 등)
    PN          VARCHAR2(200),   -- Mobile: Part Number
    CODE_NM     VARCHAR2(100)    -- Mobile: Code Name (VP/VL/VH)
);
ALTER TABLE CHIPSET_ROW_H ADD (
    PKG         VARCHAR2(200),
    PN          VARCHAR2(200),
    CODE_NM     VARCHAR2(100)
);
```

#### (3) Raw_Data 전용 테이블 신규 생성

```sql
-- Raw_Data 업로드 메타 (CHIPSET_UPLOAD의 FILE_TYPE='RAW_DATA' 행과 연결)
CREATE TABLE RAWDATA_ROW (
    RAWDATA_ROW_SEQ  NUMBER          NOT NULL,
    UPLOAD_SEQ       NUMBER          NOT NULL,   -- FK → CHIPSET_UPLOAD
    COMPANY          VARCHAR2(100),
    SEG              VARCHAR2(100),
    CHIPSET          VARCHAR2(200),
    SOC_CS           VARCHAR2(200),
    PART_NUMBER      VARCHAR2(200),
    DRAM_PROCESS     VARCHAR2(100),
    FLASH_PROCESS    VARCHAR2(100),
    DENSITY          VARCHAR2(50),
    MLC_TLC          VARCHAR2(50),
    PKG              VARCHAR2(200),
    -- Validation Status (최대 3세트)
    VAL1_DATE        VARCHAR2(20),
    VAL1_ENG         VARCHAR2(50),
    VAL1_STATUS      VARCHAR2(50),
    VAL1_REMARK      VARCHAR2(500),
    VAL2_DATE        VARCHAR2(20),
    VAL2_ENG         VARCHAR2(50),
    VAL2_STATUS      VARCHAR2(50),
    VAL2_REMARK      VARCHAR2(500),
    VAL3_DATE        VARCHAR2(20),
    VAL3_ENG         VARCHAR2(50),
    SORT_ORDER       NUMBER          DEFAULT 0,
    CONSTRAINT PK_RAWDATA_ROW PRIMARY KEY (RAWDATA_ROW_SEQ)
);

CREATE TABLE RAWDATA_ROW_H (
    RAWDATA_ROW_H_SEQ  NUMBER  NOT NULL,
    RAWDATA_ROW_SEQ    NUMBER  NOT NULL,
    UPLOAD_SEQ         NUMBER  NOT NULL,
    COMPANY          VARCHAR2(100),
    SEG              VARCHAR2(100),
    CHIPSET          VARCHAR2(200),
    SOC_CS           VARCHAR2(200),
    PART_NUMBER      VARCHAR2(200),
    DRAM_PROCESS     VARCHAR2(100),
    FLASH_PROCESS    VARCHAR2(100),
    DENSITY          VARCHAR2(50),
    MLC_TLC          VARCHAR2(50),
    PKG              VARCHAR2(200),
    VAL1_DATE        VARCHAR2(20),  VAL1_ENG  VARCHAR2(50),
    VAL1_STATUS      VARCHAR2(50),  VAL1_REMARK VARCHAR2(500),
    VAL2_DATE        VARCHAR2(20),  VAL2_ENG  VARCHAR2(50),
    VAL2_STATUS      VARCHAR2(50),  VAL2_REMARK VARCHAR2(500),
    VAL3_DATE        VARCHAR2(20),  VAL3_ENG  VARCHAR2(50),
    SORT_ORDER       NUMBER,
    CONSTRAINT PK_RAWDATA_ROW_H PRIMARY KEY (RAWDATA_ROW_H_SEQ)
);

CREATE SEQUENCE SQ_RAWDATA_ROW   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SQ_RAWDATA_ROW_H START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IDX_RAWDATA_ROW_UPLOAD   ON RAWDATA_ROW   (UPLOAD_SEQ);
CREATE INDEX IDX_RAWDATA_ROW_H_UPLOAD ON RAWDATA_ROW_H (UPLOAD_SEQ);
```

#### (4) 메인 테이블 삭제 방식 변경

현재: 업로드마다 전체 DELETE → 타입별 독립 DELETE로 변경

```
변경 전: DELETE CHIPSET_CELL (전체) → DELETE CHIPSET_ROW (전체) → ...
변경 후: DELETE CHIPSET_CELL    WHERE ROW_SEQ IN (SELECT ROW_SEQ FROM CHIPSET_ROW WHERE UPLOAD_SEQ IN (SELECT UPLOAD_SEQ FROM CHIPSET_UPLOAD WHERE FILE_TYPE = #{fileType}))
         DELETE CHIPSET_ROW     WHERE UPLOAD_SEQ IN (SELECT UPLOAD_SEQ FROM CHIPSET_UPLOAD WHERE FILE_TYPE = #{fileType})
         DELETE CHIPSET_CHIP_COL WHERE UPLOAD_SEQ IN (...)
         DELETE CHIPSET_UPLOAD  WHERE FILE_TYPE = #{fileType}
```

---

### 14-5. API 변경 사항

| 변경 항목 | 현재 | 변경 후 |
|-----------|------|---------|
| 업로드 API | `POST /api/chipset/upload` (타입 구분 없음) | `POST /api/chipset/upload?type={fileType}` 또는 자동 감지 |
| 매트릭스 조회 | `GET /api/chipset/matrix` (단일) | `GET /api/chipset/matrix?type=SERVER` (타입별) |
| 히스토리 목록 | `GET /api/chipset/history` (전체) | `GET /api/chipset/history?type=SERVER` (타입별 필터) |
| Raw_Data 조회 | 없음 | `GET /api/rawdata/matrix`, `GET /api/rawdata/history` 신규 |

**UploadResult 응답 확장:**
```json
{
  "success": true,
  "uploadSeq": 5,
  "fileType": "MOBILE",
  "detectedType": "MOBILE",
  "rowCount": 8,
  "colCount": 12,
  "message": "업로드 완료 (파일타입: MOBILE, 행: 8, 칩: 12)"
}
```

---

### 14-6. UI 설계 권장안

#### 옵션 A: 탭 분리 (권장)

```
┌──────────────────────────────────────────────┐
│  [Server]  [Client]  [Mobile]  [Raw_Data]    │  ← 탭
├──────────────────────────────────────────────┤
│  [↑ XLSX 업로드]   히스토리: [드롭다운 ▼]    │
│                                              │
│  (선택된 탭에 해당하는 매트릭스 표시)          │
└──────────────────────────────────────────────┘
```

**장점**: 명확한 구분, 실수 방지, 각 타입 히스토리 독립 관리  
**단점**: UI 복잡도 증가

#### 옵션 B: 단일 업로드 버튼 + 자동 감지

```
[↑ XLSX 업로드] 클릭
    ↓
파일 선택
    ↓
자동 감지: "Mobile.xlsx → 파일 타입: MOBILE 로 감지되었습니다. 업로드하시겠습니까?"
    ↓ 확인
업로드 완료 → 해당 타입 탭으로 자동 이동
```

**장점**: UX 단순, 파일명 실수에도 자동 교정  
**단점**: 감지 실패 시 사용자 개입 필요

#### 최종 권장: 옵션 A (탭 분리) + 자동 감지 보조

업로드 버튼은 탭마다 별도로 두되, 업로드 시 파일을 자동 감지하여 **탭과 불일치하면 경고** 표시.

```
예: [Mobile] 탭에서 Server.xlsx를 업로드하면
→ "Server.xlsx는 SERVER 형식으로 감지됩니다. [Server] 탭에서 업로드해주세요."
```

---

### 14-7. 작업 우선순위

| 순서 | 작업 | 영향 범위 | 난이도 |
|------|------|-----------|--------|
| 1 | `CHIPSET_UPLOAD`에 `FILE_TYPE` 컬럼 추가 | DB, Service, Mapper | 낮음 |
| 2 | 메인 테이블 DELETE를 `FILE_TYPE` 기준으로 변경 | Service, Mapper XML | 낮음 |
| 3 | `ChipsetExcelParser`에 파일 타입 자동 감지 로직 추가 | Parser | 중간 |
| 4 | `CHIPSET_ROW`에 Mobile 컬럼(PKG, PN, CODE_NM) 추가 | DB, Model, Mapper | 중간 |
| 5 | `RAWDATA_ROW` 신규 테이블 + API 구현 | DB, Service, Controller, Mapper | 높음 |
| 6 | Vue UI 탭 분리 + 타입별 업로드/조회 화면 | Frontend | 중간 |
| MyBatis 파라미터 | `"{item.biztripPlanSeq}` | `#{item.biztripPlanSeq}` | `"` → `#`, 닫는 `}` 누락 |

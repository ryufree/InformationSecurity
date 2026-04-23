# Gradle 설치 및 프로젝트 실행 가이드 (Windows)

> 작성일: 2026-04-22  
> Gradle 8.7 / Java 17 / Spring Boot 3.2.4  
> 설치 경로: `C:\Gradle\gradle-8.7`

---

## 실행 전 확인사항

Java 17이 설치되어 있어야 합니다. ([java-install.md](java-install.md) 참고)

```powershell
java -version
```

---

## Step 1. Gradle 다운로드

브라우저에서 다운로드:
```
https://services.gradle.org/distributions/gradle-8.7-bin.zip
```

---

## Step 2. 압축 해제

`C:\Gradle` 폴더 생성 후 zip 압축 해제.

결과 구조:
```
C:\Gradle\
└── gradle-8.7\
    └── bin\
        ├── gradle
        └── gradle.bat
```

---

## Step 3. 환경변수 PATH 등록 (영구)

```
Windows 키 → "환경 변수" 검색 → 시스템 환경 변수 편집
→ 시스템 변수 "Path" → 편집 → 새로 만들기
→ C:\Gradle\gradle-8.7\bin 입력 → 확인
```

> PATH 등록 후 반드시 **새 PowerShell 창**을 열어야 적용됩니다.

---

## Step 4. 설치 확인

새 PowerShell 창에서:

```powershell
gradle --version
```

---

## Step 5. Gradle Wrapper 생성 (최초 1회)

`gradlew.bat`는 처음에 존재하지 않습니다. 아래 명령으로 생성합니다.

```powershell
cd D:\Gitkraken\InformationSecurity\CP\chipset-app\backend
gradle wrapper
```

생성 후 backend 폴더 구조:
```
backend\
├── gradlew.bat                          ← 생성됨 (Windows 실행 스크립트)
├── gradlew                              ← 생성됨 (Linux/Mac)
├── gradle\wrapper\gradle-wrapper.jar
├── gradle\wrapper\gradle-wrapper.properties
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Step 6. 앱 실행

```powershell
cd D:\Gitkraken\InformationSecurity\CP\chipset-app\backend
.\gradlew.bat bootRun
```

정상 기동 로그:
```
Started ChipsetApplication in X.XXX seconds
```

---

## Step 7. DB 스키마(Oracle) 적용

`backend/src/main/resources/application.yml` 기본 설정은 **Oracle + `spring.sql.init.mode: never`** 이므로,
앱이 `schema.sql`을 자동으로 실행하지 않습니다.

처음 실행(또는 스키마 변경 후)에는 DBeaver 같은 툴로 아래 파일을 실행해 테이블/시퀀스를 생성하세요:

- `backend/src/main/resources/schema.sql`

기존 테이블이 구버전이라 충돌한다면(예: `ORA-00904`, `ORA-00942`) 필요 시 아래도 함께 사용합니다:

- `backend/src/main/resources/schema-drop.sql`

---

## Step 8. 동작 확인

브라우저에서 프론트엔드:
```
http://localhost:5173
```

백엔드 API 직접 확인(포트 9090):
```
http://localhost:9090/api/chipset/matrix?type=SERVER
http://localhost:9090/api/chipset/rawdata
```

---

## PATH 미등록 시 임시 실행 방법

PowerShell 창을 닫으면 사라지는 임시 PATH 설정:

```powershell
$env:PATH += ";C:\Gradle\gradle-8.7\bin"
gradle --version
```

---

## 자주 쓰는 명령어

| 명령 | 설명 |
|------|------|
| `.\gradlew.bat bootRun` | 앱 실행 |
| `.\gradlew.bat build -x test` | 테스트 제외 빌드 |
| `.\gradlew.bat bootJar` | 실행 JAR 생성 (`build/libs/`) |
| `.\gradlew.bat clean` | 빌드 결과물 삭제 |

---

## 오류 해결

| 오류 | 원인 | 해결 |
|------|------|------|
| `gradle 인식되지 않음` | PATH 미등록 | Step 3 재확인, 새 PowerShell 창 열기 |
| `gradlew.bat 인식되지 않음` | wrapper 미생성 | Step 5 실행 |
| `JAVA_HOME is not set` | Java 미설치 | [java-install.md](java-install.md) 참고 |
| `.\gradlew.bat` 앞에 `.\` 필요 | PowerShell 규칙 | `gradlew.bat` → `.\gradlew.bat` |

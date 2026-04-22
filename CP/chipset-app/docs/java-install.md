# Java 17 설치 가이드 (Windows)

> 작성일: 2026-04-22  
> 대상 버전: Java 17 LTS (Eclipse Temurin)  
> 대상 OS: Windows 10 / 11  
> 관련 프로젝트: chipset-app (Spring Boot 3.2.4)

---

## 1. 설치 방법

### 방법 A: winget 으로 설치 (권장)

PowerShell을 **관리자 권한**으로 열고 실행:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

설치 경로 (자동):
```
C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot\
```

---

### 방법 B: 브라우저에서 직접 다운로드

1. https://adoptium.net 접속
2. **Temurin 17 (LTS)** 선택
3. **Windows x64 .msi** 다운로드
4. 설치 마법사 실행

설치 옵션에서 아래 두 항목 체크 권장:
- `Add to PATH`
- `Set JAVA_HOME variable`

---

## 2. JAVA_HOME 환경변수 설정

winget 또는 .msi 설치 시 자동 등록되지 않은 경우 수동으로 설정합니다.

### GUI 방법

```
1. Windows 키 → "환경 변수" 검색
   → "시스템 환경 변수 편집" 클릭

2. [환경 변수(N)...] 버튼 클릭

3. 시스템 변수 영역 → [새로 만들기(W)...]
   변수 이름: JAVA_HOME
   변수 값:   C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot
   (설치된 실제 폴더명 확인 후 입력)

4. 시스템 변수 "Path" 선택 → [편집(I)...]
   → [새로 만들기(N)] 클릭 후 입력:
   %JAVA_HOME%\bin

5. 확인 → 확인 → 확인
```

### PowerShell 방법 (관리자 권한)

```powershell
# 설치된 JDK 경로 확인
$jdkPath = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" | Where-Object { $_.Name -like "jdk-17*" } | Select-Object -First 1).FullName
Write-Host "JDK 경로: $jdkPath"

# JAVA_HOME 설정
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkPath, "Machine")

# PATH 에 추가
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
[System.Environment]::SetEnvironmentVariable("Path", "$currentPath;$jdkPath\bin", "Machine")

Write-Host "완료. 새 PowerShell 창을 열어서 확인하세요."
```

---

## 3. 설치 확인

**새 PowerShell 창**에서 실행 (기존 창은 PATH 변경이 반영 안 됨):

```powershell
java -version
```

정상 출력 예시:
```
openjdk version "17.0.10" 2024-01-16
OpenJDK Runtime Environment Temurin-17.0.10+7 (build 17.0.10+7)
OpenJDK 64-Bit Server VM Temurin-17.0.10+7 (build 17.0.10+7, mixed mode, sharing)
```

```powershell
echo $env:JAVA_HOME
```

출력 예시:
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot
```

---

## 4. Gradle 실행 확인

Java 설치 후 Gradle도 정상 동작하는지 확인:

```powershell
gradle --version
```

정상 출력 예시:
```
------------------------------------------------------------
Gradle 8.7
------------------------------------------------------------
JVM: 17.0.10 (Eclipse Adoptium 17.0.10+7)
OS:  Windows 11 10.0 amd64
```

---

## 5. 프로젝트 실행

```powershell
cd D:\Gitkraken\InformationSecurity\CP\chipset-app\backend

# Gradle Wrapper 생성 (최초 1회)
gradle wrapper

# 앱 실행
.\gradlew.bat bootRun
```

정상 기동 확인 로그:
```
Started ChipsetApplication in X.XXX seconds (process running for X.XXX)
```

브라우저 접속:
```
http://localhost:8080
```

---

## 6. 문제 해결

### `JAVA_HOME is not set`

환경변수가 적용되지 않은 경우 — **새 PowerShell 창**에서 다시 시도.
그래도 안 되면 시스템 재시작 후 확인.

### `java: command not found`

PATH에 `%JAVA_HOME%\bin` 이 없는 경우. 위 2번 단계 재확인.

### 여러 Java 버전이 설치된 경우

현재 사용 중인 Java 확인:
```powershell
where.exe java
java -version
```

원하는 버전으로 JAVA_HOME을 명시적으로 지정하면 됩니다.

---

## 7. 관련 링크

| 리소스 | URL |
|--------|-----|
| Eclipse Temurin (Adoptium) | https://adoptium.net |
| Oracle JDK 17 | https://www.oracle.com/java/technologies/downloads/#java17 |
| Microsoft OpenJDK 17 | https://learn.microsoft.com/ko-kr/java/openjdk/download |
| Java 버전 선택 가이드 | https://whichjdk.com |

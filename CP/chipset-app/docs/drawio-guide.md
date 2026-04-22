# draw.io 사용 가이드

> 작성일: 2026-04-22  
> 공식 사이트: https://www.drawio.com  
> 웹 앱: https://app.diagrams.net  
> GitHub: https://github.com/jgraph/drawio-desktop

---

## 1. draw.io 란?

draw.io (diagrams.net)는 무료 오픈소스 다이어그램 작성 도구입니다.

| 항목 | 내용 |
|------|------|
| 라이선스 | 무료 (Apache 2.0) |
| 사용 방식 | 웹 앱 / 데스크탑 앱 / VS Code 익스텐션 |
| 저장 형식 | `.drawio` (XML), `.xml`, `.png`, `.svg`, `.pdf` |
| 주요 용도 | ERD, 플로우차트, 시퀀스 다이어그램, 아키텍처 다이어그램, 네트워크 다이어그램 |
| 협업 | 파일 공유 방식 (Google Drive, OneDrive, GitHub 연동 가능) |

---

## 2. 설치 방법

### 2-1. 웹 앱 (설치 불필요)

브라우저에서 바로 사용:
```
https://app.diagrams.net
```

### 2-2. 데스크탑 앱 (Windows)

```
1. https://github.com/jgraph/drawio-desktop/releases 접속
2. 최신 버전의 drawio-X.X.X-windows-installer.exe 다운로드
3. 설치 후 실행
```

특징:
- 오프라인 사용 가능
- 로컬 파일 직접 저장
- 웹 앱보다 빠른 응답

### 2-3. VS Code 익스텐션 (개발자 추천)

```
VS Code → Extensions (Ctrl+Shift+X)
→ "Draw.io Integration" 검색
→ 작성자: Henning Dieterichs (hediet.vscode-drawio)
→ Install
```

설치 후: `.drawio` 파일을 클릭하면 VS Code 내에서 바로 다이어그램 편집기가 열립니다.

---

## 3. 파일 열기

### 웹 앱에서 열기
```
app.diagrams.net 접속
→ "Open Existing Diagram" 클릭
→ Device 선택 → db-design.drawio 파일 선택
```

### 데스크탑 앱에서 열기
```
파일(File) → 열기(Open) → db-design.drawio 선택
```

### VS Code에서 열기
```
탐색기에서 db-design.drawio 파일 클릭
→ 자동으로 다이어그램 뷰 열림
```

---

## 4. 기본 화면 구성

```
┌──────────────────────────────────────────────────────────┐
│  메뉴바: File / Edit / View / Arrange / Extras / Help    │
├──────────┬───────────────────────────────┬───────────────┤
│          │                               │               │
│  도형    │        캔버스 영역            │  속성 패널    │
│  패널    │    (다이어그램 그리는 곳)     │  (선택 시     │
│  (좌측)  │                               │   표시)       │
│          │                               │               │
├──────────┴───────────────────────────────┴───────────────┤
│  하단: 페이지 탭  |  페이지 추가 (+)  |  확대/축소     │
└──────────────────────────────────────────────────────────┘
```

---

## 5. 기본 조작법

### 5-1. 도형 추가
| 방법 | 설명 |
|------|------|
| 좌측 패널에서 드래그 | 원하는 도형을 캔버스로 끌어다 놓기 |
| 캔버스 더블클릭 | 텍스트 상자 바로 생성 |
| 단축키 `A` | 검색창 열기 → 도형 이름 검색 |

### 5-2. 연결선 (화살표) 그리기
| 방법 | 설명 |
|------|------|
| 도형에 마우스 오버 | 파란 연결 포인트(▶) 표시됨 |
| 파란 포인트 드래그 | 다른 도형으로 연결선 생성 |
| 연결선 더블클릭 | 라벨 텍스트 추가 |

### 5-3. 주요 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl + Z` | 실행 취소 |
| `Ctrl + Y` | 다시 실행 |
| `Ctrl + S` | 저장 |
| `Ctrl + Shift + H` | 전체 화면에 맞추기 |
| `Ctrl + Shift + F` | 검색 |
| `Ctrl + A` | 전체 선택 |
| `Ctrl + C / V` | 복사 / 붙여넣기 |
| `Del` | 선택 삭제 |
| `Ctrl + G` | 그룹화 |
| `Ctrl + Shift + G` | 그룹 해제 |
| `Ctrl + +` / `Ctrl + -` | 확대 / 축소 |
| `Ctrl + Shift + H` | 화면에 맞게 전체 보기 |
| `Space + 드래그` | 캔버스 이동 (패닝) |
| `F2` | 선택된 도형 텍스트 편집 |

---

## 6. 도형 스타일 편집

### 6-1. 우클릭 메뉴
도형 우클릭 → `Edit Style` (단축키 `Ctrl + E`)

스타일 문자열 예시:
```
rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;
```

### 6-2. 주요 스타일 속성

| 속성 | 설명 | 예시 |
|------|------|------|
| `fillColor` | 배경색 | `#dae8fc` |
| `strokeColor` | 테두리색 | `#6c8ebf` |
| `fontColor` | 글자색 | `#1E3A5F` |
| `fontSize` | 글자 크기 | `12` |
| `fontStyle` | 글자 스타일 | `1`=굵게, `2`=기울임, `4`=밑줄 |
| `rounded` | 모서리 둥글게 | `1`=둥글게, `0`=직각 |
| `dashed` | 점선 | `1`=점선, `0`=실선 |
| `opacity` | 투명도 | `50` (0~100) |
| `align` | 가로 정렬 | `left`, `center`, `right` |
| `verticalAlign` | 세로 정렬 | `top`, `middle`, `bottom` |

### 6-3. 연결선 스타일

ERD용 화살표:
```
endArrow=ERmany;startArrow=ERmandOne;strokeWidth=2;
```

| 화살표 타입 | 값 | 의미 |
|------------|-----|------|
| `ERmany` | N (여러 개) | 다 쪽 (fork 모양) |
| `ERmandOne` | 1 (반드시 1) | 일 쪽 (수직선) |
| `ERone` | 1 (선택적) | 일 쪽 |
| `open` | 열린 화살표 | 일반 방향 표시 |
| `block` | 채워진 삼각형 | 클래스 상속 등 |

---

## 7. 페이지 관리 (탭)

하단 탭에서 페이지 관리:

| 동작 | 방법 |
|------|------|
| 페이지 추가 | 하단 `+` 클릭 |
| 페이지 이름 변경 | 탭 더블클릭 |
| 페이지 순서 변경 | 탭 드래그 |
| 페이지 삭제 | 탭 우클릭 → Delete |
| 페이지 복사 | 탭 우클릭 → Duplicate |

`db-design.drawio`는 2개 페이지로 구성:
- **Page 1**: 메인 테이블 ERD (CHIPSET_UPLOAD 중심)
- **Page 2**: 히스토리 테이블 구조 (_H 테이블 5개)

---

## 8. 내보내기 (Export)

### 이미지로 내보내기
```
파일(File) → 내보내기(Export As) → PNG / SVG / JPEG / PDF
```

| 형식 | 용도 |
|------|------|
| PNG | 문서 삽입용 (해상도 설정 가능) |
| SVG | 웹 삽입, 벡터 (확대해도 선명) |
| PDF | 인쇄용 |
| XML | 백업 또는 다른 도구 호환 |

### Word/PPT에 삽입
```
1. draw.io → Export As → PNG (300 DPI 권장)
2. Word → 삽입 → 그림 → 내보낸 PNG 선택
```

---

## 9. 유용한 기능

### 9-1. 자동 레이아웃
```
메뉴 → Arrange → Layout → 원하는 레이아웃 선택
  - Horizontal Tree  : 수평 트리
  - Vertical Tree    : 수직 트리
  - Organic          : 자동 배치
  - Circle           : 원형 배치
```

### 9-2. 도형 정렬
여러 도형 선택 후:
```
우클릭 → Edit → Align
  - 좌측 맞춤 / 우측 맞춤 / 가운데 맞춤
  - 상단 맞춤 / 하단 맞춤 / 수직 중앙
  - 간격 균등 배분
```

### 9-3. 컨테이너 (그룹 박스)
도형들을 하나의 박스로 묶기:
```
여러 도형 선택 → 우클릭 → Group
또는
좌측 패널 → Container 카테고리의 도형 사용
```

### 9-4. 레이어
복잡한 다이어그램에서 레이어 분리:
```
View → Layers
→ 레이어 추가 / 숨기기 / 잠금 가능
```

### 9-5. 찾기/바꾸기
```
Ctrl + Shift + F → 텍스트 검색
Edit → Find/Replace → 텍스트 일괄 변경
```

### 9-6. XML 직접 편집
```
Extras → Edit Diagram (Ctrl + Shift + X)
→ 다이어그램의 XML 소스 직접 편집 가능
→ db-design.drawio의 XML을 붙여넣기하여 불러올 수 있음
```

---

## 10. 저장 및 관리

### 저장 형식
```
파일 → 저장 (Ctrl + S) → .drawio 형식으로 저장
파일 → 다른 이름으로 저장 → .xml 형식도 가능 (.drawio와 동일한 XML)
```

### 자동 저장
- 데스크탑 앱: 변경 시 자동 저장 옵션 있음
- 웹 앱: 브라우저 탭 닫기 전 반드시 수동 저장

### Google Drive / OneDrive 연동
```
웹 앱(app.diagrams.net) 접속 시
→ Google Drive 또는 OneDrive 선택
→ 파일을 클라우드에 자동 저장
```

---

## 11. VS Code 익스텐션 상세

**hediet.vscode-drawio** 익스텐션 기능:

| 기능 | 설명 |
|------|------|
| 파일 연동 | `.drawio` 파일 저장 시 VS Code 파일 시스템에 즉시 반영 |
| 미리보기 | 편집 중 실시간 다이어그램 확인 |
| XML 편집 | 탭 우클릭 → "Open as XML" 로 소스 편집 가능 |
| 테마 | VS Code 다크/라이트 테마 자동 적용 |
| 단축키 | VS Code 단축키와 통합 |

설정 (settings.json):
```json
{
  "hediet.vscode-drawio.theme": "dark",
  "hediet.vscode-drawio.zoomFactor": 1.0
}
```

---

## 12. 이번 프로젝트 파일 활용

`docs/db-design.drawio` 구성:

### Page 1: 메인 테이블 ERD
| 테이블 | 색상 | 위치 |
|--------|------|------|
| CHIPSET_UPLOAD | 파란색 | 중앙 상단 |
| CHIPSET_CHIP_COL | 보라색 | 우측 |
| CHIPSET_ROW | 초록색 | 좌측 하단 |
| CHIPSET_CELL | 주황색 | 중앙 하단 |
| RAWDATA_ROW | 빨간색 | 우측 끝 |

관계 화살표: `ERmandOne → ERmany` (1:N 표기)

### Page 2: 히스토리 테이블
- CHIPSET_UPLOAD_H / CHIPSET_CHIP_COL_H / CHIPSET_ROW_H / CHIPSET_CELL_H / RAWDATA_ROW_H

### 다이어그램 수정 방법
```
1. db-design.drawio 열기 (draw.io 앱 또는 VS Code 익스텐션)
2. 테이블 클릭 → 컬럼 텍스트 더블클릭 → 내용 수정
3. 컬럼 추가: 기존 컬럼 행 복사(Ctrl+C) → 붙여넣기(Ctrl+V) → 텍스트 수정
4. 저장: Ctrl+S
```

---

## 13. 관련 링크

| 리소스 | URL |
|--------|-----|
| 공식 웹 앱 | https://app.diagrams.net |
| 데스크탑 앱 다운로드 | https://github.com/jgraph/drawio-desktop/releases |
| VS Code 익스텐션 | https://marketplace.visualstudio.com/items?itemName=hediet.vscode-drawio |
| 공식 문서 | https://www.drawio.com/doc |
| 스타일 레퍼런스 | https://www.drawio.com/doc/faq/shape-styles |
| YouTube 튜토리얼 | https://www.youtube.com/@drawio |

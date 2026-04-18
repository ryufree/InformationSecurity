# ChipsetValidation — 작업 히스토리

> 다음 세션에서 바로 이어서 작업할 수 있도록 의사결정과 변경 이력을 기록합니다.

---

## 프로젝트 구조

```
d:/MARU/
├── ChipsetValidation.vue        ← 원본 컴포넌트 (편집 금지, 마스터 소스)
├── ChipsetController.java       ← Spring Boot REST 컨트롤러 (백엔드 참고용)
├── ChipsetDTOs.java             ← DTO 클래스
├── ChipsetExcelService.java     ← 서버사이드 Excel 파싱 서비스
├── README.md                    ← 아키텍처·파싱 로직 가이드
├── HISTORY.md                   ← 이 파일
└── chipset-app/                 ← Vue 3 + Vite 테스트 환경
    ├── src/
    │   ├── components/
    │   │   └── ChipsetValidation.vue   ← 실제 개발/수정 파일
    │   ├── App.vue                     ← ChipsetValidation만 렌더링
    │   └── main.js                     ← style.css import 제거됨
    ├── index.html                      ← body background #0d0e14 설정됨
    └── package.json                    ← xlsx 패키지 설치됨
```

**개발 서버 실행:**
```bash
cd d:/MARU/chipset-app
npm run dev
# → http://localhost:5173 (혹은 5174, 5175... 포트 충돌 시 자동 증가)
```

---

## 세션 히스토리

### 세션 1 — Vue 테스트 환경 구축

**배경:** `ChipsetValidation.vue` 파일만 존재하고 실행할 껍데기가 없었음.

**작업 내용:**
1. `npm create vite@latest chipset-app -- --template vue` 로 프로젝트 생성
2. `npm install xlsx` — SheetJS 의존성 추가
3. `App.vue` → `ChipsetValidation.vue`만 렌더링하도록 단순화
4. `main.js` → 기본 `style.css` import 제거 (컴포넌트 자체 스타일과 충돌 방지)
5. `index.html` → `<body>` 배경색 `#0d0e14` 설정 (컴포넌트 배경색과 일치)

---

### 세션 1 — Frozen 컬럼 & Intel/AMD 레이아웃 수정

**요구사항:**
- DIMM / Product (Ver.) / Ver. / Density / Org / Speed 컬럼 좌측 고정
- Intel과 AMD가 나머지 공간을 정확히 50:50으로 차지
- Intel 영역 / AMD 영역 각각 독립적인 가로 스크롤

**시도 1 — sticky 강화 (단일 테이블 방식):**
- `frozenStyle()`에 `width` 속성 명시
- `.cv-td--frozen`에 `isolation: isolate` 추가
- `chipGroupWidth` computed 추가: 양 그룹 total px 동일하게 설정
- **한계:** 단일 `<table>` 구조에서는 그룹별 독립 스크롤 불가능

**시도 2 — 3-Pane 레이아웃 (채택):**

단일 테이블을 완전히 버리고 3개의 독립 pane으로 재설계.

```
┌──────────────────┬──────────────────────┬──────────────────────┐
│  Sticky Header   │                      │                      │
│  (position:      │   Intel Header       │   AMD Header         │
│   sticky top:0)  │   (overflow:hidden)  │   (overflow:hidden)  │
├──────────────────┼──────────────────────┼──────────────────────┤
│                  │                      │                      │
│  Frozen Body     │   Intel Body         │   AMD Body           │
│  (overflow:      │   (overflow-x:auto)  │   (overflow-x:auto)  │
│   hidden)        │   ← 독립 스크롤      │   ← 독립 스크롤      │
│                  │                      │                      │
└──────────────────┴──────────────────────┴──────────────────────┘
      수직 스크롤: .cv-layout (overflow-y: auto) 가 전체 담당
```

**핵심 구현 포인트:**

| 항목 | 구현 방법 |
|------|-----------|
| 50:50 분할 | Intel pane, AMD pane 모두 `flex: 1` |
| Frozen 고정 | 별도 pane으로 분리, `flex: none` + 고정 px 너비 |
| 독립 가로 스크롤 | `.cv-body-chip { overflow-x: auto }` |
| 헤더-바디 스크롤 동기화 | `onIntelScroll()`, `onAmdScroll()` — body 스크롤 이벤트 → header.scrollLeft 동기화 |
| 수직 스크롤 통합 | `.cv-layout { overflow-y: auto }` 한 곳에서 처리 |
| 헤더 고정 (sticky) | `.cv-head-row { position: sticky; top: 0; z-index: 30 }` — .cv-layout 기준으로 sticky |
| 컬럼 너비 일치 | `CHIP_COL_W = 100` 상수를 header `<th>`와 body `<td>` 양쪽에 동일하게 적용 |

**제거된 것들:**
- `tableScroll` ref (→ `intelBodyRef`, `amdBodyRef` 등으로 교체)
- `chipGroupWidth` computed (→ 단순 상수 `CHIP_COL_W`로 교체)
- `chipColStyle`, `chipThStyle` 함수
- `tableMinWidth` computed
- `frozenLeft` computed, `frozenStyle()` 함수 (pane 분리로 sticky 불필요)
- `totalCols` computed

---

## 현재 컴포넌트 상태 (chipset-app/src/components/ChipsetValidation.vue)

### Script — 주요 상태/함수

```js
// 상수
const CHIP_COL_W = 100  // 모든 칩 컬럼 고정 너비(px)

// Refs
const rows       = ref([])      // 파싱된 데이터 행
const chipGroups = ref([])      // [{ name, type:'intel'|'amd', cols:[{key,chip,date}] }]
const cellColors = ref({})      // { 'rowIdx_colKey': '#rrggbb' }
const lastVersion = ref('')
const intelHeadRef = ref(null)  // Intel 헤더 pane DOM ref
const amdHeadRef   = ref(null)  // AMD 헤더 pane DOM ref
const intelBodyRef = ref(null)  // Intel 바디 pane DOM ref
const amdBodyRef   = ref(null)  // AMD 바디 pane DOM ref

// Computed
const intelGroup       // chipGroups에서 type==='intel' 찾기
const amdGroup         // chipGroups에서 type==='amd' 찾기
const frozenTotalWidth // frozenCols 너비 합계 (px)
const filteredRows     // specFilters + filterDate 적용 결과

// 함수
onIntelScroll(e)  // Intel body 스크롤 시 → intelHeadRef.scrollLeft 동기화
onAmdScroll(e)    // AMD body 스크롤 시 → amdHeadRef.scrollLeft 동기화
parseExcel(buffer)
resetFilters()
downloadExcel()
```

### Frozen 컬럼 정의

```js
const frozenCols = [
  { key: 'dimm',    label: 'DIMM',          width: 90  },
  { key: 'product', label: 'Product (Ver.)', width: 130 },
  { key: 'ver',     label: 'Ver.',           width: 50  },
  { key: 'density', label: 'Density',        width: 70  },
  { key: 'org',     label: 'Org',            width: 60  },
  { key: 'speed',   label: 'Speed',          width: 70  },
]
// 합계: 470px → frozenTotalWidth
```

---

## 알려진 이슈 / 다음 작업 후보

| # | 항목 | 상태 | 비고 |
|---|------|------|------|
| 1 | Frozen body/Intel body/AMD body 행 높이 불일치 가능성 | 미확인 | 셀 내용이 `white-space: nowrap`이므로 대부분 1줄 → 높이 동일 예상. 긴 Product 이름 입력 시 확인 필요 |
| 2 | 칩 컬럼 너비 `CHIP_COL_W = 100px` 고정 | 동작 중 | 칩 이름이 100px 초과 시 잘림. 필요 시 동적 계산으로 변경 가능 |
| 3 | Spec 필터 cascading (DIMM 선택 → Product 필터링) | 미구현 | README.md에 설계 메모 있음 |
| 4 | LPD 양식 파싱 분기 | 미구현 | README.md에 설계 메모 있음 |
| 5 | Excel 다운로드 시 셀 배경색 유지 | 미구현 | SheetJS Pro 기능. ExcelJS로 교체 필요 |
| 6 | EPERM 오류 (Vite 캐시) | 간헐적 | `node_modules/.vite` 폴더 삭제 후 재시작으로 해결 |

---

## 트러블슈팅

### Vite EPERM 오류
```
Error: EPERM: operation not permitted, rename '.vite/deps_temp_xxx' -> '.vite/deps'
```
**해결:**
```bash
cd d:/MARU/chipset-app
rm -rf node_modules/.vite
npm run dev
```

### 포트 충돌
5173이 사용 중이면 5174, 5175... 자동 증가. 터미널 출력에서 실제 포트 확인.

---

## Excel 파일 구조 (파싱 기준)

```
행 0 (선택):  날짜 표기 (e.g. "04' 26")
행 1:         [병합셀: DIMM~Speed 고정] [병합셀: Intel] [병합셀: AMD]
행 2:         칩 이름 (SPR-SP, EMR-SP, ... GENOA, TURIN ...)
행 3:         출시일자 (01 '23, 12 '23 ...)
행 4+:        데이터 (RDIMM, LRDIMM 등)
```

**파싱 우선순위:**
1. `ws['!merges']`에서 Intel/AMD 병합 범위 탐지
2. 없으면 헤더 행 스캔에서 'INTEL', 'AMD' 텍스트로 폴백

**출시일자 형식:** `mm 'yy` → `{ m: number, y: number }` (parseChipDate 함수)

# Chipset Validation - 구현 가이드

## 아키텍처 결정

### 프론트엔드 파싱 방식 (권장)
Excel 파일을 **Vue 컴포넌트에서 SheetJS로 직접 파싱**합니다.
- 서버 왕복 없이 즉각적인 반응
- 대용량 파일도 브라우저 메모리에서 처리
- 셀 색상 추출은 SheetJS 기본 파싱으로 가능

### 서버 파싱 방식 (선택)
DB 저장이나 보안이 필요하면 Apache POI 기반 서비스 사용.

---

## build.gradle 의존성 추가

```groovy
dependencies {
    // Spring Boot Web
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // MyBatis
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3'

    // Apache POI (서버사이드 Excel 파싱)
    implementation 'org.apache.poi:poi:5.2.5'
    implementation 'org.apache.poi:poi-ooxml:5.2.5'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Oracle JDBC
    runtimeOnly 'com.oracle.database.jdbc:ojdbc11'
}
```

---

## Vue 프론트엔드 설정

### package.json 추가
```json
{
  "dependencies": {
    "xlsx": "^0.18.5",
    "axios": "^1.6.0"
  }
}
```

### vite.config.js proxy 설정
```js
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

---

## 엑셀 구조 파싱 로직

```
행 0:  04' 26  (날짜 행 - 무시 또는 표시용)
행 1:  DIMM | Product | Ver. | Density | Org | Speed | [Intel (병합)] | [AMD (병합)]
행 2:  (sub-headers) SPR-SP | EMR-SP | ... | GENOA | TURIN | ...
행 3:  (출시일자) 01 '23 | 12 '23 | ... | 12 '23 | 08 '24 | ...
행 4+: 실제 데이터
```

### 필터 조건 처리

#### 출시일자 필터 (>= YYYY-MM)
- A~F 이외 컬럼의 값이 `mm 'yy` 형태
- 파싱: `01 '23` → `{ m:1, y:2023 }`
- 해당 행에 필터 날짜 이후 데이터가 하나라도 있으면 행 표시

#### Spec 필터 (DIMM, Product, Ver., Density, Org, Speed)
- 계층형(하이어라키) 구조:
  - DIMM 선택 → Product 옵션이 해당 DIMM의 값만 표시
  - Product 선택 → Ver. 옵션이 해당 Product 값만 표시
  - (cascading dropdown 구현 권장)

---

## 빈 셀 처리
- 값이 없는 칩 데이터 셀 → `cv-td--empty` 클래스 → 옅은 회색 배경
- 엑셀 셀 배경색이 있으면 해당 색상 우선 적용

---

## LPD 양식 처리
LPD는 RawData 양식이 다름. 파싱 시 시트명 또는 파일명으로 분기:
```javascript
const isLPD = filename.toUpperCase().includes('LPD')
if (isLPD) {
  // LPD 전용 파싱 로직 (헤더 행 위치, 컬럼 매핑 다를 수 있음)
} else {
  // 일반 파싱
}
```

---

## Excel 다운로드
- SheetJS `XLSX.writeFile()` 사용
- 현재 필터링된 행만 다운로드
- 헤더 2중 구조(그룹 헤더 + 칩 이름) 재구성

---

## 주의사항
- SheetJS 무료 버전: 셀 스타일 읽기 지원하나 쓰기는 Pro 기능
  → 다운로드 시 원본 색상 유지가 필요하면 ExcelJS 사용
- `cellStyles: true` 옵션을 `XLSX.read()`에 반드시 포함해야 배경색 읽힘

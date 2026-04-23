package com.chipset.model;

import lombok.Data;

/**
 * CHIPSET_SPEC_COL 테이블 모델 (신규)
 * CHIPSET_ROW 의 col1..col10 이 실제 어떤 컬럼명을 가지는지 저장
 * 예) col_idx=1, col_nm="DIMM" / col_idx=2, col_nm="Product (Ver.)"
 * 이 값을 UI에서 읽어 컬럼 헤더를 동적으로 렌더링 (하드코딩 제거)
 */
@Data
public class ChipsetSpecCol {
    private Long    specColSeq;
    private Long    uploadSeq;
    private Integer colIdx;      // 1-based (1=col1 ... 최대 10=col10)
    private String  colNm;       // Excel 원본 헤더명 (예: "DIMM", "Product (Ver.)")
    private Integer sortOrder;
}

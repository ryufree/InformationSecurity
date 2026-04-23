package com.chipset.model;

import lombok.Data;

/**
 * CHIPSET_CELL_COL 테이블 모델 (구 ChipsetChipCol / CHIPSET_CHIP_COL)
 * CHIPSET_CELL 의 열 정의를 담는 테이블임을 명확히 하기 위해 이름 변경
 */
@Data
public class ChipsetCellCol {
    private Long    colSeq;
    private Long    uploadSeq;
    private String  vendor;      // 동적: "INTEL", "AMD", 기타 벤더명
    private Integer colIdx;      // 원본 Excel 컬럼 인덱스
    private String  chipNm;
    private String  chipDt;
    private Integer sortOrder;
}

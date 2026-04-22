package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetChipCol {
    private Long    colSeq;
    private Long    uploadSeq;
    private String  vendor;      // 동적: "INTEL", "AMD", 기타 벤더명
    private Integer colIdx;      // 원본 Excel 컬럼 인덱스
    private String  chipNm;
    private String  chipDt;
    private Integer sortOrder;
}

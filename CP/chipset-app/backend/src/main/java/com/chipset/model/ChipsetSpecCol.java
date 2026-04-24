package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetSpecCol {
    private Long    colSeq;       // PK (renamed from specColSeq, matches COL_SEQ in DB)
    private Long    uploadSeq;
    private Integer colIdx;       // 1-based (1=first spec col, max 10)
    private String  colNm;
    private Integer sortOrder;
}

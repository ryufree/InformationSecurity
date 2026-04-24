package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetRawdataCol {
    private Long    colSeq;
    private Long    uploadSeq;
    private Integer colIdx;
    private String  colNm;
    private Integer sortOrder;
}

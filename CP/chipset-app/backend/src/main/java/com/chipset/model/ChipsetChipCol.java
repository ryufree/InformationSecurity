package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetChipCol {
    private Long    colSeq;
    private Long    uploadSeq;
    private String  vendor;
    private Integer colIdx;
    private String  chipNm;
    private String  chipDt;
    private Integer sortOrder;
}

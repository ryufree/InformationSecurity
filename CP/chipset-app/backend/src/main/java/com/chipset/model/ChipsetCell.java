package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetCell {
    private Long    cellSeq;
    private Long    uploadSeq;
    private Integer rowIdx;     // 0-based row number within the upload
    private String  colType;    // 'SPEC' | 'CHIP' | 'RAWDATA'
    private Long    colSeq;     // FK to the matching col table (app-level integrity)
    private String  cellValue;
    private String  bgColor;
}

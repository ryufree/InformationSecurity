package com.chipset.model;

import lombok.Data;

@Data
public class ChipsetCell {
    private Long   cellSeq;
    private Long   rowSeq;
    private Long   colSeq;
    private String cellValue;
    private String bgColor;
}

package com.chipset.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class MatrixResponse {
    private Long                 uploadSeq;
    private Date                 uploadDt;
    private String               fileType;
    private List<String>         vendors;
    private List<ChipsetChipCol> chipCols;
    private List<ChipsetSpecCol> specCols;
    private List<MatrixRow>      rows;
}

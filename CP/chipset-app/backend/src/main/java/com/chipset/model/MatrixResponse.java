package com.chipset.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class MatrixResponse {
    private Long                 uploadSeq;
    private Date                 uploadDt;
    private List<String>         vendors;   // 중복 제거된 벤더 목록 (순서 유지)
    private List<ChipsetChipCol> chipCols;
    private List<ChipsetRow>     rows;
}

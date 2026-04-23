package com.chipset.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class MatrixResponse {
    private Long                  uploadSeq;
    private Date                  uploadDt;
    private String                fileType;
    private List<String>          vendors;
    private List<ChipsetCellCol>  chipCols;   // 셀 컬럼 (벤더/칩 정보)
    private List<ChipsetSpecCol>  specCols;   // 스펙 컬럼 메타 (UI 헤더 동적 렌더링용)
    private List<ChipsetRow>      rows;
}

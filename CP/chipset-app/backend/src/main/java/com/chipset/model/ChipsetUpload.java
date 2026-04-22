package com.chipset.model;

import lombok.Data;
import java.util.Date;

@Data
public class ChipsetUpload {
    private Long   uploadSeq;
    private String fileNm;
    private String fileType;   // SERVER | CLIENT | MOBILE | RAW_DATA
    private Date   uploadDt;
    private int    rowCount;
    private int    colCount;
}

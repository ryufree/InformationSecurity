package com.chipset.model;

import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class RawDataResponse {
    private Long                    uploadSeq;
    private Date                    uploadDt;
    private String                  fileType;
    private List<ChipsetRawdataCol> rawdataCols;
    private List<RawDataRow>        rows;
}

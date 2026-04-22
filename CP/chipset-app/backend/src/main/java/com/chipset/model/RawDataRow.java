package com.chipset.model;

import lombok.Data;

@Data
public class RawDataRow {
    private Long   rawdataRowSeq;
    private Long   uploadSeq;
    private String company;
    private String seg;
    private String chipset;
    private String socCs;
    private String partNumber;
    private String dramProcess;
    private String flashProcess;
    private String density;
    private String mlcTlc;
    private String pkg;
    private String val1Date;
    private String val1Eng;
    private String val1Status;
    private String val1Remark;
    private String val2Date;
    private String val2Eng;
    private String val2Status;
    private String val2Remark;
    private String val3Date;
    private String val3Eng;
    private Integer sortOrder;
}

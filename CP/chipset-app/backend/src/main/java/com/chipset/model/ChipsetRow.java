package com.chipset.model;

import lombok.Data;
import java.util.List;

@Data
public class ChipsetRow {
    private Long             rowSeq;
    private Long             uploadSeq;
    private String           dimm;
    private String           product;
    private String           ver;
    private String           density;
    private String           org;
    private String           speed;
    private Integer          sortOrder;
    private List<ChipsetCell> cells;   // 조회 시 MyBatis collection 매핑
}

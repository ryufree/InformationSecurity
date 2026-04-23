package com.chipset.model;

import lombok.Data;
import java.util.List;

/**
 * CHIPSET_ROW 테이블 모델
 * 구 버전: dimm, product, ver, density, org, speed (6개 고정 컬럼, 타입별 하드코딩)
 * 현 버전: col1 ~ col10 (최대 10개 동적 컬럼)
 *          실제 컬럼명은 CHIPSET_SPEC_COL 테이블에서 조회 (colIdx 1-based 대응)
 */
@Data
public class ChipsetRow {
    private Long   rowSeq;
    private Long   uploadSeq;
    private String col1;
    private String col2;
    private String col3;
    private String col4;
    private String col5;
    private String col6;
    private String col7;
    private String col8;
    private String col9;
    private String col10;
    private Integer sortOrder;
    private List<ChipsetCell> cells;  // 조회 시 MyBatis collection 매핑
}

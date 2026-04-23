package com.chipset.model;

import lombok.Data;

/**
 * CHIPSET_CELL 테이블 모델
 * UPLOAD_SEQ 추가: 타입별 직접 DELETE 가능 (서브쿼리 없이), 히스토리 연결 명확화
 */
@Data
public class ChipsetCell {
    private Long   cellSeq;
    private Long   rowSeq;
    private Long   colSeq;
    private Long   uploadSeq;  // 추가: 직접 upload 단위로 관리 가능
    private String cellValue;
    private String bgColor;
}

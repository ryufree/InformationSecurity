package com.chipset.model;

import lombok.Data;

@Data
public class UploadResult {
    private boolean success;
    private Long    uploadSeq;
    private int     rowCount;
    private int     colCount;
    private String  message;

    public static UploadResult success(Long uploadSeq, int rowCount, int colCount) {
        UploadResult r = new UploadResult();
        r.success   = true;
        r.uploadSeq = uploadSeq;
        r.rowCount  = rowCount;
        r.colCount  = colCount;
        r.message   = "업로드 완료 (행: " + rowCount + ", 칩 컬럼: " + colCount + ")";
        return r;
    }

    public static UploadResult error(String message) {
        UploadResult r = new UploadResult();
        r.success = false;
        r.message = message;
        return r;
    }
}

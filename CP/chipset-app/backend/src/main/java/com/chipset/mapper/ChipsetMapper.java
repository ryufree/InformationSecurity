package com.chipset.mapper;

import com.chipset.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChipsetMapper {

    // ── 메인: DELETE (파일 타입별) ──────────────────────────────
    void deleteCellsByFileType(@Param("fileType") String fileType);      // UPLOAD_SEQ 직접 사용으로 단순화
    void deleteRowsByFileType(@Param("fileType") String fileType);
    void deleteCellColsByFileType(@Param("fileType") String fileType);   // 구 deleteChipColsByFileType
    void deleteSpecColsByFileType(@Param("fileType") String fileType);   // 신규
    void deleteUploadsByFileType(@Param("fileType") String fileType);
    void deleteRawDataRowsByFileType(@Param("fileType") String fileType);

    // ── 메인: INSERT ─────────────────────────────────────────────
    void insertUpload(ChipsetUpload upload);
    void insertCellCol(ChipsetCellCol col);       // 구 insertChipCol
    void insertSpecCol(ChipsetSpecCol specCol);   // 신규
    void insertRow(ChipsetRow row);
    void insertCell(ChipsetCell cell);
    void insertRawDataRow(RawDataRow row);

    // ── 메인: SELECT (타입별) ─────────────────────────────────────
    ChipsetUpload        selectLatestUploadByType(@Param("fileType") String fileType);
    List<ChipsetCellCol> selectCellColsByType(@Param("fileType") String fileType);    // 구 selectChipColsByType
    List<ChipsetSpecCol> selectSpecColsByType(@Param("fileType") String fileType);    // 신규
    List<ChipsetRow>     selectRowsByType(@Param("fileType") String fileType);
    List<RawDataRow>     selectRawDataRowsByType(@Param("fileType") String fileType);

    // ── 히스토리: INSERT ─────────────────────────────────────────
    void insertUploadH(ChipsetUpload upload);
    void insertCellColH(ChipsetCellCol col);       // 구 insertChipColH
    void insertSpecColH(ChipsetSpecCol specCol);   // 신규
    void insertRowH(ChipsetRow row);
    void insertCellH(ChipsetCell cell);            // uploadSeq 이제 cell 객체에 포함
    void insertRawDataRowH(@Param("row") RawDataRow row);

    // ── 히스토리: SELECT ─────────────────────────────────────────
    List<ChipsetUpload>  selectHistoryByType(@Param("fileType") String fileType);
    List<ChipsetCellCol> selectCellColsH(@Param("uploadSeq") Long uploadSeq);        // 구 selectChipColsH
    List<ChipsetSpecCol> selectSpecColsH(@Param("uploadSeq") Long uploadSeq);        // 신규
    List<ChipsetRow>     selectRowsH(@Param("uploadSeq") Long uploadSeq);
    List<RawDataRow>     selectRawDataRowsH(@Param("uploadSeq") Long uploadSeq);
}

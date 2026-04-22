package com.chipset.mapper;

import com.chipset.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChipsetMapper {

    // ── 메인: DELETE (파일 타입별) ──────────────────────────────
    void deleteCellsByFileType(@Param("fileType") String fileType);
    void deleteRowsByFileType(@Param("fileType") String fileType);
    void deleteChipColsByFileType(@Param("fileType") String fileType);
    void deleteUploadsByFileType(@Param("fileType") String fileType);
    void deleteRawDataRowsByFileType(@Param("fileType") String fileType);

    // ── 메인: INSERT ─────────────────────────────────────────────
    void insertUpload(ChipsetUpload upload);
    void insertChipCol(ChipsetChipCol col);
    void insertRow(ChipsetRow row);
    void insertCell(ChipsetCell cell);
    void insertRawDataRow(RawDataRow row);

    // ── 메인: SELECT (타입별) ─────────────────────────────────────
    ChipsetUpload        selectLatestUploadByType(@Param("fileType") String fileType);
    List<ChipsetChipCol> selectChipColsByType(@Param("fileType") String fileType);
    List<ChipsetRow>     selectRowsByType(@Param("fileType") String fileType);
    List<RawDataRow>     selectRawDataRowsByType(@Param("fileType") String fileType);

    // ── 히스토리: INSERT ─────────────────────────────────────────
    void insertUploadH(ChipsetUpload upload);
    void insertChipColH(ChipsetChipCol col);
    void insertRowH(ChipsetRow row);
    void insertCellH(@Param("cell") ChipsetCell cell,
                     @Param("uploadSeq") Long uploadSeq);
    void insertRawDataRowH(@Param("row") RawDataRow row);

    // ── 히스토리: SELECT ─────────────────────────────────────────
    List<ChipsetUpload>  selectHistoryByType(@Param("fileType") String fileType);
    List<ChipsetChipCol> selectChipColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetRow>     selectRowsH(@Param("uploadSeq") Long uploadSeq);
    List<RawDataRow>     selectRawDataRowsH(@Param("uploadSeq") Long uploadSeq);
}

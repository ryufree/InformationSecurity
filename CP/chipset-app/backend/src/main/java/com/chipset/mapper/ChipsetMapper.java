package com.chipset.mapper;

import com.chipset.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChipsetMapper {

    // ── 메인: DELETE (파일 타입별) ──────────────────────────────────
    void deleteCellsByFileType(@Param("fileType") String fileType);
    void deleteChipColsByFileType(@Param("fileType") String fileType);
    void deleteSpecColsByFileType(@Param("fileType") String fileType);
    void deleteRawdataColsByFileType(@Param("fileType") String fileType);
    void deleteUploadsByFileType(@Param("fileType") String fileType);

    // ── 메인: INSERT ─────────────────────────────────────────────────
    void insertUpload(ChipsetUpload upload);
    void insertChipCol(ChipsetChipCol col);
    void insertSpecCol(ChipsetSpecCol specCol);
    void insertRawdataCol(ChipsetRawdataCol col);
    void insertCell(ChipsetCell cell);

    // ── 메인: SELECT ─────────────────────────────────────────────────
    ChipsetUpload           selectLatestUploadByType(@Param("fileType") String fileType);
    List<ChipsetChipCol>    selectChipColsByType(@Param("fileType") String fileType);
    List<ChipsetSpecCol>    selectSpecColsByType(@Param("fileType") String fileType);
    List<ChipsetRawdataCol> selectRawdataColsByType(@Param("fileType") String fileType);
    List<ChipsetCell>       selectCellsByUploadSeq(@Param("uploadSeq") Long uploadSeq);

    // ── 히스토리: INSERT ─────────────────────────────────────────────
    void insertUploadH(ChipsetUpload upload);
    void insertChipColH(ChipsetChipCol col);
    void insertSpecColH(ChipsetSpecCol specCol);
    void insertRawdataColH(ChipsetRawdataCol col);
    void insertCellH(ChipsetCell cell);

    // ── 히스토리: SELECT ─────────────────────────────────────────────
    List<ChipsetUpload>     selectHistoryByType(@Param("fileType") String fileType);
    List<ChipsetChipCol>    selectChipColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetSpecCol>    selectSpecColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetRawdataCol> selectRawdataColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetCell>       selectCellsHByUploadSeq(@Param("uploadSeq") Long uploadSeq);
}

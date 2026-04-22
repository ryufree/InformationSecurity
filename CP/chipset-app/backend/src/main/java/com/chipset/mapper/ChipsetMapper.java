package com.chipset.mapper;

import com.chipset.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChipsetMapper {

    // ── 메인: DELETE (전체 초기화) ──────────────────────────────
    void deleteAllCells();
    void deleteAllRows();
    void deleteAllChipCols();
    void deleteAllUploads();

    // ── 메인: INSERT ─────────────────────────────────────────────
    void insertUpload(ChipsetUpload upload);
    void insertChipCol(ChipsetChipCol col);
    void insertRow(ChipsetRow row);
    void insertCell(ChipsetCell cell);

    // ── 메인: SELECT ─────────────────────────────────────────────
    ChipsetUpload        selectLatestUpload();
    List<ChipsetChipCol> selectChipCols();
    List<ChipsetRow>     selectRows();

    // ── 히스토리: INSERT ─────────────────────────────────────────
    void insertUploadH(ChipsetUpload upload);
    void insertChipColH(ChipsetChipCol col);
    void insertRowH(ChipsetRow row);
    void insertCellH(@Param("cell") ChipsetCell cell,
                     @Param("uploadSeq") Long uploadSeq);

    // ── 히스토리: SELECT ─────────────────────────────────────────
    List<ChipsetUpload>  selectHistory();
    List<ChipsetChipCol> selectChipColsH(@Param("uploadSeq") Long uploadSeq);
    List<ChipsetRow>     selectRowsH(@Param("uploadSeq") Long uploadSeq);
}

package com.chipset.service;

import com.chipset.mapper.ChipsetMapper;
import com.chipset.model.*;
import com.chipset.util.ChipsetExcelParser;
import com.chipset.util.ChipsetExcelParser.CellColDef;
import com.chipset.util.ChipsetExcelParser.FileType;
import com.chipset.util.ChipsetExcelParser.ParseResult;
import com.chipset.util.ChipsetExcelParser.RawDataColDef;
import com.chipset.util.ChipsetExcelParser.RawDataRowData;
import com.chipset.util.ChipsetExcelParser.RowData;
import com.chipset.util.ChipsetExcelParser.SpecColDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChipsetService {

    private final ChipsetMapper chipsetMapper;
    private final DataSource dataSource;

    // ── 업로드 ────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public UploadResult upload(MultipartFile multipartFile) throws Exception {
        if (multipartFile.isEmpty()) return UploadResult.error("파일이 비어있습니다.");

        File tempFile = null;
        try {
            tempFile = createTempFile(multipartFile);
            String filename = multipartFile.getOriginalFilename();

            ParseResult parsed = ChipsetExcelParser.parse(tempFile, filename);
            String fileType = parsed.fileType.name();

            if (parsed.fileType == FileType.RAW_DATA) {
                return uploadRawData(parsed, filename, fileType);
            } else {
                return uploadMatrix(parsed, filename, fileType);
            }
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    private UploadResult uploadMatrix(ParseResult parsed, String filename, String fileType) {
        assertMatrixSchemaReady();

        if (parsed.cellColDefs.isEmpty())
            return UploadResult.error("Excel에서 칩 컬럼을 찾을 수 없습니다.");
        if (parsed.rows.isEmpty())
            return UploadResult.error("데이터 행이 없습니다.");

        // FK 의존 순서: CELL → CHIP_COL → SPEC_COL → UPLOAD
        chipsetMapper.deleteCellsByFileType(fileType);
        chipsetMapper.deleteChipColsByFileType(fileType);
        chipsetMapper.deleteSpecColsByFileType(fileType);
        chipsetMapper.deleteUploadsByFileType(fileType);

        ChipsetUpload upload = new ChipsetUpload();
        upload.setFileNm(filename);
        upload.setFileType(fileType);
        upload.setRowCount(parsed.rows.size());
        upload.setColCount(parsed.cellColDefs.size());
        chipsetMapper.insertUpload(upload);
        Long uploadSeq = upload.getUploadSeq();

        // 스펙 컬럼 헤더 INSERT
        List<ChipsetSpecCol> specCols = new ArrayList<>();
        Map<Integer, Long> specColIdxToSeq = new HashMap<>();
        for (SpecColDef scd : parsed.specColDefs) {
            ChipsetSpecCol specCol = new ChipsetSpecCol();
            specCol.setUploadSeq(uploadSeq);
            specCol.setColIdx(scd.colIdx);
            specCol.setColNm(truncate(scd.colNm, 100));
            specCol.setSortOrder(scd.sortOrder);
            chipsetMapper.insertSpecCol(specCol);
            specColIdxToSeq.put(scd.colIdx, specCol.getColSeq());
            specCols.add(specCol);
        }

        // 칩셋 컬럼 헤더 INSERT
        Map<Integer, Long> chipColIdxToSeq = new HashMap<>();
        List<ChipsetChipCol> chipCols = new ArrayList<>();
        for (CellColDef def : parsed.cellColDefs) {
            ChipsetChipCol col = new ChipsetChipCol();
            col.setUploadSeq(uploadSeq);
            col.setVendor(def.vendor);
            col.setColIdx(def.colIdx);
            col.setChipNm(truncate(def.chipNm, 200));
            col.setChipDt(truncate(def.chipDt, 50));
            col.setSortOrder(def.sortOrder);
            chipsetMapper.insertChipCol(col);
            chipColIdxToSeq.put(def.colIdx, col.getColSeq());
            chipCols.add(col);
        }

        // 셀 INSERT: SPEC + CHIP 모두 CHIPSET_CELL에 저장
        List<ChipsetCell> allCells = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < parsed.rows.size(); rowIdx++) {
            RowData rd = parsed.rows.get(rowIdx);

            // SPEC 셀: 각 스펙 컬럼값을 COL_TYPE='SPEC'으로 저장
            for (SpecColDef scd : parsed.specColDefs) {
                Long colSeq = specColIdxToSeq.get(scd.colIdx);
                if (colSeq == null) continue;
                int arrIdx = scd.colIdx - 1;  // 1-based → 0-based
                String val = (arrIdx < rd.specVals.length) ? rd.specVals[arrIdx] : null;

                ChipsetCell cell = new ChipsetCell();
                cell.setUploadSeq(uploadSeq);
                cell.setRowIdx(rowIdx);
                cell.setColType("SPEC");
                cell.setColSeq(colSeq);
                cell.setCellValue(truncate(val, 200));
                chipsetMapper.insertCell(cell);
                allCells.add(cell);
            }

            // CHIP 셀: 각 칩셋 컬럼값을 COL_TYPE='CHIP'으로 저장
            for (Map.Entry<Integer, String> entry : rd.cellValues.entrySet()) {
                Long colSeq = chipColIdxToSeq.get(entry.getKey());
                if (colSeq == null) continue;

                ChipsetCell cell = new ChipsetCell();
                cell.setUploadSeq(uploadSeq);
                cell.setRowIdx(rowIdx);
                cell.setColType("CHIP");
                cell.setColSeq(colSeq);
                cell.setCellValue(truncate(entry.getValue(), 200));
                cell.setBgColor(truncate(rd.cellColors.get(entry.getKey()), 10));
                chipsetMapper.insertCell(cell);
                allCells.add(cell);
            }
        }

        // 히스토리 누적
        chipsetMapper.insertUploadH(upload);
        for (ChipsetSpecCol sc  : specCols)  chipsetMapper.insertSpecColH(sc);
        for (ChipsetChipCol col : chipCols)  chipsetMapper.insertChipColH(col);
        for (ChipsetCell    c   : allCells)  chipsetMapper.insertCellH(c);

        log.info("Matrix 업로드 완료 type={} seq={} rows={} chipCols={} specCols={}",
                fileType, uploadSeq, parsed.rows.size(),
                parsed.cellColDefs.size(), parsed.specColDefs.size());
        return UploadResult.success(uploadSeq, fileType, parsed.rows.size(), parsed.cellColDefs.size());
    }

    private UploadResult uploadRawData(ParseResult parsed, String filename, String fileType) {
        if (parsed.rawDataRows.isEmpty()) return UploadResult.error("Raw_Data 행이 없습니다.");
        if (parsed.rawDataColDefs.isEmpty()) return UploadResult.error("Raw_Data 컬럼 헤더를 찾을 수 없습니다.");

        // FK 의존 순서: CELL → RAWDATA_COL → UPLOAD
        chipsetMapper.deleteCellsByFileType(fileType);
        chipsetMapper.deleteRawdataColsByFileType(fileType);
        chipsetMapper.deleteUploadsByFileType(fileType);

        ChipsetUpload upload = new ChipsetUpload();
        upload.setFileNm(filename);
        upload.setFileType(fileType);
        upload.setRowCount(parsed.rawDataRows.size());
        upload.setColCount(parsed.rawDataColDefs.size());
        chipsetMapper.insertUpload(upload);
        Long uploadSeq = upload.getUploadSeq();

        // RawData 컬럼 헤더 INSERT
        List<ChipsetRawdataCol> rawdataCols = new ArrayList<>();
        Map<Integer, Long> rawColIdxToSeq = new HashMap<>();
        for (RawDataColDef def : parsed.rawDataColDefs) {
            ChipsetRawdataCol col = new ChipsetRawdataCol();
            col.setUploadSeq(uploadSeq);
            col.setColIdx(def.colIdx);
            col.setColNm(truncate(def.colNm, 200));
            col.setSortOrder(def.sortOrder);
            chipsetMapper.insertRawdataCol(col);
            rawColIdxToSeq.put(def.colIdx, col.getColSeq());
            rawdataCols.add(col);
        }

        // 셀 INSERT: 모든 rawdata 셀을 COL_TYPE='RAWDATA'로 저장
        List<ChipsetCell> allCells = new ArrayList<>();
        for (RawDataRowData rd : parsed.rawDataRows) {
            for (Map.Entry<Integer, String> entry : rd.cellValues.entrySet()) {
                Long colSeq = rawColIdxToSeq.get(entry.getKey());
                if (colSeq == null) continue;

                ChipsetCell cell = new ChipsetCell();
                cell.setUploadSeq(uploadSeq);
                cell.setRowIdx(rd.rowIdx);
                cell.setColType("RAWDATA");
                cell.setColSeq(colSeq);
                cell.setCellValue(truncate(entry.getValue(), 200));
                chipsetMapper.insertCell(cell);
                allCells.add(cell);
            }
        }

        // 히스토리 누적
        chipsetMapper.insertUploadH(upload);
        for (ChipsetRawdataCol col : rawdataCols) chipsetMapper.insertRawdataColH(col);
        for (ChipsetCell       c   : allCells)   chipsetMapper.insertCellH(c);

        log.info("Raw_Data 업로드 완료 seq={} rows={} cols={}",
                uploadSeq, parsed.rawDataRows.size(), parsed.rawDataColDefs.size());
        return UploadResult.success(uploadSeq, fileType, parsed.rawDataRows.size(), parsed.rawDataColDefs.size());
    }

    /**
     * DB 스키마가 v1.2 기준인지 검증한다.
     * 구버전 스키마에서 업로드 시 ORA-00904/ORA-00942 대신 명확한 오류 메시지를 반환하기 위함.
     */
    private void assertMatrixSchemaReady() {
        assertSqlCompiles("SELECT COL_TYPE FROM CHIPSET_CELL WHERE 1=0",     "CHIPSET_CELL.COL_TYPE");
        assertSqlCompiles("SELECT ROW_IDX  FROM CHIPSET_CELL WHERE 1=0",     "CHIPSET_CELL.ROW_IDX");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_SPEC_COL    WHERE 1=0",     "CHIPSET_SPEC_COL");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_CHIP_COL    WHERE 1=0",     "CHIPSET_CHIP_COL");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_RAWDATA_COL WHERE 1=0",     "CHIPSET_RAWDATA_COL");
    }

    private void assertSqlCompiles(String sql, String requiredObject) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeQuery();
        } catch (SQLException e) {
            String hint = "DB schema mismatch (missing/old: " + requiredObject + "). "
                    + "Apply latest DDL: backend/src/main/resources/schema.sql (and schema-drop.sql if needed).";
            throw new IllegalStateException(hint, e);
        }
    }

    // ── 매트릭스 조회 ─────────────────────────────────────────────

    public MatrixResponse getMatrix(String fileType) {
        ChipsetUpload latest = chipsetMapper.selectLatestUploadByType(fileType);

        MatrixResponse resp = new MatrixResponse();
        resp.setFileType(fileType);
        resp.setVendors(Collections.emptyList());
        resp.setChipCols(Collections.emptyList());
        resp.setSpecCols(Collections.emptyList());
        resp.setRows(Collections.emptyList());

        if (latest == null) return resp;

        resp.setUploadSeq(latest.getUploadSeq());
        resp.setUploadDt(latest.getUploadDt());

        List<ChipsetChipCol> chipCols = chipsetMapper.selectChipColsByType(fileType);
        List<ChipsetSpecCol> specCols = chipsetMapper.selectSpecColsByType(fileType);
        List<ChipsetCell>    allCells = chipsetMapper.selectCellsByUploadSeq(latest.getUploadSeq());

        List<String> vendors = chipCols.stream()
                .map(ChipsetChipCol::getVendor).distinct().collect(Collectors.toList());

        resp.setVendors(vendors);
        resp.setChipCols(chipCols);
        resp.setSpecCols(specCols);
        resp.setRows(buildMatrixRows(allCells));
        return resp;
    }

    // ── Raw_Data 조회 ─────────────────────────────────────────────

    public RawDataResponse getRawData() {
        ChipsetUpload latest = chipsetMapper.selectLatestUploadByType("RAW_DATA");

        RawDataResponse resp = new RawDataResponse();
        resp.setFileType("RAW_DATA");
        resp.setRawdataCols(Collections.emptyList());
        resp.setRows(Collections.emptyList());

        if (latest == null) return resp;

        resp.setUploadSeq(latest.getUploadSeq());
        resp.setUploadDt(latest.getUploadDt());

        List<ChipsetRawdataCol> rawdataCols = chipsetMapper.selectRawdataColsByType("RAW_DATA");
        List<ChipsetCell>       allCells    = chipsetMapper.selectCellsByUploadSeq(latest.getUploadSeq());

        resp.setRawdataCols(rawdataCols);
        resp.setRows(buildRawDataRows(allCells));
        return resp;
    }

    // ── 히스토리 목록 ─────────────────────────────────────────────

    public List<ChipsetUpload> getHistory(String fileType) {
        return chipsetMapper.selectHistoryByType(fileType);
    }

    // ── 특정 히스토리 버전 조회 ───────────────────────────────────

    public MatrixResponse getHistoryMatrix(Long uploadSeq) {
        List<ChipsetChipCol> chipCols = chipsetMapper.selectChipColsH(uploadSeq);
        List<ChipsetSpecCol> specCols = chipsetMapper.selectSpecColsH(uploadSeq);
        List<ChipsetCell>    allCells = chipsetMapper.selectCellsHByUploadSeq(uploadSeq);

        List<String> vendors = chipCols.stream()
                .map(ChipsetChipCol::getVendor).distinct().collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setVendors(vendors);
        resp.setChipCols(chipCols);
        resp.setSpecCols(specCols);
        resp.setRows(buildMatrixRows(allCells));
        return resp;
    }

    public RawDataResponse getHistoryRawData(Long uploadSeq) {
        List<ChipsetRawdataCol> rawdataCols = chipsetMapper.selectRawdataColsH(uploadSeq);
        List<ChipsetCell>       allCells    = chipsetMapper.selectCellsHByUploadSeq(uploadSeq);

        RawDataResponse resp = new RawDataResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setFileType("RAW_DATA");
        resp.setRawdataCols(rawdataCols);
        resp.setRows(buildRawDataRows(allCells));
        return resp;
    }

    // ── 행 재구성 헬퍼 ───────────────────────────────────────────

    private List<MatrixRow> buildMatrixRows(List<ChipsetCell> allCells) {
        Map<Integer, List<ChipsetCell>> byRow = allCells.stream()
                .collect(Collectors.groupingBy(ChipsetCell::getRowIdx));

        List<Integer> sortedIdxes = new ArrayList<>(byRow.keySet());
        Collections.sort(sortedIdxes);

        List<MatrixRow> rows = new ArrayList<>();
        for (Integer rowIdx : sortedIdxes) {
            List<ChipsetCell> rowCells = byRow.get(rowIdx);
            MatrixRow mr = new MatrixRow();
            mr.setRowIdx(rowIdx);
            mr.setSpecCells(rowCells.stream()
                    .filter(c -> "SPEC".equals(c.getColType()))
                    .collect(Collectors.toList()));
            mr.setChipCells(rowCells.stream()
                    .filter(c -> "CHIP".equals(c.getColType()))
                    .collect(Collectors.toList()));
            rows.add(mr);
        }
        return rows;
    }

    private List<RawDataRow> buildRawDataRows(List<ChipsetCell> allCells) {
        Map<Integer, List<ChipsetCell>> byRow = allCells.stream()
                .collect(Collectors.groupingBy(ChipsetCell::getRowIdx));

        List<Integer> sortedIdxes = new ArrayList<>(byRow.keySet());
        Collections.sort(sortedIdxes);

        List<RawDataRow> rows = new ArrayList<>();
        for (Integer rowIdx : sortedIdxes) {
            RawDataRow row = new RawDataRow();
            row.setRowIdx(rowIdx);
            row.setCells(byRow.get(rowIdx));
            rows.add(row);
        }
        return rows;
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private File createTempFile(MultipartFile multipartFile) throws IOException {
        String name = Optional.ofNullable(multipartFile.getOriginalFilename()).orElse("upload");
        File tempFile = File.createTempFile("chipset_" + name + "_", ".xlsx");
        multipartFile.transferTo(tempFile);
        return tempFile;
    }
}

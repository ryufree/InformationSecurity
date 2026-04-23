package com.chipset.service;

import com.chipset.mapper.ChipsetMapper;
import com.chipset.model.*;
import com.chipset.util.ChipsetExcelParser;
import com.chipset.util.ChipsetExcelParser.CellColDef;
import com.chipset.util.ChipsetExcelParser.FileType;
import com.chipset.util.ChipsetExcelParser.ParseResult;
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

        // 타입별 메인 테이블 삭제 (FK 의존 순서: CELL → ROW → CELL_COL → SPEC_COL → UPLOAD)
        chipsetMapper.deleteCellsByFileType(fileType);
        chipsetMapper.deleteRowsByFileType(fileType);
        chipsetMapper.deleteCellColsByFileType(fileType);
        chipsetMapper.deleteSpecColsByFileType(fileType);
        chipsetMapper.deleteUploadsByFileType(fileType);

        ChipsetUpload upload = new ChipsetUpload();
        upload.setFileNm(filename);
        upload.setFileType(fileType);
        upload.setRowCount(parsed.rows.size());
        upload.setColCount(parsed.cellColDefs.size());
        chipsetMapper.insertUpload(upload);
        Long uploadSeq = upload.getUploadSeq();

        // 스펙 컬럼 메타 INSERT (좌측 고정 컬럼 헤더명)
        List<ChipsetSpecCol> specCols = new ArrayList<>();
        for (SpecColDef scd : parsed.specColDefs) {
            ChipsetSpecCol specCol = new ChipsetSpecCol();
            specCol.setUploadSeq(uploadSeq);
            specCol.setColIdx(scd.colIdx);
            specCol.setColNm(truncate(scd.colNm, 200));
            specCol.setSortOrder(scd.sortOrder);
            chipsetMapper.insertSpecCol(specCol);
            specCols.add(specCol);
        }

        // 셀 컬럼 INSERT (벤더/칩셋)
        Map<Integer, Long> colIdxToSeq = new HashMap<>();
        List<ChipsetCellCol> cellCols  = new ArrayList<>();

        for (CellColDef def : parsed.cellColDefs) {
            ChipsetCellCol col = new ChipsetCellCol();
            col.setUploadSeq(uploadSeq);
            col.setVendor(def.vendor);
            col.setColIdx(def.colIdx);
            col.setChipNm(truncate(def.chipNm, 200));
            col.setChipDt(truncate(def.chipDt, 50));
            col.setSortOrder(def.sortOrder);
            chipsetMapper.insertCellCol(col);
            colIdxToSeq.put(def.colIdx, col.getColSeq());
            cellCols.add(col);
        }

        // 행 + 셀 INSERT
        List<ChipsetRow> rows = new ArrayList<>();
        for (RowData rd : parsed.rows) {
            ChipsetRow row = new ChipsetRow();
            row.setUploadSeq(uploadSeq);
            // specVals[0..9] → col1..col10
            String[] v = rd.specVals;
            row.setCol1(truncate(v.length > 0 ? v[0] : null, 200));
            row.setCol2(truncate(v.length > 1 ? v[1] : null, 200));
            row.setCol3(truncate(v.length > 2 ? v[2] : null, 200));
            row.setCol4(truncate(v.length > 3 ? v[3] : null, 200));
            row.setCol5(truncate(v.length > 4 ? v[4] : null, 200));
            row.setCol6(truncate(v.length > 5 ? v[5] : null, 200));
            row.setCol7(truncate(v.length > 6 ? v[6] : null, 200));
            row.setCol8(truncate(v.length > 7 ? v[7] : null, 200));
            row.setCol9(truncate(v.length > 8 ? v[8] : null, 200));
            row.setCol10(truncate(v.length > 9 ? v[9] : null, 200));
            row.setSortOrder(rd.sortOrder);
            chipsetMapper.insertRow(row);

            List<ChipsetCell> cells = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : rd.cellValues.entrySet()) {
                Long colSeq = colIdxToSeq.get(entry.getKey());
                if (colSeq == null) continue;
                ChipsetCell cell = new ChipsetCell();
                cell.setRowSeq(row.getRowSeq());
                cell.setColSeq(colSeq);
                cell.setUploadSeq(uploadSeq);   // 이제 cell에도 uploadSeq 저장
                cell.setCellValue(truncate(entry.getValue(), 200));
                cell.setBgColor(truncate(rd.cellColors.get(entry.getKey()), 20));
                chipsetMapper.insertCell(cell);
                cells.add(cell);
            }
            row.setCells(cells);
            rows.add(row);
        }

        // 히스토리 누적
        chipsetMapper.insertUploadH(upload);
        for (ChipsetSpecCol sc  : specCols)  chipsetMapper.insertSpecColH(sc);
        for (ChipsetCellCol col : cellCols)  chipsetMapper.insertCellColH(col);
        for (ChipsetRow row : rows) {
            chipsetMapper.insertRowH(row);
            for (ChipsetCell cell : row.getCells()) chipsetMapper.insertCellH(cell);
        }

        log.info("업로드 완료 type={} seq={} rows={} cellCols={} specCols={}",
                fileType, uploadSeq, parsed.rows.size(),
                parsed.cellColDefs.size(), parsed.specColDefs.size());
        return UploadResult.success(uploadSeq, fileType, parsed.rows.size(), parsed.cellColDefs.size());
    }

    /**
     * Oracle 사용 시 schema.sql 을 앱에서 자동 실행하지 않도록 되어 있어(spring.sql.init.mode=never),
     * DB 스키마가 구버전인 상태로 업로드를 시도하면 ORA-00904/ORA-00942 같은 예외로 500이 발생한다.
     * 업로드 초반에 "컬럼/테이블 존재 여부"를 빠르게 검증해서, 원인/해결 방법을 명확하게 안내한다.
     */
    private void assertMatrixSchemaReady() {
        // "WHERE 1=0" 로 실제 데이터 스캔 없이 컬럼/테이블 존재만 검증
        assertSqlCompiles("SELECT COL10 FROM CHIPSET_ROW WHERE 1=0", "CHIPSET_ROW.COL10");
        assertSqlCompiles("SELECT COL10 FROM CHIPSET_ROW_H WHERE 1=0", "CHIPSET_ROW_H.COL10");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_SPEC_COL WHERE 1=0", "CHIPSET_SPEC_COL");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_CELL_COL WHERE 1=0", "CHIPSET_CELL_COL");
        assertSqlCompiles("SELECT 1 FROM CHIPSET_CELL WHERE 1=0", "CHIPSET_CELL");
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

    private UploadResult uploadRawData(ParseResult parsed, String filename, String fileType) {
        if (parsed.rawDataRows.isEmpty()) return UploadResult.error("Raw_Data 행이 없습니다.");

        chipsetMapper.deleteRawDataRowsByFileType(fileType);
        chipsetMapper.deleteUploadsByFileType(fileType);

        ChipsetUpload upload = new ChipsetUpload();
        upload.setFileNm(filename);
        upload.setFileType(fileType);
        upload.setRowCount(parsed.rawDataRows.size());
        upload.setColCount(0);
        chipsetMapper.insertUpload(upload);
        Long uploadSeq = upload.getUploadSeq();

        List<RawDataRow> rows = new ArrayList<>();
        for (RawDataRowData rd : parsed.rawDataRows) {
            RawDataRow row = new RawDataRow();
            row.setUploadSeq(uploadSeq);
            row.setCompany(rd.company);
            row.setSeg(rd.seg);
            row.setChipset(rd.chipset);
            row.setSocCs(rd.socCs);
            row.setPartNumber(rd.partNumber);
            row.setDramProcess(rd.dramProcess);
            row.setFlashProcess(rd.flashProcess);
            row.setDensity(rd.density);
            row.setMlcTlc(rd.mlcTlc);
            row.setPkg(rd.pkg);
            row.setVal1Date(rd.val1Date);
            row.setVal1Eng(rd.val1Eng);
            row.setVal1Status(rd.val1Status);
            row.setVal1Remark(rd.val1Remark);
            row.setVal2Date(rd.val2Date);
            row.setVal2Eng(rd.val2Eng);
            row.setVal2Status(rd.val2Status);
            row.setVal2Remark(rd.val2Remark);
            row.setVal3Date(rd.val3Date);
            row.setVal3Eng(rd.val3Eng);
            row.setSortOrder(rd.sortOrder);
            chipsetMapper.insertRawDataRow(row);
            rows.add(row);
        }

        chipsetMapper.insertUploadH(upload);
        for (RawDataRow row : rows) chipsetMapper.insertRawDataRowH(row);

        log.info("Raw_Data 업로드 완료 seq={} rows={}", uploadSeq, rows.size());
        return UploadResult.success(uploadSeq, fileType, rows.size(), 0);
    }

    // ── 매트릭스 조회 ─────────────────────────────────────────────

    public MatrixResponse getMatrix(String fileType) {
        ChipsetUpload        latest   = chipsetMapper.selectLatestUploadByType(fileType);
        List<ChipsetCellCol> cellCols = chipsetMapper.selectCellColsByType(fileType);
        List<ChipsetSpecCol> specCols = chipsetMapper.selectSpecColsByType(fileType);
        List<ChipsetRow>     rows     = chipsetMapper.selectRowsByType(fileType);

        List<String> vendors = cellCols.stream()
                .map(ChipsetCellCol::getVendor).distinct().collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        if (latest != null) {
            resp.setUploadSeq(latest.getUploadSeq());
            resp.setUploadDt(latest.getUploadDt());
        }
        resp.setFileType(fileType);
        resp.setVendors(vendors);
        resp.setChipCols(cellCols);
        resp.setSpecCols(specCols);
        resp.setRows(rows);
        return resp;
    }

    // ── Raw_Data 조회 ─────────────────────────────────────────────

    public RawDataResponse getRawData() {
        ChipsetUpload   latest = chipsetMapper.selectLatestUploadByType("RAW_DATA");
        List<RawDataRow> rows  = chipsetMapper.selectRawDataRowsByType("RAW_DATA");

        RawDataResponse resp = new RawDataResponse();
        if (latest != null) {
            resp.setUploadSeq(latest.getUploadSeq());
            resp.setUploadDt(latest.getUploadDt());
        }
        resp.setFileType("RAW_DATA");
        resp.setRows(rows);
        return resp;
    }

    // ── 히스토리 목록 ─────────────────────────────────────────────

    public List<ChipsetUpload> getHistory(String fileType) {
        return chipsetMapper.selectHistoryByType(fileType);
    }

    // ── 특정 히스토리 버전 조회 ───────────────────────────────────

    public MatrixResponse getHistoryMatrix(Long uploadSeq) {
        List<ChipsetCellCol> cellCols = chipsetMapper.selectCellColsH(uploadSeq);
        List<ChipsetSpecCol> specCols = chipsetMapper.selectSpecColsH(uploadSeq);
        List<ChipsetRow>     rows     = chipsetMapper.selectRowsH(uploadSeq);

        List<String> vendors = cellCols.stream()
                .map(ChipsetCellCol::getVendor).distinct().collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setVendors(vendors);
        resp.setChipCols(cellCols);
        resp.setSpecCols(specCols);
        resp.setRows(rows);
        return resp;
    }

    public RawDataResponse getHistoryRawData(Long uploadSeq) {
        RawDataResponse resp = new RawDataResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setFileType("RAW_DATA");
        resp.setRows(chipsetMapper.selectRawDataRowsH(uploadSeq));
        return resp;
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

package com.chipset.service;

import com.chipset.mapper.ChipsetMapper;
import com.chipset.model.*;
import com.chipset.util.ChipsetExcelParser;
import com.chipset.util.ChipsetExcelParser.ChipColDef;
import com.chipset.util.ChipsetExcelParser.FileType;
import com.chipset.util.ChipsetExcelParser.ParseResult;
import com.chipset.util.ChipsetExcelParser.RawDataRowData;
import com.chipset.util.ChipsetExcelParser.RowData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChipsetService {

    private final ChipsetMapper chipsetMapper;

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
        if (parsed.chipColDefs.isEmpty())
            return UploadResult.error("Excel에서 칩 컬럼을 찾을 수 없습니다.");
        if (parsed.rows.isEmpty())
            return UploadResult.error("데이터 행이 없습니다.");

        // 타입별 메인 테이블 삭제
        chipsetMapper.deleteCellsByFileType(fileType);
        chipsetMapper.deleteRowsByFileType(fileType);
        chipsetMapper.deleteChipColsByFileType(fileType);
        chipsetMapper.deleteUploadsByFileType(fileType);

        ChipsetUpload upload = new ChipsetUpload();
        upload.setFileNm(filename);
        upload.setFileType(fileType);
        upload.setRowCount(parsed.rows.size());
        upload.setColCount(parsed.chipColDefs.size());
        chipsetMapper.insertUpload(upload);
        Long uploadSeq = upload.getUploadSeq();

        Map<Integer, Long> colIdxToSeq = new HashMap<>();
        List<ChipsetChipCol> chipCols  = new ArrayList<>();

        for (ChipColDef def : parsed.chipColDefs) {
            ChipsetChipCol col = new ChipsetChipCol();
            col.setUploadSeq(uploadSeq);
            col.setVendor(def.vendor);
            col.setColIdx(def.colIdx);
            col.setChipNm(def.chipNm);
            col.setChipDt(def.chipDt);
            col.setSortOrder(def.sortOrder);
            chipsetMapper.insertChipCol(col);
            colIdxToSeq.put(def.colIdx, col.getColSeq());
            chipCols.add(col);
        }

        List<ChipsetRow> rows = new ArrayList<>();
        for (RowData rd : parsed.rows) {
            ChipsetRow row = new ChipsetRow();
            row.setUploadSeq(uploadSeq);
            row.setDimm(rd.dimm);
            row.setProduct(rd.product);
            row.setVer(rd.ver);
            row.setDensity(rd.density);
            row.setOrg(rd.org);
            row.setSpeed(rd.speed);
            row.setSortOrder(rd.sortOrder);
            chipsetMapper.insertRow(row);

            List<ChipsetCell> cells = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : rd.cellValues.entrySet()) {
                Long colSeq = colIdxToSeq.get(entry.getKey());
                if (colSeq == null) continue;
                ChipsetCell cell = new ChipsetCell();
                cell.setRowSeq(row.getRowSeq());
                cell.setColSeq(colSeq);
                cell.setCellValue(entry.getValue());
                cell.setBgColor(rd.cellColors.get(entry.getKey()));
                chipsetMapper.insertCell(cell);
                cells.add(cell);
            }
            row.setCells(cells);
            rows.add(row);
        }

        // 히스토리 누적
        chipsetMapper.insertUploadH(upload);
        for (ChipsetChipCol col : chipCols)  chipsetMapper.insertChipColH(col);
        for (ChipsetRow row : rows) {
            chipsetMapper.insertRowH(row);
            for (ChipsetCell cell : row.getCells()) chipsetMapper.insertCellH(cell, uploadSeq);
        }

        log.info("업로드 완료 type={} seq={} rows={} cols={}",
                fileType, uploadSeq, parsed.rows.size(), parsed.chipColDefs.size());
        return UploadResult.success(uploadSeq, fileType, parsed.rows.size(), parsed.chipColDefs.size());
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
        ChipsetUpload latest      = chipsetMapper.selectLatestUploadByType(fileType);
        List<ChipsetChipCol> cols = chipsetMapper.selectChipColsByType(fileType);
        List<ChipsetRow>     rows = chipsetMapper.selectRowsByType(fileType);

        List<String> vendors = cols.stream()
                .map(ChipsetChipCol::getVendor).distinct().collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        if (latest != null) {
            resp.setUploadSeq(latest.getUploadSeq());
            resp.setUploadDt(latest.getUploadDt());
        }
        resp.setFileType(fileType);
        resp.setVendors(vendors);
        resp.setChipCols(cols);
        resp.setRows(rows);
        return resp;
    }

    // ── Raw_Data 조회 ─────────────────────────────────────────────

    public RawDataResponse getRawData() {
        ChipsetUpload latest    = chipsetMapper.selectLatestUploadByType("RAW_DATA");
        List<RawDataRow>  rows  = chipsetMapper.selectRawDataRowsByType("RAW_DATA");

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
        List<ChipsetChipCol> cols = chipsetMapper.selectChipColsH(uploadSeq);
        List<ChipsetRow>     rows = chipsetMapper.selectRowsH(uploadSeq);

        List<String> vendors = cols.stream()
                .map(ChipsetChipCol::getVendor).distinct().collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setVendors(vendors);
        resp.setChipCols(cols);
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

    private File createTempFile(MultipartFile multipartFile) throws IOException {
        String name = Optional.ofNullable(multipartFile.getOriginalFilename()).orElse("upload");
        File tempFile = File.createTempFile("chipset_" + name + "_", ".xlsx");
        multipartFile.transferTo(tempFile);
        return tempFile;
    }
}

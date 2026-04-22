package com.chipset.service;

import com.chipset.mapper.ChipsetMapper;
import com.chipset.model.*;
import com.chipset.util.ChipsetExcelParser;
import com.chipset.util.ChipsetExcelParser.ChipColDef;
import com.chipset.util.ChipsetExcelParser.ParseResult;
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

    // ── 업로드: 메인 덮어쓰기 + 히스토리 누적 ───────────────────────

    @Transactional(rollbackFor = Exception.class)
    public UploadResult upload(MultipartFile multipartFile) throws Exception {
        if (multipartFile.isEmpty()) {
            return UploadResult.error("파일이 비어있습니다.");
        }

        File tempFile = null;
        try {
            // 1. 임시 파일에 저장
            tempFile = createTempFile(multipartFile);

            // 2. Excel 파싱
            ParseResult parsed = ChipsetExcelParser.parse(tempFile);
            if (parsed.chipColDefs.isEmpty()) {
                return UploadResult.error("Excel에서 칩 컬럼을 찾을 수 없습니다. (Intel/AMD 등 벤더 헤더 확인)");
            }
            if (parsed.rows.isEmpty()) {
                return UploadResult.error("데이터 행이 없습니다.");
            }

            // ── 메인 테이블: 전체 삭제 → 새 데이터 삽입 (덮어쓰기) ──
            chipsetMapper.deleteAllCells();
            chipsetMapper.deleteAllRows();
            chipsetMapper.deleteAllChipCols();
            chipsetMapper.deleteAllUploads();

            // 3. CHIPSET_UPLOAD INSERT (selectKey가 uploadSeq 자동 설정)
            ChipsetUpload upload = new ChipsetUpload();
            upload.setFileNm(multipartFile.getOriginalFilename());
            upload.setRowCount(parsed.rows.size());
            upload.setColCount(parsed.chipColDefs.size());
            chipsetMapper.insertUpload(upload);
            Long uploadSeq = upload.getUploadSeq();

            // 4. CHIPSET_CHIP_COL INSERT + colIdx → colSeq 매핑 구성
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
                chipsetMapper.insertChipCol(col);   // selectKey → col.colSeq 설정됨
                colIdxToSeq.put(def.colIdx, col.getColSeq());
                chipCols.add(col);
            }

            // 5. CHIPSET_ROW + CHIPSET_CELL INSERT
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
                chipsetMapper.insertRow(row);       // selectKey → row.rowSeq 설정됨

                List<ChipsetCell> cells = new ArrayList<>();
                for (Map.Entry<Integer, String> entry : rd.cellValues.entrySet()) {
                    int    colIdx = entry.getKey();
                    String val    = entry.getValue();
                    Long   colSeq = colIdxToSeq.get(colIdx);
                    if (colSeq == null) continue;

                    ChipsetCell cell = new ChipsetCell();
                    cell.setRowSeq(row.getRowSeq());
                    cell.setColSeq(colSeq);
                    cell.setCellValue(val);
                    cell.setBgColor(rd.cellColors.get(colIdx));
                    chipsetMapper.insertCell(cell); // selectKey → cell.cellSeq 설정됨
                    cells.add(cell);
                }
                row.setCells(cells);
                rows.add(row);
            }

            // ── 히스토리 테이블: INSERT 누적 (삭제 없음) ─────────────
            chipsetMapper.insertUploadH(upload);

            for (ChipsetChipCol col : chipCols) {
                chipsetMapper.insertChipColH(col);
            }
            for (ChipsetRow row : rows) {
                chipsetMapper.insertRowH(row);
                for (ChipsetCell cell : row.getCells()) {
                    chipsetMapper.insertCellH(cell, uploadSeq);
                }
            }

            log.info("업로드 완료 uploadSeq={} rows={} cols={}",
                    uploadSeq, parsed.rows.size(), parsed.chipColDefs.size());
            return UploadResult.success(uploadSeq, parsed.rows.size(), parsed.chipColDefs.size());

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // ── 현재 매트릭스 조회 ────────────────────────────────────────

    public MatrixResponse getMatrix() {
        ChipsetUpload latest       = chipsetMapper.selectLatestUpload();
        List<ChipsetChipCol> cols  = chipsetMapper.selectChipCols();
        List<ChipsetRow>     rows  = chipsetMapper.selectRows();

        List<String> vendors = cols.stream()
                .map(ChipsetChipCol::getVendor)
                .distinct()
                .collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        if (latest != null) {
            resp.setUploadSeq(latest.getUploadSeq());
            resp.setUploadDt(latest.getUploadDt());
        }
        resp.setVendors(vendors);
        resp.setChipCols(cols);
        resp.setRows(rows);
        return resp;
    }

    // ── 히스토리 목록 ─────────────────────────────────────────────

    public List<ChipsetUpload> getHistory() {
        return chipsetMapper.selectHistory();
    }

    // ── 특정 히스토리 버전 매트릭스 조회 ─────────────────────────

    public MatrixResponse getHistoryMatrix(Long uploadSeq) {
        List<ChipsetChipCol> cols = chipsetMapper.selectChipColsH(uploadSeq);
        List<ChipsetRow>     rows = chipsetMapper.selectRowsH(uploadSeq);

        List<String> vendors = cols.stream()
                .map(ChipsetChipCol::getVendor)
                .distinct()
                .collect(Collectors.toList());

        MatrixResponse resp = new MatrixResponse();
        resp.setUploadSeq(uploadSeq);
        resp.setVendors(vendors);
        resp.setChipCols(cols);
        resp.setRows(rows);
        return resp;
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────

    private File createTempFile(MultipartFile multipartFile) throws IOException {
        String originalName = Optional.ofNullable(multipartFile.getOriginalFilename())
                .orElse("upload");
        File tempFile = File.createTempFile("chipset_" + originalName + "_", ".xlsx");
        multipartFile.transferTo(tempFile);
        return tempFile;
    }
}

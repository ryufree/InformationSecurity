package com.example.chipset.service;

import com.example.chipset.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ChipsetExcelService {

    private static final int SPEC_COL_COUNT = 6;
    private static final String[] SPEC_KEYS = {"dimm","product","ver","density","org","speed"};
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s*'(\\d{2})");

    // 마지막 업로드 데이터 (실제 프로젝트에서는 DB 저장)
    private ChipsetUploadResponse lastData = null;

    public ChipsetUploadResponse parseAndSave(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            List<CellRangeAddress> merges = sheet.getMergedRegions();

            // ── 헤더 행 탐색 ─────────────────────────────
            int headerRowIdx = findHeaderRow(sheet);
            Row headerRow    = sheet.getRow(headerRowIdx);

            // ── Intel / AMD 그룹 범위 탐색 ───────────────
            int[] intelRange = {-1,-1};
            int[] amdRange   = {-1,-1};

            for (CellRangeAddress merge : merges) {
                Row r = sheet.getRow(merge.getFirstRow());
                if (r == null) continue;
                Cell c = r.getCell(merge.getFirstColumn());
                if (c == null) continue;
                String v = getCellStringValue(c).toUpperCase();
                if ("INTEL".equals(v)) {
                    intelRange[0] = merge.getFirstColumn();
                    intelRange[1] = merge.getLastColumn();
                }
                if ("AMD".equals(v)) {
                    amdRange[0] = merge.getFirstColumn();
                    amdRange[1] = merge.getLastColumn();
                }
            }

            // ── 날짜행 탐색 (마지막 유효 행에서 mm 'yy 패턴) ──
            int dateRowIdx = findDateRow(sheet, headerRowIdx);
            Row dateRow = dateRowIdx >= 0 ? sheet.getRow(dateRowIdx) : null;

            // ── 칩 컬럼 정의 구성 ──────────────────────────
            List<ChipColumnDef> chipCols = new ArrayList<>();
            int lastCol = headerRow.getLastCellNum();

            if (intelRange[0] >= 0) {
                for (int c = intelRange[0]; c <= intelRange[1]; c++) {
                    chipCols.add(buildChipCol(sheet, headerRowIdx, dateRow, c, "intel"));
                }
            }
            if (amdRange[0] >= 0) {
                for (int c = amdRange[0]; c <= amdRange[1]; c++) {
                    chipCols.add(buildChipCol(sheet, headerRowIdx, dateRow, c, "amd"));
                }
            }
            // 그룹 감지 실패 시 fallback
            if (chipCols.isEmpty()) {
                for (int c = SPEC_COL_COUNT; c < lastCol; c++) {
                    chipCols.add(buildChipCol(sheet, headerRowIdx, dateRow, c, "intel"));
                }
            }

            // ── 데이터 행 파싱 ─────────────────────────────
            List<Map<String,Object>> rows = new ArrayList<>();
            for (int ri = headerRowIdx + 1; ri <= sheet.getLastRowNum(); ri++) {
                Row row = sheet.getRow(ri);
                if (row == null || isRowEmpty(row)) continue;

                Map<String,Object> rowMap = new LinkedHashMap<>();
                rowMap.put("__idx", ri);

                // Spec columns A~F
                for (int si = 0; si < SPEC_COL_COUNT; si++) {
                    Cell cell = row.getCell(si);
                    rowMap.put(SPEC_KEYS[si], cell != null ? getCellStringValue(cell) : "");
                }

                // Chip columns
                for (ChipColumnDef col : chipCols) {
                    Cell cell = row.getCell(col.getColIdx());
                    String value = cell != null ? getCellStringValue(cell) : "";
                    rowMap.put(col.getKey(), value);

                    // 배경색 추출
                    if (cell != null) {
                        String hex = extractBgColor(cell);
                        if (hex != null) rowMap.put(col.getKey() + "__color", hex);
                    }
                }
                rows.add(rowMap);
            }

            // ── 응답 구성 ──────────────────────────────────
            ChipsetUploadResponse resp = new ChipsetUploadResponse();
            resp.setLastVersion(LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")));
            resp.setChipCols(chipCols);
            resp.setRows(rows);
            resp.setTotalCount(rows.size());

            this.lastData = resp;
            return resp;
        }
    }

    public ChipsetUploadResponse getAllData() {
        return lastData;
    }

    // ── Helpers ─────────────────────────────────────────────────

    private int findHeaderRow(Sheet sheet) {
        for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String v = getCellStringValue(cell).toUpperCase();
                if (v.equals("DIMM") || v.contains("PRODUCT")) return r;
            }
        }
        return 1;
    }

    private int findDateRow(Sheet sheet, int headerRowIdx) {
        for (int r = sheet.getLastRowNum(); r > headerRowIdx; r--) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (DATE_PATTERN.matcher(getCellStringValue(cell)).find()) return r;
            }
        }
        return -1;
    }

    private ChipColumnDef buildChipCol(Sheet sheet, int headerRowIdx,
                                        Row dateRow, int colIdx, String type) {
        Row headerRow = sheet.getRow(headerRowIdx);
        Cell headerCell = headerRow != null ? headerRow.getCell(colIdx) : null;
        String chip = headerCell != null ? getCellStringValue(headerCell) : "col" + colIdx;
        String date = "";
        if (dateRow != null) {
            Cell dc = dateRow.getCell(colIdx);
            if (dc != null) date = getCellStringValue(dc);
        }
        return new ChipColumnDef("chip_" + colIdx, chip, date, colIdx, type);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private String extractBgColor(Cell cell) {
        try {
            CellStyle style = cell.getCellStyle();
            if (style == null) return null;
            Color color = style.getFillForegroundColorColor();
            if (color instanceof XSSFColor xc) {
                byte[] rgb = xc.getRGB();
                if (rgb != null && rgb.length == 3) {
                    String hex = String.format("%02X%02X%02X",
                            rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                    if (!"FFFFFF".equals(hex) && !"000000".equals(hex)) {
                        return "#" + hex;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (Cell cell : row) {
            if (cell != null && !getCellStringValue(cell).isEmpty()) return false;
        }
        return true;
    }
}

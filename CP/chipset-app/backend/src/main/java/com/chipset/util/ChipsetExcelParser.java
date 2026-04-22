package com.chipset.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.io.File;
import java.util.*;

/**
 * Apache POI 표준 API로 Chipset Validation Excel을 파싱한다.
 * 벤더(Intel/AMD 등)는 병합 셀에서 동적으로 감지하므로 컬럼 구조가 바뀌어도 대응된다.
 */
public class ChipsetExcelParser {

    private static final int SPEC_COL_COUNT = 6; // DIMM, Product, Ver., Density, Org, Speed

    // ── 파싱 결과 DTO ───────────────────────────────────────────────

    public static class ParseResult {
        public final List<ChipColDef> chipColDefs = new ArrayList<>();
        public final List<RowData>    rows        = new ArrayList<>();
    }

    /** Excel에서 읽은 칩 컬럼 정의 (DB seq 없음) */
    public static class ChipColDef {
        public String vendor;
        public int    colIdx;     // 원본 Excel 컬럼 인덱스
        public String chipNm;
        public String chipDt;
        public int    sortOrder;
    }

    /** Excel에서 읽은 데이터 행 */
    public static class RowData {
        public String dimm, product, ver, density, org, speed;
        public int    sortOrder;
        public final Map<Integer, String> cellValues = new HashMap<>(); // colIdx → 값
        public final Map<Integer, String> cellColors = new HashMap<>(); // colIdx → #RRGGBB
    }

    // ── 진입점 ─────────────────────────────────────────────────────

    public static ParseResult parse(File file) throws Exception {
        ParseResult result = new ParseResult();

        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            int headerRowIdx   = findHeaderRow(sheet, fmt);
            int chipNameRowIdx = headerRowIdx + 1;
            int dateRowIdx     = headerRowIdx + 2;
            int dataStartIdx   = headerRowIdx + 3;

            buildChipColDefs(sheet, fmt, headerRowIdx, chipNameRowIdx, dateRowIdx, result);
            parseDataRows(sheet, fmt, dataStartIdx, result);
        }
        return result;
    }

    // ── 헤더 행 탐색 ────────────────────────────────────────────────

    private static int findHeaderRow(Sheet sheet, DataFormatter fmt) {
        for (int r = 0; r <= Math.min(4, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = fmt.formatCellValue(cell).trim();
                if ("DIMM".equalsIgnoreCase(val) || val.toUpperCase().contains("PRODUCT")) {
                    return r;
                }
            }
        }
        return 0;
    }

    // ── 칩 컬럼 구조 파악 (동적 벤더) ──────────────────────────────

    private static void buildChipColDefs(Sheet sheet, DataFormatter fmt,
                                         int headerRowIdx, int chipNameRowIdx,
                                         int dateRowIdx, ParseResult result) {
        Row groupRow    = sheet.getRow(headerRowIdx);
        Row chipNameRow = sheet.getRow(chipNameRowIdx);
        Row dateRow     = sheet.getRow(dateRowIdx);
        if (groupRow == null || chipNameRow == null) return;

        // 1순위: 병합 셀에서 벤더 감지
        Map<Integer, String>  vendorByStart = new LinkedHashMap<>();
        Map<Integer, Integer> vendorEndCol  = new HashMap<>();

        for (CellRangeAddress merged : sheet.getMergedRegions()) {
            if (merged.getFirstRow() != headerRowIdx) continue;
            if (merged.getFirstColumn() < SPEC_COL_COUNT) continue;
            Cell cell = groupRow.getCell(merged.getFirstColumn());
            if (cell == null) continue;
            String val = fmt.formatCellValue(cell).trim();
            if (val.isEmpty()) continue;
            vendorByStart.put(merged.getFirstColumn(), val.toUpperCase());
            vendorEndCol.put(merged.getFirstColumn(), merged.getLastColumn());
        }

        // 2순위: 병합 없으면 헤더 행 스캔
        if (vendorByStart.isEmpty()) {
            int lastCellNum = chipNameRow.getLastCellNum();
            for (int c = SPEC_COL_COUNT; c < lastCellNum; c++) {
                Cell cell = groupRow.getCell(c);
                if (cell == null) continue;
                String val = fmt.formatCellValue(cell).trim();
                if (val.isEmpty()) continue;
                vendorByStart.put(c, val.toUpperCase());
            }
            List<Integer> starts = new ArrayList<>(vendorByStart.keySet());
            Collections.sort(starts);
            for (int i = 0; i < starts.size(); i++) {
                int start = starts.get(i);
                int end   = (i + 1 < starts.size())
                        ? starts.get(i + 1) - 1
                        : chipNameRow.getLastCellNum() - 1;
                vendorEndCol.put(start, end);
            }
        }

        // 칩 컬럼 객체 생성
        int sortOrder = 0;
        List<Integer> startCols = new ArrayList<>(vendorByStart.keySet());
        Collections.sort(startCols);

        for (int startCol : startCols) {
            String vendor = vendorByStart.get(startCol);
            int    endCol = vendorEndCol.getOrDefault(startCol, startCol);

            for (int c = startCol; c <= endCol; c++) {
                Cell chipCell = chipNameRow.getCell(c);
                if (chipCell == null) continue;
                String chipNm = fmt.formatCellValue(chipCell).trim();
                if (chipNm.isEmpty()) continue;

                ChipColDef def = new ChipColDef();
                def.vendor    = vendor;
                def.colIdx    = c;
                def.chipNm    = chipNm;
                def.chipDt    = (dateRow != null && dateRow.getCell(c) != null)
                        ? fmt.formatCellValue(dateRow.getCell(c)).trim() : "";
                def.sortOrder = sortOrder++;
                result.chipColDefs.add(def);
            }
        }
    }

    // ── 데이터 행 파싱 ────────────────────────────────────────────

    private static void parseDataRows(Sheet sheet, DataFormatter fmt,
                                      int dataStartIdx, ParseResult result) {
        Set<Integer> chipColIdxSet = new HashSet<>();
        for (ChipColDef def : result.chipColDefs) {
            chipColIdxSet.add(def.colIdx);
        }

        int sortOrder = 0;
        for (int r = dataStartIdx; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (isRowEmpty(row, fmt)) continue;

            RowData rd = new RowData();
            rd.dimm      = getCellStr(row, 0, fmt);
            rd.product   = getCellStr(row, 1, fmt);
            rd.ver       = getCellStr(row, 2, fmt);
            rd.density   = getCellStr(row, 3, fmt);
            rd.org       = getCellStr(row, 4, fmt);
            rd.speed     = getCellStr(row, 5, fmt);
            rd.sortOrder = sortOrder++;

            for (int chipColIdx : chipColIdxSet) {
                Cell   cell  = row.getCell(chipColIdx);
                String val   = (cell != null) ? fmt.formatCellValue(cell).trim() : "";
                String color = extractBgColor(cell);
                rd.cellValues.put(chipColIdx, val);
                if (color != null) rd.cellColors.put(chipColIdx, color);
            }
            result.rows.add(rd);
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────

    private static boolean isRowEmpty(Row row, DataFormatter fmt) {
        for (int c = 0; c < SPEC_COL_COUNT; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !fmt.formatCellValue(cell).trim().isEmpty()) return false;
        }
        return true;
    }

    private static String getCellStr(Row row, int colIdx, DataFormatter fmt) {
        Cell cell = row.getCell(colIdx);
        return (cell != null) ? fmt.formatCellValue(cell).trim() : "";
    }

    private static String extractBgColor(Cell cell) {
        if (cell == null) return null;
        CellStyle style = cell.getCellStyle();
        if (!(style instanceof XSSFCellStyle)) return null;
        XSSFColor color = ((XSSFCellStyle) style).getFillForegroundColorColor();
        if (color == null) return null;
        byte[] rgb = color.getRGB();
        if (rgb == null || rgb.length < 3) return null;
        // & 0xFF: byte → unsigned int 변환 (음수 방지)
        String hex = String.format("#%02X%02X%02X",
                rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        if ("#FFFFFF".equals(hex) || "#000000".equals(hex)) return null;
        return hex;
    }
}

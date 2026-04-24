package com.chipset.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.io.File;
import java.util.*;

/**
 * Apache POI 표준 API로 Chipset Validation Excel을 파싱한다.
 * Server / Client / Mobile / Raw_Data 4가지 파일 타입을 자동 감지한다.
 */
public class ChipsetExcelParser {

    // ── 파일 타입 ──────────────────────────────────────────────────

    public enum FileType {
        SERVER, CLIENT, MOBILE, RAW_DATA;

        /** 시트 상단 키워드와 파일명으로 타입 자동 감지 */
        public static FileType detect(String filename, Sheet sheet, DataFormatter fmt) {
            for (int r = 0; r <= Math.min(3, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (Cell cell : row) {
                    String v = fmt.formatCellValue(cell).trim().toUpperCase();
                    if (v.contains("TARGET AP") || v.contains("SORTING KEY")) return RAW_DATA;
                }
            }
            for (int r = 0; r <= Math.min(3, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c <= 1; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;
                    String v = fmt.formatCellValue(cell).trim().toUpperCase();
                    if ("PKG".equals(v)) return MOBILE;
                }
            }
            if (filename != null) {
                String upper = filename.toUpperCase();
                if (upper.contains("CLIENT")) return CLIENT;
                if (upper.contains("MOBILE")) return MOBILE;
                if (upper.contains("RAW"))    return RAW_DATA;
            }
            return SERVER;
        }
    }

    // ── 파싱 결과 DTO ───────────────────────────────────────────────

    public static class ParseResult {
        public FileType                   fileType       = FileType.SERVER;
        public final List<CellColDef>     cellColDefs    = new ArrayList<>();
        public final List<SpecColDef>     specColDefs    = new ArrayList<>();
        public final List<RowData>        rows           = new ArrayList<>();
        public final List<RawDataColDef>  rawDataColDefs = new ArrayList<>();
        public final List<RawDataRowData> rawDataRows    = new ArrayList<>();
    }

    /** 칩셋 컬럼 정의 (벤더/칩셋 헤더 행) */
    public static class CellColDef {
        public String vendor;
        public int    colIdx;
        public String chipNm;
        public String chipDt;
        public int    sortOrder;
    }

    /** 스펙 컬럼 메타 정의 (좌측 고정 컬럼의 Excel 헤더명) */
    public static class SpecColDef {
        public int    colIdx;    // 1-based
        public String colNm;
        public int    sortOrder;
    }

    /** RawData 컬럼 정의 (0-based Excel 컬럼 인덱스) */
    public static class RawDataColDef {
        public int    colIdx;    // 0-based Excel column index
        public String colNm;
        public int    sortOrder;
    }

    /** Server / Client / Mobile 공용 행 데이터 */
    public static class RowData {
        public String[] specVals  = new String[10];
        public int      sortOrder;
        public final Map<Integer, String> cellValues = new HashMap<>();
        public final Map<Integer, String> cellColors = new HashMap<>();
    }

    /** Raw_Data 행 데이터 (generic — colIdx → value) */
    public static class RawDataRowData {
        public int rowIdx;
        public final Map<Integer, String> cellValues = new HashMap<>();
    }

    // ── 진입점 ─────────────────────────────────────────────────────

    public static ParseResult parse(File file, String filename) throws Exception {
        ParseResult result = new ParseResult();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            result.fileType = FileType.detect(filename, sheet, fmt);
            if (result.fileType == FileType.RAW_DATA) {
                parseRawData(sheet, fmt, result);
            } else {
                parseMatrix(sheet, fmt, result);
            }
        }
        return result;
    }

    // ── Matrix 파싱 (Server / Client / Mobile 공용) ─────────────────

    private static void parseMatrix(Sheet sheet, DataFormatter fmt, ParseResult result) {
        boolean isMobile = (result.fileType == FileType.MOBILE);

        int headerRowIdx   = findHeaderRow(sheet, fmt, isMobile);
        int chipNameRowIdx = isMobile ? headerRowIdx : headerRowIdx + 1;
        int dateRowIdx     = isMobile ? headerRowIdx + 2 : headerRowIdx + 2;
        int dataStartIdx   = isMobile ? headerRowIdx + 3 : headerRowIdx + 3;
        int specColCount   = isMobile ? 5 : 6;

        buildSpecColDefs(sheet, fmt, headerRowIdx, specColCount, result);
        buildCellColDefs(sheet, fmt, headerRowIdx, chipNameRowIdx, dateRowIdx,
                         specColCount, result);
        parseDataRows(sheet, fmt, dataStartIdx, specColCount, result);
    }

    // ── 헤더 행 탐색 ────────────────────────────────────────────────

    private static int findHeaderRow(Sheet sheet, DataFormatter fmt, boolean isMobile) {
        for (int r = 0; r <= Math.min(4, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = fmt.formatCellValue(cell).trim().toUpperCase();
                if (isMobile) {
                    if ("PKG".equals(val)) return r;
                } else {
                    if ("DIMM".equals(val) || val.contains("PRODUCT")) return r;
                }
            }
        }
        return 0;
    }

    // ── 스펙 컬럼 메타 읽기 ─────────────────────────────────────────

    private static void buildSpecColDefs(Sheet sheet, DataFormatter fmt,
                                         int headerRowIdx, int specColCount,
                                         ParseResult result) {
        Row headerRow = sheet.getRow(headerRowIdx);
        if (headerRow == null) return;
        for (int i = 0; i < specColCount && i < 10; i++) {
            Cell cell = headerRow.getCell(i);
            String nm = (cell != null) ? fmt.formatCellValue(cell).trim() : "";
            if (nm.isEmpty()) nm = "Col" + (i + 1);
            SpecColDef scd = new SpecColDef();
            scd.colIdx    = i + 1;  // 1-based
            scd.colNm     = nm;
            scd.sortOrder = i;
            result.specColDefs.add(scd);
        }
    }

    // ── 셀 컬럼 구조 파악 (동적 벤더) ──────────────────────────────

    private static void buildCellColDefs(Sheet sheet, DataFormatter fmt,
                                         int headerRowIdx, int chipNameRowIdx,
                                         int dateRowIdx, int specColCount,
                                         ParseResult result) {
        Row groupRow    = sheet.getRow(headerRowIdx);
        Row chipNameRow = sheet.getRow(chipNameRowIdx);
        Row dateRow     = sheet.getRow(dateRowIdx);
        if (groupRow == null || chipNameRow == null) return;

        Map<Integer, String>  vendorByStart = new LinkedHashMap<>();
        Map<Integer, Integer> vendorEndCol  = new HashMap<>();

        // 1순위: 병합 셀에서 벤더 감지
        for (CellRangeAddress merged : sheet.getMergedRegions()) {
            if (merged.getFirstRow() != headerRowIdx) continue;
            if (merged.getFirstColumn() < specColCount) continue;
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
            for (int c = specColCount; c < lastCellNum; c++) {
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

                CellColDef def = new CellColDef();
                def.vendor    = vendor;
                def.colIdx    = c;
                def.chipNm    = chipNm;
                def.chipDt    = (dateRow != null && dateRow.getCell(c) != null)
                        ? fmt.formatCellValue(dateRow.getCell(c)).trim() : "";
                def.sortOrder = sortOrder++;
                result.cellColDefs.add(def);
            }
        }
    }

    // ── 데이터 행 파싱 ────────────────────────────────────────────

    private static void parseDataRows(Sheet sheet, DataFormatter fmt,
                                      int dataStartIdx, int specColCount,
                                      ParseResult result) {
        Set<Integer> chipColIdxSet = new HashSet<>();
        for (CellColDef def : result.cellColDefs) chipColIdxSet.add(def.colIdx);

        int sortOrder = 0;
        for (int r = dataStartIdx; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (isRowEmpty(row, fmt, specColCount)) continue;

            RowData rd = new RowData();
            for (int i = 0; i < specColCount && i < 10; i++) {
                rd.specVals[i] = getCellStr(row, i, fmt);
            }
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

    // ── Raw_Data 파싱 ──────────────────────────────────────────────
    // Row 0: 섹션 그룹 헤더 (Target AP / Sorting KEY / Validation Status)
    // Row 1: 컬럼 헤더 (Company, Seg, Chipset, ...)
    // Row 2+: 데이터

    private static void parseRawData(Sheet sheet, DataFormatter fmt, ParseResult result) {
        int headerRowIdx = 1;
        int dataStartIdx = 2;

        // 컬럼 헤더 읽기
        Row headerRow = sheet.getRow(headerRowIdx);
        if (headerRow != null) {
            int sortOrder = 0;
            int lastCol = headerRow.getLastCellNum();
            for (int c = 1; c < lastCol; c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) continue;
                String nm = fmt.formatCellValue(cell).trim();
                if (nm.isEmpty()) continue;
                RawDataColDef def = new RawDataColDef();
                def.colIdx    = c;
                def.colNm     = nm;
                def.sortOrder = sortOrder++;
                result.rawDataColDefs.add(def);
            }
        }

        Set<Integer> colIdxSet = new HashSet<>();
        for (RawDataColDef def : result.rawDataColDefs) colIdxSet.add(def.colIdx);

        int rowIdx = 0;
        for (int r = dataStartIdx; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            if (getCellStr(row, 1, fmt).isEmpty() && getCellStr(row, 3, fmt).isEmpty()) continue;

            RawDataRowData rd = new RawDataRowData();
            rd.rowIdx = rowIdx++;
            for (int colIdx : colIdxSet) {
                rd.cellValues.put(colIdx, getCellStr(row, colIdx, fmt));
            }
            result.rawDataRows.add(rd);
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────

    private static boolean isRowEmpty(Row row, DataFormatter fmt, int specColCount) {
        for (int c = 0; c < specColCount; c++) {
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
        String hex = String.format("#%02X%02X%02X",
                rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        if ("#FFFFFF".equals(hex) || "#000000".equals(hex)) return null;
        return hex;
    }
}

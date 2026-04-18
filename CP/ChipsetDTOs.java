// ── ChipsetUploadResponse.java ───────────────────────────────────
package com.example.chipset.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChipsetUploadResponse {
    private String lastVersion;          // "2025.04.14 09:30:00"
    private List<ChipColumnDef> chipCols;
    private List<Map<String,Object>> rows;
    private int totalCount;
}

// ── ChipColumnDef.java ───────────────────────────────────────────
package com.example.chipset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChipColumnDef {
    private String key;      // "chip_6"
    private String chip;     // "SPR-SP(4800)"
    private String date;     // "01 '23"
    private int    colIdx;   // 6
    private String type;     // "intel" | "amd"
}

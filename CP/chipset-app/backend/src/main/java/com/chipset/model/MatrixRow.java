package com.chipset.model;

import lombok.Data;
import java.util.List;

@Data
public class MatrixRow {
    private Integer           rowIdx;
    private List<ChipsetCell> specCells;
    private List<ChipsetCell> chipCells;
}

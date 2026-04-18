<template>
  <div class="cv-root">
    <!-- ─── Header ──────────────────────────────────────────── -->
    <header class="cv-header">
      <div class="cv-header__left">
        <span class="cv-header__badge">CHIPSET VALIDATION</span>
        <h1 class="cv-header__title">Compatibility Matrix</h1>
      </div>
      <div class="cv-header__right">
        <div v-if="lastVersion" class="cv-version">
          <span class="cv-version__label">LAST VERSION</span>
          <span class="cv-version__value">{{ lastVersion }}</span>
        </div>
        <label class="cv-btn cv-btn--upload">
          <input type="file" accept=".xlsx,.xls" @change="onUpload" hidden />
          <IconUpload /> UPLOAD EXCEL
        </label>
        <button class="cv-btn cv-btn--download" :disabled="!rows.length" @click="downloadExcel">
          <IconDownload /> DOWNLOAD
        </button>
      </div>
    </header>

    <!-- ─── Filter Bar ───────────────────────────────────────── -->
    <section class="cv-filter" v-if="rows.length">
      <div class="cv-filter__group">
        <label>출시일자 이후</label>
        <input
          type="month"
          v-model="filterDate"
          class="cv-input"
          placeholder="YYYY-MM"
        />
      </div>
      <div class="cv-filter__group" v-for="f in specFilters" :key="f.key">
        <label>{{ f.label }}</label>
        <select v-model="f.value" class="cv-input">
          <option value="">전체</option>
          <option v-for="o in f.options" :key="o" :value="o">{{ o }}</option>
        </select>
      </div>
      <button class="cv-btn cv-btn--reset" @click="resetFilters">RESET</button>
    </section>

    <!-- ─── Empty / Loading ──────────────────────────────────── -->
    <div v-if="!rows.length" class="cv-empty">
      <div class="cv-empty__icon">⬆</div>
      <p>Excel 파일을 업로드하면 데이터가 표시됩니다.</p>
      <p class="cv-empty__sub">지원 형식: .xlsx · .xls</p>
    </div>

    <!-- ─── Table ────────────────────────────────────────────── -->
    <div v-else class="cv-table-wrap">
      <div class="cv-table-scroll" ref="tableScroll">
        <table class="cv-table" :style="{ minWidth: tableMinWidth + 'px' }">
          <thead>
            <!-- Row 1: Group headers -->
            <tr>
              <th
                v-for="col in frozenCols"
                :key="'f1-' + col.key"
                class="cv-th cv-th--frozen cv-th--spec"
                :style="frozenStyle(col)"
                :rowspan="2"
              >
                {{ col.label }}
              </th>
              <th
                v-for="grp in chipGroups"
                :key="'g-' + grp.name"
                :colspan="grp.cols.length"
                class="cv-th"
                :class="`cv-th--${grp.type}`"
              >
                {{ grp.name }}
              </th>
            </tr>
            <!-- Row 2: Sub-headers (chip names + dates) -->
            <tr>
              <template v-for="grp in chipGroups" :key="'sub-' + grp.name">
                <th
                  v-for="col in grp.cols"
                  :key="'s-' + col.key"
                  class="cv-th cv-th--sub"
                  :class="`cv-th--${grp.type}`"
                >
                  <div class="cv-th-chip">{{ col.chip }}</div>
                  <div class="cv-th-date">{{ col.date }}</div>
                </th>
              </template>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, ri) in filteredRows"
              :key="ri"
              class="cv-tr"
              :class="{ 'cv-tr--alt': ri % 2 === 1 }"
            >
              <!-- Frozen spec columns -->
              <td
                v-for="col in frozenCols"
                :key="'fc-' + col.key"
                class="cv-td cv-td--frozen cv-td--spec"
                :style="frozenStyle(col)"
              >
                {{ row[col.key] }}
              </td>
              <!-- Chip data columns -->
              <template v-for="grp in chipGroups" :key="'dg-' + grp.name">
                <td
                  v-for="col in grp.cols"
                  :key="'d-' + col.key"
                  class="cv-td"
                  :class="[
                    `cv-td--${grp.type}`,
                    !row[col.key] ? 'cv-td--empty' : '',
                  ]"
                  :style="cellStyle(row, col)"
                >
                  {{ row[col.key] || '' }}
                </td>
              </template>
            </tr>
            <tr v-if="!filteredRows.length">
              <td :colspan="totalCols" class="cv-td cv-td--nodata">
                조건에 맞는 데이터가 없습니다.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import * as XLSX from 'xlsx'

/* ── Icon stubs (replace with your icon library) ─────────────── */
const IconUpload = { template: `<span style="margin-right:6px">↑</span>` }
const IconDownload = { template: `<span style="margin-right:6px">↓</span>` }

/* ── State ────────────────────────────────────────────────────── */
const rows       = ref([])          // parsed data rows
const chipGroups = ref([])          // [{ name, type:'intel'|'amd', cols:[{key,chip,date}] }]
const cellColors = ref({})          // { 'rowIdx_colKey': '#rrggbb' }
const lastVersion = ref('')
const tableScroll = ref(null)

const filterDate = ref('')
const specFilters = reactive([
  { key: 'dimm',    label: 'DIMM',         value: '', options: [] },
  { key: 'product', label: 'Product(Ver.)', value: '', options: [] },
  { key: 'ver',     label: 'Ver.',          value: '', options: [] },
  { key: 'density', label: 'Density',       value: '', options: [] },
  { key: 'org',     label: 'Org',           value: '', options: [] },
  { key: 'speed',   label: 'Speed',         value: '', options: [] },
])

/* ── Frozen spec columns (A~F) ───────────────────────────────── */
const frozenCols = [
  { key: 'dimm',    label: 'DIMM',          width: 90 },
  { key: 'product', label: 'Product (Ver.)', width: 130 },
  { key: 'ver',     label: 'Ver.',           width: 50 },
  { key: 'density', label: 'Density',        width: 70 },
  { key: 'org',     label: 'Org',            width: 60 },
  { key: 'speed',   label: 'Speed',          width: 70 },
]

/* ── Computed ─────────────────────────────────────────────────── */
const totalCols = computed(() =>
  frozenCols.length +
  chipGroups.value.reduce((a, g) => a + g.cols.length, 0)
)

const tableMinWidth = computed(() =>
  frozenCols.reduce((a, c) => a + c.width, 0) +
  chipGroups.value.reduce((a, g) => a + g.cols.length * 88, 0)
)

const frozenLeft = computed(() => {
  let acc = 0
  const map = {}
  for (const col of frozenCols) {
    map[col.key] = acc
    acc += col.width
  }
  return map
})

const frozenStyle = (col) => ({
  position: 'sticky',
  left: frozenLeft.value[col.key] + 'px',
  minWidth: col.width + 'px',
  maxWidth: col.width + 'px',
  zIndex: 10,
})

const filteredRows = computed(() => {
  let result = rows.value

  // Spec filters
  for (const f of specFilters) {
    if (f.value) {
      result = result.filter(r => String(r[f.key]) === f.value)
    }
  }

  // Date filter: hide rows where ALL chip dates are before the filter
  if (filterDate.value) {
    const [fy, fm] = filterDate.value.split('-').map(Number) // 2025, 2
    result = result.filter(row => {
      // collect all chip dates in this row
      const dates = []
      for (const grp of chipGroups.value) {
        for (const col of grp.cols) {
          const v = row[col.key]
          if (v) dates.push(parseChipDate(v))
        }
      }
      // keep if at least one chip date >= filter date
      return dates.some(d => d && (d.y > fy || (d.y === fy && d.m >= fm)))
    })
  }

  return result
})

/* ── Parse mm 'yy chip date ──────────────────────────────────── */
function parseChipDate(str) {
  if (!str) return null
  // formats: "01 '23"  "12 '23"
  const m = String(str).match(/(\d{1,2})\s*'(\d{2})/)
  if (!m) return null
  return { m: parseInt(m[1]), y: 2000 + parseInt(m[2]) }
}

/* ── Cell background color ───────────────────────────────────── */
function cellStyle(row, col) {
  const key = `${row.__idx}_${col.key}`
  const color = cellColors.value[key]
  if (color) return { backgroundColor: color }
  return {}
}

/* ── Excel Upload ─────────────────────────────────────────────── */
function onUpload(e) {
  const file = e.target.files[0]
  if (!file) return

  // Record upload timestamp
  const now = new Date()
  lastVersion.value = now.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })

  const reader = new FileReader()
  reader.onload = (ev) => parseExcel(ev.target.result, file.name)
  reader.readAsArrayBuffer(file)
}

function parseExcel(buffer, filename) {
  const wb = XLSX.read(buffer, { type: 'array', cellStyles: true })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const range = XLSX.utils.decode_range(ws['!ref'])

  /* ── Row 0: date header (e.g. "04' 26") ── */
  /* ── Row 1: column headers ── */
  /* ── Row 2+: data ── */

  // Read all raw cells for header analysis
  const rawRows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })

  // Find header row (row with 'DIMM' or 'RDIMM')
  let headerRowIdx = 1
  for (let r = 0; r < Math.min(5, rawRows.length); r++) {
    if (rawRows[r].some(c => String(c).toUpperCase() === 'DIMM' ||
                              String(c).toUpperCase() === 'RDIMM' ||
                              String(c).toUpperCase().includes('PRODUCT'))) {
      headerRowIdx = r
      break
    }
  }

  const headerRow = rawRows[headerRowIdx] || []

  // Detect Intel / AMD group spans from merged cells or header content
  // Header layout: [DIMM, Product, Ver., Density, Org, Speed, Intel...(N cols), AMD...(M cols)]
  // Then trailing: chip names row, date row
  // Based on sample: after Speed, find 'Intel' and 'AMD' markers
  let intelStart = -1, intelEnd = -1, amdStart = -1, amdEnd = -1

  // Find Intel/AMD group from merged cell metadata (ws['!merges'])
  const merges = ws['!merges'] || []
  for (const merge of merges) {
    const cell = ws[XLSX.utils.encode_cell({ r: merge.s.r, c: merge.s.c })]
    if (!cell) continue
    const v = String(cell.v || '').toUpperCase()
    if (v === 'INTEL') { intelStart = merge.s.c; intelEnd = merge.e.c }
    if (v === 'AMD')   { amdStart  = merge.s.c; amdEnd  = merge.e.c }
  }

  // Fallback: scan header row for Intel/AMD keywords
  if (intelStart < 0) {
    for (let c = 0; c < headerRow.length; c++) {
      const v = String(headerRow[c]).toUpperCase()
      if (v === 'INTEL' && intelStart < 0) intelStart = c
      if (v === 'AMD'   && amdStart  < 0) amdStart  = c
    }
    if (intelStart >= 0 && amdStart >= 0)  intelEnd = amdStart - 1
    if (amdStart   >= 0) amdEnd = headerRow.length - 1
  }

  // Chip names row = headerRowIdx + 1  (or same row after the group header)
  // Date row = last row of rawRows (or detect from content)
  // From sample data structure, chip names AND dates are in a separate row
  // Let's look for the date row: last row that has mm 'yy pattern
  let chipNameRowIdx = headerRowIdx
  let dateRowIdx     = -1

  for (let r = rawRows.length - 1; r > headerRowIdx; r--) {
    const hasDate = rawRows[r].some(c => /\d{1,2}\s*'\d{2}/.test(String(c)))
    if (hasDate) { dateRowIdx = r; break }
  }

  // First data row
  const dataStartRow = headerRowIdx + 1

  // Build chip column definitions
  const chipColDefs = []
  const SPEC_COUNT = frozenCols.length  // 6

  if (intelStart >= SPEC_COUNT) {
    for (let c = intelStart; c <= intelEnd && c < headerRow.length; c++) {
      const chip = String(rawRows[chipNameRowIdx][c] || rawRows[headerRowIdx][c] || `col${c}`)
      const date = dateRowIdx >= 0 ? String(rawRows[dateRowIdx][c] || '') : ''
      chipColDefs.push({ key: `chip_${c}`, chip, date, colIdx: c, type: 'intel' })
    }
  }
  if (amdStart >= SPEC_COUNT) {
    for (let c = amdStart; c <= amdEnd && c < headerRow.length; c++) {
      const chip = String(rawRows[chipNameRowIdx][c] || rawRows[headerRowIdx][c] || `col${c}`)
      const date = dateRowIdx >= 0 ? String(rawRows[dateRowIdx][c] || '') : ''
      chipColDefs.push({ key: `chip_${c}`, chip, date, colIdx: c, type: 'amd' })
    }
  }

  // If merge detection failed, use all columns after SPEC_COUNT as chips
  if (chipColDefs.length === 0) {
    for (let c = SPEC_COUNT; c < headerRow.length; c++) {
      const chip = String(headerRow[c] || `col${c}`)
      chipColDefs.push({ key: `chip_${c}`, chip, date: '', colIdx: c, type: 'intel' })
    }
  }

  // Build chip groups
  const groups = []
  const intelCols = chipColDefs.filter(d => d.type === 'intel')
  const amdCols   = chipColDefs.filter(d => d.type === 'amd')
  if (intelCols.length) groups.push({ name: 'Intel', type: 'intel', cols: intelCols })
  if (amdCols.length)   groups.push({ name: 'AMD',   type: 'amd',   cols: amdCols   })
  chipGroups.value = groups

  // Build data rows + collect cell colors
  const newRows = []
  const newColors = {}
  const specKeys = ['dimm', 'product', 'ver', 'density', 'org', 'speed']

  for (let r = dataStartRow; r <= range.e.r; r++) {
    const rawRow = rawRows[r]
    if (!rawRow || rawRow.every(c => c === '' || c === null || c === undefined)) continue

    const row = { __idx: r }
    specKeys.forEach((k, i) => { row[k] = String(rawRow[i] || '') })

    for (const colDef of chipColDefs) {
      const val = rawRow[colDef.colIdx]
      row[colDef.key] = val !== undefined && val !== '' ? String(val) : ''

      // Extract cell color
      const cellAddr = XLSX.utils.encode_cell({ r, c: colDef.colIdx })
      const cell = ws[cellAddr]
      if (cell && cell.s && cell.s.fgColor && cell.s.fgColor.rgb) {
        const rgb = cell.s.fgColor.rgb
        if (rgb && rgb !== 'FFFFFF' && rgb !== '000000' && rgb.length === 6) {
          newColors[`${r}_${colDef.key}`] = `#${rgb}`
        }
      }
    }

    newRows.push(row)
  }

  rows.value = newRows
  cellColors.value = newColors

  // Populate filter options
  for (const f of specFilters) {
    f.value = ''
    const vals = [...new Set(newRows.map(r => r[f.key]).filter(Boolean))].sort()
    f.options = vals
  }
}

/* ── Filters reset ────────────────────────────────────────────── */
function resetFilters() {
  filterDate.value = ''
  for (const f of specFilters) f.value = ''
}

/* ── Excel Download ───────────────────────────────────────────── */
function downloadExcel() {
  const wb = XLSX.utils.book_new()

  // Build header rows
  const headerRow1 = ['DIMM', 'Product (Ver.)', 'Ver.', 'Density', 'Org', 'Speed']
  const headerRow2 = ['', '', '', '', '', '']
  const dateRow    = ['', '', '', '', '', '']

  for (const grp of chipGroups.value) {
    headerRow1.push(grp.name, ...Array(grp.cols.length - 1).fill(''))
    for (const col of grp.cols) {
      headerRow2.push(col.chip)
      dateRow.push(col.date)
    }
  }

  const allCols = chipGroups.value.flatMap(g => g.cols)
  const dataRows = filteredRows.value.map(row => [
    row.dimm, row.product, row.ver, row.density, row.org, row.speed,
    ...allCols.map(c => row[c.key] || ''),
  ])

  const ws = XLSX.utils.aoa_to_sheet([headerRow1, headerRow2, dateRow, ...dataRows])
  XLSX.utils.book_append_sheet(wb, ws, 'ChipsetValidation')
  XLSX.writeFile(wb, `ChipsetValidation_${new Date().toISOString().slice(0,10)}.xlsx`)
}
</script>

<style scoped>
/* ── Root ───────────────────────────────────────────────────── */
.cv-root {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  background: #0d0e14;
  color: #c8ccd6;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Header ─────────────────────────────────────────────────── */
.cv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  background: #13141d;
  border-bottom: 2px solid #1e3a5f;
  gap: 16px;
  flex-wrap: wrap;
}
.cv-header__left { display: flex; align-items: center; gap: 16px; }
.cv-header__badge {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #3b82f6;
  background: rgba(59,130,246,0.12);
  border: 1px solid rgba(59,130,246,0.3);
  padding: 3px 10px;
  border-radius: 3px;
}
.cv-header__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #e8eaf0;
  letter-spacing: 0.04em;
}
.cv-header__right { display: flex; align-items: center; gap: 12px; }

/* ── Version badge ──────────────────────────────────────────── */
.cv-version {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}
.cv-version__label { font-size: 9px; color: #6b7280; letter-spacing: 0.12em; }
.cv-version__value { font-size: 12px; color: #f59e0b; font-weight: 600; }

/* ── Buttons ─────────────────────────────────────────────────── */
.cv-btn {
  display: inline-flex;
  align-items: center;
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  padding: 8px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.cv-btn--upload {
  background: #1d4ed8;
  color: #fff;
  border: 1px solid #3b82f6;
}
.cv-btn--upload:hover { background: #2563eb; }
.cv-btn--download {
  background: #064e3b;
  color: #6ee7b7;
  border: 1px solid #10b981;
}
.cv-btn--download:hover:not(:disabled) { background: #065f46; }
.cv-btn--download:disabled { opacity: 0.4; cursor: not-allowed; }
.cv-btn--reset {
  background: #1f2937;
  color: #9ca3af;
  border: 1px solid #374151;
  margin-left: 8px;
}
.cv-btn--reset:hover { background: #374151; color: #d1d5db; }

/* ── Filter Bar ─────────────────────────────────────────────── */
.cv-filter {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 28px;
  background: #111218;
  border-bottom: 1px solid #1e293b;
}
.cv-filter__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cv-filter__group label {
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #4b5563;
}
.cv-input {
  font-family: inherit;
  font-size: 12px;
  background: #1a1c27;
  border: 1px solid #2d3148;
  color: #c8ccd6;
  padding: 6px 10px;
  border-radius: 4px;
  outline: none;
  transition: border-color 0.15s;
  min-width: 110px;
}
.cv-input:focus { border-color: #3b82f6; }

/* ── Empty State ─────────────────────────────────────────────── */
.cv-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #374151;
}
.cv-empty__icon { font-size: 48px; opacity: 0.3; }
.cv-empty p { margin: 0; font-size: 14px; }
.cv-empty__sub { font-size: 11px; color: #1f2937; }

/* ── Table Wrapper ───────────────────────────────────────────── */
.cv-table-wrap { flex: 1; overflow: hidden; padding: 16px 28px 28px; }
.cv-table-scroll {
  overflow: auto;
  max-height: calc(100vh - 220px);
  border: 1px solid #1e293b;
  border-radius: 6px;
  background: #0f1018;
}
/* Scrollbar styling */
.cv-table-scroll::-webkit-scrollbar { width: 8px; height: 8px; }
.cv-table-scroll::-webkit-scrollbar-track { background: #111218; }
.cv-table-scroll::-webkit-scrollbar-thumb { background: #2d3148; border-radius: 4px; }
.cv-table-scroll::-webkit-scrollbar-thumb:hover { background: #3d4468; }

/* ── Table ───────────────────────────────────────────────────── */
.cv-table {
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11px;
  white-space: nowrap;
  width: 100%;
}

/* ── TH ─────────────────────────────────────────────────────── */
.cv-th {
  padding: 8px 10px;
  text-align: center;
  font-weight: 700;
  letter-spacing: 0.06em;
  font-size: 10px;
  border-bottom: 1px solid #1e293b;
  border-right: 1px solid #1a2035;
  position: sticky;
  top: 0;
  z-index: 5;
  background: #13141d;
  color: #94a3b8;
}
.cv-th:first-child { border-left: none; }
.cv-th--spec {
  background: #0f1729;
  color: #60a5fa;
  z-index: 20;
}
.cv-th--frozen { top: 0; }
.cv-th--intel {
  background: #0d1f38;
  color: #93c5fd;
}
.cv-th--amd {
  background: #1a0f2e;
  color: #c4b5fd;
}
.cv-th--sub {
  top: 37px; /* second header row */
  font-weight: 600;
  font-size: 9px;
}
.cv-th-chip { font-weight: 700; letter-spacing: 0.04em; }
.cv-th-date { font-size: 8px; color: #4b5563; margin-top: 2px; }

/* ── TR ─────────────────────────────────────────────────────── */
.cv-tr { transition: background 0.1s; }
.cv-tr:hover .cv-td { background-color: #161828 !important; }
.cv-tr--alt .cv-td:not(.cv-td--frozen):not(.cv-td--spec) { background: rgba(255,255,255,0.012); }

/* ── TD ─────────────────────────────────────────────────────── */
.cv-td {
  padding: 5px 10px;
  border-bottom: 1px solid #131520;
  border-right: 1px solid #131520;
  text-align: center;
  background: #0f1018;
  color: #c8ccd6;
  transition: background 0.1s;
}
.cv-td--frozen, .cv-td--spec {
  background: #0d1220;
  color: #e2e8f0;
  font-weight: 500;
  position: sticky;
  z-index: 3;
}
.cv-td--intel { color: #93c5fd; }
.cv-td--amd   { color: #c4b5fd; }

/* Empty cell */
.cv-td--empty {
  background: #0c0d12 !important;
  color: transparent;
}
.cv-td--empty::after {
  content: '';
  display: inline-block;
  width: 14px;
  height: 14px;
  background: #1a1d2a;
  border-radius: 2px;
}

.cv-td--nodata {
  text-align: center;
  padding: 32px;
  color: #374151;
  font-size: 13px;
}
</style>

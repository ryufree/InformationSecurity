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
        <input type="month" v-model="filterDate" class="cv-input" placeholder="YYYY-MM" />
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

    <!-- ─── Empty ───────────────────────────────────────────── -->
    <div v-if="!rows.length" class="cv-empty">
      <div class="cv-empty__icon">⬆</div>
      <p>Excel 파일을 업로드하면 데이터가 표시됩니다.</p>
      <p class="cv-empty__sub">지원 형식: .xlsx · .xls</p>
    </div>

    <!-- ─── 3-Pane Layout ────────────────────────────────────── -->
    <div v-else class="cv-layout">

      <!-- ══ Sticky Header ══ -->
      <div class="cv-head-row">

        <!-- Frozen header (always visible) -->
        <div class="cv-head-frozen" :style="{ width: frozenTotalWidth + 'px' }">
          <table class="cv-tbl">
            <colgroup>
              <col
                v-for="col in frozenCols" :key="col.key"
                :style="{ width: col.width + 'px' }"
              >
            </colgroup>
            <thead>
              <!-- Row 1: group label row — visually same height as Intel/AMD group row -->
              <tr class="cv-hrow cv-hrow--g">
                <th
                  v-for="col in frozenCols" :key="col.key"
                  class="cv-th cv-th--spec"
                  :style="{ width: col.width + 'px', minWidth: col.width + 'px' }"
                >{{ col.label }}</th>
              </tr>
              <!-- Row 2: invisible spacer — forces same height as chip sub-header row -->
              <tr class="cv-hrow cv-hrow--s">
                <th
                  v-for="col in frozenCols" :key="'sp-' + col.key"
                  class="cv-th cv-th--spec cv-th--spacer"
                  :style="{ width: col.width + 'px', minWidth: col.width + 'px' }"
                >
                  <div class="cv-th-chip cv-th--ghost">x</div>
                  <div class="cv-th-date cv-th--ghost">x</div>
                </th>
              </tr>
            </thead>
          </table>
        </div>

        <!-- Intel header -->
        <div class="cv-head-chip" ref="intelHeadRef" v-if="intelGroup">
          <table
            class="cv-tbl cv-tbl--chip"
            :style="{ width: intelTableWidth + 'px', minWidth: intelTableWidth + 'px' }"
          >
            <colgroup>
              <col v-for="col in intelGroup.cols" :key="col.key"
                   :style="{ width: colWidth('intel', col) + 'px' }">
            </colgroup>
            <thead>
              <tr class="cv-hrow cv-hrow--g">
                <th
                  v-if="intelPinnedCols.length"
                  :colspan="intelPinnedCols.length"
                  class="cv-th cv-th--intel cv-th--group cv-th--group-pinned"
                  :style="groupHeaderStyle('intel')"
                >Intel</th>
                <th
                  v-if="intelScrollableColCount"
                  :colspan="intelScrollableColCount"
                  class="cv-th cv-th--intel cv-th--group"
                ></th>
              </tr>
              <tr class="cv-hrow cv-hrow--s">
                <th
                  v-for="(col, idx) in intelGroup.cols" :key="col.key"
                  class="cv-th cv-th--sub cv-th--intel"
                  :class="{ 'cv-chip-sticky': isPinnedChipCol('intel', idx) }"
                  :style="chipStickyStyle('intel', idx, true)"
                >
                  <div class="cv-th-chip">{{ col.chip }}</div>
                  <div class="cv-th-date">{{ col.date }}</div>
                  <span class="cv-col-resizer" @mousedown="startColResize('intel', col, $event)"></span>
                </th>
              </tr>
            </thead>
          </table>
        </div>

        <!-- AMD header -->
        <div class="cv-head-chip" ref="amdHeadRef" v-if="amdGroup">
          <table
            class="cv-tbl cv-tbl--chip"
            :style="{ width: amdTableWidth + 'px', minWidth: amdTableWidth + 'px' }"
          >
            <colgroup>
              <col v-for="col in amdGroup.cols" :key="col.key"
                   :style="{ width: colWidth('amd', col) + 'px' }">
            </colgroup>
            <thead>
              <tr class="cv-hrow cv-hrow--g">
                <th
                  v-if="amdPinnedCols.length"
                  :colspan="amdPinnedCols.length"
                  class="cv-th cv-th--amd cv-th--group cv-th--group-pinned"
                  :style="groupHeaderStyle('amd')"
                >AMD</th>
                <th
                  v-if="amdScrollableColCount"
                  :colspan="amdScrollableColCount"
                  class="cv-th cv-th--amd cv-th--group"
                ></th>
              </tr>
              <tr class="cv-hrow cv-hrow--s">
                <th
                  v-for="(col, idx) in amdGroup.cols" :key="col.key"
                  class="cv-th cv-th--sub cv-th--amd"
                  :class="{ 'cv-chip-sticky': isPinnedChipCol('amd', idx) }"
                  :style="chipStickyStyle('amd', idx, true)"
                >
                  <div class="cv-th-chip">{{ col.chip }}</div>
                  <div class="cv-th-date">{{ col.date }}</div>
                  <span class="cv-col-resizer" @mousedown="startColResize('amd', col, $event)"></span>
                </th>
              </tr>
            </thead>
          </table>
        </div>

      </div><!-- /cv-head-row -->

      <!-- ══ Scrollable Body ══ -->
      <div class="cv-body-row">

        <!-- Frozen body -->
        <div class="cv-body-frozen" :style="{ width: frozenTotalWidth + 'px' }">
          <table class="cv-tbl">
            <colgroup>
              <col
                v-for="col in frozenCols" :key="col.key"
                :style="{ width: col.width + 'px' }"
              >
            </colgroup>
            <tbody>
              <tr
                v-for="(row, ri) in filteredRows" :key="ri"
                class="cv-tr" :class="{ 'cv-tr--alt': ri % 2 === 1 }"
              >
                <td
                  v-for="col in frozenCols" :key="col.key"
                  class="cv-td cv-td--spec"
                  :style="{ width: col.width + 'px', minWidth: col.width + 'px' }"
                >{{ row[col.key] }}</td>
              </tr>
              <tr v-if="!filteredRows.length">
                <td :colspan="frozenCols.length" class="cv-td cv-td--nodata">
                  조건에 맞는 데이터가 없습니다.
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Intel body -->
        <div
          class="cv-body-chip cv-body-chip--intel"
          ref="intelBodyRef"
          v-if="intelGroup"
          @scroll.passive="onIntelScroll"
        >
          <table
            class="cv-tbl"
            :style="{ width: intelTableWidth + 'px', minWidth: intelTableWidth + 'px' }"
          >
            <colgroup>
              <col
                v-for="col in intelGroup.cols" :key="col.key"
                :style="{ width: colWidth('intel', col) + 'px' }"
              >
            </colgroup>
            <tbody>
              <tr
                v-for="(row, ri) in filteredRows" :key="ri"
                class="cv-tr" :class="{ 'cv-tr--alt': ri % 2 === 1 }"
              >
                <td
                  v-for="(col, idx) in intelGroup.cols" :key="col.key"
                  class="cv-td cv-td--intel"
                  :class="{
                    'cv-td--empty': !row[col.key],
                    'cv-chip-sticky': isPinnedChipCol('intel', idx),
                  }"
                  :style="{
                    ...cellStyle(row, col),
                    ...chipStickyStyle('intel', idx),
                    width: colWidth('intel', col) + 'px',
                    minWidth: colWidth('intel', col) + 'px',
                  }"
                >{{ row[col.key] || '' }}</td>
              </tr>
              <tr v-if="!filteredRows.length">
                <td :colspan="intelGroup.cols.length" class="cv-td cv-td--nodata"></td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- AMD body -->
        <div
          class="cv-body-chip cv-body-chip--amd"
          ref="amdBodyRef"
          v-if="amdGroup"
          @scroll.passive="onAmdScroll"
        >
          <table
            class="cv-tbl"
            :style="{ width: amdTableWidth + 'px', minWidth: amdTableWidth + 'px' }"
          >
            <colgroup>
              <col
                v-for="col in amdGroup.cols" :key="col.key"
                :style="{ width: colWidth('amd', col) + 'px' }"
              >
            </colgroup>
            <tbody>
              <tr
                v-for="(row, ri) in filteredRows" :key="ri"
                class="cv-tr" :class="{ 'cv-tr--alt': ri % 2 === 1 }"
              >
                <td
                  v-for="(col, idx) in amdGroup.cols" :key="col.key"
                  class="cv-td cv-td--amd"
                  :class="{
                    'cv-td--empty': !row[col.key],
                    'cv-chip-sticky': isPinnedChipCol('amd', idx),
                  }"
                  :style="{
                    ...cellStyle(row, col),
                    ...chipStickyStyle('amd', idx),
                    width: colWidth('amd', col) + 'px',
                    minWidth: colWidth('amd', col) + 'px',
                  }"
                >{{ row[col.key] || '' }}</td>
              </tr>
              <tr v-if="!filteredRows.length">
                <td :colspan="amdGroup.cols.length" class="cv-td cv-td--nodata"></td>
              </tr>
            </tbody>
          </table>
        </div>

      </div><!-- /cv-body-row -->

    </div><!-- /cv-layout -->
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onUnmounted } from 'vue'
import * as XLSX from 'xlsx'

/* ── Icons ────────────────────────────────────────────────────── */
const IconUpload   = { template: `<span style="margin-right:6px">↑</span>` }
const IconDownload = { template: `<span style="margin-right:6px">↓</span>` }

/* ── State ────────────────────────────────────────────────────── */
const rows       = ref([])
const chipGroups = ref([])
const cellColors = ref({})
const lastVersion = ref('')

const filterDate  = ref('')
const specFilters = reactive([
  { key: 'dimm',    label: 'DIMM',         value: '', options: [] },
  { key: 'product', label: 'Product(Ver.)', value: '', options: [] },
  { key: 'ver',     label: 'Ver.',          value: '', options: [] },
  { key: 'density', label: 'Density',       value: '', options: [] },
  { key: 'org',     label: 'Org',           value: '', options: [] },
  { key: 'speed',   label: 'Speed',         value: '', options: [] },
])

/* ── Frozen spec columns ──────────────────────────────────────── */
const frozenCols = [
  { key: 'dimm',    label: 'DIMM',          width: 90  },
  { key: 'product', label: 'Product (Ver.)', width: 130 },
  { key: 'ver',     label: 'Ver.',           width: 50  },
  { key: 'density', label: 'Density',        width: 70  },
  { key: 'org',     label: 'Org',            width: 60  },
  { key: 'speed',   label: 'Speed',          width: 70  },
]

const pinnedChipCounts = {
  intel: 3,
  amd: 2,
}

/* ── Pane refs & scroll sync ──────────────────────────────────── */
const intelHeadRef = ref(null)
const amdHeadRef   = ref(null)
const intelBodyRef = ref(null)
const amdBodyRef   = ref(null)

function onIntelScroll(e) {
  if (intelHeadRef.value) intelHeadRef.value.scrollLeft = e.target.scrollLeft
}
function onAmdScroll(e) {
  if (amdHeadRef.value) amdHeadRef.value.scrollLeft = e.target.scrollLeft
}

/* ── Chip pane width (ResizeObserver) ────────────────────────────
   헤더 테이블과 바디 테이블이 별도 DOM이므로
   pane 실제 너비를 측정해 동일한 명시적 픽셀 너비를 양쪽에 주입한다.
   ─────────────────────────────────────────────────────────────── */
const CHIP_COL_MIN = 80          // 절대 최소 컬럼 너비(px)
const CHIP_COL_MAX = 600         // 드래그로 늘릴 최대 너비(px)
const CHAR_PX      = 6.5         // JetBrains Mono ~9px 기준 글자폭 추정치
const CELL_PAD     = 24          // 좌우 패딩 합계

const chipPaneWidth = ref(0)
let _ro = null

watch([intelBodyRef, amdBodyRef], ([intel, amd]) => {
  if (_ro) { _ro.disconnect(); _ro = null }
  const el = intel ?? amd
  if (!el) return
  _ro = new ResizeObserver(([entry]) => {
    chipPaneWidth.value = entry.contentRect.width
  })
  _ro.observe(el)
}, { flush: 'post' })

onUnmounted(() => { if (_ro) _ro.disconnect() })

/* 칩 이름 길이 기반 최소 너비 계산 */
function chipMinW(group) {
  if (!group?.cols.length) return CHIP_COL_MIN
  return Math.ceil(Math.max(
    CHIP_COL_MIN,
    ...group.cols.map(c =>
      Math.max(c.chip.length, c.date.length) * CHAR_PX + CELL_PAD
    )
  ))
}

/* 실제 컬럼 너비: pane 여유가 있으면 균등 분할, 없으면 최소 너비 사용 */
function calcColW(group, paneW) {
  const minW = chipMinW(group)
  if (!group || !paneW) return minW
  return Math.max(minW, Math.floor(paneW / group.cols.length))
}

/* ── Computed ─────────────────────────────────────────────────── */
const intelGroup = computed(() => chipGroups.value.find(g => g.type === 'intel') ?? null)
const amdGroup   = computed(() => chipGroups.value.find(g => g.type === 'amd')   ?? null)

const intelColW = computed(() => calcColW(intelGroup.value, chipPaneWidth.value))
const amdColW   = computed(() => calcColW(amdGroup.value,   chipPaneWidth.value))
const intelPinnedCount = computed(() => Math.min(pinnedChipCounts.intel, intelGroup.value?.cols.length ?? 0))
const amdPinnedCount   = computed(() => Math.min(pinnedChipCounts.amd, amdGroup.value?.cols.length ?? 0))
const intelPinnedCols = computed(() => intelGroup.value?.cols.slice(0, intelPinnedCount.value) ?? [])
const amdPinnedCols   = computed(() => amdGroup.value?.cols.slice(0, amdPinnedCount.value) ?? [])
const intelScrollableColCount = computed(() => Math.max((intelGroup.value?.cols.length ?? 0) - intelPinnedCount.value, 0))
const amdScrollableColCount   = computed(() => Math.max((amdGroup.value?.cols.length ?? 0) - amdPinnedCount.value, 0))

/* ── Column resize (Intel / AMD) ─────────────────────────────── */
const COL_WIDTHS_KEY = 'cv_colWidths_v1'
const colWidthOverrides = reactive({
  intel: {},
  amd: {},
})

Object.assign(colWidthOverrides, loadColWidthOverrides())

watch(colWidthOverrides, () => {
  try {
    localStorage.setItem(COL_WIDTHS_KEY, JSON.stringify(colWidthOverrides))
  } catch {}
}, { deep: true })

function loadColWidthOverrides() {
  try {
    const raw = localStorage.getItem(COL_WIDTHS_KEY)
    if (!raw) return { intel: {}, amd: {} }
    const parsed = JSON.parse(raw)
    return {
      intel: (parsed && typeof parsed === 'object' && parsed.intel && typeof parsed.intel === 'object') ? parsed.intel : {},
      amd: (parsed && typeof parsed === 'object' && parsed.amd && typeof parsed.amd === 'object') ? parsed.amd : {},
    }
  } catch {
    return { intel: {}, amd: {} }
  }
}

function clamp(n, min, max) {
  return Math.min(max, Math.max(min, n))
}

function chipColMinW(col) {
  if (!col) return CHIP_COL_MIN
  return Math.ceil(Math.max(
    CHIP_COL_MIN,
    Math.max(String(col.chip ?? '').length, String(col.date ?? '').length) * CHAR_PX + CELL_PAD
  ))
}

function baseColWByType(type) {
  return type === 'amd' ? amdColW.value : intelColW.value
}

function colsByType(type) {
  return type === 'amd' ? (amdGroup.value?.cols ?? []) : (intelGroup.value?.cols ?? [])
}

function colWidth(type, col) {
  const key = col?.key
  const baseW = baseColWByType(type)
  const minW = chipColMinW(col)
  const overrideW = key ? colWidthOverrides[type]?.[key] : undefined
  const w = Number.isFinite(overrideW) ? overrideW : baseW
  return Math.max(minW, w)
}

const intelTableWidth = computed(() => colsByType('intel').reduce((a, c) => a + colWidth('intel', c), 0))
const amdTableWidth   = computed(() => colsByType('amd').reduce((a, c) => a + colWidth('amd', c), 0))

function startColResize(type, col, e) {
  if (!col?.key) return
  e?.preventDefault?.()
  e?.stopPropagation?.()

  const startX = e.clientX
  const minW = chipColMinW(col)
  const startW = colWidth(type, col)

  const onMove = (ev) => {
    const dx = ev.clientX - startX
    const next = clamp(startW + dx, minW, CHIP_COL_MAX)
    colWidthOverrides[type][col.key] = Math.round(next)
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

const frozenTotalWidth = computed(() => frozenCols.reduce((a, c) => a + c.width, 0))

const filteredRows = computed(() => {
  let result = rows.value

  for (const f of specFilters) {
    if (f.value) result = result.filter(r => String(r[f.key]) === f.value)
  }

  if (filterDate.value) {
    const [fy, fm] = filterDate.value.split('-').map(Number)
    result = result.filter(row => {
      const dates = []
      for (const grp of chipGroups.value)
        for (const col of grp.cols) {
          const v = row[col.key]
          if (v) dates.push(parseChipDate(v))
        }
      return dates.some(d => d && (d.y > fy || (d.y === fy && d.m >= fm)))
    })
  }

  return result
})

/* ── Helpers ──────────────────────────────────────────────────── */
function parseChipDate(str) {
  if (!str) return null
  const m = String(str).match(/(\d{1,2})\s*'(\d{2})/)
  if (!m) return null
  return { m: parseInt(m[1]), y: 2000 + parseInt(m[2]) }
}

function cellStyle(row, col) {
  const color = cellColors.value[`${row.__idx}_${col.key}`]
  return color ? { backgroundColor: color } : {}
}

function pinnedCountByType(type) {
  return type === 'amd' ? amdPinnedCount.value : intelPinnedCount.value
}

function isPinnedChipCol(type, index) {
  return index < pinnedCountByType(type)
}

function pinnedLeftPx(type, index) {
  const cols = colsByType(type)
  const max = Math.min(index, pinnedCountByType(type), cols.length)
  let left = 0
  for (let i = 0; i < max; i++) left += colWidth(type, cols[i])
  return left
}

function chipStickyStyle(type, index, isHeader = false) {
  if (!isPinnedChipCol(type, index)) return {}

  return {
    position: 'sticky',
    left: `${pinnedLeftPx(type, index)}px`,
    zIndex: isHeader ? 45 : 15,
  }
}

function groupHeaderStyle(type) {
  const pinnedCount = pinnedCountByType(type)
  if (!pinnedCount) return {}

  return {
    position: 'sticky',
    left: '0px',
    zIndex: 46,
  }
}

/* ── Excel Upload ─────────────────────────────────────────────── */
function onUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  const now = new Date()
  lastVersion.value = now.toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
  const reader = new FileReader()
  reader.onload = (ev) => parseExcel(ev.target.result)
  reader.readAsArrayBuffer(file)
}

function parseExcel(buffer) {
  const wb = XLSX.read(buffer, { type: 'array', cellStyles: true })
  const ws = wb.Sheets[wb.SheetNames[0]]
  const range = XLSX.utils.decode_range(ws['!ref'])
  const rawRows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })

  // Find header row
  let headerRowIdx = 1
  for (let r = 0; r < Math.min(5, rawRows.length); r++) {
    if (rawRows[r].some(c =>
        String(c).toUpperCase() === 'DIMM' ||
        String(c).toUpperCase() === 'RDIMM' ||
        String(c).toUpperCase().includes('PRODUCT'))) {
      headerRowIdx = r; break
    }
  }

  const headerRow = rawRows[headerRowIdx] || []
  let intelStart = -1, intelEnd = -1, amdStart = -1, amdEnd = -1

  // Detect groups via merged cells
  for (const merge of (ws['!merges'] || [])) {
    const cell = ws[XLSX.utils.encode_cell({ r: merge.s.r, c: merge.s.c })]
    if (!cell) continue
    const v = String(cell.v || '').toUpperCase()
    if (v === 'INTEL') { intelStart = merge.s.c; intelEnd = merge.e.c }
    if (v === 'AMD')   { amdStart  = merge.s.c; amdEnd  = merge.e.c }
  }

  // Fallback: scan header row
  if (intelStart < 0) {
    for (let c = 0; c < headerRow.length; c++) {
      const v = String(headerRow[c]).toUpperCase()
      if (v === 'INTEL' && intelStart < 0) intelStart = c
      if (v === 'AMD'   && amdStart  < 0) amdStart  = c
    }
    if (intelStart >= 0 && amdStart >= 0) intelEnd = amdStart - 1
    if (amdStart >= 0) amdEnd = headerRow.length - 1
  }

  // Find date row
  let chipNameRowIdx = headerRowIdx
  let dateRowIdx = -1
  for (let r = rawRows.length - 1; r > headerRowIdx; r--) {
    if (rawRows[r].some(c => /\d{1,2}\s*'\d{2}/.test(String(c)))) {
      dateRowIdx = r; break
    }
  }

  const dataStartRow = headerRowIdx + 1
  const SPEC_COUNT = frozenCols.length
  const chipColDefs = []

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

  if (chipColDefs.length === 0) {
    for (let c = SPEC_COUNT; c < headerRow.length; c++) {
      const chip = String(headerRow[c] || `col${c}`)
      chipColDefs.push({ key: `chip_${c}`, chip, date: '', colIdx: c, type: 'intel' })
    }
  }

  const intelCols = chipColDefs.filter(d => d.type === 'intel')
  const amdCols   = chipColDefs.filter(d => d.type === 'amd')
  chipGroups.value = [
    ...(intelCols.length ? [{ name: 'Intel', type: 'intel', cols: intelCols }] : []),
    ...(amdCols.length   ? [{ name: 'AMD',   type: 'amd',   cols: amdCols   }] : []),
  ]

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
      const cellAddr = XLSX.utils.encode_cell({ r, c: colDef.colIdx })
      const cell = ws[cellAddr]
      if (cell?.s?.fgColor?.rgb) {
        const rgb = cell.s.fgColor.rgb
        if (rgb && rgb !== 'FFFFFF' && rgb !== '000000' && rgb.length === 6)
          newColors[`${r}_${colDef.key}`] = `#${rgb}`
      }
    }
    newRows.push(row)
  }

  rows.value = newRows
  cellColors.value = newColors

  for (const f of specFilters) {
    f.value = ''
    f.options = [...new Set(newRows.map(r => r[f.key]).filter(Boolean))].sort()
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
  XLSX.writeFile(wb, `ChipsetValidation_${new Date().toISOString().slice(0, 10)}.xlsx`)
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

/* ── App Header ─────────────────────────────────────────────── */
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
.cv-header__left  { display: flex; align-items: center; gap: 16px; }
.cv-header__badge {
  font-size: 10px; font-weight: 700; letter-spacing: 0.18em;
  color: #3b82f6; background: rgba(59,130,246,0.12);
  border: 1px solid rgba(59,130,246,0.3);
  padding: 3px 10px; border-radius: 3px;
}
.cv-header__title {
  margin: 0; font-size: 18px; font-weight: 600;
  color: #e8eaf0; letter-spacing: 0.04em;
}
.cv-header__right { display: flex; align-items: center; gap: 12px; }

/* ── Version badge ──────────────────────────────────────────── */
.cv-version { display: flex; flex-direction: column; align-items: flex-end; line-height: 1.2; }
.cv-version__label { font-size: 9px; color: #6b7280; letter-spacing: 0.12em; }
.cv-version__value { font-size: 12px; color: #f59e0b; font-weight: 600; }

/* ── Buttons ─────────────────────────────────────────────────── */
.cv-btn {
  display: inline-flex; align-items: center;
  font-family: inherit; font-size: 11px; font-weight: 700;
  letter-spacing: 0.1em; padding: 8px 16px;
  border-radius: 4px; border: none; cursor: pointer; transition: all 0.15s;
}
.cv-btn--upload { background: #1d4ed8; color: #fff; border: 1px solid #3b82f6; }
.cv-btn--upload:hover { background: #2563eb; }
.cv-btn--download { background: #064e3b; color: #6ee7b7; border: 1px solid #10b981; }
.cv-btn--download:hover:not(:disabled) { background: #065f46; }
.cv-btn--download:disabled { opacity: 0.4; cursor: not-allowed; }
.cv-btn--reset { background: #1f2937; color: #9ca3af; border: 1px solid #374151; margin-left: 8px; }
.cv-btn--reset:hover { background: #374151; color: #d1d5db; }

/* ── Filter Bar ─────────────────────────────────────────────── */
.cv-filter {
  display: flex; align-items: flex-end; flex-wrap: wrap;
  gap: 12px; padding: 14px 28px;
  background: #111218; border-bottom: 1px solid #1e293b;
}
.cv-filter__group { display: flex; flex-direction: column; gap: 4px; }
.cv-filter__group label { font-size: 9px; font-weight: 700; letter-spacing: 0.12em; color: #4b5563; }
.cv-input {
  font-family: inherit; font-size: 12px;
  background: #1a1c27; border: 1px solid #2d3148;
  color: #c8ccd6; padding: 6px 10px; border-radius: 4px;
  outline: none; transition: border-color 0.15s; min-width: 110px;
}
.cv-input:focus { border-color: #3b82f6; }

/* ── Empty State ─────────────────────────────────────────────── */
.cv-empty {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 10px; color: #374151;
}
.cv-empty__icon { font-size: 48px; opacity: 0.3; }
.cv-empty p { margin: 0; font-size: 14px; }
.cv-empty__sub { font-size: 11px; color: #1f2937; }

/* ══════════════════════════════════════════════════════════════
   3-Pane Layout
   ══════════════════════════════════════════════════════════════ */

/* Outer layout: flex column, vertically scrollable */
.cv-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: calc(100vh - 170px);
}
.cv-layout::-webkit-scrollbar { width: 8px; }
.cv-layout::-webkit-scrollbar-track { background: #111218; }
.cv-layout::-webkit-scrollbar-thumb { background: #2d3148; border-radius: 4px; }
.cv-layout::-webkit-scrollbar-thumb:hover { background: #3d4468; }

/* ── Sticky header row ──────────────────────────────────────── */
.cv-head-row {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  background: #13141d;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}

/* ── Body row ───────────────────────────────────────────────── */
.cv-body-row {
  display: flex;
  flex: 1;
}

/* ── Frozen pane (header + body) ────────────────────────────── */
.cv-head-frozen {
  flex: none;
  overflow: hidden;
  border-right: 2px solid #1e3a5f;
  background: #0f1729;
}
.cv-body-frozen {
  flex: none;
  overflow: hidden;
  border-right: 2px solid #1e3a5f;
  background: #0d1220;
}

/* ── Chip panes (Intel / AMD) — equal 50% split ─────────────── */
.cv-head-chip {
  flex: 1;
  overflow: hidden;       /* header does NOT scroll — synced via scrollLeft in JS */
  min-width: 0;
}
.cv-head-chip + .cv-head-chip {
  border-left: 2px solid #2d1a4a;
}

.cv-body-chip {
  flex: 1;
  overflow-x: auto;       /* ← independent horizontal scroll per group */
  overflow-y: hidden;
  min-width: 0;
}
.cv-body-chip + .cv-body-chip {
  border-left: 2px solid #2d1a4a;
}

/* Scrollbar for chip panes */
.cv-body-chip::-webkit-scrollbar { height: 8px; }
.cv-body-chip::-webkit-scrollbar-track { background: #111218; }
.cv-body-chip::-webkit-scrollbar-thumb { background: #2d3148; border-radius: 4px; }
.cv-body-chip::-webkit-scrollbar-thumb:hover { background: #3d4468; }

/* ── Table base ─────────────────────────────────────────────── */
.cv-tbl {
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11px;
  white-space: nowrap;
  table-layout: fixed;
  width: 100%;
}

/* ── TH ─────────────────────────────────────────────────────── */
.cv-th {
  padding: 8px 10px;
  text-align: center;
  vertical-align: middle;
  font-weight: 700;
  letter-spacing: 0.06em;
  font-size: 10px;
  border-bottom: 1px solid #1e293b;
  border-right: 1px solid #1a2035;
  color: #94a3b8;
  background: #13141d;
}
.cv-th:last-child { border-right: none; }

/* 칩 서브헤더: 절대 잘림 없음 */
.cv-th--chip-cell {
  overflow: visible;
  white-space: nowrap;
}

.cv-th--spec {
  background: #0f1729;
  color: #60a5fa;
}
.cv-th--intel {
  background: #0d1f38;
  color: #93c5fd;
}
.cv-th--amd {
  background: #1a0f2e;
  color: #c4b5fd;
}
.cv-th--sub {
  position: relative;
  font-weight: 600;
  font-size: 9px;
}
.cv-th--group {
  position: relative;
}
.cv-th--group-pinned {
  box-shadow: 1px 0 0 #1e3a5f;
}
.cv-hrow--g .cv-th {
  height: 34px;
  padding-top: 0;
  padding-bottom: 0;
}
.cv-hrow--s .cv-th {
  height: 40px;
  padding-top: 6px;
  padding-bottom: 6px;
}
.cv-th-chip { font-weight: 700; letter-spacing: 0.04em; }
.cv-th-date { font-size: 8px; color: #4b5563; margin-top: 2px; }

.cv-col-resizer {
  position: absolute;
  top: 0;
  right: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  background: transparent;
}
.cv-col-resizer::after {
  content: '';
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 3px;
  width: 2px;
  border-radius: 2px;
  background: rgba(148, 163, 184, 0.25);
}
.cv-col-resizer:hover::after {
  background: rgba(148, 163, 184, 0.55);
}

/* Ghost content in frozen header row 2 — invisible but occupies height */
.cv-th--ghost {
  visibility: hidden;
  user-select: none;
  pointer-events: none;
}
/* Frozen spacer cells: no bottom border so it doesn't add visual noise */
.cv-th--spacer {
  border-bottom: none !important;
}

.cv-chip-sticky {
  position: sticky;
}
.cv-th.cv-chip-sticky,
.cv-td.cv-chip-sticky {
  box-shadow: 1px 0 0 rgba(30, 58, 95, 0.95);
}
.cv-th--amd.cv-chip-sticky,
.cv-td--amd.cv-chip-sticky {
  box-shadow: 1px 0 0 rgba(88, 28, 135, 0.95);
}

/* ── TR ─────────────────────────────────────────────────────── */
.cv-tr { transition: background 0.1s; }
.cv-tr--alt .cv-td:not(.cv-td--spec) { background: rgba(255,255,255,0.013); }

/* ── TD ─────────────────────────────────────────────────────── */
.cv-td {
  padding: 5px 10px;
  height: 32px;
  border-bottom: 1px solid #131520;
  border-right: 1px solid #131520;
  text-align: center;
  vertical-align: middle;
  background: #0f1018;
  color: #c8ccd6;
  white-space: nowrap;
  box-sizing: border-box;
}
.cv-td:last-child { border-right: none; }

.cv-td--spec {
  background: #0d1220;
  color: #e2e8f0;
  font-weight: 500;
}
.cv-td--intel { color: #93c5fd; }
.cv-td--amd   { color: #c4b5fd; }
.cv-td--intel.cv-chip-sticky { background: #0f1018; }
.cv-td--amd.cv-chip-sticky { background: #0f1018; }

/* Empty cell */
.cv-td--empty {
  background: #0c0d12 !important;
  color: transparent;
}
.cv-td--empty::after {
  content: '';
  display: inline-block;
  width: 14px; height: 14px;
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

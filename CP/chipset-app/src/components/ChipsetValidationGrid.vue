<template>
  <div class="tg-root">

    <!-- ─── Header ─────────────────────────────────────────────── -->
    <header class="tg-header flex items-center justify-between ph4 pv3">
      <div class="flex items-center">
        <span class="tg-badge f7 fw7 ttu tracked mr3">Chipset Validation</span>
        <span class="tg-title f5 fw6 tracked">Compatibility Matrix</span>
      </div>
      <span v-if="version" class="tg-version f7 fw6">{{ version }}</span>
    </header>

    <!-- ─── Loading / Error ─────────────────────────────────────── -->
    <div v-if="loading" class="tg-state flex items-center justify-center">
      <span class="f6 tg-muted">Loading…</span>
    </div>
    <div v-else-if="error" class="tg-state flex items-center justify-center">
      <span class="f6 tg-error">{{ error }}</span>
    </div>

    <!-- ─── Grid ─────────────────────────────────────────────────── -->
    <div v-else class="tg-layout">

      <!-- ══ Sticky header (3 rows) ══ -->
      <div class="tg-head flex">

        <!-- Frozen spec header -->
        <div class="tg-head-frozen" :style="{ width: frozenWidth + 'px' }">
          <table class="tg-tbl" :style="{ width: frozenWidth + 'px' }">
            <colgroup>
              <col v-for="c in specCols" :key="c.key" :style="{ width: c.w + 'px' }">
            </colgroup>
            <thead>
              <tr class="tg-hrow tg-hrow-g">
                <th
                  v-for="c in specCols" :key="c.key"
                  class="tg-th tg-th-spec tc f7 fw7 ttu tracked"
                  :class="c.key"
                  rowspan="3"
                  :style="{ width: c.w + 'px', minWidth: c.w + 'px' }"
                >{{ c.label }}</th>
              </tr>
              <tr class="tg-hrow tg-hrow-cn"></tr>
              <tr class="tg-hrow tg-hrow-dt"></tr>
            </thead>
          </table>
        </div>

        <!-- Intel header -->
        <div class="tg-head-chip tg-head-intel" ref="intelHeadRef">
          <table class="tg-tbl" :style="{ width: intelTableW + 'px', minWidth: intelTableW + 'px' }">
            <colgroup>
              <col v-for="c in intelCols" :key="c.key" :style="{ width: chipColW + 'px' }">
            </colgroup>
            <thead>
              <tr class="tg-hrow tg-hrow-g">
                <th
                  :colspan="intelCols.length"
                  class="tg-th tg-th-intel-grp tc f7 fw7 ttu tracked"
                  style="position:sticky;left:0;z-index:46"
                >Intel</th>
              </tr>
              <tr class="tg-hrow tg-hrow-cn">
                <th
                  v-for="c in intelCols" :key="c.key"
                  class="tg-th tg-th-intel tc f7 fw7"
                  :style="{ width: chipColW + 'px', minWidth: chipColW + 'px' }"
                >{{ c.chip }}</th>
              </tr>
              <tr class="tg-hrow tg-hrow-dt">
                <th
                  v-for="c in intelCols" :key="'d-'+c.key"
                  class="tg-th tg-th-date-intel tc f7"
                  :style="{ width: chipColW + 'px', minWidth: chipColW + 'px' }"
                >{{ c.date }}</th>
              </tr>
            </thead>
          </table>
        </div>

        <!-- AMD header -->
        <div class="tg-head-chip tg-head-amd" ref="amdHeadRef" v-if="amdCols.length">
          <table class="tg-tbl" :style="{ width: amdTableW + 'px', minWidth: amdTableW + 'px' }">
            <colgroup>
              <col v-for="c in amdCols" :key="c.key" :style="{ width: chipColW + 'px' }">
            </colgroup>
            <thead>
              <tr class="tg-hrow tg-hrow-g">
                <th
                  :colspan="amdCols.length"
                  class="tg-th tg-th-amd-grp tc f7 fw7 ttu tracked"
                  style="position:sticky;left:0;z-index:46"
                >AMD</th>
              </tr>
              <tr class="tg-hrow tg-hrow-cn">
                <th
                  v-for="c in amdCols" :key="c.key"
                  class="tg-th tg-th-amd tc f7 fw7"
                  :style="{ width: chipColW + 'px', minWidth: chipColW + 'px' }"
                >{{ c.chip }}</th>
              </tr>
              <tr class="tg-hrow tg-hrow-dt">
                <th
                  v-for="c in amdCols" :key="'d-'+c.key"
                  class="tg-th tg-th-date-amd tc f7"
                  :style="{ width: chipColW + 'px', minWidth: chipColW + 'px' }"
                >{{ c.date }}</th>
              </tr>
            </thead>
          </table>
        </div>

      </div><!-- /tg-head -->

      <!-- ══ Body ══ -->
      <div class="tg-body flex">

        <!-- Frozen body -->
        <div class="tg-body-frozen" :style="{ width: frozenWidth + 'px' }">
          <table class="tg-tbl" :style="{ width: frozenWidth + 'px' }">
            <colgroup>
              <col v-for="c in specCols" :key="c.key" :style="{ width: c.w + 'px' }">
            </colgroup>
            <tbody>
              <tr v-for="(row, ri) in rows" :key="ri" class="tg-tr">
                <td
                  v-for="c in specCols" :key="c.key"
                  class="tg-td tg-td-spec tc f7"
                  :style="{
                    width: c.w + 'px', minWidth: c.w + 'px',
                    ...(cellColor(row.__idx, c.key) ? { background: cellColor(row.__idx, c.key), color: '#000' } : {}),
                  }"
                >{{ row[c.key] }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Intel body -->
        <div class="tg-body-chip tg-body-intel" ref="intelBodyRef" @scroll.passive="syncIntel">
          <table class="tg-tbl" :style="{ width: intelTableW + 'px', minWidth: intelTableW + 'px' }">
            <colgroup>
              <col v-for="c in intelCols" :key="c.key" :style="{ width: chipColW + 'px' }">
            </colgroup>
            <tbody>
              <tr v-for="(row, ri) in rows" :key="ri" class="tg-tr">
                <td
                  v-for="c in intelCols" :key="c.key"
                  class="tg-td tg-td-intel tc f7"
                  :class="{ 'tg-td-empty': !row[c.key] }"
                  :style="{
                    width: chipColW + 'px', minWidth: chipColW + 'px',
                    ...(cellColor(row.__idx, c.key) ? { background: cellColor(row.__idx, c.key), color: '#000' } : {}),
                  }"
                >{{ row[c.key] }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- AMD body -->
        <div class="tg-body-chip tg-body-amd" ref="amdBodyRef" @scroll.passive="syncAmd" v-if="amdCols.length">
          <table class="tg-tbl" :style="{ width: amdTableW + 'px', minWidth: amdTableW + 'px' }">
            <colgroup>
              <col v-for="c in amdCols" :key="c.key" :style="{ width: chipColW + 'px' }">
            </colgroup>
            <tbody>
              <tr v-for="(row, ri) in rows" :key="ri" class="tg-tr">
                <td
                  v-for="c in amdCols" :key="c.key"
                  class="tg-td tg-td-amd tc f7"
                  :class="{ 'tg-td-empty': !row[c.key] }"
                  :style="{
                    width: chipColW + 'px', minWidth: chipColW + 'px',
                    ...(cellColor(row.__idx, c.key) ? { background: cellColor(row.__idx, c.key), color: '#000' } : {}),
                  }"
                >{{ row[c.key] }}</td>
              </tr>
            </tbody>
          </table>
        </div>

      </div><!-- /tg-body -->
    </div><!-- /tg-layout -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as XLSX from 'xlsx'
import 'tachyons/css/tachyons.min.css'
import excelUrl from '../data/ChipsetValidation_Fixed.xlsx?url'

/* ── Layout constants ─────────────────────────────────────────── */
const specCols = [
  { key: 'dimm',    label: 'DIMM',          w: 90  },
  { key: 'product', label: 'Product (Ver.)', w: 130 },
  { key: 'ver',     label: 'Ver.',           w: 50  },
  { key: 'density', label: 'Density',        w: 70  },
  { key: 'org',     label: 'Org',            w: 60  },
  { key: 'speed',   label: 'Speed',          w: 70  },
]
const frozenWidth = computed(() => specCols.reduce((a, c) => a + c.w, 0))

const CHIP_COL_W = 90
const chipColW = ref(CHIP_COL_W)

/* ── State ────────────────────────────────────────────────────── */
const loading    = ref(true)
const error      = ref('')
const version    = ref('')
const rows       = ref([])
const intelCols  = ref([])
const amdCols    = ref([])
const colorMap   = ref({})

const intelTableW = computed(() => intelCols.value.length * chipColW.value)
const amdTableW   = computed(() => amdCols.value.length  * chipColW.value)

function cellColor(rowIdx, key) {
  return colorMap.value[`${rowIdx}_${key}`] ?? null
}

/* ── Scroll sync refs ─────────────────────────────────────────── */
const intelHeadRef = ref(null)
const amdHeadRef   = ref(null)
const intelBodyRef = ref(null)
const amdBodyRef   = ref(null)

function syncIntel(e) {
  if (intelHeadRef.value) intelHeadRef.value.scrollLeft = e.target.scrollLeft
}
function syncAmd(e) {
  if (amdHeadRef.value) amdHeadRef.value.scrollLeft = e.target.scrollLeft
}

/* ── ResizeObserver: fit chip col width to pane ───────────────── */
let _ro = null
onMounted(async () => {
  await loadExcel()
  watchPaneWidth()
})
onUnmounted(() => { _ro?.disconnect() })

function watchPaneWidth() {
  const el = intelBodyRef.value ?? amdBodyRef.value
  if (!el) return
  _ro = new ResizeObserver(([entry]) => {
    const total = intelCols.value.length + amdCols.value.length
    if (total > 0) {
      const fit = Math.floor(entry.contentRect.width / intelCols.value.length)
      chipColW.value = Math.max(CHIP_COL_W, fit)
    }
  })
  _ro.observe(el)
}

/* ── Excel load & parse ───────────────────────────────────────── */
async function loadExcel() {
  try {
    const res = await fetch(excelUrl)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const buf = await res.arrayBuffer()
    parseExcel(buf)
  } catch (e) {
    error.value = `파일 로드 실패: ${e.message}`
  } finally {
    loading.value = false
  }
}

function extractRgb(ws, r, c) {
  const cell = ws[XLSX.utils.encode_cell({ r, c })]
  const rgb = cell?.s?.fgColor?.rgb
  if (!rgb || rgb === 'FFFFFF' || rgb === '000000' || rgb.length !== 6) return null
  return `#${rgb}`
}

function parseExcel(buffer) {
  const wb   = XLSX.read(buffer, { type: 'array', cellStyles: true })
  const ws   = wb.Sheets[wb.SheetNames[0]]
  const raw  = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' })
  const range = XLSX.utils.decode_range(ws['!ref'])

  // Row layout: 0=version, 1=group labels, 2=chip names, 3=dates, 4+=data
  version.value = String(raw[0]?.[0] ?? '').trim()

  const groupRow    = raw[1] ?? []
  const chipNameRow = raw[2] ?? []
  const dateRow     = raw[3] ?? []
  const dataStart   = 4
  const SPEC_COUNT  = specCols.length

  // Detect Intel / AMD column ranges from merge info
  let intelStart = -1, intelEnd = -1, amdStart = -1, amdEnd = -1
  for (const m of (ws['!merges'] ?? [])) {
    const cell = ws[XLSX.utils.encode_cell({ r: m.s.r, c: m.s.c })]
    const v = String(cell?.v ?? '').toUpperCase()
    if (v === 'INTEL') { intelStart = m.s.c; intelEnd = m.e.c }
    if (v === 'AMD')   { amdStart   = m.s.c; amdEnd   = m.e.c }
  }

  // Build chip column definitions
  const newIntel = []
  const newAmd   = []
  if (intelStart >= SPEC_COUNT) {
    for (let c = intelStart; c <= intelEnd; c++) {
      newIntel.push({
        key:    `chip_${c}`,
        chip:   String(chipNameRow[c] ?? '').replace(/\n/g, ' '),
        date:   String(dateRow[c]     ?? ''),
        colIdx: c,
      })
    }
  }
  if (amdStart >= SPEC_COUNT) {
    for (let c = amdStart; c <= amdEnd; c++) {
      newAmd.push({
        key:    `chip_${c}`,
        chip:   String(chipNameRow[c] ?? '').replace(/\n/g, ' '),
        date:   String(dateRow[c]     ?? ''),
        colIdx: c,
      })
    }
  }
  intelCols.value = newIntel
  amdCols.value   = newAmd

  const allChipCols = [...newIntel, ...newAmd]
  const specKeys    = specCols.map(c => c.key)
  const newRows     = []
  const newColors   = {}

  for (let r = dataStart; r <= range.e.r; r++) {
    const rawRow = raw[r]
    if (!rawRow || rawRow.every(v => v === '' || v == null)) continue

    const row = { __idx: r }
    specKeys.forEach((k, i) => {
      row[k] = String(rawRow[i] ?? '')
      const bg = extractRgb(ws, r, i)
      if (bg) newColors[`${r}_${k}`] = bg
    })
    for (const col of allChipCols) {
      const val = rawRow[col.colIdx]
      row[col.key] = (val !== undefined && val !== '') ? String(val) : ''
      const bg = extractRgb(ws, r, col.colIdx)
      if (bg) newColors[`${r}_${col.key}`] = bg
    }
    newRows.push(row)
  }

  rows.value     = newRows
  colorMap.value = newColors
}
</script>

<style scoped>
/* ── Root ───────────────────────────────────────────────────── */
.tg-root {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  background: #0d0e14;
  color: #c8ccd6;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ── Header ─────────────────────────────────────────────────── */
.tg-header {
  background: #13141d;
  border-bottom: 2px solid #1e3a5f;
  flex-shrink: 0;
}
.tg-badge {
  background: rgba(59,130,246,.12);
  border: 1px solid rgba(59,130,246,.3);
  color: #3b82f6;
  padding: 3px 10px;
  border-radius: 3px;
}
.tg-title { color: #e8eaf0; }
.tg-version { color: #f59e0b; }
.tg-muted  { color: #4b5563; }
.tg-error  { color: #ef4444; }

/* ── State overlay ───────────────────────────────────────────── */
.tg-state { flex: 1; }

/* ── Layout wrapper ──────────────────────────────────────────── */
.tg-layout {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: calc(100vh - 58px);
}
.tg-layout::-webkit-scrollbar { width: 8px; }
.tg-layout::-webkit-scrollbar-track { background: #111218; }
.tg-layout::-webkit-scrollbar-thumb { background: #2d3148; border-radius: 4px; }

/* ── Sticky header row ───────────────────────────────────────── */
.tg-head {
  position: sticky;
  top: 0;
  z-index: 30;
  background: #13141d;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}

/* ── Body row ────────────────────────────────────────────────── */
.tg-body { display: flex; flex: 1; }

/* ── Frozen pane ─────────────────────────────────────────────── */
.tg-head-frozen,
.tg-body-frozen {
  flex: none;
  overflow: hidden;
  border-right: 2px solid #1e3a5f;
}
.tg-head-frozen { background: #0f1729; }
.tg-body-frozen { background: #0d1220; }

/* ── Chip panes ──────────────────────────────────────────────── */
.tg-head-chip { flex: 1; overflow: hidden; min-width: 0; }
.tg-head-amd  { border-left: 2px solid #2d1a4a; }

.tg-body-chip { flex: 1; overflow-x: auto; overflow-y: hidden; min-width: 0; }
.tg-body-amd  { border-left: 2px solid #2d1a4a; }

.tg-body-chip::-webkit-scrollbar { height: 8px; }
.tg-body-chip::-webkit-scrollbar-track { background: #111218; }
.tg-body-chip::-webkit-scrollbar-thumb { background: #2d3148; border-radius: 4px; }

/* ── Table ───────────────────────────────────────────────────── */
.tg-tbl {
  border-collapse: separate;
  border-spacing: 0;
  white-space: nowrap;
  table-layout: fixed;
}

/* ── TH ──────────────────────────────────────────────────────── */
.tg-th {
  padding: 0 8px;
  vertical-align: middle;
  letter-spacing: .06em;
  border-bottom: 1px solid #1e293b;
  border-right: 1px solid #1a2035;
  color: #94a3b8;
  background: #13141d;
  box-sizing: border-box;
}
.tg-th:last-child { border-right: none; }

/* Header row heights */
.tg-hrow-g  .tg-th { height: 34px; }
.tg-hrow-cn .tg-th { height: 28px; }
.tg-hrow-dt .tg-th { height: 20px; color: #6b7280; }

/* Spec header */
.tg-th-spec        { background: #0f1729; color: #60a5fa; }
/* Intel */
.tg-th-intel-grp   { background: #0d1f38; color: #93c5fd; }
.tg-th-intel       { background: #0d1f38; color: #93c5fd; }
.tg-th-date-intel  { background: #0a1525; color: #4b5563; }
/* AMD */
.tg-th-amd-grp     { background: #1a0f2e; color: #c4b5fd; }
.tg-th-amd         { background: #1a0f2e; color: #c4b5fd; }
.tg-th-date-amd    { background: #150a26; color: #4b5563; }

/* ── TR / TD ─────────────────────────────────────────────────── */
.tg-tr:hover .tg-td { filter: brightness(1.15); }

.tg-td {
  padding: 5px 8px;
  height: 32px;
  border-bottom: 1px solid #1a1c24;
  border-right: 1px solid #1a1c24;
  vertical-align: middle;
  background: #111318;
  color: #c8ccd6;
  white-space: nowrap;
  box-sizing: border-box;
}
.tg-td:last-child { border-right: none; }

.tg-td-spec  { background: #0d1220; color: #e2e8f0; font-weight: 500; }
.tg-td-intel { color: #93c5fd; }
.tg-td-amd   { color: #c4b5fd; }

.tg-td-empty {
  background: #222428 !important;
  color: transparent !important;
}
</style>

<template>
  <div style="font-family:'JetBrains Mono','Courier New',monospace; font-size:12px;
              background:#0d0e14; min-height:100vh; padding:24px; color:#c8ccd6">

    <!-- 헤더 -->
    <div style="display:flex; align-items:center; margin-bottom:16px;
                padding-bottom:12px; border-bottom:1px solid #1e293b">
      <span style="font-size:10px; font-weight:700; letter-spacing:.15em; color:#3b82f6;
                   border:1px solid rgba(59,130,246,.4); background:rgba(59,130,246,.08);
                   padding:3px 6px; border-radius:3px; margin-right:12px">
        CHIPSET DB
      </span>
      <h1 style="margin:0; font-size:16px; font-weight:700; color:#fff; letter-spacing:.04em">
        Compatibility Matrix
      </h1>
      <span v-if="uploadDt" style="margin-left:auto; font-size:10px; color:#f59e0b">
        {{ uploadDt }}
      </span>
    </div>

    <!-- 탭 -->
    <div style="display:flex; gap:4px; border-bottom:1px solid #1e293b; margin-bottom:12px">
      <button v-for="tab in TABS" :key="tab.type"
              style="background:transparent; border:none; border-bottom:2px solid transparent;
                     font-size:11px; font-weight:600; padding:6px 10px 8px; margin-bottom:-1px;
                     cursor:pointer; letter-spacing:.08em; font-family:inherit"
              :style="activeTab === tab.type
                ? { color: tab.color, borderBottomColor: tab.color }
                : { color: '#4b5563' }"
              @click="switchTab(tab.type)">
        {{ tab.label }}
      </button>
    </div>

    <!-- 업로드 컨트롤 -->
    <div style="display:flex; align-items:center; gap:10px; flex-wrap:wrap; margin-bottom:12px">
      <label style="cursor:pointer; font-size:11px; font-weight:700; padding:6px 10px;
                    border-radius:4px; border:1px solid"
             :style="{ color: activeColor, borderColor: activeColor+'66',
                       background: activeColor+'14' }">
        <input type="file" accept=".xlsx,.xls" style="display:none" @change="onFileChange" />
        ↑ XLSX 선택
      </label>
      <span v-if="fileName" style="font-size:11px; color:#9ca3af">{{ fileName }}</span>
      <span v-if="detectedType && detectedType !== activeTab"
            style="font-size:10px; color:#fbbf24; border:1px solid #fbbf2444;
                   background:#fbbf2410; padding:3px 6px; border-radius:3px">
        ⚠ {{ detectedType }} 형식으로 감지됨
      </span>
      <button v-if="selectedFile" :disabled="uploading"
              style="background:transparent; font-size:11px; font-weight:700; padding:6px 10px;
                     border-radius:4px; font-family:inherit; cursor:pointer; letter-spacing:.1em"
              :style="uploading
                ? 'color:#6b7280;border:1px solid #374151;cursor:not-allowed'
                : 'color:#10b981;border:1px solid rgba(16,185,129,.4)'"
              @click="doUpload">
        {{ uploading ? 'UPLOADING...' : '→ DB 저장' }}
      </button>
      <button v-if="!currentData"
              style="background:transparent; font-size:11px; padding:6px 10px; border-radius:4px;
                     color:#9ca3af; border:1px solid #374151; cursor:pointer; font-family:inherit"
              @click="loadData">
        DB 불러오기
      </button>
      <select v-if="history.length"
              style="background:#1a1c27; color:#c8ccd6; border:1px solid #2d3148;
                     padding:6px 8px; border-radius:4px; font-size:11px; font-family:inherit"
              @change="onHistorySelect">
        <option value="">── 히스토리 ──</option>
        <option v-for="h in history" :key="h.uploadSeq" :value="h.uploadSeq">
          {{ formatDt(h.uploadDt) }} — {{ h.fileNm }} ({{ h.rowCount }}행)
        </option>
      </select>
    </div>

    <!-- 결과 메시지 -->
    <p v-if="msg" style="font-size:11px; margin-bottom:12px"
       :style="msgOk ? 'color:#6ee7b7' : 'color:#f87171'">{{ msg }}</p>

    <!-- 로딩 -->
    <div v-if="loading" style="text-align:center; margin-top:60px; font-size:11px; color:#4b5563">
      Loading...
    </div>

    <!-- ── Matrix 테이블 (SERVER / CLIENT / MOBILE) ── -->
    <template v-else-if="activeTab !== 'RAW_DATA'">
      <div v-if="currentData && currentData.rows && currentData.rows.length"
           style="overflow-x:auto; overflow-y:auto; max-height:calc(100vh - 210px)">
        <table style="border-collapse:separate; border-spacing:0; white-space:nowrap; min-width:max-content">
          <colgroup>
            <col v-for="(spec, i) in SPEC_COLS" :key="'scol'+i"
                 :style="{ width: spec.w+'px', minWidth: spec.w+'px' }">
            <col v-for="col in currentData.chipCols" :key="'ccol'+col.colSeq"
                 style="width:82px; min-width:82px">
          </colgroup>
          <thead>
            <!-- 행 1: 벤더 그룹 -->
            <tr>
              <th v-for="(spec, i) in SPEC_COLS" :key="'h1s'+i"
                  :style="frozenThStyle(i, 0, true)"></th>
              <th v-for="(vendor, vi) in currentData.vendors" :key="'h1v'+vi"
                  :colspan="colsByVendor(vendor).length"
                  :style="vendorThStyle(vi, 0)">
                {{ vendor }}
              </th>
            </tr>
            <!-- 행 2: 스펙 레이블 + 칩 이름 -->
            <tr>
              <th v-for="(spec, i) in SPEC_COLS" :key="'h2s'+i"
                  :style="frozenThStyle(i, ROW1_H, false)">
                {{ spec.label }}
              </th>
              <th v-for="col in currentData.chipCols" :key="'h2c'+col.colSeq"
                  :style="chipThStyle(col, ROW1_H)">
                {{ col.chipNm }}
              </th>
            </tr>
            <!-- 행 3: 출시일 -->
            <tr>
              <th v-for="(spec, i) in SPEC_COLS" :key="'h3s'+i"
                  :style="frozenThStyle(i, ROW1_H + ROW2_H, true)"></th>
              <th v-for="col in currentData.chipCols" :key="'h3c'+col.colSeq"
                  :style="chipDateThStyle(col, ROW1_H + ROW2_H)">
                {{ col.chipDt }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in currentData.rows" :key="row.rowIdx"
                style="border-bottom:1px solid #1a1c24"
                @mouseenter="hoverRow = row.rowIdx"
                @mouseleave="hoverRow = null">
              <td v-for="(spec, i) in SPEC_COLS" :key="'ds'+i"
                  :style="frozenTdStyle(i, hoverRow === row.rowIdx)">
                {{ specCellValue(row, spec) }}
              </td>
              <td v-for="col in currentData.chipCols" :key="'dc'+col.colSeq"
                  :style="chipCellStyle(row, col, hoverRow === row.rowIdx)">
                {{ chipCellValue(row, col) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else style="text-align:center; margin-top:60px; color:#374151">
        <div style="font-size:40px; opacity:.3">⬆</div>
        <p style="margin-top:8px; font-size:11px">
          {{ activeTab }} Excel을 업로드하거나 "DB 불러오기"를 클릭하세요.
        </p>
      </div>
    </template>

    <!-- ── Raw_Data 테이블 ── -->
    <template v-else>
      <div v-if="currentData && currentData.rows && currentData.rows.length"
           style="overflow-x:auto; overflow-y:auto; max-height:calc(100vh - 210px)">
        <table style="border-collapse:separate; border-spacing:0; white-space:nowrap; min-width:max-content">
          <thead>
            <!-- 섹션 그룹 헤더 -->
            <tr>
              <th v-for="sec in RAW_SECTIONS" :key="sec.label"
                  :colspan="sec.count"
                  :style="{
                    textAlign: 'center', fontWeight: '700', fontSize: '10px',
                    letterSpacing: '.1em', padding: '6px 8px',
                    position: 'sticky', top: '0', zIndex: 10,
                    background: sec.bg, color: sec.color,
                    borderRight: '1px solid #1a2035', borderBottom: '1px solid #1e293b'
                  }">
                {{ sec.label }}
              </th>
            </tr>
            <!-- 컬럼 헤더 -->
            <tr>
              <th v-for="col in currentData.rawdataCols" :key="col.colSeq"
                  :style="rawHeaderThStyle(col)">
                {{ col.colNm }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in currentData.rows" :key="row.rowIdx">
              <td v-for="col in currentData.rawdataCols" :key="col.colSeq"
                  :style="rawCellStyle(rawCellValue(row, col), col)">
                {{ rawCellValue(row, col) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else style="text-align:center; margin-top:60px; color:#374151">
        <div style="font-size:40px; opacity:.3">⬆</div>
        <p style="margin-top:8px; font-size:11px">
          Raw_Data.xlsx를 업로드하거나 "DB 불러오기"를 클릭하세요.
        </p>
      </div>
    </template>

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import axios from 'axios'

const API = 'http://localhost:9090/api/chipset'

function getErrorMessage(e) {
  const data = e?.response?.data
  const msg =
    (typeof data === 'string' ? data : data?.message) ??
    e?.message ??
    String(e)
  return msg || 'Unknown error'
}

const TABS = [
  { type: 'SERVER',   label: 'Server',   color: '#3b82f6' },
  { type: 'CLIENT',   label: 'Client',   color: '#8b5cf6' },
  { type: 'MOBILE',   label: 'Mobile',   color: '#10b981' },
  { type: 'RAW_DATA', label: 'Raw Data', color: '#f59e0b' },
]

const ROW1_H = 30
const ROW2_H = 30

function guessWidth(colNm) {
  const n = (colNm || '').length
  if (n <= 3)  return 55
  if (n <= 5)  return 65
  if (n <= 8)  return 80
  if (n <= 12) return 95
  return 115
}

function rawColWidth(colNm) {
  const n = (colNm || '').length
  if (n <= 3)  return 60
  if (n <= 5)  return 70
  if (n <= 8)  return 90
  if (n <= 12) return 110
  return 140
}

// colIdx 범위로 섹션 구분 (Excel 고정 레이아웃)
function rawColBg(colIdx) {
  if (colIdx <= 4)  return '#0d1f38'
  if (colIdx <= 10) return '#102840'
  return '#1f1040'
}
function rawColFc(colIdx) {
  if (colIdx <= 4)  return '#60a5fa'
  if (colIdx <= 10) return '#93c5fd'
  return '#c4b5fd'
}

const VENDOR_COLORS = [
  { bg: '#0d1f38', text: '#93c5fd', border: '#1e3a5f' },
  { bg: '#1a0f2e', text: '#c4b5fd', border: '#2d1a4a' },
  { bg: '#0f2318', text: '#6ee7b7', border: '#1a4a2e' },
  { bg: '#2a1500', text: '#fbbf24', border: '#4a2a00' },
  { bg: '#1a0a0a', text: '#f87171', border: '#4a1a1a' },
]

// ── state ──
const activeTab    = ref('SERVER')
const selectedFile = ref(null)
const fileName     = ref('')
const detectedType = ref('')
const uploading    = ref(false)
const loading      = ref(false)
const msg          = ref('')
const msgOk        = ref(false)
const uploadDt     = ref('')
const hoverRow     = ref(null)

const tabData    = ref({ SERVER: null, CLIENT: null, MOBILE: null, RAW_DATA: null })
const tabHistory = ref({ SERVER: [], CLIENT: [], MOBILE: [], RAW_DATA: [] })

const currentData = computed(() => tabData.value[activeTab.value])
const history     = computed(() => tabHistory.value[activeTab.value])
const activeColor = computed(() => TABS.find(t => t.type === activeTab.value)?.color ?? '#3b82f6')

// specCols → UI 렌더링용 (colSeq 포함, 셀 조회에 사용)
const SPEC_COLS = computed(() => {
  const sc = currentData.value?.specCols
  if (!sc?.length) return []
  return sc.map(s => ({
    colSeq: s.colSeq,
    label:  s.colNm,
    w:      guessWidth(s.colNm),
  }))
})

// rawdataCols 기반 섹션 그룹 (Target AP / Sorting KEY / Validation Status)
const RAW_SECTIONS = computed(() => {
  const cols = currentData.value?.rawdataCols ?? []
  if (!cols.length) return []
  const targetAp  = cols.filter(c => c.colIdx >= 1 && c.colIdx <= 4)
  const sortingKey = cols.filter(c => c.colIdx >= 5 && c.colIdx <= 10)
  const valStatus  = cols.filter(c => c.colIdx >= 11)
  const sections = []
  if (targetAp.length)  sections.push({ label: 'Target AP',          count: targetAp.length,  color: '#93c5fd', bg: '#0d1f38' })
  if (sortingKey.length) sections.push({ label: 'Sorting KEY',        count: sortingKey.length, color: '#93c5fd', bg: '#102840' })
  if (valStatus.length)  sections.push({ label: 'Validation Status',  count: valStatus.length,  color: '#c4b5fd', bg: '#1f1040' })
  return sections
})

// 고정 컬럼의 누적 left 오프셋
const specLeftOffsets = computed(() => {
  const offsets = []
  let left = 0
  for (const col of SPEC_COLS.value) {
    offsets.push(left)
    left += col.w
  }
  return offsets
})

// ── 탭 전환 ──
async function switchTab(type) {
  activeTab.value = type
  selectedFile.value = null
  fileName.value     = ''
  detectedType.value = ''
  msg.value          = ''
  const tasks = []
  if (!tabData.value[type])    tasks.push(loadData())
  if (!tabHistory.value[type].length) tasks.push(loadHistory())
  await Promise.all(tasks)
}

// ── 파일 선택 ──
function onFileChange(e) {
  selectedFile.value = e.target.files[0] ?? null
  fileName.value     = selectedFile.value?.name ?? ''
  detectedType.value = ''
  msg.value          = ''
  if (fileName.value) {
    const u = fileName.value.toUpperCase()
    if      (u.includes('RAW'))    detectedType.value = 'RAW_DATA'
    else if (u.includes('MOBILE')) detectedType.value = 'MOBILE'
    else if (u.includes('CLIENT')) detectedType.value = 'CLIENT'
    else if (u.includes('SERVER')) detectedType.value = 'SERVER'
  }
}

// ── 업로드 ──
async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  msg.value       = ''
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const { data } = await axios.post(`${API}/upload`, form)
    msgOk.value = data.success
    msg.value   = data.message
    if (data.success) {
      const serverType = data.fileType
      if (serverType && serverType !== activeTab.value) activeTab.value = serverType
      await Promise.all([loadData(), loadHistory()])
    }
  } catch (e) {
    msgOk.value = false
    const m = getErrorMessage(e)
    msg.value = m.startsWith('서버 오류:') ? m : ('서버 오류: ' + m)
  } finally {
    uploading.value    = false
    selectedFile.value = null
    fileName.value     = ''
    detectedType.value = ''
  }
}

// ── 데이터 로드 ──
async function loadData() {
  loading.value = true
  try {
    const type = activeTab.value
    if (type === 'RAW_DATA') {
      const { data } = await axios.get(`${API}/rawdata`)
      tabData.value.RAW_DATA = data
      uploadDt.value = data.uploadDt ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
    } else {
      const { data } = await axios.get(`${API}/matrix?type=${type}`)
      tabData.value[type] = data
      uploadDt.value = data.uploadDt ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
    }
  } catch (e) {
    msg.value   = 'DB 조회 실패: ' + getErrorMessage(e)
    msgOk.value = false
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const type = activeTab.value
    const { data } = await axios.get(`${API}/history?type=${type}`)
    tabHistory.value[type] = data
  } catch { /* 무시 */ }
}

async function onHistorySelect(e) {
  const uploadSeq = e.target.value
  if (!uploadSeq) return
  loading.value = true
  try {
    const type = activeTab.value
    const url  = type === 'RAW_DATA'
      ? `${API}/rawdata/history/${uploadSeq}`
      : `${API}/history/${uploadSeq}`
    const { data } = await axios.get(url)
    tabData.value[type] = data
    uploadDt.value = '(히스토리 버전)'
  } finally {
    loading.value = false
  }
}

// ── 초기 로드 ──
Promise.all([loadData(), loadHistory()])

// ── 셀 값 헬퍼 ──

function specCellValue(row, spec) {
  return row.specCells?.find(c => c.colSeq === spec.colSeq)?.cellValue ?? ''
}

function chipCellValue(row, col) {
  return row.chipCells?.find(c => c.colSeq === col.colSeq)?.cellValue ?? ''
}

function rawCellValue(row, col) {
  return row.cells?.find(c => c.colSeq === col.colSeq)?.cellValue ?? ''
}

// ── 스타일 헬퍼 ──

function vendorColor(vi) {
  return VENDOR_COLORS[vi % VENDOR_COLORS.length]
}
function vendorIdx(vendor) {
  return currentData.value?.vendors.indexOf(vendor) ?? 0
}
function colsByVendor(vendor) {
  return currentData.value?.chipCols.filter(c => c.vendor === vendor) ?? []
}

function frozenThStyle(i, topPx, blank) {
  const spec   = SPEC_COLS.value[i]
  const isLast = i === SPEC_COLS.value.length - 1
  return {
    position:    'sticky',
    left:        specLeftOffsets.value[i] + 'px',
    top:         topPx + 'px',
    zIndex:      30,
    width:       spec.w + 'px',
    minWidth:    spec.w + 'px',
    background:  '#0f1729',
    color:       blank ? 'transparent' : '#60a5fa',
    textAlign:   'center',
    fontWeight:  '700',
    fontSize:    '10px',
    padding:     '5px 4px',
    borderRight: isLast ? '2px solid #1e3a5f' : '1px solid #1a2035',
    borderBottom:'1px solid #1a2035',
    letterSpacing: '.06em',
    boxSizing:   'border-box',
  }
}

function frozenTdStyle(i, hovered) {
  const spec   = SPEC_COLS.value[i]
  const isLast = i === SPEC_COLS.value.length - 1
  return {
    position:    'sticky',
    left:        specLeftOffsets.value[i] + 'px',
    zIndex:      5,
    width:       spec.w + 'px',
    minWidth:    spec.w + 'px',
    background:  hovered ? '#182040' : '#0d1220',
    color:       '#e2e8f0',
    textAlign:   'center',
    fontSize:    '11px',
    padding:     '5px 4px',
    borderRight: isLast ? '2px solid #1e3a5f' : '1px solid #1a2035',
    borderBottom:'1px solid #1a1c24',
    boxSizing:   'border-box',
  }
}

function vendorThStyle(vi, topPx) {
  const c = vendorColor(vi)
  return {
    position:    'sticky',
    top:         topPx + 'px',
    zIndex:      10,
    textAlign:   'center',
    fontWeight:  '700',
    fontSize:    '11px',
    letterSpacing:'.12em',
    padding:     '6px 8px',
    background:  c.bg,
    color:       c.text,
    borderRight: `2px solid ${c.border}`,
    borderBottom:`1px solid ${c.border}`,
    boxSizing:   'border-box',
  }
}

function chipThStyle(col, topPx) {
  const c = vendorColor(vendorIdx(col.vendor))
  return {
    position:    'sticky',
    top:         topPx + 'px',
    zIndex:      10,
    textAlign:   'center',
    fontWeight:  '700',
    fontSize:    '10px',
    padding:     '5px 4px',
    background:  c.bg,
    color:       c.text,
    borderRight: `1px solid ${c.border}`,
    borderBottom:`1px solid ${c.border}`,
    boxSizing:   'border-box',
  }
}

function chipDateThStyle(col, topPx) {
  const c = vendorColor(vendorIdx(col.vendor))
  return {
    position:    'sticky',
    top:         topPx + 'px',
    zIndex:      10,
    textAlign:   'center',
    fontWeight:  '400',
    fontSize:    '9px',
    padding:     '3px 4px',
    background:  c.bg,
    color:       c.text,
    opacity:     '.75',
    borderRight: `1px solid ${c.border}`,
    borderBottom:`2px solid ${c.border}`,
    boxSizing:   'border-box',
  }
}

function chipCellStyle(row, col, hovered) {
  const val  = chipCellValue(row, col)
  const cell = row.chipCells?.find(c => c.colSeq === col.colSeq)
  const vc   = vendorColor(vendorIdx(col.vendor))
  const base = {
    textAlign:   'center',
    fontSize:    '11px',
    padding:     '5px 4px',
    borderRight: `1px solid ${vc.border}`,
    borderBottom:'1px solid #1a1c24',
    boxSizing:   'border-box',
  }
  if (!val) {
    return { ...base, background: hovered ? '#1a1c28' : '#111318', color: 'transparent' }
  }
  if (cell?.bgColor) {
    const bg = cell.bgColor.startsWith('#') ? cell.bgColor : '#' + cell.bgColor
    return { ...base, background: hovered ? bg + 'cc' : bg, color: '#fff', fontWeight: '600' }
  }
  return { ...base, background: hovered ? '#182040' : 'transparent', color: vc.text }
}

function rawHeaderThStyle(col) {
  return {
    textAlign:    'center',
    fontWeight:   '700',
    fontSize:     '10px',
    letterSpacing:'.08em',
    padding:      '5px 6px',
    position:     'sticky',
    top:          '30px',
    zIndex:       10,
    minWidth:     rawColWidth(col.colNm) + 'px',
    background:   rawColBg(col.colIdx),
    color:        rawColFc(col.colIdx),
    borderRight:  '1px solid #1a2035',
    borderBottom: '2px solid #1e293b',
  }
}

function rawCellStyle(val, col) {
  const base = {
    textAlign:    'center',
    fontSize:     '11px',
    padding:      '5px 6px',
    borderRight:  '1px solid #1a2035',
    borderBottom: '1px solid #1a1c24',
    background:   '#0d1220',
    color:        '#e2e8f0',
    minWidth:     rawColWidth(col.colNm) + 'px',
  }
  if (!val) return base
  const v = String(val).toLowerCase()
  if (v === 'pass')            return { ...base, background: '#00B050', color: '#fff' }
  if (v === 'fail')            return { ...base, background: '#c0392b', color: '#fff' }
  if (v === 'in progress')     return { ...base, background: '#FF6600', color: '#fff' }
  if (v.startsWith('check'))   return { ...base, background: '#FF0000', color: '#fff' }
  return base
}

function formatDt(dt) {
  if (!dt) return ''
  return new Date(dt).toLocaleString('ko-KR', {
    year: '2-digit', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
* { box-sizing: border-box; }
</style>

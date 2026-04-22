<template>
  <div class="bg-near-black min-vh-100 pa4 white"
       style="font-family:'JetBrains Mono','Courier New',monospace; font-size:12px">

    <!-- ── 헤더 ──────────────────────────────────────────────── -->
    <div class="flex items-center mb3 pb3" style="border-bottom:1px solid #1e293b">
      <span class="f7 fw7 tracked ba pa1 br1 mr3"
            style="font-size:10px;letter-spacing:.15em;color:#3b82f6;
                   border-color:rgba(59,130,246,.4);background:rgba(59,130,246,.08)">
        CHIPSET DB
      </span>
      <h1 class="ma0 fw7 white" style="font-size:16px;letter-spacing:.04em">
        Compatibility Matrix
      </h1>
      <span v-if="uploadDt" class="ml-auto f7" style="color:#f59e0b;font-size:10px">
        {{ uploadDt }}
      </span>
    </div>

    <!-- ── 탭 ──────────────────────────────────────────────────── -->
    <div class="flex mb3" style="gap:4px;border-bottom:1px solid #1e293b">
      <button v-for="tab in TABS" :key="tab.type"
              class="f7 fw6 pa2 pointer"
              style="background:transparent;border:none;border-bottom:2px solid transparent;
                     letter-spacing:.08em;padding-bottom:8px;margin-bottom:-1px"
              :style="activeTab === tab.type
                ? { color: tab.color, borderBottomColor: tab.color }
                : { color: '#4b5563' }"
              @click="switchTab(tab.type)">
        {{ tab.label }}
      </button>
    </div>

    <!-- ── 업로드 컨트롤 ─────────────────────────────────────── -->
    <div class="flex items-center mb3 flex-wrap" style="gap:10px">
      <label class="pointer f7 fw7 ba pa2 br2"
             :style="{ cursor:'pointer', color: activeColor,
                       borderColor: activeColor+'66',
                       background: activeColor+'14' }">
        <input type="file" accept=".xlsx,.xls" class="dn" @change="onFileChange" />
        ↑ XLSX 선택
      </label>

      <span v-if="fileName" class="f7" style="color:#9ca3af">{{ fileName }}</span>

      <!-- 감지 타입 경고 -->
      <span v-if="detectedType && detectedType !== activeTab"
            class="f7 ba pa1 br1"
            style="color:#fbbf24;border-color:#fbbf2444;background:#fbbf2410;font-size:10px">
        ⚠ {{ detectedType }} 형식으로 감지됨 — 계속 업로드하시겠습니까?
      </span>

      <button v-if="selectedFile"
              class="f7 fw7 ba pa2 br2 pointer"
              :disabled="uploading"
              style="background:transparent;letter-spacing:.1em"
              :style="uploading
                ? 'color:#6b7280;border-color:#374151;cursor:not-allowed'
                : 'color:#10b981;border-color:rgba(16,185,129,.4)'"
              @click="doUpload">
        {{ uploading ? 'UPLOADING...' : '→ DB 저장' }}
      </button>

      <button v-if="!currentData"
              class="f7 fw6 ba pa2 br2 pointer"
              style="background:transparent;color:#9ca3af;border-color:#374151"
              @click="loadData">
        DB 불러오기
      </button>

      <!-- 히스토리 드롭다운 -->
      <select v-if="history.length"
              class="f7 pa2 br2"
              style="background:#1a1c27;color:#c8ccd6;border:1px solid #2d3148"
              @change="onHistorySelect">
        <option value="">── 히스토리 ──</option>
        <option v-for="h in history" :key="h.uploadSeq" :value="h.uploadSeq">
          {{ formatDt(h.uploadDt) }} — {{ h.fileNm }} ({{ h.rowCount }}행)
        </option>
      </select>
    </div>

    <!-- 결과 메시지 -->
    <p v-if="msg" class="mb3 f7"
       :style="msgOk ? 'color:#6ee7b7' : 'color:#f87171'">
      {{ msg }}
    </p>

    <!-- ── 로딩 ─────────────────────────────────────────────── -->
    <div v-if="loading" class="tc mt5 f7" style="color:#4b5563">Loading...</div>

    <!-- ── Matrix 테이블 (SERVER / CLIENT / MOBILE) ─────────── -->
    <template v-else-if="activeTab !== 'RAW_DATA'">
      <div v-if="currentData && currentData.rows && currentData.rows.length"
           class="overflow-x-auto">

        <!-- 행 1: 벤더 그룹 헤더 -->
        <div class="flex" style="border-bottom:2px solid #1e293b">
          <div v-for="lbl in specLabels" :key="'gh'+lbl"
               class="flex-none tc fw7 f7 pa2"
               style="min-width:80px;background:#0f1729;color:#60a5fa;
                      border-right:1px solid #1a2035;font-size:10px;letter-spacing:.1em">
            {{ lbl }}
          </div>
          <div v-for="(vendor, vi) in currentData.vendors" :key="vendor"
               class="flex-none tc fw7 pa2"
               :style="{
                 minWidth: (colsByVendor(vendor).length * 90) + 'px',
                 fontSize: '10px', letterSpacing: '.12em',
                 ...vendorStyle(vi)
               }">
            {{ vendor }}
          </div>
        </div>

        <!-- 행 2: 칩 이름 + 출시일 -->
        <div class="flex" style="border-bottom:2px solid #1e293b">
          <div v-for="lbl in specLabels" :key="'ch'+lbl"
               class="flex-none pa2"
               style="min-width:80px;background:#0f1729;border-right:1px solid #1a2035"/>
          <div v-for="col in currentData.chipCols" :key="col.colSeq"
               class="flex-none tc pa2"
               :style="{ minWidth:'90px', ...chipColStyle(col) }">
            <div class="fw7" style="font-size:10px">{{ col.chipNm }}</div>
            <div style="font-size:9px;opacity:.55">{{ col.chipDt }}</div>
          </div>
        </div>

        <!-- 데이터 행 -->
        <div v-for="row in currentData.rows" :key="row.rowSeq"
             class="flex cv-row"
             style="border-bottom:1px solid #1a1c24">
          <div v-for="(val, si) in specValues(row)" :key="si"
               class="flex-none tc pa2"
               style="min-width:80px;background:#0d1220;color:#e2e8f0;
                      border-right:1px solid #1a2035;font-size:11px">
            {{ val }}
          </div>
          <div v-for="col in currentData.chipCols" :key="col.colSeq"
               class="flex-none tc pa2"
               style="min-width:90px;font-size:11px;border-right:1px solid #1a1c24"
               :style="chipCellStyle(row, col)">
            {{ cellValue(row, col) }}
          </div>
        </div>
      </div>

      <div v-else class="tc mt5" style="color:#374151">
        <div style="font-size:40px;opacity:.3">⬆</div>
        <p class="mt2 f7">{{ activeTab }} Excel을 업로드하거나 "DB 불러오기"를 클릭하세요.</p>
      </div>
    </template>

    <!-- ── Raw_Data 테이블 ───────────────────────────────────── -->
    <template v-else>
      <div v-if="currentData && currentData.rows && currentData.rows.length"
           class="overflow-x-auto">

        <!-- 섹션 헤더 -->
        <div class="flex" style="border-bottom:2px solid #1e293b">
          <div class="flex-none tc fw7 pa2"
               style="min-width:320px;background:#0d1f38;color:#93c5fd;
                      font-size:10px;letter-spacing:.1em">
            Target AP
          </div>
          <div class="flex-none tc fw7 pa2"
               style="min-width:440px;background:#102840;color:#93c5fd;
                      font-size:10px;letter-spacing:.1em">
            Sorting KEY
          </div>
          <div class="flex-none tc fw7 pa2"
               style="min-width:560px;background:#1f1040;color:#c4b5fd;
                      font-size:10px;letter-spacing:.1em">
            Validation Status
          </div>
        </div>

        <!-- 컬럼 헤더 -->
        <div class="flex" style="border-bottom:1px solid #1e293b">
          <div v-for="h in RAW_HEADERS" :key="h.key"
               class="flex-none tc fw7 pa2"
               style="font-size:10px;letter-spacing:.08em"
               :style="{ minWidth: h.w+'px', background: h.bg, color: h.fc,
                         borderRight: '1px solid #1a2035' }">
            {{ h.label }}
          </div>
        </div>

        <!-- 데이터 행 -->
        <div v-for="row in currentData.rows" :key="row.rawdataRowSeq"
             class="flex cv-row"
             style="border-bottom:1px solid #1a1c24">
          <div v-for="h in RAW_HEADERS" :key="h.key"
               class="flex-none tc pa2"
               style="font-size:11px;border-right:1px solid #1a2035"
               :style="rawCellStyle(row[h.key], h)">
            {{ row[h.key] ?? '' }}
          </div>
        </div>
      </div>

      <div v-else class="tc mt5" style="color:#374151">
        <div style="font-size:40px;opacity:.3">⬆</div>
        <p class="mt2 f7">Raw_Data.xlsx를 업로드하거나 "DB 불러오기"를 클릭하세요.</p>
      </div>
    </template>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import axios from 'axios'

const API = 'http://localhost:9090/api/chipset'

const TABS = [
  { type: 'SERVER',   label: 'Server',   color: '#3b82f6' },
  { type: 'CLIENT',   label: 'Client',   color: '#8b5cf6' },
  { type: 'MOBILE',   label: 'Mobile',   color: '#10b981' },
  { type: 'RAW_DATA', label: 'Raw Data', color: '#f59e0b' },
]

// Mobile 탭에서는 스펙 컬럼 레이블 변경
const SPEC_LABELS_SERVER = ['DIMM', 'Product', 'Ver.', 'Density', 'Org', 'Speed']
const SPEC_LABELS_MOBILE = ['PKG', 'Density', 'Product', 'P/N', 'Code Name']

const RAW_HEADERS = [
  { key: 'company',     label: 'Company',      w: 80,  bg: '#0f1729', fc: '#60a5fa' },
  { key: 'seg',         label: 'Seg',           w: 80,  bg: '#0f1729', fc: '#60a5fa' },
  { key: 'chipset',     label: 'Chipset',       w: 100, bg: '#0f1729', fc: '#60a5fa' },
  { key: 'socCs',       label: 'SoC CS',        w: 120, bg: '#0f1729', fc: '#60a5fa' },
  { key: 'partNumber',  label: 'Part Number',   w: 140, bg: '#102840', fc: '#93c5fd' },
  { key: 'dramProcess', label: 'DRAM\nPROCES',  w: 90,  bg: '#102840', fc: '#93c5fd' },
  { key: 'flashProcess',label: 'Flash\nProcess',w: 80,  bg: '#102840', fc: '#93c5fd' },
  { key: 'density',     label: 'Density',       w: 70,  bg: '#102840', fc: '#93c5fd' },
  { key: 'mlcTlc',      label: 'MLC/TLC',       w: 70,  bg: '#102840', fc: '#93c5fd' },
  { key: 'pkg',         label: 'PKG',           w: 100, bg: '#102840', fc: '#93c5fd' },
  { key: 'val1Date',    label: 'Date',          w: 80,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val1Eng',     label: 'Eng.',          w: 50,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val1Status',  label: 'Status',        w: 80,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val1Remark',  label: 'Remark',        w: 120, bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val2Date',    label: 'Date',          w: 80,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val2Eng',     label: 'Eng.',          w: 50,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val2Status',  label: 'Status',        w: 80,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val2Remark',  label: 'Remark',        w: 120, bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val3Date',    label: 'Date',          w: 80,  bg: '#1f1040', fc: '#c4b5fd' },
  { key: 'val3Eng',     label: 'Eng.',          w: 50,  bg: '#1f1040', fc: '#c4b5fd' },
]

const VENDOR_COLORS = [
  { bg: '#0d1f38', text: '#93c5fd', border: '#1e3a5f' },
  { bg: '#1a0f2e', text: '#c4b5fd', border: '#2d1a4a' },
  { bg: '#0f2318', text: '#6ee7b7', border: '#1a4a2e' },
  { bg: '#2a1500', text: '#fbbf24', border: '#4a2a00' },
  { bg: '#1a0a0a', text: '#f87171', border: '#4a1a1a' },
]

// ── state ──────────────────────────────────────────────────────
const activeTab    = ref('SERVER')
const selectedFile = ref(null)
const fileName     = ref('')
const detectedType = ref('')
const uploading    = ref(false)
const loading      = ref(false)
const msg          = ref('')
const msgOk        = ref(false)
const uploadDt     = ref('')

// 탭별 데이터 캐시
const tabData    = ref({ SERVER: null, CLIENT: null, MOBILE: null, RAW_DATA: null })
const tabHistory = ref({ SERVER: [], CLIENT: [], MOBILE: [], RAW_DATA: [] })

const currentData = computed(() => tabData.value[activeTab.value])
const history     = computed(() => tabHistory.value[activeTab.value])
const activeColor = computed(() => TABS.find(t => t.type === activeTab.value)?.color ?? '#3b82f6')

const specLabels  = computed(() =>
  activeTab.value === 'MOBILE' ? SPEC_LABELS_MOBILE : SPEC_LABELS_SERVER
)

// ── 탭 전환 ────────────────────────────────────────────────────
async function switchTab(type) {
  activeTab.value = type
  selectedFile.value = null
  fileName.value     = ''
  detectedType.value = ''
  msg.value          = ''
  if (!tabData.value[type]) await loadData()
}

// ── 이벤트 핸들러 ──────────────────────────────────────────────
function onFileChange(e) {
  selectedFile.value = e.target.files[0] ?? null
  fileName.value     = selectedFile.value?.name ?? ''
  detectedType.value = ''
  msg.value          = ''
  // 파일명으로 간이 감지
  if (fileName.value) {
    const u = fileName.value.toUpperCase()
    if      (u.includes('RAW'))    detectedType.value = 'RAW_DATA'
    else if (u.includes('MOBILE')) detectedType.value = 'MOBILE'
    else if (u.includes('CLIENT')) detectedType.value = 'CLIENT'
    else if (u.includes('SERVER')) detectedType.value = 'SERVER'
  }
}

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
      // 서버에서 감지한 fileType으로 탭 자동 이동
      const serverType = data.fileType
      if (serverType && serverType !== activeTab.value) {
        activeTab.value = serverType
      }
      await Promise.all([loadData(), loadHistory()])
    }
  } catch (e) {
    msgOk.value = false
    msg.value   = '서버 오류: ' + (e.response?.data?.message ?? e.message)
  } finally {
    uploading.value    = false
    selectedFile.value = null
    fileName.value     = ''
    detectedType.value = ''
  }
}

// ── 데이터 로드 ────────────────────────────────────────────────
async function loadData() {
  loading.value = true
  try {
    const type = activeTab.value
    if (type === 'RAW_DATA') {
      const { data } = await axios.get(`${API}/rawdata`)
      tabData.value.RAW_DATA = data
      uploadDt.value = data.uploadDt
        ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
    } else {
      const { data } = await axios.get(`${API}/matrix?type=${type}`)
      tabData.value[type] = data
      uploadDt.value = data.uploadDt
        ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
    }
  } catch (e) {
    msg.value   = 'DB 조회 실패: ' + e.message
    msgOk.value = false
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const type = activeTab.value
    const url  = type === 'RAW_DATA'
      ? `${API}/history?type=RAW_DATA`
      : `${API}/history?type=${type}`
    const { data } = await axios.get(url)
    tabHistory.value[type] = data
  } catch { /* 히스토리 로드 실패는 무시 */ }
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

// 탭 변경 시 히스토리도 로드
watch(activeTab, async () => {
  if (!tabHistory.value[activeTab.value].length) await loadHistory()
})

onMounted(async () => {
  await Promise.all([loadData(), loadHistory()])
})

// ── 화면 렌더링 헬퍼 ────────────────────────────────────────────
function colsByVendor(vendor) {
  return currentData.value?.chipCols.filter(c => c.vendor === vendor) ?? []
}
function vendorIdx(vendor) {
  return currentData.value?.vendors.indexOf(vendor) ?? 0
}
function vendorColor(vi) {
  return VENDOR_COLORS[vi % VENDOR_COLORS.length]
}
function vendorStyle(vi) {
  const c = vendorColor(vi)
  return { background: c.bg, color: c.text, borderRight: `2px solid ${c.border}` }
}
function chipColStyle(col) {
  const c = vendorColor(vendorIdx(col.vendor))
  return { background: c.bg, color: c.text, borderRight: `1px solid ${c.border}` }
}
function specValues(row) {
  if (activeTab.value === 'MOBILE') {
    // Mobile: dimm=PKG, density, product, org=P/N, ver=CodeName
    return [row.dimm, row.density, row.product, row.org, row.ver]
  }
  return [row.dimm, row.product, row.ver, row.density, row.org, row.speed]
}
function cellValue(row, col) {
  return row.cells?.find(c => c.colSeq === col.colSeq)?.cellValue ?? ''
}
function chipCellStyle(row, col) {
  const val  = cellValue(row, col)
  const cell = row.cells?.find(c => c.colSeq === col.colSeq)
  if (!val) return { background: '#222428', color: 'transparent' }
  if (cell?.bgColor) return { background: cell.bgColor, color: '#fff' }
  return { color: vendorColor(vendorIdx(col.vendor)).text }
}
function rawCellStyle(val, h) {
  const base = { background: '#0d1220', color: '#e2e8f0', minWidth: h.w + 'px' }
  if (!val) return base
  const v = String(val).toLowerCase()
  if (v === 'pass')        return { ...base, background: '#00B050', color: '#fff' }
  if (v === 'fail')        return { ...base, background: '#c0392b', color: '#fff' }
  if (v === 'in progress') return { ...base, background: '#FF6600', color: '#fff' }
  if (v.startsWith('check')) return { ...base, background: '#FF0000', color: '#fff' }
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
.cv-row:hover > div { filter: brightness(1.15); }
.overflow-x-auto { overflow-x: auto; }
</style>

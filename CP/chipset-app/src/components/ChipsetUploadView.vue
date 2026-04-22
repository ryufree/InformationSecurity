<template>
  <div class="bg-near-black min-vh-100 pa4 white"
       style="font-family:'JetBrains Mono','Courier New',monospace; font-size:12px">

    <!-- ── 헤더 ──────────────────────────────────────────────── -->
    <div class="flex items-center mb4 pb3" style="border-bottom:1px solid #1e293b">
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

    <!-- ── 업로드 컨트롤 ─────────────────────────────────────── -->
    <div class="flex items-center mb3 flex-wrap" style="gap:10px">
      <label class="pointer f7 fw7 ba pa2 br2"
             style="letter-spacing:.1em;cursor:pointer;color:#3b82f6;
                    border-color:rgba(59,130,246,.4);background:rgba(59,130,246,.08)">
        <input type="file" accept=".xlsx,.xls" class="dn" @change="onFileChange" />
        ↑ XLSX 선택
      </label>

      <span v-if="fileName" class="f7" style="color:#9ca3af">{{ fileName }}</span>

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

      <button v-if="!matrix"
              class="f7 fw6 ba pa2 br2 pointer"
              style="background:transparent;color:#9ca3af;border-color:#374151"
              @click="loadMatrix">
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

    <!-- ── 매트릭스 테이블 ─────────────────────────────────── -->
    <div v-if="matrix && matrix.rows.length" class="overflow-x-auto">

      <!-- 행 1: 벤더 그룹 헤더 -->
      <div class="flex" style="border-bottom:2px solid #1e293b">
        <!-- 스펙 헤더 자리 (비워둠) -->
        <div v-for="lbl in SPEC_LABELS" :key="'gh'+lbl"
             class="flex-none tc fw7 f7 pa2"
             style="min-width:80px;background:#0f1729;color:#60a5fa;
                    border-right:1px solid #1a2035;font-size:10px;letter-spacing:.1em">
          {{ lbl }}
        </div>
        <!-- 벤더 그룹 -->
        <div v-for="(vendor, vi) in matrix.vendors" :key="vendor"
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
        <div v-for="lbl in SPEC_LABELS" :key="'ch'+lbl"
             class="flex-none pa2"
             style="min-width:80px;background:#0f1729;border-right:1px solid #1a2035">
        </div>
        <div v-for="col in matrix.chipCols" :key="col.colSeq"
             class="flex-none tc pa2"
             :style="{ minWidth:'90px', ...chipColStyle(col) }">
          <div class="fw7" style="font-size:10px">{{ col.chipNm }}</div>
          <div style="font-size:9px;opacity:.55">{{ col.chipDt }}</div>
        </div>
      </div>

      <!-- 데이터 행 -->
      <div v-for="row in matrix.rows" :key="row.rowSeq"
           class="flex cv-row"
           style="border-bottom:1px solid #1a1c24">
        <!-- 스펙 셀 -->
        <div v-for="(val, si) in specValues(row)" :key="si"
             class="flex-none tc pa2"
             style="min-width:80px;background:#0d1220;color:#e2e8f0;
                    border-right:1px solid #1a2035;font-size:11px">
          {{ val }}
        </div>
        <!-- 칩 셀 -->
        <div v-for="col in matrix.chipCols" :key="col.colSeq"
             class="flex-none tc pa2"
             style="min-width:90px;font-size:11px;border-right:1px solid #1a1c24"
             :style="chipCellStyle(row, col)">
          {{ cellValue(row, col) }}
        </div>
      </div>
    </div>

    <!-- 빈 상태 -->
    <div v-else-if="!loading" class="tc mt5" style="color:#374151">
      <div style="font-size:40px;opacity:.3">⬆</div>
      <p class="mt2 f7">Excel을 업로드하거나 "DB 불러오기"를 클릭하세요.</p>
    </div>

    <div v-if="loading" class="tc mt5 f7" style="color:#4b5563">
      Loading...
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

const API = 'http://localhost:8080/api/chipset'

const SPEC_LABELS = ['DIMM', 'Product', 'Ver.', 'Density', 'Org', 'Speed']

// 벤더별 색상 팔레트 — 인덱스 기반 자동 배정
const VENDOR_COLORS = [
  { bg: '#0d1f38', text: '#93c5fd', border: '#1e3a5f' }, // 파랑 (Intel 계열)
  { bg: '#1a0f2e', text: '#c4b5fd', border: '#2d1a4a' }, // 보라 (AMD 계열)
  { bg: '#0f2318', text: '#6ee7b7', border: '#1a4a2e' }, // 초록
  { bg: '#2a1500', text: '#fbbf24', border: '#4a2a00' }, // 주황
  { bg: '#1a0a0a', text: '#f87171', border: '#4a1a1a' }, // 빨강
]

// ── state ──────────────────────────────────────────────────────
const selectedFile = ref(null)
const fileName     = ref('')
const uploading    = ref(false)
const loading      = ref(false)
const msg          = ref('')
const msgOk        = ref(false)
const matrix       = ref(null)
const history      = ref([])
const uploadDt     = ref('')

// ── 이벤트 핸들러 ──────────────────────────────────────────────
function onFileChange(e) {
  selectedFile.value = e.target.files[0] ?? null
  fileName.value     = selectedFile.value?.name ?? ''
  msg.value          = ''
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
      await Promise.all([loadMatrix(), loadHistory()])
    }
  } catch (e) {
    msgOk.value = false
    msg.value   = '서버 오류: ' + (e.response?.data?.message ?? e.message)
  } finally {
    uploading.value = false
  }
}

async function loadMatrix() {
  loading.value = true
  try {
    const { data } = await axios.get(`${API}/matrix`)
    matrix.value  = data
    uploadDt.value = data.uploadDt
      ? new Date(data.uploadDt).toLocaleString('ko-KR')
      : ''
  } catch (e) {
    msg.value   = 'DB 조회 실패: ' + e.message
    msgOk.value = false
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  try {
    const { data } = await axios.get(`${API}/history`)
    history.value = data
  } catch { /* 히스토리 로드 실패는 조용히 무시 */ }
}

async function onHistorySelect(e) {
  const uploadSeq = e.target.value
  if (!uploadSeq) return
  loading.value = true
  try {
    const { data } = await axios.get(`${API}/history/${uploadSeq}`)
    matrix.value   = data
    uploadDt.value = '(히스토리 버전)'
  } finally {
    loading.value = false
  }
}

// 마운트 시 기존 DB 데이터 자동 로드
onMounted(async () => {
  await Promise.all([loadMatrix(), loadHistory()])
})

// ── 화면 렌더링 헬퍼 ────────────────────────────────────────────
function colsByVendor(vendor) {
  return matrix.value?.chipCols.filter(c => c.vendor === vendor) ?? []
}

function vendorIdx(vendor) {
  return matrix.value?.vendors.indexOf(vendor) ?? 0
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
  return [row.dimm, row.product, row.ver, row.density, row.org, row.speed]
}

function cellValue(row, col) {
  return row.cells?.find(c => c.colSeq === col.colSeq)?.cellValue ?? ''
}

function chipCellStyle(row, col) {
  const val  = cellValue(row, col)
  const cell = row.cells?.find(c => c.colSeq === col.colSeq)
  if (!val) return { background: '#222428', color: 'transparent' }
  if (cell?.bgColor) return { background: cell.bgColor, color: '#000' }
  return { color: vendorColor(vendorIdx(col.vendor)).text }
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

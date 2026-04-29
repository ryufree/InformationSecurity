<template>
  <div style="font-family:'JetBrains Mono','Courier New',monospace; font-size:12px;
              background:#0d0e14; min-height:100vh; color:#c8ccd6">

    <!-- Vue 2 Options API 배너 -->
    <div style="background:#451a03; border-bottom:2px solid #92400e; padding:5px 16px;
                display:flex; align-items:center; gap:12px">
      <span style="font-size:10px; font-weight:700; letter-spacing:.15em; color:#fde68a;
                   border:1px solid #b45309; background:#78350f;
                   padding:2px 8px; border-radius:3px">VUE 2.0</span>
      <span style="font-size:10px; color:#d97706; letter-spacing:.08em">
        Options API — <code style="color:#fbbf24">export default &#123; data(), computed:&#123;&#125;, methods:&#123;&#125; &#125;</code>
      </span>
    </div>

    <!-- 헤더 -->
    <div style="display:flex; align-items:center; margin-bottom:0;
                padding:14px 24px; background:#13141d; border-bottom:1px solid #1e293b">
      <span style="font-size:10px; font-weight:700; letter-spacing:.15em; color:#f59e0b;
                   border:1px solid rgba(245,158,11,.4); background:rgba(245,158,11,.08);
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
    <div style="display:flex; gap:4px; border-bottom:1px solid #1e293b; padding:0 24px; background:#13141d">
      <button v-for="tab in TABS" :key="tab.type"
              style="background:transparent; border:none; border-bottom:2px solid transparent;
                     font-size:11px; font-weight:600; padding:8px 10px 10px; margin-bottom:-1px;
                     cursor:pointer; letter-spacing:.08em; font-family:inherit"
              :style="activeTab === tab.type
                ? { color: tab.color, borderBottomColor: tab.color }
                : { color: '#4b5563' }"
              @click="switchTab(tab.type)">
        {{ tab.label }}
      </button>
    </div>

    <div style="padding:16px 24px">

      <!-- 업로드 컨트롤 -->
      <div style="display:flex; align-items:center; gap:10px; flex-wrap:wrap; margin-bottom:12px">
        <label style="cursor:pointer; font-size:11px; font-weight:700; padding:6px 10px;
                      border-radius:4px; border:1px solid"
               :style="{ color: activeColor, borderColor: activeColor+'66', background: activeColor+'14' }">
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

      <!-- Matrix 테이블 (SERVER / CLIENT / MOBILE) -->
      <template v-else-if="activeTab !== 'RAW_DATA'">
        <div v-if="currentData && currentData.rows && currentData.rows.length"
             style="overflow-x:auto; overflow-y:auto; max-height:calc(100vh - 260px)">
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

      <!-- Raw_Data 테이블 -->
      <template v-else>
        <div v-if="currentData && currentData.rows && currentData.rows.length"
             style="overflow-x:auto; overflow-y:auto; max-height:calc(100vh - 260px)">
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
  </div>
</template>

<script>
// Vue 2 Options API 스타일
// Vue 3 Composition API와 달리 ref/computed/onMounted 등을 import하지 않는다.
import axios from 'axios'

const API = 'http://localhost:9090/api/chipset'

export default {
  name: 'ChipsetUploadViewV2',

  // ── Vue 2: data() 함수로 반응형 상태 선언 ──────────────────────
  // Vue 3 Composition API: const x = ref(...)  /  const obj = reactive({...})
  data() {
    return {
      TABS: [
        { type: 'SERVER',   label: 'Server',   color: '#3b82f6' },
        { type: 'CLIENT',   label: 'Client',   color: '#8b5cf6' },
        { type: 'MOBILE',   label: 'Mobile',   color: '#10b981' },
        { type: 'RAW_DATA', label: 'Raw Data', color: '#f59e0b' },
      ],
      ROW1_H: 30,
      ROW2_H: 30,
      VENDOR_COLORS: [
        { bg: '#0d1f38', text: '#93c5fd', border: '#1e3a5f' },
        { bg: '#1a0f2e', text: '#c4b5fd', border: '#2d1a4a' },
        { bg: '#0f2318', text: '#6ee7b7', border: '#1a4a2e' },
        { bg: '#2a1500', text: '#fbbf24', border: '#4a2a00' },
        { bg: '#1a0a0a', text: '#f87171', border: '#4a1a1a' },
      ],
      activeTab:    'SERVER',
      selectedFile: null,
      fileName:     '',
      detectedType: '',
      uploading:    false,
      loading:      false,
      msg:          '',
      msgOk:        false,
      uploadDt:     '',
      hoverRow:     null,
      // 키를 미리 선언해 두면 Vue 2에서도 $set 없이 직접 할당으로 반응성이 유지된다.
      tabData:    { SERVER: null, CLIENT: null, MOBILE: null, RAW_DATA: null },
      tabHistory: { SERVER: [],   CLIENT: [],   MOBILE: [],   RAW_DATA: []   },
    }
  },

  // ── Vue 2: computed 객체 ────────────────────────────────────────
  // Vue 3 Composition API: const x = computed(() => ...)
  computed: {
    currentData() { return this.tabData[this.activeTab] },
    history()     { return this.tabHistory[this.activeTab] },
    activeColor() {
      const tab = this.TABS.find(t => t.type === this.activeTab)
      return tab ? tab.color : '#3b82f6'
    },
    SPEC_COLS() {
      const sc = this.currentData && this.currentData.specCols
      if (!sc || !sc.length) return []
      return sc.map(s => ({ colSeq: s.colSeq, label: s.colNm, w: this.guessWidth(s.colNm) }))
    },
    RAW_SECTIONS() {
      const cols = (this.currentData && this.currentData.rawdataCols) || []
      if (!cols.length) return []
      const targetAp   = cols.filter(c => c.colIdx >= 1  && c.colIdx <= 4)
      const sortingKey = cols.filter(c => c.colIdx >= 5  && c.colIdx <= 10)
      const valStatus  = cols.filter(c => c.colIdx >= 11)
      const sections = []
      if (targetAp.length)   sections.push({ label: 'Target AP',         count: targetAp.length,   color: '#93c5fd', bg: '#0d1f38' })
      if (sortingKey.length) sections.push({ label: 'Sorting KEY',       count: sortingKey.length, color: '#93c5fd', bg: '#102840' })
      if (valStatus.length)  sections.push({ label: 'Validation Status', count: valStatus.length,  color: '#c4b5fd', bg: '#1f1040' })
      return sections
    },
    specLeftOffsets() {
      const offsets = []
      let left = 0
      for (const col of this.SPEC_COLS) { offsets.push(left); left += col.w }
      return offsets
    },
  },

  // ── Vue 2: created 라이프사이클 훅 ─────────────────────────────
  // Vue 3 Composition API: onMounted(() => { ... })  /  setup() 내 직접 실행
  created() {
    Promise.all([this.loadData(), this.loadHistory()])
  },

  // ── Vue 2: methods 객체 ─────────────────────────────────────────
  // Vue 3 Composition API: 함수를 setup() 안에서 직접 선언
  // 모든 반응형 상태 접근에 this. 를 사용한다.
  methods: {
    getErrorMessage(e) {
      const d = e && e.response && e.response.data
      return (typeof d === 'string' ? d : (d && d.message)) || (e && e.message) || String(e) || 'Unknown error'
    },
    guessWidth(colNm) {
      const n = (colNm || '').length
      if (n <= 3) return 55; if (n <= 5) return 65
      if (n <= 8) return 80; if (n <= 12) return 95
      return 115
    },
    rawColWidth(colNm) {
      const n = (colNm || '').length
      if (n <= 3) return 60; if (n <= 5) return 70
      if (n <= 8) return 90; if (n <= 12) return 110
      return 140
    },
    rawColBg(colIdx) {
      if (colIdx <= 4)  return '#0d1f38'
      if (colIdx <= 10) return '#102840'
      return '#1f1040'
    },
    rawColFc(colIdx) {
      if (colIdx <= 4)  return '#60a5fa'
      if (colIdx <= 10) return '#93c5fd'
      return '#c4b5fd'
    },
    vendorColor(vi) { return this.VENDOR_COLORS[vi % this.VENDOR_COLORS.length] },
    vendorIdx(vendor) {
      const v = this.currentData && this.currentData.vendors
      return v ? v.indexOf(vendor) : 0
    },
    colsByVendor(vendor) {
      const c = this.currentData && this.currentData.chipCols
      return c ? c.filter(col => col.vendor === vendor) : []
    },

    async switchTab(type) {
      this.activeTab    = type
      this.selectedFile = null
      this.fileName     = ''
      this.detectedType = ''
      this.msg          = ''
      const tasks = []
      if (!this.tabData[type])           tasks.push(this.loadData())
      if (!this.tabHistory[type].length) tasks.push(this.loadHistory())
      await Promise.all(tasks)
    },
    onFileChange(e) {
      this.selectedFile = e.target.files[0] || null
      this.fileName     = this.selectedFile ? this.selectedFile.name : ''
      this.detectedType = ''
      this.msg          = ''
      if (this.fileName) {
        const u = this.fileName.toUpperCase()
        if      (u.includes('RAW'))    this.detectedType = 'RAW_DATA'
        else if (u.includes('MOBILE')) this.detectedType = 'MOBILE'
        else if (u.includes('CLIENT')) this.detectedType = 'CLIENT'
        else if (u.includes('SERVER')) this.detectedType = 'SERVER'
      }
    },
    async doUpload() {
      if (!this.selectedFile) return
      this.uploading = true
      this.msg       = ''
      try {
        const form = new FormData()
        form.append('file', this.selectedFile)
        const { data } = await axios.post(API + '/upload', form)
        this.msgOk = data.success
        this.msg   = data.message
        if (data.success) {
          if (data.fileType && data.fileType !== this.activeTab) this.activeTab = data.fileType
          await Promise.all([this.loadData(), this.loadHistory()])
        }
      } catch (e) {
        this.msgOk = false
        const m = this.getErrorMessage(e)
        this.msg = m.startsWith('서버 오류:') ? m : ('서버 오류: ' + m)
      } finally {
        this.uploading    = false
        this.selectedFile = null
        this.fileName     = ''
        this.detectedType = ''
      }
    },
    async loadData() {
      this.loading = true
      try {
        const type = this.activeTab
        if (type === 'RAW_DATA') {
          const { data } = await axios.get(API + '/rawdata')
          this.tabData['RAW_DATA'] = data
          this.uploadDt = data.uploadDt ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
        } else {
          const { data } = await axios.get(API + '/matrix?type=' + type)
          this.tabData[type] = data
          this.uploadDt = data.uploadDt ? new Date(data.uploadDt).toLocaleString('ko-KR') : ''
        }
      } catch (e) {
        this.msg   = 'DB 조회 실패: ' + this.getErrorMessage(e)
        this.msgOk = false
      } finally {
        this.loading = false
      }
    },
    async loadHistory() {
      try {
        const type = this.activeTab
        const { data } = await axios.get(API + '/history?type=' + type)
        this.tabHistory[type] = data
      } catch { /* 무시 */ }
    },
    async onHistorySelect(e) {
      const uploadSeq = e.target.value
      if (!uploadSeq) return
      this.loading = true
      try {
        const type = this.activeTab
        const url  = type === 'RAW_DATA'
          ? API + '/rawdata/history/' + uploadSeq
          : API + '/history/' + uploadSeq
        const { data } = await axios.get(url)
        this.tabData[type] = data
        this.uploadDt = '(히스토리 버전)'
      } finally {
        this.loading = false
      }
    },

    specCellValue(row, spec) {
      const cell = (row.specCells || []).find(c => c.colSeq === spec.colSeq)
      return cell ? cell.cellValue : ''
    },
    chipCellValue(row, col) {
      const cell = (row.chipCells || []).find(c => c.colSeq === col.colSeq)
      return cell ? cell.cellValue : ''
    },
    rawCellValue(row, col) {
      const cell = (row.cells || []).find(c => c.colSeq === col.colSeq)
      return cell ? cell.cellValue : ''
    },

    frozenThStyle(i, topPx, blank) {
      const spec = this.SPEC_COLS[i]
      const isLast = i === this.SPEC_COLS.length - 1
      return {
        position: 'sticky', left: this.specLeftOffsets[i] + 'px', top: topPx + 'px',
        zIndex: 30, width: spec.w + 'px', minWidth: spec.w + 'px',
        background: '#0f1729', color: blank ? 'transparent' : '#60a5fa',
        textAlign: 'center', fontWeight: '700', fontSize: '10px', padding: '5px 4px',
        borderRight: isLast ? '2px solid #1e3a5f' : '1px solid #1a2035',
        borderBottom: '1px solid #1a2035', letterSpacing: '.06em', boxSizing: 'border-box',
      }
    },
    frozenTdStyle(i, hovered) {
      const spec = this.SPEC_COLS[i]
      const isLast = i === this.SPEC_COLS.length - 1
      return {
        position: 'sticky', left: this.specLeftOffsets[i] + 'px',
        zIndex: 5, width: spec.w + 'px', minWidth: spec.w + 'px',
        background: hovered ? '#182040' : '#0d1220', color: '#e2e8f0',
        textAlign: 'center', fontSize: '11px', padding: '5px 4px',
        borderRight: isLast ? '2px solid #1e3a5f' : '1px solid #1a2035',
        borderBottom: '1px solid #1a1c24', boxSizing: 'border-box',
      }
    },
    vendorThStyle(vi, topPx) {
      const c = this.vendorColor(vi)
      return {
        position: 'sticky', top: topPx + 'px', zIndex: 10,
        textAlign: 'center', fontWeight: '700', fontSize: '11px', letterSpacing: '.12em',
        padding: '6px 8px', background: c.bg, color: c.text,
        borderRight: '2px solid ' + c.border, borderBottom: '1px solid ' + c.border,
        boxSizing: 'border-box',
      }
    },
    chipThStyle(col, topPx) {
      const c = this.vendorColor(this.vendorIdx(col.vendor))
      return {
        position: 'sticky', top: topPx + 'px', zIndex: 10,
        textAlign: 'center', fontWeight: '700', fontSize: '10px', padding: '5px 4px',
        background: c.bg, color: c.text,
        borderRight: '1px solid ' + c.border, borderBottom: '1px solid ' + c.border,
        boxSizing: 'border-box',
      }
    },
    chipDateThStyle(col, topPx) {
      const c = this.vendorColor(this.vendorIdx(col.vendor))
      return {
        position: 'sticky', top: topPx + 'px', zIndex: 10,
        textAlign: 'center', fontWeight: '400', fontSize: '9px', padding: '3px 4px',
        background: c.bg, color: c.text, opacity: '.75',
        borderRight: '1px solid ' + c.border, borderBottom: '2px solid ' + c.border,
        boxSizing: 'border-box',
      }
    },
    chipCellStyle(row, col, hovered) {
      const val  = this.chipCellValue(row, col)
      const cell = (row.chipCells || []).find(c => c.colSeq === col.colSeq)
      const vc   = this.vendorColor(this.vendorIdx(col.vendor))
      const base = {
        textAlign: 'center', fontSize: '11px', padding: '5px 4px',
        borderRight: '1px solid ' + vc.border, borderBottom: '1px solid #1a1c24',
        boxSizing: 'border-box',
      }
      if (!val)
        return Object.assign({}, base, { background: hovered ? '#1a1c28' : '#111318', color: 'transparent' })
      if (cell && cell.bgColor) {
        const bg = cell.bgColor.startsWith('#') ? cell.bgColor : '#' + cell.bgColor
        return Object.assign({}, base, { background: hovered ? bg + 'cc' : bg, color: '#fff', fontWeight: '600' })
      }
      return Object.assign({}, base, { background: hovered ? '#182040' : 'transparent', color: vc.text })
    },
    rawHeaderThStyle(col) {
      return {
        textAlign: 'center', fontWeight: '700', fontSize: '10px', letterSpacing: '.08em',
        padding: '5px 6px', position: 'sticky', top: '30px', zIndex: 10,
        minWidth: this.rawColWidth(col.colNm) + 'px',
        background: this.rawColBg(col.colIdx), color: this.rawColFc(col.colIdx),
        borderRight: '1px solid #1a2035', borderBottom: '2px solid #1e293b',
      }
    },
    rawCellStyle(val, col) {
      const base = {
        textAlign: 'center', fontSize: '11px', padding: '5px 6px',
        borderRight: '1px solid #1a2035', borderBottom: '1px solid #1a1c24',
        background: '#0d1220', color: '#e2e8f0',
        minWidth: this.rawColWidth(col.colNm) + 'px',
      }
      if (!val) return base
      const v = String(val).toLowerCase()
      if (v === 'pass')           return Object.assign({}, base, { background: '#00B050', color: '#fff' })
      if (v === 'fail')           return Object.assign({}, base, { background: '#c0392b', color: '#fff' })
      if (v === 'in progress')    return Object.assign({}, base, { background: '#FF6600', color: '#fff' })
      if (v.startsWith('check'))  return Object.assign({}, base, { background: '#FF0000', color: '#fff' })
      return base
    },
    formatDt(dt) {
      if (!dt) return ''
      return new Date(dt).toLocaleString('ko-KR', {
        year: '2-digit', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit',
      })
    },
  },
}
</script>

<style scoped>
* { box-sizing: border-box; }
</style>

<script setup>
import { shallowRef } from 'vue'
import HomeScreen from './components/HomeScreen.vue'
import ChipsetValidation from './components/ChipsetValidation.vue'
import ChipsetUploadView from './components/ChipsetUploadView.vue'

const NAV_HEIGHT = 44

const apps = [
  {
    id: 'chipset-validation',
    name: 'ChipsetValidation.vue',
    description: '로컬 Excel 직접 파싱 (DB 없음)',
    component: ChipsetValidation,
  },
  {
    id: 'chipset-db',
    name: 'ChipsetUploadView.vue',
    description: 'Upload → Oracle/H2 DB → Grid 출력',
    component: ChipsetUploadView,
  },
]

const currentApp = shallowRef(null)

function openApp(app) { currentApp.value = app }
function goHome() { currentApp.value = null }
</script>

<template>
  <HomeScreen v-if="!currentApp" :apps="apps" @open="openApp" />
  <template v-else>
    <nav class="g-nav">
      <button class="g-nav__back" @click="goHome">← HOME</button>
      <span class="g-nav__name">{{ currentApp.name }}</span>
    </nav>
    <component :is="currentApp.component" :top-offset="NAV_HEIGHT" />
  </template>
</template>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #0d0e14; }

.g-nav {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 44px;
  padding: 0 20px;
  background: #0d0e14;
  border-bottom: 1px solid #1e293b;
  position: sticky;
  top: 0;
  z-index: 100;
}
.g-nav__back {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: #3b82f6;
  background: transparent;
  border: 1px solid rgba(59,130,246,0.3);
  border-radius: 4px;
  padding: 5px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.g-nav__back:hover { background: rgba(59,130,246,0.1); }
.g-nav__name {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 12px;
  color: #6b7280;
  letter-spacing: 0.05em;
}
</style>

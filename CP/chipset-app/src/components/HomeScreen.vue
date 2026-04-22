<template>
  <div class="hs-root">
    <header class="hs-header">
      <div class="hs-header__left">
        <span class="hs-header__badge">CHIPSET APP</span>
        <h1 class="hs-header__title">Component Library</h1>
      </div>
    </header>

    <div class="hs-body">
      <p class="hs-hint">실행할 컴포넌트를 선택하세요</p>
      <div class="hs-grid">
        <button
          v-for="app in apps"
          :key="app.id"
          class="hs-card"
          @click="$emit('open', app)"
        >
          <div class="hs-card__icon">
            <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
              <rect x="2" y="2" width="32" height="32" rx="6" fill="#0d1f38" stroke="#1e3a5f" stroke-width="1.5"/>
              <path d="M18 10L26 14.5V23.5L18 28L10 23.5V14.5L18 10Z" stroke="#3b82f6" stroke-width="1.5" fill="rgba(59,130,246,0.08)"/>
              <path d="M18 10V28M10 14.5L26 23.5M26 14.5L10 23.5" stroke="#3b82f6" stroke-width="1" opacity="0.4"/>
            </svg>
          </div>
          <div class="hs-card__info">
            <div class="hs-card__name">{{ app.name }}</div>
            <div class="hs-card__desc">{{ app.description }}</div>
          </div>
          <div class="hs-card__arrow">→</div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ apps: Array })
defineEmits(['open'])
</script>

<style scoped>
.hs-root {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  background: #0d0e14;
  color: #c8ccd6;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.hs-header {
  display: flex;
  align-items: center;
  padding: 18px 28px;
  background: #13141d;
  border-bottom: 2px solid #1e3a5f;
}
.hs-header__left { display: flex; align-items: center; gap: 16px; }
.hs-header__badge {
  font-size: 10px; font-weight: 700; letter-spacing: 0.18em;
  color: #3b82f6; background: rgba(59,130,246,0.12);
  border: 1px solid rgba(59,130,246,0.3);
  padding: 3px 10px; border-radius: 3px;
}
.hs-header__title {
  margin: 0; font-size: 18px; font-weight: 600;
  color: #e8eaf0; letter-spacing: 0.04em;
}

.hs-body {
  flex: 1;
  padding: 40px 28px;
}

.hs-hint {
  font-size: 11px;
  color: #4b5563;
  letter-spacing: 0.08em;
  margin-bottom: 24px;
}

.hs-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 640px;
}

.hs-card {
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
  padding: 16px 20px;
  background: #13141d;
  border: 1px solid #1e293b;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  font-family: inherit;
  color: inherit;
}
.hs-card:hover {
  background: #1a1c2e;
  border-color: #3b82f6;
  box-shadow: 0 0 0 1px rgba(59,130,246,0.2);
}
.hs-card:active { transform: scale(0.995); }

.hs-card__icon { flex: none; }

.hs-card__info { flex: 1; }
.hs-card__name {
  font-size: 13px;
  font-weight: 700;
  color: #e2e8f0;
  letter-spacing: 0.04em;
  margin-bottom: 3px;
}
.hs-card__desc {
  font-size: 10px;
  color: #4b5563;
  letter-spacing: 0.08em;
}

.hs-card__arrow {
  font-size: 16px;
  color: #1e3a5f;
  transition: color 0.15s, transform 0.15s;
}
.hs-card:hover .hs-card__arrow {
  color: #3b82f6;
  transform: translateX(3px);
}
</style>

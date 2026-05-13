<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useThemeStore, type ThemeId } from '../stores/theme'

const theme = useThemeStore()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

const PRESETS: { id: ThemeId; label: string; hint: string; swatch: string }[] = [
  { id: 'aurora', label: '极光', hint: '青紫 · 经典', swatch: 'linear-gradient(135deg,#38bdf8,#8b5cf6)' },
  { id: 'ember', label: '余烬', hint: '暖橙 · 玫红', swatch: 'linear-gradient(135deg,#fb923c,#e11d48)' },
  { id: 'forest', label: '森野', hint: '翠绿 · 青绿', swatch: 'linear-gradient(135deg,#34d399,#0d9488)' },
  { id: 'midnight', label: '午夜', hint: '冷灰 · 克制', swatch: 'linear-gradient(135deg,#94a3b8,#475569)' },
  { id: 'daybreak', label: '云光', hint: '浅色 · 清爽', swatch: 'linear-gradient(135deg,#bae6fd,#e0e7ff)' },
]

function pick(id: ThemeId) {
  theme.setTheme(id)
  open.value = false
}

function toggle() {
  open.value = !open.value
}

function onDocPointerDown(e: MouseEvent) {
  if (!open.value) return
  const t = e.target as Node | null
  if (t && rootRef.value?.contains(t)) return
  open.value = false
}

onMounted(() => document.addEventListener('pointerdown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown, true))
</script>

<template>
  <div ref="rootRef" class="theme-settings">
    <button
      type="button"
      class="theme-fab"
      aria-haspopup="dialog"
      :aria-expanded="open"
      aria-label="主题与外观"
      @click.stop="toggle"
    >
      <span class="fab-icon" aria-hidden="true">⚙</span>
    </button>
    <Transition name="panel">
      <div
        v-if="open"
        class="theme-panel"
        role="dialog"
        aria-label="选择颜色主题"
        @click.stop
      >
        <div class="panel-head">
          <span class="panel-title">颜色主题</span>
          <button type="button" class="panel-close" aria-label="关闭" @click="open = false">×</button>
        </div>
        <p class="panel-desc">选择后自动保存，下次打开页面仍会生效。</p>
        <ul class="preset-list">
          <li v-for="p in PRESETS" :key="p.id">
            <button
              type="button"
              class="preset-btn"
              :class="{ active: theme.themeId === p.id }"
              @click="pick(p.id)"
            >
              <span class="swatch" :style="{ background: p.swatch }" />
              <span class="preset-text">
                <span class="preset-label">{{ p.label }}</span>
                <span class="preset-hint">{{ p.hint }}</span>
              </span>
              <span v-if="theme.themeId === p.id" class="check" aria-hidden="true">✓</span>
            </button>
          </li>
        </ul>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.theme-settings {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 9990;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.theme-fab {
  width: 48px;
  height: 48px;
  border-radius: 999px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface-card-solid);
  color: var(--theme-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35);
  backdrop-filter: var(--theme-blur);
  -webkit-backdrop-filter: var(--theme-blur);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease;
}
.theme-fab:hover {
  transform: scale(1.05);
  border-color: var(--theme-accent-glow);
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.4);
}
.fab-icon {
  font-size: 22px;
  line-height: 1;
  opacity: 0.92;
}
.theme-panel {
  width: min(300px, calc(100vw - 32px));
  padding: 14px 14px 12px;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface-card-solid);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  backdrop-filter: var(--theme-blur);
  -webkit-backdrop-filter: var(--theme-blur);
  color: var(--theme-text);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.panel-title {
  font-weight: 600;
  font-size: 15px;
}
.panel-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--theme-text-muted);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.panel-close:hover {
  color: var(--theme-text);
  background: var(--theme-link-hover-bg);
}
.panel-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--theme-text-muted);
  line-height: 1.45;
}
.preset-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.preset-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--theme-border);
  background: rgba(0, 0, 0, 0.12);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}
.preset-btn:hover {
  border-color: var(--theme-border-strong);
  background: var(--theme-link-hover-bg);
}
.preset-btn.active {
  border-color: var(--theme-accent-glow);
  box-shadow: 0 0 0 1px var(--theme-accent-soft);
}
.swatch {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
  border: 1px solid var(--theme-border-strong);
}
.preset-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.preset-label {
  font-size: 14px;
  font-weight: 600;
}
.preset-hint {
  font-size: 11px;
  color: var(--theme-text-muted);
}
.check {
  color: var(--theme-accent);
  font-weight: 700;
  font-size: 14px;
}
.panel-enter-active,
.panel-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>

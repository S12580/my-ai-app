<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useThemeStore, type ThemeId } from '../stores/theme'

const theme = useThemeStore()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const panelView = ref<'menu' | 'theme' | 'font'>('menu')
const FONT_SIZE_KEY = 'my-ai-app-font-size'
const fontSize = ref<'12px' | '13px' | '14px' | '15px' | '16px' | '18px'>('14px')

const FONT_SIZE_LEVELS = [
  { value: '12px', label: '细' },
  { value: '13px', label: '小' },
  { value: '14px', label: '标准' },
  { value: '15px', label: '中大' },
  { value: '16px', label: '大' },
  { value: '18px', label: '特大' },
] as const

const fontSizeIndex = computed(() =>
  Math.max(
    0,
    FONT_SIZE_LEVELS.findIndex((x) => x.value === fontSize.value),
  ),
)
const fontSummary = computed(() => {
  const level = FONT_SIZE_LEVELS[fontSizeIndex.value]
  return `${level.label} (${level.value})`
})

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
  if (open.value) panelView.value = 'menu'
}

function openThemePicker() {
  panelView.value = 'theme'
}

function openFontSettings() {
  panelView.value = 'font'
}

function backToMenu() {
  panelView.value = 'menu'
}

function applyFontDom() {
  document.documentElement.style.setProperty('--app-font-size', fontSize.value)
}

function setFontSize(next: '12px' | '13px' | '14px' | '15px' | '16px' | '18px') {
  fontSize.value = next
  try {
    localStorage.setItem(FONT_SIZE_KEY, next)
  } catch {
    /* ignore */
  }
  applyFontDom()
}

function onFontSizeSliderInput(event: Event) {
  const target = event.target as HTMLInputElement | null
  if (!target) return
  const idx = Number(target.value)
  const picked = FONT_SIZE_LEVELS[idx]
  if (!picked) return
  setFontSize(picked.value)
}

function onDocPointerDown(e: MouseEvent) {
  if (!open.value) return
  const t = e.target as Node | null
  if (t && rootRef.value?.contains(t)) return
  open.value = false
}

onMounted(() => {
  try {
    const savedSize = localStorage.getItem(FONT_SIZE_KEY)
    if (
      savedSize === '12px' ||
      savedSize === '13px' ||
      savedSize === '14px' ||
      savedSize === '15px' ||
      savedSize === '16px' ||
      savedSize === '18px'
    ) {
      fontSize.value = savedSize
    }
  } catch {
    /* ignore */
  }
  applyFontDom()
  document.addEventListener('pointerdown', onDocPointerDown, true)
})
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
        :aria-label="panelView === 'theme' ? '选择颜色主题' : panelView === 'font' ? '字体设置' : '设置菜单'"
        @click.stop
      >
        <div class="panel-head">
          <button
            v-if="panelView !== 'menu'"
            type="button"
            class="panel-back"
            aria-label="返回设置菜单"
            @click="backToMenu"
          >
            ←
          </button>
          <span class="panel-title">
            {{ panelView === 'theme' ? '颜色主题' : panelView === 'font' ? '字体设置' : '设置' }}
          </span>
          <button type="button" class="panel-close" aria-label="关闭" @click="open = false">×</button>
        </div>
        <template v-if="panelView === 'menu'">
          <p class="panel-desc">选择一个设置项进入配置。</p>
          <ul class="menu-list">
            <li>
              <button type="button" class="menu-entry" @click="openThemePicker">
                <span class="entry-icon" aria-hidden="true">🎨</span>
                <span class="entry-main">
                  <span class="entry-label">主题</span>
                  <span class="entry-hint">切换页面皮肤与配色</span>
                </span>
                <span class="entry-arrow" aria-hidden="true">›</span>
              </button>
            </li>
            <li>
              <button type="button" class="menu-entry" @click="openFontSettings">
                <span class="entry-icon" aria-hidden="true">🅰️</span>
                <span class="entry-main">
                  <span class="entry-label">字体</span>
                  <span class="entry-hint">{{ fontSummary }}</span>
                </span>
                <span class="entry-arrow" aria-hidden="true">›</span>
              </button>
            </li>
          </ul>
        </template>
        <template v-else-if="panelView === 'theme'">
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
        </template>
        <template v-else>
          <p class="panel-desc">设置会自动保存并立即应用。</p>
          <div class="font-block">
            <div class="font-label">字体大小</div>
            <div class="font-slider-wrap">
              <input
                class="font-slider"
                type="range"
                min="0"
                :max="FONT_SIZE_LEVELS.length - 1"
                step="1"
                :value="fontSizeIndex"
                @input="onFontSizeSliderInput"
              />
              <div class="font-scale-labels">
                <span v-for="x in FONT_SIZE_LEVELS" :key="x.value">{{ x.label }}</span>
              </div>
              <div class="font-size-value">{{ FONT_SIZE_LEVELS[fontSizeIndex].value }}</div>
            </div>
          </div>
        </template>
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
  display: block;
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
  position: absolute;
  left: 0;
  bottom: calc(100% + 10px);
  width: min(264px, calc(100vw - 32px));
  max-height: min(70vh, 560px);
  overflow: auto;
  box-sizing: border-box;
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
  justify-content: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.panel-back {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--theme-text-muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.panel-back:hover {
  color: var(--theme-text);
  background: var(--theme-link-hover-bg);
}
.panel-title {
  font-weight: 600;
  font-size: 15px;
  margin-right: auto;
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
.menu-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.menu-entry {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--theme-border);
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.menu-entry:hover {
  border-color: var(--theme-accent-glow);
  background: transparent;
}
.entry-icon {
  font-size: 18px;
  line-height: 1;
}
.entry-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.entry-label {
  font-size: 14px;
  font-weight: 600;
}
.entry-hint {
  font-size: 11px;
  color: var(--theme-text-muted);
}
.entry-arrow {
  font-size: 20px;
  color: var(--theme-text-muted);
}
.font-block {
  margin-top: 10px;
}
.font-label {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin-bottom: 8px;
}
.font-slider-wrap {
  width: 100%;
}
.font-slider {
  width: 100%;
  accent-color: var(--theme-accent);
}
.font-scale-labels {
  margin-top: 6px;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  font-size: 10px;
  color: var(--theme-text-muted);
  text-align: center;
}
.font-size-value {
  margin-top: 6px;
  font-size: 12px;
  color: var(--theme-text);
}
.preset-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--theme-border);
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}
.preset-btn:hover {
  border-color: var(--theme-border-strong);
  background: transparent;
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


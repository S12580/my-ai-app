<script setup lang="ts">
import { watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useConfirmStore } from '../stores/confirm'

const confirm = useConfirmStore()
const { visible, title, message, confirmText, cancelText, danger } = storeToRefs(confirm)

function onBackdrop(e: MouseEvent) {
  if (e.target === e.currentTarget) confirm.submit(false)
}

function onKey(e: KeyboardEvent) {
  if (!visible.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    confirm.submit(false)
  }
}

watch(visible, (v, _prev, onCleanup) => {
  if (!v) return
  const prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', onKey, true)
  onCleanup(() => {
    document.removeEventListener('keydown', onKey, true)
    document.body.style.overflow = prevOverflow
  })
})
</script>

<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div
        v-if="visible"
        class="confirm-overlay"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="'confirm-title'"
        @click="onBackdrop"
      >
        <div class="confirm-card" @click.stop>
          <div class="confirm-icon-wrap" :class="{ danger }" aria-hidden="true">
            <svg class="confirm-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path
                v-if="danger"
                d="M12 9v3.5m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                v-else
                d="M12 8v4m0 4h.01M12 2a10 10 0 100 20 10 10 0 000-20z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </div>
          <h2 id="confirm-title" class="confirm-title">{{ title }}</h2>
          <p class="confirm-msg">{{ message }}</p>
          <div class="confirm-actions">
            <button type="button" class="btn btn-cancel" @click="confirm.submit(false)">
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="btn btn-ok"
              :class="{ 'btn-danger': danger }"
              @click="confirm.submit(true)"
            >
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 10040;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  box-sizing: border-box;
  background: transparent;
}
.confirm-card {
  width: min(400px, calc(100vw - 32px));
  padding: 22px 22px 18px;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface-card-solid);
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.38),
    0 0 0 1px rgba(0, 0, 0, 0.06);
  color: var(--theme-text);
}
.confirm-icon-wrap {
  width: 44px;
  height: 44px;
  margin: 0 auto 14px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--theme-accent) 14%, transparent);
  color: var(--theme-accent);
}
.confirm-icon-wrap.danger {
  background: color-mix(in srgb, #f87171 18%, transparent);
  color: #f87171;
}
.confirm-icon {
  width: 26px;
  height: 26px;
}
.confirm-title {
  margin: 0 0 10px;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
  letter-spacing: 0.02em;
}
.confirm-msg {
  margin: 0 0 22px;
  font-size: 14px;
  line-height: 1.55;
  color: var(--theme-text-muted);
  text-align: center;
}
.confirm-actions {
  display: flex;
  gap: 10px;
  justify-content: stretch;
}
.btn {
  flex: 1;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    transform 0.1s ease;
}
.btn-cancel {
  background: var(--theme-control-fill);
  border-color: var(--theme-border-strong);
  color: var(--theme-text);
}
.btn-cancel:hover {
  border-color: var(--theme-border-strong);
  background: color-mix(in srgb, var(--theme-text) 8%, transparent);
}
.btn-ok {
  background: var(--theme-primary-gradient);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 2px 12px var(--theme-btn-glow);
}
.btn-ok:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px var(--theme-btn-glow-strong);
}
.btn-danger {
  background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
  box-shadow: 0 2px 14px rgba(220, 38, 38, 0.35);
}
.btn-danger:hover {
  box-shadow: 0 4px 18px rgba(185, 28, 28, 0.45);
}
.confirm-fade-enter-active,
.confirm-fade-leave-active {
  transition: opacity 0.2s ease;
}
.confirm-fade-enter-active .confirm-card,
.confirm-fade-leave-active .confirm-card {
  transition:
    transform 0.22s ease,
    opacity 0.22s ease;
}
.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}
.confirm-fade-enter-from .confirm-card,
.confirm-fade-leave-to .confirm-card {
  transform: scale(0.96) translateY(8px);
  opacity: 0;
}
</style>

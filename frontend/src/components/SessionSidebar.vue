<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useChatStore } from '../stores/chat'
import { useConfirmStore } from '../stores/confirm'

const chat = useChatStore()
const confirm = useConfirmStore()
const menuOpenId = ref<number | null>(null)
const menuTop = ref(0)
const menuLeft = ref(0)

async function onNew() {
  await chat.createSession()
}

async function pick(id: number) {
  menuOpenId.value = null
  await chat.selectSession(id)
}

function toggleMenu(id: number, event: MouseEvent) {
  if (menuOpenId.value === id) {
    menuOpenId.value = null
    return
  }
  const target = event.currentTarget as HTMLElement | null
  if (!target) return
  const rect = target.getBoundingClientRect()
  const menuWidth = 110
  menuTop.value = rect.bottom + 6
  menuLeft.value = Math.max(8, Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - 8))
  menuOpenId.value = id
}

async function renameSession(id: number, currentTitle: string) {
  menuOpenId.value = null
  const value = window.prompt('请输入新会话名称', currentTitle || '未命名')
  if (value == null) return
  await chat.renameSession(id, value)
}

async function removeSession(id: number) {
  menuOpenId.value = null
  const ok = await confirm.ask({
    title: '删除会话',
    message: '确定删除该会话吗？该操作不可撤销。',
    confirmText: '删除',
    cancelText: '取消',
    danger: true,
  })
  if (!ok) return
  await chat.removeSession(id)
}

function closeMenu() {
  menuOpenId.value = null
}

onMounted(() => {
  window.addEventListener('scroll', closeMenu, true)
  window.addEventListener('resize', closeMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', closeMenu, true)
  window.removeEventListener('resize', closeMenu)
})
</script>

<template>
  <aside class="sidebar">
    <button type="button" class="new-btn" @click="onNew" :disabled="chat.loadingSessions">
      新建会话
    </button>
    <ul class="list">
      <li
        v-for="s in chat.sessions"
        :key="s.id"
        :class="['item', { active: s.id === chat.currentSessionId }]"
        @click="pick(s.id)"
      >
        <div class="row-top">
          <span class="item-title">{{ s.title || '未命名' }}</span>
          <div class="menu-wrap">
            <button type="button" class="menu-btn" @click.stop="toggleMenu(s.id, $event)">⋯</button>
          </div>
        </div>
        <span class="item-time">{{ s.updatedAt?.slice(0, 16).replace('T', ' ') }}</span>
      </li>
    </ul>
    <Teleport to="body">
      <div
        v-if="menuOpenId !== null"
        class="menu-backdrop"
        @click="closeMenu"
      >
        <div
          class="menu floating"
          :style="{ top: `${menuTop}px`, left: `${menuLeft}px` }"
          @click.stop
        >
          <button
            type="button"
            class="link-btn"
            @click.stop="renameSession(menuOpenId, chat.sessions.find(s => s.id === menuOpenId)?.title || '')"
          >
            重命名
          </button>
          <button type="button" class="link-btn danger" @click.stop="removeSession(menuOpenId)">删除</button>
        </div>
      </div>
    </Teleport>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 288px;
  flex-shrink: 0;
  border-right: var(--theme-pane-divider);
  display: flex;
  flex-direction: column;
  background: var(--theme-surface-glass);
  backdrop-filter: var(--theme-blur);
  -webkit-backdrop-filter: var(--theme-blur);
}
.new-btn {
  margin: 16px 14px 12px;
  padding: 11px 16px;
  border-radius: 999px;
  border: none;
  background: var(--theme-primary-gradient);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 4px 18px var(--theme-btn-glow);
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
}
.new-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 22px var(--theme-btn-glow-strong);
}
.new-btn:disabled {
  opacity: 0.5;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0 10px 20px;
  overflow-y: auto;
}
.item {
  padding: 12px 12px 10px;
  border-radius: var(--theme-radius);
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.item:not(:last-child) {
  border-bottom-color: var(--theme-session-divider);
}
.item:hover {
  background: color-mix(in srgb, var(--theme-accent) 10%, transparent);
  border-color: var(--theme-border);
}
.item:hover:not(:last-child) {
  border-bottom-color: var(--theme-session-divider);
}
.item.active {
  background: color-mix(in srgb, var(--theme-accent) 18%, transparent);
  border-color: var(--theme-accent-glow);
  box-shadow: 0 0 28px color-mix(in srgb, var(--theme-accent) 12%, transparent);
}
.item.active:not(:last-child) {
  border-bottom-color: var(--theme-session-divider);
}
.item-title {
  display: block;
  font-size: var(--app-font-size, 14px);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.row-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.menu-wrap {
  margin-left: auto;
  position: relative;
}
.menu-btn {
  border: none;
  background: transparent;
  color: var(--theme-text-muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
  border-radius: 6px;
}
.menu-btn:hover {
  color: var(--theme-text);
  background: var(--theme-link-hover-bg);
}
.menu {
  position: absolute;
  min-width: 88px;
  background: var(--theme-surface-card-solid);
  border: 1px solid var(--theme-border-strong);
  border-radius: 12px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 10;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.menu.floating {
  position: fixed;
  min-width: 110px;
  z-index: 1001;
}
.menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
}
.link-btn {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--theme-text);
  font-size: 12px;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
}
.link-btn:hover {
  background: color-mix(in srgb, var(--theme-accent) 14%, transparent);
}
.link-btn.danger:hover {
  background: rgba(244, 33, 46, 0.12);
  color: #f87171;
}
.item-time {
  font-size: calc(var(--app-font-size, 14px) - 4px);
  color: var(--theme-text-muted);
  margin-top: 4px;
  display: block;
}
</style>

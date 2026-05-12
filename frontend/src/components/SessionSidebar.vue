<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useChatStore } from '../stores/chat'

const chat = useChatStore()
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
  if (!window.confirm('确定删除该会话吗？该操作不可撤销。')) return
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
  width: 280px;
  border-right: 1px solid #2f3336;
  display: flex;
  flex-direction: column;
  background: #000;
}
.new-btn {
  margin: 12px;
  padding: 10px 12px;
  border-radius: 999px;
  border: none;
  background: #1d9bf0;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}
.new-btn:disabled {
  opacity: 0.5;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0 8px 16px;
  overflow-y: auto;
}
.item {
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  margin-bottom: 4px;
}
.item:hover {
  background: #16181c;
}
.item.active {
  background: #16181c;
  outline: 1px solid #38444d;
}
.item-title {
  display: block;
  font-size: 15px;
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
  color: #9ba1a6;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
}
.menu-btn:hover {
  color: #e7e9ea;
}
.menu {
  position: absolute;
  min-width: 88px;
  background: #0f1419;
  border: 1px solid #38444d;
  border-radius: 10px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  z-index: 10;
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
  color: #d0d5d9;
  font-size: 12px;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 8px;
}
.link-btn:hover {
  background: #1c232b;
}
.link-btn.danger:hover {
  background: #2a1114;
  color: #f4212e;
}
.item-time {
  font-size: 12px;
  color: #71767b;
}
</style>

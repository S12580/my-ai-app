<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'

const chat = useChatStore()
const bottom = ref<HTMLElement | null>(null)

watch(
  () => chat.messages.length + chat.messages.map((m) => m.content).join('').length,
  async () => {
    await nextTick()
    bottom.value?.scrollIntoView({ behavior: 'smooth' })
  },
)

async function onDeleteMessage(id: number) {
  if (!window.confirm('确定删除这条消息吗？')) return
  await chat.removeMessage(id)
}
</script>

<template>
  <div class="messages">
    <div v-if="!chat.currentSessionId" class="empty">请选择或新建会话</div>
    <template v-else>
      <div
        v-for="m in chat.messages"
        :key="m.id + '-' + m.role"
        :class="['bubble', m.role]"
      >
        <div class="bubble-head">
          <div class="role">{{ m.role === 'user' ? '你' : '助手' }}</div>
          <div class="menu-wrap">
            <button type="button" class="menu-btn" @click.stop>⋯</button>
            <div class="menu" @click.stop>
              <button type="button" class="menu-item danger" @click.stop="onDeleteMessage(m.id)">删除</button>
            </div>
          </div>
        </div>
        <div class="text">{{ m.content }}</div>
      </div>
      <div ref="bottom" />
    </template>
  </div>
</template>

<style scoped>
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.empty {
  margin: auto;
  color: #71767b;
}
.bubble {
  max-width: min(720px, 92%);
  padding: 12px 14px;
  border-radius: 16px;
  line-height: 1.5;
}
.bubble.user {
  align-self: flex-end;
  background: #1d9bf0;
  color: #fff;
}
.bubble.assistant {
  align-self: flex-start;
  background: #16181c;
  border: 1px solid #38444d;
}
.role {
  font-size: 12px;
  opacity: 0.85;
  margin-bottom: 6px;
}
.bubble-head {
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
  color: inherit;
  opacity: 0.65;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  padding: 0 4px;
}
.menu-btn:hover {
  opacity: 1;
}
.menu {
  position: absolute;
  top: 22px;
  right: 0;
  min-width: 72px;
  background: #0f1419;
  border: 1px solid #38444d;
  border-radius: 8px;
  padding: 4px;
  z-index: 10;
  display: none;
}
.menu-wrap:hover .menu,
.menu-wrap:focus-within .menu {
  display: block;
}
.menu-item {
  width: 100%;
  border: none;
  background: transparent;
  color: #d0d5d9;
  text-align: left;
  font-size: 12px;
  border-radius: 6px;
  padding: 6px 8px;
  cursor: pointer;
}
.menu-item:hover {
  background: #1c232b;
}
.menu-item.danger:hover {
  background: #2a1114;
  color: #f4212e;
}
.text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

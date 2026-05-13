<script setup lang="ts">
import { onMounted } from 'vue'
import { useChatStore } from '../stores/chat'
import SessionSidebar from '../components/SessionSidebar.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'

const chat = useChatStore()

onMounted(async () => {
  await chat.loadRagDocuments()
  await chat.refreshSessions()
  if (chat.sessions.length === 0) {
    await chat.createSession()
  } else if (chat.currentSessionId == null) {
    await chat.selectSession(chat.sessions[0].id)
  }
})
</script>

<template>
  <div class="layout">
    <SessionSidebar />
    <main class="main">
      <header class="toolbar">
        <span class="title">{{ chat.currentSession?.title || '新会话' }}</span>
        <div class="toolbar-right">
          <label class="stream-toggle">
            <input type="checkbox" v-model="chat.ragMode" />
            RAG 模式
          </label>
          <label class="stream-toggle">
            <input type="checkbox" v-model="chat.streamMode" />
            流式输出
          </label>
        </div>
      </header>
      <MessageList />
      <Composer />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: calc(100vh - var(--theme-nav-h));
  background: transparent;
  color: var(--theme-text);
  font-family: var(--app-font-family);
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
  overflow: hidden;
  background: var(--theme-main-backdrop);
  backdrop-filter: saturate(130%) blur(14px);
  -webkit-backdrop-filter: saturate(130%) blur(14px);
  box-shadow: var(--theme-main-pane-inset);
}
.main::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  background:
    radial-gradient(44% 34% at 16% 14%, color-mix(in srgb, var(--theme-accent) 10%, transparent), transparent 78%),
    radial-gradient(34% 26% at 82% 10%, color-mix(in srgb, var(--theme-glow-violet) 50%, transparent), transparent 80%),
    radial-gradient(36% 28% at 70% 88%, color-mix(in srgb, var(--theme-glow-cyan) 55%, transparent), transparent 82%);
  opacity: 0.45;
}
.main::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  opacity: 0.08;
  background:
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--theme-text) 8%, transparent) 0%,
      transparent 36%
    ),
    radial-gradient(120% 80% at 50% 100%, color-mix(in srgb, var(--theme-bg-base) 18%, transparent), transparent 70%);
}
.main > * {
  position: relative;
  z-index: 1;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--theme-border);
  background: var(--theme-toolbar-bg);
}
.title {
  font-weight: 600;
  font-size: 15px;
  letter-spacing: 0.02em;
}
.stream-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--theme-text-muted);
  cursor: pointer;
  user-select: none;
}
.stream-toggle input {
  accent-color: var(--theme-accent);
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
</style>


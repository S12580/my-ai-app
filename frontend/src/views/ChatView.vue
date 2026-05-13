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
  font-family: system-ui, sans-serif;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--theme-main-backdrop);
  backdrop-filter: saturate(130%) blur(14px);
  -webkit-backdrop-filter: saturate(130%) blur(14px);
  box-shadow: var(--theme-main-pane-inset);
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

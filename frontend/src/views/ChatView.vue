<script setup lang="ts">
import { onMounted } from 'vue'
import { useChatStore } from '../stores/chat'
import SessionSidebar from '../components/SessionSidebar.vue'
import MessageList from '../components/MessageList.vue'
import Composer from '../components/Composer.vue'

const chat = useChatStore()

onMounted(async () => {
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
        <label class="stream-toggle">
          <input type="checkbox" v-model="chat.streamMode" :disabled="chat.sending" />
          流式输出
        </label>
      </header>
      <MessageList />
      <Composer />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  background: #0f1419;
  color: #e7e9ea;
  font-family: system-ui, sans-serif;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #2f3336;
}
.title {
  font-weight: 600;
}
.stream-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #71767b;
}
</style>

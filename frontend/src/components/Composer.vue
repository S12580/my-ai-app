<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from '../stores/chat'

const chat = useChatStore()
const text = ref('')

async function submit() {
  const t = text.value
  if (!t.trim() || chat.sending) return
  text.value = ''
  await chat.send(t)
}
</script>

<template>
  <footer class="composer">
    <textarea
      v-model="text"
      rows="3"
      placeholder="输入消息，Enter 发送，Shift+Enter 换行"
      :disabled="chat.sending || chat.currentSessionId == null"
      @keydown.enter.exact.prevent="submit"
    />
    <button type="button" class="send" :disabled="chat.sending || chat.currentSessionId == null" @click="submit">
      {{ chat.sending ? '发送中…' : '发送' }}
    </button>
  </footer>
</template>

<style scoped>
.composer {
  padding: 12px 16px 20px;
  border-top: 1px solid #2f3336;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: #000;
}
textarea {
  flex: 1;
  resize: none;
  border-radius: 12px;
  border: 1px solid #38444d;
  background: #16181c;
  color: #e7e9ea;
  padding: 12px;
  font: inherit;
}
textarea:focus {
  outline: 2px solid #1d9bf0;
  border-color: transparent;
}
.send {
  padding: 12px 20px;
  border-radius: 999px;
  border: none;
  background: #1d9bf0;
  color: #fff;
  font-weight: 600;
  cursor: pointer;
  align-self: stretch;
}
.send:disabled {
  opacity: 0.5;
}
</style>

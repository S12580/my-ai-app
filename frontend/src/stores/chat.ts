import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<api.Session[]>([])
  const currentSessionId = ref<number | null>(null)
  const messages = ref<api.Message[]>([])
  const loadingSessions = ref(false)
  const sending = ref(false)
  const streamMode = ref(true)

  const currentSession = computed(() =>
    sessions.value.find((s) => s.id === currentSessionId.value) ?? null,
  )

  async function refreshSessions() {
    loadingSessions.value = true
    try {
      const page = await api.listSessions(0, 100)
      sessions.value = page.content
    } finally {
      loadingSessions.value = false
    }
  }

  async function createSession() {
    const s = await api.createSession()
    sessions.value = [s, ...sessions.value.filter((x) => x.id !== s.id)]
    await selectSession(s.id)
    return s
  }

  async function selectSession(id: number) {
    currentSessionId.value = id
    messages.value = await api.listMessages(id)
  }

  async function maybeSetTitleFromFirstMessage(content: string) {
    const sid = currentSessionId.value
    if (sid == null) return
    const s = sessions.value.find((x) => x.id === sid)
    if (s && (!s.title || s.title.trim() === '')) {
      const title = content.slice(0, 30) + (content.length > 30 ? '…' : '')
      const updated = await api.patchSessionTitle(sid, title)
      const idx = sessions.value.findIndex((x) => x.id === sid)
      if (idx >= 0) sessions.value[idx] = updated
    }
  }

  async function send(content: string) {
    const sid = currentSessionId.value
    if (sid == null || !content.trim()) return

    sending.value = true
    try {
      await maybeSetTitleFromFirstMessage(content.trim())

      if (!streamMode.value) {
        const res = await api.sendMessageSync(sid, content.trim())
        messages.value.push(res.userMessage, res.assistantMessage)
        await refreshSessions()
        return
      }

      const userMsg: api.Message = {
        id: -Date.now(),
        sessionId: sid,
        role: 'user',
        content: content.trim(),
        createdAt: new Date().toISOString(),
      }
      const assistantPlaceholder: api.Message = {
        id: -(Date.now() + 1),
        sessionId: sid,
        role: 'assistant',
        content: '',
        createdAt: new Date().toISOString(),
      }
      messages.value.push(userMsg, assistantPlaceholder)

      await api.sendMessageStream(sid, content.trim(), undefined, undefined, (ev) => {
        if (ev.type === 'delta') {
          const last = messages.value[messages.value.length - 1]
          if (last && last.role === 'assistant') {
            last.content += ev.text
          }
        } else if (ev.type === 'done') {
          const last = messages.value[messages.value.length - 1]
          if (last && last.role === 'assistant') {
            last.id = ev.assistantMessageId
          }
        } else if (ev.type === 'error') {
          const last = messages.value[messages.value.length - 1]
          if (last && last.role === 'assistant') {
            last.content += '\n\n[错误] ' + ev.message
          }
        }
      })

      await refreshSessions()
      if (currentSessionId.value === sid) {
        messages.value = await api.listMessages(sid)
      }
    } finally {
      sending.value = false
    }
  }

  async function renameSession(id: number, title: string) {
    const next = title.trim()
    if (!next) return
    const updated = await api.patchSessionTitle(id, next)
    const idx = sessions.value.findIndex((s) => s.id === id)
    if (idx >= 0) sessions.value[idx] = updated
  }

  async function removeSession(id: number) {
    await api.deleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSessionId.value === id) {
      messages.value = []
      if (sessions.value.length > 0) {
        await selectSession(sessions.value[0].id)
      } else {
        currentSessionId.value = null
      }
    }
  }

  async function removeMessage(messageId: number) {
    const sid = currentSessionId.value
    if (sid == null) return
    await api.deleteMessage(sid, messageId)
    messages.value = messages.value.filter((m) => m.id !== messageId)
    await refreshSessions()
  }

  return {
    sessions,
    currentSessionId,
    messages,
    loadingSessions,
    sending,
    streamMode,
    currentSession,
    refreshSessions,
    createSession,
    selectSession,
    renameSession,
    removeSession,
    removeMessage,
    send,
  }
})

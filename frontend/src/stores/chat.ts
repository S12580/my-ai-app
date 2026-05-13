import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../api/chat'
import { isLikelyImageAttachment } from '../utils/chatAttachments'

function revokeMessageBlobUrls(msgs: api.Message[]) {
  for (const m of msgs) {
    for (const x of m._localAttachmentExtracts ?? []) {
      if (x.objectUrl) URL.revokeObjectURL(x.objectUrl)
    }
  }
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<api.Session[]>([])
  const currentSessionId = ref<number | null>(null)
  const messages = ref<api.Message[]>([])
  const loadingSessions = ref(false)
  /** 调用 analyze 接口：图片仅上传占位；PDF/文本仍会抽取正文 */
  const analyzingAttachments = ref(false)
  /** 已提交，等待助手同步或流式回复结束 */
  const waitingAssistantReply = ref(false)
  const sending = computed(
    () => analyzingAttachments.value || waitingAssistantReply.value,
  )
  const sendButtonLabel = computed(() => {
    if (analyzingAttachments.value) return '处理附件中…'
    if (waitingAssistantReply.value) return '回复中…'
    return '发送'
  })
  const streamMode = ref(true)
  const ragMode = ref(false)
  /** All KB document ids for RAG scope (lightweight). */
  const ragDocumentIds = ref<number[]>([])
  const ragLoading = ref(false)

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
    revokeMessageBlobUrls(messages.value)
    currentSessionId.value = id
    messages.value = await api.listMessages(id)
  }

  async function maybeSetTitleFromFirstMessage(seed: string) {
    const sid = currentSessionId.value
    if (sid == null) return
    const s = sessions.value.find((x) => x.id === sid)
    if (s && (!s.title || s.title.trim() === '')) {
      const title = seed.slice(0, 30) + (seed.length > 30 ? '…' : '')
      const updated = await api.patchSessionTitle(sid, title)
      const idx = sessions.value.findIndex((x) => x.id === sid)
      if (idx >= 0) sessions.value[idx] = updated
    }
  }

  async function send(content: string, attachmentFiles?: File[]) {
    const sid = currentSessionId.value
    const trimmed = (content ?? '').trim()
    const files = attachmentFiles?.length
      ? [...attachmentFiles].slice(0, api.MAX_CHAT_ATTACHMENTS_PER_MESSAGE)
      : []
    if (sid == null || (!trimmed && files.length === 0)) return

    try {
      let attachments: api.MessageAttachmentPart[] | undefined
      if (files.length > 0) {
        analyzingAttachments.value = true
        try {
          const analyzed = await api.analyzeChatAttachments(sid, files)
          attachments = analyzed.map((a) => ({
            attachmentId: a.attachmentId,
            fileName: a.fileName,
            mimeType: a.mimeType,
            extractedText: a.extractedText,
          }))
        } finally {
          analyzingAttachments.value = false
        }
      }

      const titleSeed = trimmed || attachments?.[0]?.fileName || '附件消息'
      await maybeSetTitleFromFirstMessage(titleSeed)

      const ragIds = ragDocumentIds.value
      const optimisticUserPreview = trimmed

      waitingAssistantReply.value = true
      try {
        if (!streamMode.value) {
          const res = await api.sendMessageSync(
            sid,
            trimmed,
            undefined,
            undefined,
            ragMode.value,
            ragIds,
            attachments,
          )
          messages.value.push(res.userMessage, res.assistantMessage)
          await refreshSessions()
          return
        }

        const userMsg: api.Message = {
          id: -Date.now(),
          sessionId: sid,
          role: 'user',
          content: optimisticUserPreview,
          createdAt: new Date().toISOString(),
          metaJson:
            attachments?.length ?
              JSON.stringify({
                attachments: attachments.map((a) => ({
                  attachmentId: a.attachmentId,
                  fileName: a.fileName,
                  mimeType: a.mimeType ?? '',
                })),
              })
            : undefined,
          _localAttachmentExtracts:
            attachments?.map((a, i) => ({
              fileName: a.fileName,
              text: a.extractedText,
              attachmentId: a.attachmentId,
              mimeType: a.mimeType,
              objectUrl:
                files[i] && isLikelyImageAttachment(files[i].type, files[i].name)
                  ? URL.createObjectURL(files[i]!)
                  : undefined,
            })),
        }
        const assistantPlaceholder: api.Message = {
          id: -(Date.now() + 1),
          sessionId: sid,
          role: 'assistant',
          content: '',
          createdAt: new Date().toISOString(),
        }
        messages.value.push(userMsg, assistantPlaceholder)

        await api.sendMessageStream(
          sid,
          trimmed,
          undefined,
          undefined,
          ragMode.value,
          ragIds,
          attachments,
          (ev) => {
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
          },
        )

        await refreshSessions()
        if (currentSessionId.value === sid) {
          revokeMessageBlobUrls(messages.value)
          messages.value = await api.listMessages(sid)
        }
      } finally {
        waitingAssistantReply.value = false
      }
    } catch (e) {
      analyzingAttachments.value = false
      waitingAssistantReply.value = false
      throw e
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
      revokeMessageBlobUrls(messages.value)
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
    const victim = messages.value.find((m) => m.id === messageId)
    if (victim) revokeMessageBlobUrls([victim])
    await api.deleteMessage(sid, messageId)
    messages.value = messages.value.filter((m) => m.id !== messageId)
    await refreshSessions()
  }

  async function loadRagDocuments() {
    ragLoading.value = true
    try {
      ragDocumentIds.value = await api.listRagDocumentIds()
    } finally {
      ragLoading.value = false
    }
  }

  async function uploadRagDocument(file: File) {
    await api.uploadRagDocument(file)
    await loadRagDocuments()
  }

  return {
    sessions,
    currentSessionId,
    messages,
    loadingSessions,
    analyzingAttachments,
    waitingAssistantReply,
    sending,
    sendButtonLabel,
    streamMode,
    ragMode,
    ragDocumentIds,
    ragLoading,
    currentSession,
    refreshSessions,
    createSession,
    selectSession,
    renameSession,
    removeSession,
    removeMessage,
    loadRagDocuments,
    uploadRagDocument,
    send,
  }
})

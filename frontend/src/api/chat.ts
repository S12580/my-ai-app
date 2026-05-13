const BASE = '/api'

/** Must match backend {@code ChatService.MAX_CHAT_ATTACH_FILES}. */
export const MAX_CHAT_ATTACHMENTS_PER_MESSAGE = 3

export interface Session {
  id: number
  title: string
  model: string
  createdAt: string
  updatedAt: string
}

export function chatAttachmentFileUrl(
  sessionId: number,
  messageId: number,
  attachmentId: string,
): string {
  return `${BASE}/sessions/${sessionId}/messages/${messageId}/attachments/${encodeURIComponent(attachmentId)}`
}

export interface Message {
  id: number
  sessionId: number
  role: string
  content: string
  createdAt: string
  metaJson?: string | null
  /** Client-only (e.g. optimistic): bodies for download before message matches persisted shape */
  _localAttachmentExtracts?: {
    fileName: string
    text: string
    attachmentId?: string
    mimeType?: string
    objectUrl?: string
  }[]
}

export interface AnalyzedAttachmentPart {
  attachmentId: string
  fileName: string
  mimeType: string
  extractedText: string
}

export interface MessageAttachmentPart {
  attachmentId: string
  fileName: string
  mimeType?: string
  extractedText: string
}

export interface RagDocument {
  id: number
  name: string
  status: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
}

export interface PageRagDocuments {
  content: RagDocument[]
  totalElements: number
  page: number
  size: number
}

export interface PageSessions {
  content: Session[]
  totalElements: number
  page: number
  size: number
}

export async function listSessions(page = 0, size = 50): Promise<PageSessions> {
  const r = await fetch(`${BASE}/sessions?page=${page}&size=${size}`)
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function createSession(title?: string, model?: string): Promise<Session> {
  const r = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: title ?? '', model }),
  })
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function patchSessionTitle(id: number, title: string): Promise<Session> {
  const r = await fetch(`${BASE}/sessions/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title }),
  })
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function deleteSession(id: number): Promise<void> {
  const r = await fetch(`${BASE}/sessions/${id}`, {
    method: 'DELETE',
  })
  if (!r.ok) throw new Error(await r.text())
}

export async function listMessages(sessionId: number, beforeId?: number, limit = 500): Promise<Message[]> {
  let url = `${BASE}/sessions/${sessionId}/messages?limit=${limit}`
  if (beforeId != null) url += `&beforeId=${beforeId}`
  const r = await fetch(url)
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function deleteMessage(sessionId: number, messageId: number): Promise<void> {
  const r = await fetch(`${BASE}/sessions/${sessionId}/messages/${messageId}`, {
    method: 'DELETE',
  })
  if (!r.ok) throw new Error(await r.text())
}

export async function sendMessageSync(
  sessionId: number,
  content: string,
  model?: string,
  maxTokens?: number,
  useRag?: boolean,
  ragDocumentIds?: number[],
  attachments?: MessageAttachmentPart[],
) {
  const r = await fetch(`${BASE}/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, model, maxTokens, useRag, ragDocumentIds, attachments }),
  })
  if (!r.ok) throw new Error(await r.text())
  return r.json() as Promise<{ userMessage: Message; assistantMessage: Message }>
}

export type StreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'done'; assistantMessageId: number }
  | { type: 'error'; message: string }

export async function sendMessageStream(
  sessionId: number,
  content: string,
  model?: string,
  maxTokens?: number,
  useRag?: boolean,
  ragDocumentIds?: number[],
  attachments?: MessageAttachmentPart[],
  onEvent?: (ev: StreamEvent) => void,
): Promise<void> {
  const r = await fetch(`${BASE}/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, model, maxTokens, useRag, ragDocumentIds, attachments }),
  })
  if (!r.ok) throw new Error(await r.text())
  const reader = r.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      const t = line.trim()
      if (!t) continue
      try {
        const ev = JSON.parse(t) as StreamEvent
        onEvent?.(ev)
      } catch {
        // ignore malformed chunks
      }
    }
  }
  const tail = buffer.trim()
  if (tail) {
    try {
      onEvent?.(JSON.parse(tail) as StreamEvent)
    } catch {
      /* noop */
    }
  }
}

export async function listRagDocuments(page = 0, size = 20): Promise<PageRagDocuments> {
  const r = await fetch(`${BASE}/rag/documents?page=${page}&size=${size}`)
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function listRagDocumentIds(): Promise<number[]> {
  const r = await fetch(`${BASE}/rag/document-ids`)
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function uploadRagDocument(file: File): Promise<RagDocument> {
  const fd = new FormData()
  fd.append('file', file)
  const r = await fetch(`${BASE}/rag/documents`, {
    method: 'POST',
    body: fd,
  })
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

export async function deleteRagDocument(id: number): Promise<void> {
  const r = await fetch(`${BASE}/rag/documents/${id}`, { method: 'DELETE' })
  if (!r.ok) throw new Error(await r.text())
}

export async function analyzeChatAttachments(sessionId: number, files: File[]): Promise<AnalyzedAttachmentPart[]> {
  const fd = new FormData()
  for (const f of files) {
    fd.append('files', f)
  }
  const r = await fetch(`${BASE}/sessions/${sessionId}/attachments/analyze`, {
    method: 'POST',
    body: fd,
  })
  if (!r.ok) throw new Error(await r.text())
  return r.json()
}

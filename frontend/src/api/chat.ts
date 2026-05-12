const BASE = '/api'

export interface Session {
  id: number
  title: string
  model: string
  createdAt: string
  updatedAt: string
}

export interface Message {
  id: number
  sessionId: number
  role: string
  content: string
  createdAt: string
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

export async function sendMessageSync(sessionId: number, content: string, model?: string, maxTokens?: number) {
  const r = await fetch(`${BASE}/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, model, maxTokens }),
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
  onEvent?: (ev: StreamEvent) => void,
): Promise<void> {
  const r = await fetch(`${BASE}/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, model, maxTokens }),
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

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { useChatStore } from '../stores/chat'
import { useConfirmStore } from '../stores/confirm'
import { chatAttachmentFileUrl, type Message } from '../api/chat'
import { isLikelyImageAttachment, userBubbleVisibleText } from '../utils/chatAttachments'

const chat = useChatStore()
const confirm = useConfirmStore()
const bottom = ref<HTMLElement | null>(null)

const imagePreviewUrl = ref<string | null>(null)
const imagePreviewCaption = ref('')
const CHAT_TIME_SEPARATOR_GAP_MS = 5 * 60 * 1000

function openImagePreview(url: string, caption: string) {
  imagePreviewUrl.value = url
  imagePreviewCaption.value = caption
}

function closeImagePreview() {
  imagePreviewUrl.value = null
  imagePreviewCaption.value = ''
}

watch(imagePreviewUrl, (url, _prev, onCleanup) => {
  if (!url) return
  const prevOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'Escape') closeImagePreview()
  }
  document.addEventListener('keydown', onKey)
  onCleanup(() => {
    document.removeEventListener('keydown', onKey)
    document.body.style.overflow = prevOverflow
  })
})

type MetaAtt = { fileName: string; mimeType?: string; attachmentId?: string }

function parseMetaAttachments(metaJson: string | null | undefined): MetaAtt[] | null {
  if (!metaJson) return null
  try {
    const j = JSON.parse(metaJson) as { attachments?: MetaAtt[] }
    const a = j.attachments
    if (!a?.length) return null
    return a
  } catch {
    return null
  }
}

type UserAttachRow = {
  fileName: string
  mimeType: string
  isImage: boolean
  previewUrl?: string
  fileUrl?: string
}

function userAttachmentRows(m: Message): UserAttachRow[] {
  if (m.role !== 'user') return []

  if (m._localAttachmentExtracts?.length) {
    const sid = chat.currentSessionId
    return m._localAttachmentExtracts.map((x) => {
      const mime = x.mimeType ?? ''
      const isImage = isLikelyImageAttachment(mime, x.fileName)
      const persisted =
        sid != null && m.id > 0 && x.attachmentId
          ? chatAttachmentFileUrl(sid, m.id, x.attachmentId)
          : undefined
      return {
        fileName: x.fileName,
        mimeType: mime,
        isImage,
        previewUrl: x.objectUrl ?? (isImage ? persisted : undefined),
        fileUrl: persisted ?? x.objectUrl,
      }
    })
  }

  const meta = parseMetaAttachments(m.metaJson)
  if (!meta?.length) return []

  return meta.map((entry) => {
    const mime = entry.mimeType ?? ''
    const isImage = isLikelyImageAttachment(mime, entry.fileName)
    const sid = chat.currentSessionId
    const persisted =
      sid != null && m.id > 0 && entry.attachmentId
        ? chatAttachmentFileUrl(sid, m.id, entry.attachmentId)
        : undefined
    return {
      fileName: entry.fileName,
      mimeType: mime,
      isImage,
      previewUrl: isImage ? persisted : undefined,
      fileUrl: persisted,
    }
  })
}

function visibleUserText(m: Message): string {
  if (m.role !== 'user') return m.content
  if (m._localAttachmentExtracts?.length) return (m.content ?? '').trimEnd()
  return userBubbleVisibleText(m.content)
}

function bubbleTimeText(raw: string | null | undefined): string {
  const t = (raw ?? '').trim()
  if (!t) return ''
  if (t.includes('T')) return t.slice(0, 16).replace('T', ' ')
  return t
}

type MessageRenderItem =
  | { kind: 'time'; key: string; text: string }
  | { kind: 'message'; key: string; message: Message }

const renderItems = computed<MessageRenderItem[]>(() => {
  const out: MessageRenderItem[] = []
  let prevTs: number | null = null
  chat.messages.forEach((m, idx) => {
    const ts = Date.parse(m.createdAt ?? '')
    const validTs = Number.isFinite(ts) ? ts : null
    if (
      validTs != null &&
      (prevTs == null || validTs - prevTs >= CHAT_TIME_SEPARATOR_GAP_MS)
    ) {
      out.push({
        kind: 'time',
        key: `t-${m.id}-${idx}-${validTs}`,
        text: bubbleTimeText(m.createdAt),
      })
      prevTs = validTs
    } else if (validTs != null) {
      prevTs = validTs
    }
    out.push({
      kind: 'message',
      key: `m-${m.id}-${idx}-${m.role}`,
      message: m,
    })
  })
  return out
})

watch(
  () =>
    chat.messages.length +
    chat.messages.map((m) => m.content + (m.metaJson ?? '')).join('').length,
  async () => {
    await nextTick()
    bottom.value?.scrollIntoView({ behavior: 'smooth' })
  },
)

async function onDeleteMessage(id: number) {
  const ok = await confirm.ask({
    title: '删除消息',
    message: '确定删除这条消息吗？',
    confirmText: '删除',
    cancelText: '取消',
    danger: true,
  })
  if (!ok) return
  await chat.removeMessage(id)
}

async function onCopyMessage(m: Message) {
  const text =
    m.role === 'user'
      ? visibleUserText(m)
      : (m.content ?? '')
  if (!text.trim()) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', 'true')
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
  }
}
</script>

<template>
  <div class="messages">
    <div v-if="!chat.currentSessionId" class="empty">请选择或新建会话</div>
    <template v-else>
      <template v-for="item in renderItems" :key="item.key">
        <div v-if="item.kind === 'time'" class="chat-time-divider">
          <span>{{ item.text }}</span>
        </div>
        <div
          v-else
          :class="['msg-row', item.message.role]"
        >
        <div :class="['msg-meta', item.message.role]">
          <template v-if="item.message.role !== 'user'">
            <div class="role">AI 助手</div>
            <div v-if="bubbleTimeText(item.message.createdAt)" class="bubble-time-inline">
              {{ bubbleTimeText(item.message.createdAt) }}
            </div>
          </template>
          <div
            v-else-if="bubbleTimeText(item.message.createdAt)"
            class="bubble-time-inline"
          >
            {{ bubbleTimeText(item.message.createdAt) }}
          </div>
        </div>
        <div :class="['bubble', item.message.role]">
          <div class="bubble-head">
          <div class="menu-wrap">
            <button type="button" class="menu-btn" @click.stop>⋯</button>
            <div class="menu" @click.stop>
              <button type="button" class="menu-item" @click.stop="onCopyMessage(item.message)">复制</button>
              <button type="button" class="menu-item danger" @click.stop="onDeleteMessage(item.message.id)">删除</button>
            </div>
          </div>
          </div>
          <div
            v-if="item.message.role === 'user' && userAttachmentRows(item.message).length"
            class="attach-previews"
          >
            <div
              v-for="(row, idx) in userAttachmentRows(item.message)"
              :key="idx + '-' + row.fileName"
              class="attach-item"
            >
              <div class="attach-meta-row">
                <span class="attach-kind-icon" aria-hidden="true">
                  <svg
                    v-if="row.isImage"
                    class="attach-kind-svg"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                  <svg
                    v-else
                    class="attach-kind-svg"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
                <span class="attach-name" :title="row.fileName">{{ row.fileName }}</span>
                <a
                  v-if="row.fileUrl"
                  class="attach-download"
                  :href="row.fileUrl"
                  :download="row.fileName"
                  target="_blank"
                  rel="noopener noreferrer"
                  :title="'下载 ' + row.fileName"
                  @click.stop
                >下载</a>
              </div>
              <button
                v-if="row.isImage && row.previewUrl"
                type="button"
                class="thumb-wrap"
                :title="'点击预览：' + row.fileName"
                @click="openImagePreview(row.previewUrl!, row.fileName)"
              >
                <img :src="row.previewUrl" class="thumb" alt="" />
              </button>
            </div>
          </div>
          <div v-if="item.message.role !== 'user' || visibleUserText(item.message)" class="text">
            {{ item.message.role === 'user' ? visibleUserText(item.message) : item.message.content }}
          </div>
        </div>
      </div>
      </template>
      <div ref="bottom" />
    </template>
    <Teleport to="body">
      <div
        v-if="imagePreviewUrl"
        class="img-lightbox"
        role="dialog"
        aria-modal="true"
        aria-label="图片预览"
        @click.self="closeImagePreview"
      >
        <button type="button" class="lightbox-close" aria-label="关闭" @click="closeImagePreview">
          ×
        </button>
        <img
          :src="imagePreviewUrl"
          class="lightbox-img"
          :alt="imagePreviewCaption"
          @click.stop
        />
        <div v-if="imagePreviewCaption" class="lightbox-caption">{{ imagePreviewCaption }}</div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.chat-time-divider {
  display: flex;
  justify-content: center;
  margin: 2px 0;
}
.chat-time-divider > span {
  font-size: 11px;
  color: var(--theme-text-muted);
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--theme-text) 8%, transparent);
}
.empty {
  margin: auto;
  color: var(--theme-text-muted);
  font-size: 14px;
}
.msg-row {
  max-width: min(720px, 92%);
  display: flex;
  flex-direction: column;
  position: relative;
}
.msg-row.user {
  align-self: flex-end;
}
.msg-row.assistant {
  align-self: flex-start;
}
.msg-meta {
  min-height: 16px;
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
.msg-meta.user {
  justify-content: flex-end;
  padding-right: 8px;
}
.msg-meta.assistant {
  justify-content: flex-start;
  padding-left: 6px;
}
.bubble-time-inline {
  font-size: 11px;
  color: var(--theme-text-muted);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}
.msg-row:hover .bubble-time-inline {
  opacity: 1;
}
.bubble {
  padding: 12px 14px;
  border-radius: var(--theme-radius);
  line-height: 1.55;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.18);
}
.bubble.user {
  background: var(--theme-user-bubble);
  color: #f8fafc;
  border: 1px solid rgba(255, 255, 255, 0.12);
}
.bubble.assistant {
  background: var(--theme-assistant-bubble);
  border: 1px solid var(--theme-border-strong);
  color: var(--theme-text);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}
.role {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1;
  padding: 0;
  border: none;
  background: transparent;
  color: color-mix(in srgb, var(--theme-accent) 72%, var(--theme-text) 28%);
  margin-bottom: 0;
  text-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
}
.msg-meta.assistant .bubble-time-inline {
  margin-left: 8px;
}
.attach-previews {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 10px;
}
.attach-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-radius: 10px;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.attach-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.attach-kind-icon {
  flex-shrink: 0;
  display: inline-flex;
  opacity: 0.9;
}
.attach-kind-svg {
  display: block;
}
.attach-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.attach-download {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.12);
  color: inherit;
  text-decoration: none;
  line-height: 1.2;
}
.attach-download:hover {
  background: rgba(255, 255, 255, 0.2);
}
@media (hover: hover) and (pointer: fine) {
  .attach-download {
    opacity: 0;
    visibility: hidden;
    pointer-events: none;
    transition:
      opacity 0.12s ease,
      visibility 0.12s ease;
  }
  .attach-item:hover .attach-download {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
  }
}
.thumb-wrap {
  display: block;
  padding: 0;
  margin: 0;
  border: none;
  background: transparent;
  cursor: zoom-in;
  border-radius: 10px;
  overflow: hidden;
  max-width: min(210px, 100%);
}
.thumb-wrap:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
.thumb {
  display: block;
  width: 100%;
  max-height: 165px;
  border-radius: 10px;
  object-fit: contain;
  background: rgba(0, 0, 0, 0.15);
}
.img-lightbox {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px 24px;
  background: var(--theme-lightbox-bg);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-sizing: border-box;
}
.lightbox-close {
  position: absolute;
  top: 12px;
  right: 16px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
}
.lightbox-close:hover {
  background: rgba(255, 255, 255, 0.22);
}
.lightbox-img {
  max-width: min(96vw, 1200px);
  max-height: min(88vh, 900px);
  object-fit: contain;
  border-radius: 8px;
}
.lightbox-caption {
  margin-top: 12px;
  font-size: 13px;
  color: var(--theme-text);
  max-width: 90vw;
  text-align: center;
  word-break: break-all;
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
  background: var(--theme-surface-card-solid);
  border: 1px solid var(--theme-border-strong);
  border-radius: 10px;
  padding: 4px;
  z-index: 10;
  display: none;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.35);
}
.menu-wrap:hover .menu,
.menu-wrap:focus-within .menu {
  display: block;
}
.menu-item {
  width: 100%;
  border: none;
  background: transparent;
  color: var(--theme-text);
  text-align: left;
  font-size: 12px;
  border-radius: 6px;
  padding: 6px 8px;
  cursor: pointer;
}
.menu-item:hover {
  background: color-mix(in srgb, var(--theme-accent) 16%, transparent);
}
.menu-item.danger:hover {
  background: rgba(244, 33, 46, 0.12);
  color: #f87171;
}
.text {
  font-size: var(--app-font-size, 14px);
  white-space: pre-wrap;
  word-break: break-word;
  font-family:
    inherit,
    'Apple Color Emoji',
    'Segoe UI Emoji',
    'Segoe UI Symbol',
    'Noto Color Emoji',
    sans-serif;
}
</style>

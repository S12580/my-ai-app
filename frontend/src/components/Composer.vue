<script setup lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useChatStore } from '../stores/chat'
import { MAX_CHAT_ATTACHMENTS_PER_MESSAGE } from '../api/chat'

const CHAT_EMOJI_LIST = (
  '😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 ☺️ 😚 😙 🥲 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 💪 🙏 ✨ 🎉 🔥 💯 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 🙌 👏 🤝 👀 🧠 💭 🗯️ 💤 ⭐ 🌟 ✅ ❌ ❓ ❗️ 💬 🤷 🤦'
).split(' ')

const chat = useChatStore()
const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const taSel = ref({ start: 0, end: 0 })
const emojiOpen = ref(false)
const emojiRootRef = ref<HTMLElement | null>(null)
const imageAttachInput = ref<HTMLInputElement | null>(null)
const fileAttachInput = ref<HTMLInputElement | null>(null)
/** Files to send with the next message only (not knowledge base). */
const pendingChatFiles = ref<File[]>([])

function syncTextareaSelection() {
  const el = textareaRef.value
  if (!el) return
  taSel.value = { start: el.selectionStart, end: el.selectionEnd }
}

function toggleEmojiPanel() {
  if (chat.sending || chat.currentSessionId == null) return
  syncTextareaSelection()
  emojiOpen.value = !emojiOpen.value
}

function insertEmoji(emoji: string) {
  const el = textareaRef.value
  const v = text.value
  const start = el ? taSel.value.start : v.length
  const end = el ? taSel.value.end : v.length
  text.value = v.slice(0, start) + emoji + v.slice(end)
  const pos = start + emoji.length
  taSel.value = { start: pos, end: pos }
  emojiOpen.value = false
  nextTick(() => {
    el?.focus()
    el?.setSelectionRange(pos, pos)
  })
}

function fileKey(f: File) {
  return `${f.name}\0${f.size}\0${f.lastModified}`
}

function mergeIncomingFiles(files: FileList | null | undefined) {
  if (!files?.length) return
  const max = MAX_CHAT_ATTACHMENTS_PER_MESSAGE
  const merged: File[] = [...pendingChatFiles.value]
  const keys = new Set(merged.map(fileKey))
  for (const f of Array.from(files)) {
    if (merged.length >= max) break
    const k = fileKey(f)
    if (keys.has(k)) continue
    keys.add(k)
    merged.push(f)
  }
  pendingChatFiles.value = merged
}

function onImageAttachChange() {
  mergeIncomingFiles(imageAttachInput.value?.files ?? null)
  if (imageAttachInput.value) imageAttachInput.value.value = ''
}

function onFileAttachChange() {
  mergeIncomingFiles(fileAttachInput.value?.files ?? null)
  if (fileAttachInput.value) fileAttachInput.value.value = ''
}

function clearPendingFiles() {
  pendingChatFiles.value = []
}

function removePendingAt(index: number) {
  pendingChatFiles.value = pendingChatFiles.value.filter((_, i) => i !== index)
}

function openImagePicker() {
  imageAttachInput.value?.click()
}

function openDocPicker() {
  fileAttachInput.value?.click()
}

async function submit() {
  const t = text.value
  const files = [...pendingChatFiles.value]
  if ((!t.trim() && files.length === 0) || chat.sending) return
  text.value = ''
  pendingChatFiles.value = []
  await chat.send(t, files.length ? files : undefined)
}

function onDocPointerDown(e: PointerEvent) {
  if (!emojiOpen.value) return
  const t = e.target as Node | null
  if (t && emojiRootRef.value?.contains(t)) return
  emojiOpen.value = false
}

function onDocKeydown(e: KeyboardEvent) {
  if (!emojiOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    emojiOpen.value = false
  }
}

function onDocFocusIn(e: FocusEvent) {
  if (!emojiOpen.value) return
  const t = e.target as Node | null
  if (t && emojiRootRef.value?.contains(t)) return
  emojiOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown, true)
  document.addEventListener('keydown', onDocKeydown, true)
  document.addEventListener('focusin', onDocFocusIn, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onDocKeydown, true)
  document.removeEventListener('focusin', onDocFocusIn, true)
})
</script>

<template>
  <footer class="composer">
    <div
      class="message-box"
      :class="{ disabled: chat.sending || chat.currentSessionId == null }"
    >
      <textarea
        ref="textareaRef"
        v-model="text"
        rows="3"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        :disabled="chat.sending || chat.currentSessionId == null"
        @keydown.enter.exact.prevent="submit"
        @select="syncTextareaSelection"
        @click="syncTextareaSelection"
        @keyup="syncTextareaSelection"
      />
      <div v-if="pendingChatFiles.length" class="pending-attachments">
        <div class="pending-one-line">
          <div class="pending-chips-scroll" aria-label="待发送附件文件名">
            <template v-for="(file, index) in pendingChatFiles" :key="fileKey(file) + '-' + index">
              <span class="pending-file-chip">
                <span class="pending-file-chip-name" :title="file.name">{{ file.name }}</span>
                <button
                  type="button"
                  class="pending-file-remove"
                  :title="'移除「' + file.name + '」'"
                  :aria-label="'移除附件 ' + file.name"
                  @click="removePendingAt(index)"
                >
                  <span aria-hidden="true">×</span>
                </button>
              </span>
              <span v-if="index < pendingChatFiles.length - 1" class="pending-chip-sep" aria-hidden="true">·</span>
            </template>
          </div>
          <div class="pending-tail">
            <span class="pending-count">{{ pendingChatFiles.length }}/{{ MAX_CHAT_ATTACHMENTS_PER_MESSAGE }}</span>
            <button type="button" class="pending-clear-all" aria-label="移除全部附件" @click="clearPendingFiles">
              全部清除
            </button>
          </div>
        </div>
      </div>
      <div class="message-box-footer">
        <div class="footer-start">
          <input
            ref="imageAttachInput"
            type="file"
            class="sr-only"
            multiple
            accept="image/png,image/jpeg,image/gif,image/webp,.png,.jpg,.jpeg,.gif,.webp"
            :disabled="chat.sending || chat.currentSessionId == null"
            @change="onImageAttachChange"
          />
          <input
            ref="fileAttachInput"
            type="file"
            class="sr-only"
            multiple
            accept=".pdf,.md,.txt"
            :disabled="chat.sending || chat.currentSessionId == null"
            @change="onFileAttachChange"
          />
          <button
            type="button"
            class="attach-btn"
            title="添加图片（使用视觉模型回复）"
            aria-label="添加图片"
            :disabled="chat.sending || chat.currentSessionId == null"
            @click="openImagePicker"
          >
            <svg
              class="attach-icon"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
          <button
            type="button"
            class="attach-btn"
            title="添加文件（PDF、Markdown、纯文本）"
            aria-label="添加文件"
            :disabled="chat.sending || chat.currentSessionId == null"
            @click="openDocPicker"
          >
            <svg
              class="attach-icon"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>
          <div ref="emojiRootRef" class="emoji-wrap">
            <button
              type="button"
              class="emoji-toggle"
              title="表情"
              :disabled="chat.sending || chat.currentSessionId == null"
              @click="toggleEmojiPanel"
            >
              😊
            </button>
            <div
              v-if="emojiOpen"
              class="emoji-panel"
              role="listbox"
              aria-label="选择表情"
              @mousedown.prevent
            >
              <button
                v-for="(ch, i) in CHAT_EMOJI_LIST"
                :key="i + '-' + ch"
                type="button"
                class="emoji-cell"
                :aria-label="'插入表情 ' + ch"
                @click="insertEmoji(ch)"
              >
                {{ ch }}
              </button>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="send"
          :disabled="chat.sending || chat.currentSessionId == null || (!text.trim() && pendingChatFiles.length === 0)"
          @click="submit"
        >
          {{ chat.sendButtonLabel }}
        </button>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.composer {
  padding: 14px 20px 22px;
  border-top: 1px solid var(--theme-border);
  background: var(--theme-composer-bg);
  backdrop-filter: saturate(130%) blur(12px);
  -webkit-backdrop-filter: saturate(130%) blur(12px);
  position: relative;
  z-index: 0;
}
.message-box {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface-card);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  /* 允许表情面板上穿出容器，避免被裁切 */
  overflow: visible;
  box-shadow: 0 4px 28px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 2;
}
.message-box:focus-within:not(.disabled) {
  outline: 2px solid var(--theme-accent);
  outline-offset: 0;
  border-color: transparent;
}
.message-box.disabled {
  opacity: 0.6;
}
.message-box textarea {
  width: 100%;
  box-sizing: border-box;
  resize: none;
  border: none;
  background: transparent;
  color: var(--theme-text);
  padding: 14px 14px 10px;
  font: inherit;
  font-family:
    inherit,
    'Apple Color Emoji',
    'Segoe UI Emoji',
    'Segoe UI Symbol',
    'Noto Color Emoji',
    sans-serif;
  min-height: 4.5rem;
}
.message-box textarea:focus {
  outline: none;
}
.message-box textarea::placeholder {
  color: #a1a1aa;
}
.pending-attachments {
  flex-shrink: 0;
  padding: 4px 10px 6px;
  border-top: 1px solid var(--theme-border);
  background: color-mix(in srgb, var(--theme-text) 4%, transparent);
}
.pending-one-line {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 10px;
  min-width: 0;
  width: 100%;
}
.pending-chips-scroll {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px 4px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--theme-text-muted) 45%, transparent) transparent;
}
.pending-chips-scroll::-webkit-scrollbar {
  height: 4px;
}
.pending-chips-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: color-mix(in srgb, var(--theme-text-muted) 40%, transparent);
}
.pending-file-chip {
  display: inline-flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 1px;
  flex-shrink: 0;
  max-width: min(220px, 55vw);
  min-width: 0;
}
.pending-file-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.35;
  color: var(--theme-text);
}
.pending-chip-sep {
  flex-shrink: 0;
  color: var(--theme-text-muted);
  font-size: 12px;
  padding: 0 1px;
  user-select: none;
}
.pending-file-remove {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--theme-text-muted);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}
.pending-file-remove:hover {
  background: color-mix(in srgb, var(--theme-text) 12%, transparent);
  color: var(--theme-text);
}
.pending-tail {
  flex-shrink: 0;
  margin-left: auto;
  display: inline-flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  font-size: 11px;
  color: var(--theme-text-muted);
  white-space: nowrap;
  padding-left: 4px;
}
.pending-count {
  font-variant-numeric: tabular-nums;
}
.pending-clear-all {
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 11px;
  color: var(--theme-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
  white-space: nowrap;
}
.pending-clear-all:hover {
  color: var(--theme-text);
}
.message-box-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  padding: 8px 10px 10px;
  border-top: 1px solid var(--theme-border);
}
.footer-start {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  flex: 1;
  min-width: 0;
}
.footer-start .hint {
  flex-basis: 100%;
}
.emoji-wrap {
  position: relative;
  flex-shrink: 0;
  z-index: 5;
}
.emoji-toggle {
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-control-fill);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  color: var(--theme-text);
}
.emoji-toggle:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-accent) 16%, transparent);
  border-color: var(--theme-accent-glow);
}
.emoji-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.emoji-panel {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  right: auto;
  z-index: 100;
  display: grid;
  grid-template-columns: repeat(8, 2rem);
  gap: 2px;
  padding: 8px;
  max-height: 200px;
  overflow-y: auto;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-surface-card-solid);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
}
.emoji-cell {
  width: 2rem;
  height: 2rem;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 1.25rem;
  line-height: 2rem;
  cursor: pointer;
}
.emoji-cell:hover {
  background: color-mix(in srgb, var(--theme-accent) 16%, transparent);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.attach-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 9px;
  min-width: 38px;
  min-height: 36px;
  border-radius: 10px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-control-fill);
  color: var(--theme-text);
  cursor: pointer;
}
.attach-icon {
  display: block;
  flex-shrink: 0;
}
.attach-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-accent) 14%, transparent);
  border-color: var(--theme-accent-glow);
}
.attach-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.hint {
  font-size: 11px;
  color: var(--theme-text-muted);
  line-height: 1.35;
  flex: 1 1 auto;
  min-width: 140px;
}
.send {
  flex-shrink: 0;
  margin-left: auto;
  position: relative;
  z-index: 1;
  padding: 8px 18px;
  min-height: 38px;
  border-radius: 8px;
  border: none;
  background: var(--theme-primary-gradient);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  align-self: center;
  box-shadow: 0 2px 12px var(--theme-btn-glow);
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
}
.send:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px var(--theme-btn-glow-strong);
}
.send:disabled {
  opacity: 0.5;
}
</style>
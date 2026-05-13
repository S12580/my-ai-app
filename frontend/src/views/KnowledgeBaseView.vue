<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import * as api from '../api/chat'
import { useChatStore } from '../stores/chat'
import { useConfirmStore } from '../stores/confirm'

const chat = useChatStore()
const confirm = useConfirmStore()

const docs = ref<api.RagDocument[]>([])
const loading = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize.value)))
const pageLabel = computed(() => {
  const te = totalElements.value
  if (te === 0) return '第 0 / 0 页'
  const from = page.value * pageSize.value + 1
  const to = Math.min(te, (page.value + 1) * pageSize.value)
  return `第 ${page.value + 1} / ${totalPages.value} 页（${from}–${to} / 共 ${te} 条）`
})

function normalizeStatus(raw: string | undefined | null): string {
  const k = (raw ?? '').toLowerCase().trim()
  if (k === 'ready' || k === 'failed' || k === 'processing') return k
  return 'unknown'
}

function statusLabel(raw: string | undefined | null): string {
  const k = normalizeStatus(raw)
  if (k !== 'unknown') return k
  const t = (raw ?? '').trim()
  return t || 'unknown'
}

async function refresh() {
  loading.value = true
  try {
    let res = await api.listRagDocuments(page.value, pageSize.value)
    totalElements.value = res.totalElements
    const maxPage = res.totalElements === 0 ? 0 : Math.ceil(res.totalElements / pageSize.value) - 1
    if (page.value > maxPage) {
      page.value = maxPage
      res = await api.listRagDocuments(page.value, pageSize.value)
      totalElements.value = res.totalElements
    }
    docs.value = res.content
  } finally {
    loading.value = false
  }
}

function goPrev() {
  if (page.value <= 0) return
  page.value -= 1
  void refresh()
}

function goNext() {
  if (page.value + 1 >= totalPages.value) return
  page.value += 1
  void refresh()
}

function onPageSizeChange() {
  page.value = 0
  void refresh()
}

onMounted(refresh)

function openFilePicker() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onPickFile() {
  const f = fileInput.value?.files?.[0]
  if (!f) return
  uploading.value = true
  try {
    await api.uploadRagDocument(f)
    page.value = 0
    await refresh()
    await chat.loadRagDocuments()
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function onDelete(id: number, name: string) {
  const ok = await confirm.ask({
    title: '删除文档',
    message: `确定从知识库删除「${name}」吗？该操作不可撤销。`,
    confirmText: '删除',
    cancelText: '取消',
    danger: true,
  })
  if (!ok) return
  await api.deleteRagDocument(id)
  await refresh()
  await chat.loadRagDocuments()
}
</script>

<template>
  <div class="kb-page">
    <header class="kb-header">
      <RouterLink class="back-link" to="/" aria-label="返回">
        <svg
          class="back-icon"
          width="22"
          height="22"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          aria-hidden="true"
        >
          <!-- 左折角箭头（返回） -->
          <path
            d="M15 19l-7-7 7-7"
            stroke="currentColor"
            stroke-width="2.25"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </RouterLink>
      <h1>知识库管理</h1>
      <div class="actions">
        <div class="upload-wrap">
          <input
            ref="fileInput"
            type="file"
            class="sr-only"
            accept=".pdf,.md,.txt,.png,.jpg,.jpeg,.gif,.webp"
            :disabled="uploading"
            @change="onPickFile"
          />
          <button
            type="button"
            class="pick-btn"
            :disabled="uploading"
            @click="openFilePicker"
          >
            <span class="pick-icon" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path
                  d="M12 16V4m0 0 4 4m-4-4-4 4M4 20h16"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="pick-label">{{ uploading ? '上传中…' : '选择文件' }}</span>
          </button>
          <span class="pick-meta">PDF、MD、TXT、PNG、JPG…</span>
        </div>
      </div>
    </header>

    <p class="hint">此处上传的文档会入库并用于「聊天」里开启 RAG 模式时的检索；与聊天框内的临时附件无关。</p>

    <div v-if="loading" class="loading-card">
      <span class="spinner" aria-hidden="true" />
      <span>加载文档列表…</span>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="col-name">名称</th>
            <th class="col-status">状态</th>
            <th class="col-time">更新时间</th>
            <th class="col-actions" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="d in docs" :key="d.id">
            <td class="col-name cell-name">{{ d.name }}</td>
            <td class="col-status">
              <div class="status-stack">
                <span :class="['badge', 'badge--' + normalizeStatus(d.status)]">{{ statusLabel(d.status) }}</span>
                <span v-if="d.errorMessage" class="err">{{ d.errorMessage }}</span>
              </div>
            </td>
            <td class="col-time muted">{{ d.updatedAt }}</td>
            <td class="col-actions">
              <button type="button" class="btn-del" @click="onDelete(d.id, d.name)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="docs.length === 0" class="empty-inline">
        <p class="empty-title">暂无文档</p>
        <p class="empty-desc">点击上方「选择文件」上传，处理完成后即可在聊天中开启 RAG 使用。</p>
      </div>
      <footer v-if="!loading && totalElements > 0" class="pager">
        <div class="pager-info">{{ pageLabel }}</div>
        <div class="pager-controls">
          <label class="page-size-label">
            每页
            <select v-model.number="pageSize" class="page-size-select" @change="onPageSizeChange">
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
            </select>
            条
          </label>
          <button type="button" class="pager-btn" :disabled="page <= 0" @click="goPrev">上一页</button>
          <button type="button" class="pager-btn" :disabled="page + 1 >= totalPages" @click="goNext">
            下一页
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.kb-page {
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: transparent;
  color: var(--theme-text);
  padding: 24px 20px 48px;
  font-family: system-ui, sans-serif;
}
@media (min-width: 900px) {
  .kb-page {
    padding: 24px 32px 48px;
  }
}
.kb-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  padding: 16px 20px;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border);
  background: var(--theme-surface-glass);
  backdrop-filter: var(--theme-blur);
  -webkit-backdrop-filter: var(--theme-blur);
}
.kb-header h1 {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 600;
  flex: 1;
  min-width: 140px;
  letter-spacing: 0.02em;
}
.back-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  color: var(--theme-text);
  text-decoration: none;
  border-radius: 10px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-control-fill);
  transition:
    color 0.15s ease,
    background 0.15s ease,
    border-color 0.15s ease;
}
.back-link:hover {
  color: var(--theme-accent);
  border-color: var(--theme-accent-glow);
  background: var(--theme-accent-soft);
}
.back-icon {
  display: block;
}
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}
@media (min-width: 720px) {
  .actions {
    width: auto;
    flex: 0 0 auto;
    margin-left: auto;
  }
}
.upload-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px dashed var(--theme-border-strong);
  background: color-mix(in srgb, var(--theme-accent) 4%, transparent);
}
.pick-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 18px;
  border-radius: 10px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-primary-gradient);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 12px var(--theme-btn-glow);
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease,
    opacity 0.15s ease;
}
.pick-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 18px var(--theme-btn-glow-strong);
}
.pick-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
}
.pick-icon {
  display: flex;
  color: rgba(255, 255, 255, 0.95);
}
.pick-label {
  line-height: 1.2;
}
.pick-meta {
  font-size: 12px;
  color: var(--theme-text-muted);
  line-height: 1.4;
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
.hint {
  color: var(--theme-text-muted);
  font-size: 13px;
  margin-bottom: 22px;
  max-width: 720px;
  line-height: 1.55;
}
.muted {
  color: var(--theme-text-muted);
  font-size: 13px;
}
.loading-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 22px;
  border-radius: var(--theme-radius);
  border: 1px solid var(--theme-border);
  background: var(--theme-surface-glass-heavy);
  color: var(--theme-text-muted);
  font-size: 14px;
}
.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--theme-border-strong);
  border-top-color: var(--theme-accent);
  border-radius: 50%;
  animation: kb-spin 0.7s linear infinite;
}
@keyframes kb-spin {
  to {
    transform: rotate(360deg);
  }
}
.table-wrap {
  position: relative;
  width: 100%;
  border-radius: var(--theme-radius);
  overflow: hidden;
  border: 1px solid var(--theme-border);
  background: var(--theme-surface-glass-heavy);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}
.table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 14px;
}
.table th,
.table td {
  text-align: left;
  padding: 14px 16px;
  border-bottom: 1px solid var(--theme-border);
  vertical-align: top;
}
.table th {
  color: var(--theme-text-muted);
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: var(--theme-table-head-bg);
}
.table tbody tr:hover {
  background: color-mix(in srgb, var(--theme-accent) 6%, transparent);
}
.table tbody tr:last-child td {
  border-bottom: none;
}
.col-name {
  width: 36%;
}
.col-status {
  width: 28%;
}
.col-time {
  width: 24%;
  white-space: nowrap;
}
.col-actions {
  width: 12%;
  text-align: right;
  white-space: nowrap;
}
.cell-name {
  font-weight: 500;
  color: var(--theme-text);
  word-break: break-word;
}
.status-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 999px;
  letter-spacing: 0.02em;
  border: 1px solid transparent;
}
.badge--ready {
  background: var(--kb-status-ready-bg);
  color: var(--kb-status-ready-fg);
  border-color: var(--kb-status-ready-border);
}
.badge--failed {
  background: var(--kb-status-failed-bg);
  color: var(--kb-status-failed-fg);
  border-color: var(--kb-status-failed-border);
}
.badge--processing {
  background: var(--kb-status-processing-bg);
  color: var(--kb-status-processing-fg);
  border-color: var(--kb-status-processing-border);
}
.badge--unknown {
  background: var(--kb-status-unknown-bg);
  color: var(--kb-status-unknown-fg);
  border-color: var(--kb-status-unknown-border);
}
.err {
  display: block;
  font-size: 12px;
  line-height: 1.45;
  color: var(--kb-status-failed-fg);
  max-width: 320px;
}
.btn-del {
  background: transparent;
  border: 1px solid var(--theme-border-strong);
  color: var(--kb-status-failed-fg);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.btn-del:hover {
  background: color-mix(in srgb, var(--kb-status-failed-fg) 12%, transparent);
  border-color: color-mix(in srgb, var(--kb-status-failed-fg) 45%, transparent);
}
.empty-inline {
  padding: 28px 20px 32px;
  text-align: center;
  border-top: 1px solid var(--theme-border);
  background: color-mix(in srgb, var(--theme-text) 3%, transparent);
}
.empty-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
}
.empty-desc {
  margin: 0;
  font-size: 13px;
  color: var(--theme-text-muted);
  line-height: 1.55;
  max-width: 420px;
  margin-inline: auto;
}
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 16px;
  padding: 14px 16px;
  border-top: 1px solid var(--theme-border);
  background: color-mix(in srgb, var(--theme-text) 3%, transparent);
}
.pager-info {
  font-size: 13px;
  color: var(--theme-text-muted);
}
.pager-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 12px;
}
.page-size-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--theme-text-muted);
}
.page-size-select {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-control-fill);
  color: var(--theme-text);
  font-size: 13px;
  cursor: pointer;
}
.pager-btn {
  padding: 7px 14px;
  border-radius: 8px;
  border: 1px solid var(--theme-border-strong);
  background: var(--theme-control-fill);
  color: var(--theme-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}
.pager-btn:hover:not(:disabled) {
  border-color: var(--theme-accent-glow);
  background: color-mix(in srgb, var(--theme-accent) 8%, transparent);
}
.pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>

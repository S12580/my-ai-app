import { defineStore } from 'pinia'

export type ConfirmAskOptions = {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  /** 主按钮使用警示样式（删除等） */
  danger?: boolean
}

export const useConfirmStore = defineStore('confirm', {
  state: () => ({
    visible: false,
    title: '确认',
    message: '',
    confirmText: '确定',
    cancelText: '取消',
    danger: false,
    _resolve: null as null | ((value: boolean) => void),
  }),
  actions: {
    ask(options: ConfirmAskOptions): Promise<boolean> {
      return new Promise((resolve) => {
        this.title = options.title ?? '确认'
        this.message = options.message
        this.confirmText = options.confirmText ?? '确定'
        this.cancelText = options.cancelText ?? '取消'
        this.danger = options.danger ?? false
        this._resolve = resolve
        this.visible = true
      })
    },
    submit(ok: boolean) {
      const r = this._resolve
      this._resolve = null
      this.visible = false
      r?.(ok)
    },
  },
})

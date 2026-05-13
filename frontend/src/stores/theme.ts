import { defineStore } from 'pinia'

export const THEME_IDS = ['aurora', 'ember', 'forest', 'midnight', 'daybreak'] as const
export type ThemeId = (typeof THEME_IDS)[number]

const STORAGE_KEY = 'my-ai-app-theme'

function isThemeId(v: string | null): v is ThemeId {
  return v != null && (THEME_IDS as readonly string[]).includes(v)
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    themeId: 'daybreak' as ThemeId,
  }),
  actions: {
    init() {
      try {
        const raw = localStorage.getItem(STORAGE_KEY)
        if (isThemeId(raw)) this.themeId = raw
      } catch {
        /* ignore */
      }
      this.applyDom()
    },
    setTheme(id: ThemeId) {
      this.themeId = id
      try {
        localStorage.setItem(STORAGE_KEY, id)
      } catch {
        /* ignore */
      }
      this.applyDom()
    },
    applyDom() {
      document.documentElement.setAttribute('data-theme', this.themeId)
    },
  },
})

<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import ThemeSettings from './components/ThemeSettings.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
</script>

<template>
  <div class="app-root">
    <nav class="app-nav">
      <RouterLink class="nav-link" to="/">会话列表</RouterLink>
      <RouterLink class="nav-link" to="/knowledge">知识库管理</RouterLink>
    </nav>
    <RouterView />
    <ThemeSettings />
    <ConfirmDialog />
  </div>
</template>

<style>
.app-root {
  min-height: 100vh;
}
.app-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: var(--theme-nav-h);
  padding: 10px 20px;
  background: var(--theme-app-nav-bg);
  border-bottom: 1px solid var(--theme-border);
  backdrop-filter: var(--theme-blur);
  -webkit-backdrop-filter: var(--theme-blur);
  font-family: var(--app-font-family);
}
.nav-link {
  color: var(--theme-text-muted);
  text-decoration: none;
  font-size: 14px;
  padding: 8px 14px;
  border-radius: 999px;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}
.nav-link:hover {
  color: var(--theme-text);
  background: var(--theme-link-hover-bg);
}
.nav-link.router-link-active {
  color: var(--theme-text);
  font-weight: 600;
  background: var(--theme-accent-soft);
  box-shadow: 0 0 0 1px var(--theme-accent-glow);
}
</style>


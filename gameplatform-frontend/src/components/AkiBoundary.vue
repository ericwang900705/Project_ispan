<script setup>
import { computed } from 'vue'
import { session, connectedFor } from '../api'
import '../style.css'
import '../styles/workspace.css'
import '../styles/member-support-home.css'
import '../styles/publisher.css'
const props = defineProps({ feature: String, label: String, roles: Array })
const ready = computed(() => connectedFor(props.feature))
const allowed = computed(() => props.roles.includes(session.user?.role))
const staff = computed(() => ['ADMIN','SUPPORT','REVIEWER'].includes(session.user?.role))
</script>
<template>
  <section class="aki-module" :class="{ 'publisher-workspace': feature === 'publisher', 'staff-workspace': staff }">
    <div class="support-ui support-page support-scope" :class="staff ? 'staff-ui' : 'member-ui'">
      <section v-if="!ready" class="aki-pending" role="status"><h1>{{ label }}</h1><strong>尚未串接</strong><p>{{ !session.user ? '等待主系統提供登入身分與資料服務。' : '此功能的資料服務尚未串接。' }}</p></section>
      <section v-else-if="!allowed" class="aki-pending" role="alert"><h1>{{ label }}</h1><p>目前身分無權限使用此功能。</p></section>
      <slot v-else :key="session.user.role + ':' + session.user.id" />
    </div>
  </section>
</template>
<style>
.aki-module { --workspace-background: linear-gradient(to bottom,#e7f6ff,#79bceb); background: var(--workspace-background); min-height: 70vh; width: 100%; }
.aki-module .aki-pending { max-width: 820px; margin: auto; padding: 64px 24px; }
.aki-module .aki-pending h1 { margin-bottom: 32px; }
.aki-module .aki-pending strong { display: block; color: #24677f; font-size: 24px; }
.aki-module .aki-pending p { color: #607e90; line-height: 1.8; }
</style>

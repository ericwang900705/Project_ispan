<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { frontendArea, session } from './api'
import { areaForRoute } from './auth-area.js'
import StaffApp from './StaffApp.vue'
import MemberApp from './MemberApp.vue'
import GameWindowShell from './components/GameWindowShell.vue'

const route = useRoute()
const expectedArea = computed(() => areaForRoute(route, session.loginArea, frontendArea))
const staff = computed(() => expectedArea.value === 'staff')
</script>
<template>
    <div v-if="!session.ready || expectedArea !== session.loginArea" role="status" class="session-area-loading">正在確認登入狀態…</div>
    <StaffApp v-else-if="staff" />
    <GameWindowShell v-else-if="route.name === 'PlayGame'" />
    <MemberApp v-else />
</template>

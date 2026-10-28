<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { frontendArea, session } from './api'
import StaffApp from './StaffApp.vue'
import MemberApp from './MemberApp.vue'
import GameWindowShell from './components/GameWindowShell.vue'

const route = useRoute()
const staff = computed(() =>
    frontendArea === 'staff' || route.path.startsWith('/staff') || route.path.startsWith('/admin') || (route.path.startsWith('/tickets') && ['ADMIN', 'STAFF', 'REVIEWER', 'SUPPORT'].includes(session.user?.role))
)
</script>
<template>
    <StaffApp v-if="staff" />
    <GameWindowShell v-else-if="route.name === 'PlayGame'" />
    <MemberApp v-else />
</template>

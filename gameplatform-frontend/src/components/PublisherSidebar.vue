<script setup>
import { useRoute, RouterLink } from 'vue-router'
import { publisherFunctions, publisherGroups, publisherIcons, publisherLink } from '../publisher-functions'
import UiIcon from './UiIcon.vue'
const route = useRoute()
const active = key => key === 'notifications' ? route.path === '/publisher/notifications' : key === 'sales' ? route.path === '/publisher/sales' : route.path === '/publisher/games' && (route.query.mode || 'list') === key
</script>
<template>
  <aside class="publisher-global-sidebar" aria-label="發行商工作區">
    <RouterLink class="publisher-global-home" to="/publisher/overview"><UiIcon name="dashboard" /> <strong>發行商工作區</strong></RouterLink>
    <div v-for="group in publisherGroups" :key="group.title" class="publisher-global-group">
      <h2>{{group.title}}</h2>
      <RouterLink v-for="key in group.modes.filter(x => x !== 'notifications')" :key="key" :to="publisherLink(key)" :class="{active: active(key)}"><UiIcon :name="publisherIcons[key]" /><span>{{ publisherFunctions.find(x => x.mode === key)?.label }}</span></RouterLink>
    </div>
  </aside>
</template>

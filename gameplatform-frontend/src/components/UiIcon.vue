<script setup>
defineProps({ name: { type: String, default: 'chat' } })
const paths = {
  bell: ['M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z', 'M10 21h4'],
  alert: ['M12 3 2 21h20Z', 'M12 9v5M12 17h.01'],
  plus: ['M12 5v14M5 12h14'],
  edit: ['m16 3 5 5-12 12-6 1 1-6ZM14 5l5 5'],
  image: ['M3 3h18v18H3Z', 'M7 8h.01M3 17l6-6 4 4 3-3 5 5'],
  power: ['M12 2v10', 'M6.3 5.5a9 9 0 1 0 11.4 0'],
  dashboard: ['M3 3h7v7H3ZM14 3h7v7h-7ZM3 14h7v7H3ZM14 14h7v7h-7Z'],
  users: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a8 8 0 0 1 16 0v2M19 4a4 4 0 0 1 0 8M22 20v-2a6 6 0 0 0-3-5'],
  cart: ['M2 3h3l3 13h11l3-10H6M9 21h.01M19 21h.01'],
  wallet: ['M3 5h16v16H3ZM3 5V3h14M15 10h6v6h-6ZM17 13h.01'],
  building: ['M3 21h18M5 21V6h14v15M9 21v-5h6v5M8 9h1M15 9h1M8 12h1M15 12h1M8 3h8'],
  tag: ['M3 3h7l11 11-7 7L3 10ZM7 7h.01'],
  shield: ['M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6ZM8 12l3 3 5-6'],
  menu: ['M3 6h18M3 12h18M3 18h18'],
  chevron: ['m8 5 7 7-7 7'],
  trend: ['M3 17 9 11l4 4 8-10M15 5h6v6'],
  chat: ['M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5A8.5 8.5 0 0 1 10.5 3h2a8.5 8.5 0 0 1 8.5 8.5Z', 'M7 10h10M7 14h6'],
  headset: ['M3 13v-1a9 9 0 0 1 18 0v1', 'M3 12h3v8H3a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1ZM21 12h-3v8h3a1 1 0 0 0 1-1v-6a1 1 0 0 0-1-1Z', 'M18 20v1h-6'],
  file: ['M6 2h8l4 4v16H6Z', 'M14 2v5h4M9 11h6M9 15h6M9 19h4'],
  folder: ['M3 5h6l2 3h10v13H3Z'],
  user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M4 22v-2a8 8 0 0 1 16 0v2'],
  game: ['M7 7h10a5 5 0 0 1 4.8 3.7l1.1 5.3a3.3 3.3 0 0 1-5.5 3.2L15 17H9l-3.4 2.2A3.3 3.3 0 0 1 .1 16l1.1-5.3A5 5 0 0 1 7 7Z', 'M6 10v6M3 13h6M16 11h.01M19 14h.01'],
  tree: ['M12 2 6 9h3l-5 6h5l-6 6h18l-6-6h5l-5-6h3Z', 'M12 21v2'],
  check: ['m5 12 4 4L19 6'],
  arrow: ['M4 12h16m-6-6 6 6-6 6'],
  search: ['M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z', 'm15 15 6 6'],
  close: ['m6 6 12 12M18 6 6 18'],
}
</script>
<template>
  <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path v-for="(path, index) in paths[name] || paths.chat" :key="index" :d="path" />
  </svg>
</template>
<style scoped>.ui-icon { width: 22px; height: 22px; flex-shrink: 0; vertical-align: middle; }</style>

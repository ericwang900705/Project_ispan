<script setup>
defineProps({ secondary: { type: String, default: '儲存並留在本頁' }, primary: { type: String, default: '儲存並前往下一步 ▶' }, next: { type: String, required: true }, busy: Boolean })
</script>
<template><footer class="publisher-save-actions"><button type="submit" class="secondary" data-next="" :disabled="busy">{{ busy ? '儲存中…' : secondary }}</button><button type="submit" class="primary" :data-next="next" :disabled="busy">{{ primary }}</button></footer></template>

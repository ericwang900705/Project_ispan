<script setup>
import { ref, watch } from 'vue'
import UiIcon from './UiIcon.vue'
const props = defineProps({ game: { type: Object, required: true } })
const failed = ref(false)
watch(() => props.game.coverUrl, () => { failed.value = false })
</script>
<template>
  <div class="review-cover">
    <img v-if="game.coverUrl && !failed" :src="game.coverUrl" :alt="game.gameName + ' 封面'" @error="failed = true" />
    <div v-else class="cover-placeholder"><UiIcon name="game" /><small>尚無封面</small></div>
  </div>
</template>

<script setup>
import UiIcon from './UiIcon.vue'
defineProps({
  title: String, description: String, eyebrow: String, icon: String,
  count: [Number, String], countLabel: { type: String, default: '筆資料' }
})
</script>
<template>
  <header class="management-heading">
    <div class="management-heading-main">
      <span class="management-heading-icon">
        <UiIcon :name="icon || 'shield'" />
      </span>
      <div>
        <span class="management-eyebrow">{{ eyebrow }}</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
    </div>
    <div v-if="count !== undefined" class="management-heading-count">
      <strong>{{ count }}</strong>
      <span>{{ countLabel }}</span>
    </div>
  </header>
</template>

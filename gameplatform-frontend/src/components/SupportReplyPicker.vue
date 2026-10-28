<script setup>
// 客服範本只帶入草稿，不自動發送訊息。
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { api } from '../api'
const emit = defineEmits(['choose'])
const rows = ref([]),
  selected = ref(null),
  query = ref(''),
  page = ref(0),
  more = ref(false),
  error = ref(''),
  loading = ref(false)
let run = 0
async function load(reset = false) {
  if (reset) page.value = 0
  const id = ++run
  loading.value = true
  error.value = ''
  try {
    const result = await api(
      '/support/reply-templates?' +
        new URLSearchParams({ q: query.value, page: page.value }),
    )
    if (id === run) {
      rows.value = result.items
      more.value = result.hasMore
      selected.value = null
    }
  } catch (e) {
    if (id === run) error.value = e.message
  } finally {
    if (id === run) loading.value = false
  }
}
onMounted(load)
onBeforeUnmount(() => {
  run++
})
</script>
<template>
  <section class="sx-replies" aria-label="客服常用回覆">
    <div class="sx-section-line">
      <strong>常用回覆</strong>
      <form @submit.prevent="load(true)">
        <input
          v-model="query"
          aria-label="搜尋常用回覆"
          placeholder="搜尋範本"
          maxlength="100"
        /><button :disabled="loading">搜尋</button>
      </form>
    </div>
    <p v-if="error" class="sx-error" role="alert">{{ error }}</p>
    <div class="sx-chips">
      <button
        v-for="row in rows"
        :key="row.id"
        type="button"
        :class="{ active: selected?.id === row.id }"
        @click="selected = row"
      >
        {{ row.title }}</button
      ><small v-if="!loading && !rows.length">沒有符合的範本</small>
    </div>
    <div v-if="selected" class="sx-template-preview">
      <span>{{ selected.content }}</span
      ><button type="button" @click="emit('choose', selected.content)">
        加入草稿
      </button>
    </div>
    <div v-if="page || more" class="sx-pager">
      <button
        type="button"
        :disabled="!page || loading"
        @click="page--; load()"
      >
        上一頁</button
      ><button
        type="button"
        :disabled="!more || loading"
        @click="page++; load()"
      >
        下一頁
      </button>
    </div>
  </section>
</template>

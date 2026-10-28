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
    <div v-if="page || more" class="sx-pager unified-pager">
      <button v-if="page > 0"
        type="button"
        :disabled="!page || loading"
        @click="page--; load()"
      >
        上一頁</button
      ><button v-if="more"
        type="button"
        :disabled="!more || loading"
        @click="page++; load()"
      >
        下一頁
      </button>
    </div>
  </section>
</template>

<style>
/* 後台與發行商分頁統一樣式：僅渲染可翻頁的方向，第一頁不顯示上一頁、末頁不顯示下一頁。 */
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 24px auto 12px;
  padding: 8px 12px;
  min-height: 56px;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button {
  appearance: none;
  min-height: 38px;
  min-width: 76px;
  padding: 8px 16px;
  border: 1px solid #c6dcec;
  border-radius: 9px;
  background: rgba(255,255,255,.96);
  color: #305674;
  font-weight: 500;
  box-shadow: none;
  line-height: 1.3;
  cursor: pointer;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:hover:not(:disabled) {
  background: #f0f7ff;
  border-color: #8eb8d3;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:disabled {
  opacity: .5;
  cursor: not-allowed;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > span {
  color: #355673;
  font-size: 14px;
  white-space: nowrap;
}

</style>

<script setup>
// 公開 FAQ 搜尋與分類；只顯示後端已發布內容。
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { api, session } from '../api'
import { useRouter } from 'vue-router'
const props = defineProps({ standalone: Boolean })
const router = useRouter(),
  query = ref(''),
  category = ref(''),
  categories = ref([]),
  rows = ref([]),
  page = ref(0),
  more = ref(false),
  loading = ref(false),
  error = ref(''),
  expanded = ref(null)
let run = 0,
  alive = true
async function load(reset = false) {
  if (reset) page.value = 0
  const id = ++run
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ q: query.value, page: page.value })
    if (category.value) params.set('categoryId', category.value)
    const value = await api('/public/faqs?' + params)
    if (alive && id === run) {
      rows.value = value.items
      more.value = value.hasMore
      expanded.value = value.items[0]?.id ?? null
    }
  } catch (e) {
    if (alive && id === run) error.value = e.message
  } finally {
    if (alive && id === run) loading.value = false
  }
}
function contact() {
  if (session.user) {
    if (props.standalone) router.push('/member/support?chat=1')
    else window.dispatchEvent(new Event('open-chat'))
  } else
    router.push({
      path: '/member/login',
      query: { redirect: '/member/support?chat=1' },
    })
}
onMounted(async () => {
  load()
  try {
    const value = await api('/public/faq-categories')
    if (alive) categories.value = value
  } catch {
    /* FAQ 搜尋仍可使用全部分類。 */
  }
})
onBeforeUnmount(() => {
  alive = false
  run++
})
</script>
<template>
  <section class="support-faq-panel" aria-label="FAQ 常見問題">
    <header class="sx-section-line">
      <div>
        <span class="sx-eyebrow">HELP &amp; SUPPORT</span>
        <h2>需要什麼協助？</h2>
      </div>
      <small>常見問題 FAQ</small>
    </header>
    <form class="sx-search" @submit.prevent="load(true)">
      <input
        v-model="query"
        maxlength="100"
        aria-label="搜尋 FAQ"
        placeholder="搜尋付款、優惠券或帳號問題"
      /><button class="sx-primary" :disabled="loading">搜尋</button>
    </form>
    <nav class="sx-chips" aria-label="FAQ 分類">
      <button
        type="button"
        :class="{ active: !category }"
        @click="category = ''; load(true)"
      >
        全部</button
      ><button
        v-for="item in categories"
        :key="item.id"
        type="button"
        :class="{ active: category === String(item.id) }"
        @click="category = String(item.id); load(true)"
      >
        {{ item.name }}
      </button>
    </nav>
    <p v-if="error" class="sx-error" role="alert">
      {{ error }} <button @click="load()">重試</button>
    </p>
    <p v-if="loading" role="status">正在查詢常見問題…</p>
    <article v-for="row in rows" :key="row.id" class="sx-faq-row">
      <button
        type="button"
        :aria-expanded="expanded === row.id"
        @click="expanded = expanded === row.id ? null : row.id"
      >
        <strong>{{ row.title }}</strong
        ><span>{{ expanded === row.id ? '−' : '＋' }}</span>
      </button>
      <p v-if="expanded === row.id">{{ row.content }}</p>
    </article>
    <p v-if="!loading && !error && !rows.length" class="sx-empty">
      沒有符合的常見問題，可直接聯絡客服。
    </p>
    <div v-if="page || more" class="sx-pager unified-pager">
      <button v-if="page > 0"
        :disabled="!page || loading"
        @click="page--; load()"
      >
        上一頁</button
      ><button v-if="more"
        :disabled="!more || loading"
        @click="page++; load()"
      >
        下一頁
      </button>
    </div>
    <footer class="sx-faq-contact">
      <span>找不到答案？讓客服幫你。</span
      ><button type="button" class="sx-primary" @click="contact">
        聯絡客服 →
      </button>
    </footer>
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

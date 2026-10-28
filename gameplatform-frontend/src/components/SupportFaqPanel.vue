<script setup>
// 公開 FAQ：搜尋已發布資料、切換分類與頁面，不把草稿或後台內容放到前台。
import { ref, onMounted, onBeforeUnmount, useId } from 'vue'
import { api, session } from '../api'
import { useRouter } from 'vue-router'
import UiIcon from './UiIcon.vue'
const props = defineProps({ standalone: Boolean })
const router = useRouter(), prefix = useId()
const query = ref(''), category = ref(''), categories = ref([]), rows = ref([])
const page = ref(0), more = ref(false), loading = ref(false), error = ref(''), expanded = ref(null)
const appliedQuery = ref('')
const shortcuts = [{ label: 'ZIP 下載與更新', term: 'ZIP' }, { label: '退款申請', term: '退款' }, { label: '優惠券', term: '優惠券' }, { label: 'Google 綁定', term: 'Google' }]
let run = 0, alive = true
async function load(reset = false) {
  if (reset) page.value = 0
  const id = ++run, term = query.value.trim()
  loading.value = true; error.value = ''
  try {
    const params = new URLSearchParams({ q: term, page: page.value })
    if (category.value) params.set('categoryId', category.value)
    const value = await api('/public/faqs?' + params)
    if (alive && id === run) {
      rows.value = value.items; more.value = value.hasMore
      expanded.value = value.items[0]?.id ?? null; appliedQuery.value = term
    }
  } catch (e) { if (alive && id === run) error.value = e.message }
  finally { if (alive && id === run) loading.value = false }
}
function clear() { query.value = ''; category.value = ''; load(true) }
function shortcut(term) { query.value = term; category.value = ''; load(true) }
function contact() {
  if (session.user) {
    if (props.standalone) router.push('/member/support?chat=1')
    else window.dispatchEvent(new Event('open-chat'))
  } else router.push({ path: '/member/login', query: { redirect: '/member/support?chat=1' } })
}
onMounted(async () => {
  load()
  try { const value = await api('/public/faq-categories'); if (alive) categories.value = value }
  catch { /* 分類無法載入時，全部問題與文字搜尋仍可使用。 */ }
})
onBeforeUnmount(() => { alive = false; run++ })
</script>
<template>
  <section class="support-faq-panel faq-polished" aria-label="FAQ 常見問題">
    <header class="faq-search-heading"><span class="faq-search-symbol"><UiIcon name="search" /></span><div><h2>想找哪一方面的協助？</h2><p>輸入關鍵字，或從下方分類開始尋找。</p></div></header>
    <form class="faq-search" role="search" @submit.prevent="load(true)">
      <div class="faq-query"><UiIcon name="search" /><label :for="`${prefix}-search`" class="faq-sr-only">搜尋常見問題</label><input :id="`${prefix}-search`" v-model="query" maxlength="100" placeholder="例如：更新 ZIP、退款、付款、Google" :disabled="loading"><button v-if="query" class="faq-input-clear" type="button" aria-label="清空關鍵字" :disabled="loading" @click="query = ''"><UiIcon name="close" /></button></div>
      <button class="faq-search-submit" type="submit" :disabled="loading"><UiIcon name="search" />{{ loading ? '搜尋中…' : '搜尋問題' }}</button>
      <button class="faq-reset" type="button" :disabled="loading || (!query && !category && !appliedQuery)" @click="clear">清除篩選</button>
    </form>
    <div class="faq-shortcuts"><span>熱門搜尋</span><button v-for="item in shortcuts" :key="item.term" type="button" :disabled="loading" @click="shortcut(item.term)">{{ item.label }}</button></div>
    <nav class="faq-categories" aria-label="FAQ 分類"><button type="button" :class="{ active: !category }" :aria-pressed="!category" :disabled="loading" @click="category = ''; load(true)">全部問題</button><button v-for="item in categories" :key="item.id" type="button" :class="{ active: category === String(item.id) }" :aria-pressed="category === String(item.id)" :disabled="loading" @click="category = String(item.id); load(true)">{{ item.name }}</button></nav>
    <div class="faq-results-heading" role="status" aria-live="polite"><strong>{{ appliedQuery ? `「${appliedQuery}」的搜尋結果` : '常見問題列表' }}</strong><span>{{ loading ? '正在搜尋…' : error ? '載入失敗' : `第 ${page + 1} 頁・本頁 ${rows.length} 則` }}</span></div>
    <p v-if="error" class="sx-error" role="alert">{{ error }} <button type="button" class="faq-reset" @click="load()">重新載入</button></p>
    <div v-else class="faq-results" :aria-busy="loading">
      <article v-for="row in rows" :key="row.id" class="sx-faq-row" :class="{ 'is-expanded': expanded === row.id }">
        <button type="button" :id="`${prefix}-question-${row.id}`" :aria-expanded="expanded === row.id" :aria-controls="`${prefix}-answer-${row.id}`" @click="expanded = expanded === row.id ? null : row.id"><span class="faq-question-text"><small v-if="row.categoryName">{{ row.categoryName }}</small><strong>{{ row.title }}</strong></span><span class="faq-expand-symbol" aria-hidden="true">{{ expanded === row.id ? '−' : '＋' }}</span></button>
        <p v-show="expanded === row.id" :id="`${prefix}-answer-${row.id}`" :aria-labelledby="`${prefix}-question-${row.id}`">{{ row.content }}</p>
      </article>
      <div v-if="!loading && !rows.length" class="faq-empty"><UiIcon name="search" /><h3>暫時找不到符合的問題</h3><p>試試較短的關鍵字，或清除分類後重新搜尋。</p><button type="button" class="faq-reset" @click="clear">查看全部問題</button></div>
    </div>
    <nav v-if="page || more" class="faq-pagination" aria-label="常見問題分頁"><button type="button" :disabled="!page || loading" @click="page--; load()">上一頁</button><span>第 {{ page + 1 }} 頁</span><button type="button" :disabled="!more || loading" @click="page++; load()">下一頁</button></nav>
    <footer class="faq-contact"><div><strong>還是沒有找到答案？</strong><p>告訴我們遇到的情況，客服會協助你。</p></div><button type="button" class="faq-contact-button" @click="contact"><UiIcon name="chat" />{{ session.user ? '詢問線上客服' : '登入後聯絡客服' }}<UiIcon name="arrow" /></button></footer>
  </section>
</template>
<style scoped>
.faq-polished { padding: clamp(20px, 3vw, 34px); margin: 26px 0; color: #eaf0ff; border: 1px solid #384c78; border-radius: 22px; background: linear-gradient(135deg,#172447,#101934); box-shadow: 0 16px 48px #05091c2b; }
.faq-search-heading { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }.faq-search-symbol { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 15px; background: #e5b95618; color: #edc976; flex-shrink: 0; }.faq-search-heading h2 { margin: 0; font-size: clamp(20px,2.5vw,25px); color: #f3f6ff; }.faq-search-heading p { margin: 7px 0 0; color: #b9c7e3; font-size: 14px; line-height: 1.6; }
.faq-search { display: flex; align-items: stretch; gap: 10px; }.faq-query { display: flex; flex: 1; min-width: 0; align-items: center; gap: 12px; padding: 0 14px; min-height: 54px; border: 1px solid #546990; border-radius: 12px; background: #0c1630; color: #a5b9dd; box-sizing: border-box; }.faq-query:focus-within { border-color: #e8be67; box-shadow: 0 0 0 3px #e8be6718; }.faq-query input { min-width: 0; flex: 1; width: 100%; padding: 15px 0; border: 0; border-radius: 0; outline: 0; box-shadow: none; background: transparent; color: #f5f7ff; font-size: 15px; }.faq-query input::placeholder { color: #9cafd2; }.faq-query > .ui-icon { width: 20px; }
.faq-polished button { font: inherit; cursor: pointer; transition: background .15s, border-color .15s, color .15s; }.faq-polished button:disabled { opacity: .55; cursor: not-allowed; }.faq-polished button:focus-visible { outline: 2px solid #f4ce82; outline-offset: 3px; }
.faq-search-submit, .faq-contact-button { display: inline-flex; align-items: center; justify-content: center; gap: 8px; min-height: 48px; padding: 12px 22px; border: 1px solid #e7bc66; border-radius: 12px; background: #e7bc66; color: #18213c; font-weight: 750 !important; white-space: nowrap; }.faq-search-submit:hover:not(:disabled), .faq-contact-button:hover { background: #f6d58b; border-color: #f6d58b; }.faq-search-submit .ui-icon, .faq-contact-button .ui-icon { width: 18px; height: 18px; }
.faq-polished .faq-reset { min-height: 44px; padding: 10px 15px; background: #263453; color: #e3ecff; border: 1px solid #54678a; border-radius: 11px; white-space: nowrap; }.faq-polished .faq-reset:hover:not(:disabled) { border-color: #a7bbdf; background: #344567; }.faq-polished .faq-input-clear { display: grid; place-items: center; width: 32px; height: 32px; min-height: 0; padding: 6px; background: transparent; border: 0; color: #c2d0e9; }.faq-input-clear .ui-icon { width: 17px; height: 17px; }
.faq-shortcuts { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin: 14px 0 24px; font-size: 12px; }.faq-shortcuts > span { color: #a9bad8; margin-right: 4px; }.faq-polished .faq-shortcuts button { background: transparent; color: #e5c780; border: 0; min-height: 34px; padding: 5px 9px; border-radius: 8px; font-size: 12px; }.faq-shortcuts button:hover { background: #e9bf6812; text-decoration: underline; }
.faq-categories { display: flex; flex-wrap: wrap; gap: 8px; padding-bottom: 23px; margin-bottom: 0; border-bottom: 1px solid #34486e; }.faq-polished .faq-categories button { padding: 9px 15px; min-height: 40px; border: 1px solid #3f537a; border-radius: 22px; color: #c9d6ee; background: #1a2949; font-size: 13px; }.faq-categories button:hover:not(:disabled) { background: #283c60; border-color: #8297b9; }.faq-polished .faq-categories button.active { background: #e7bc66; border-color: #e7bc66; color: #17203a; font-weight: 750; }
.faq-results-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin: 24px 0 14px; }.faq-results-heading strong { font-size: 15px; color: #f0f4ff; overflow-wrap: anywhere; }.faq-results-heading > span { color: #acbcdb; font-size: 12px; }
.faq-polished .sx-faq-row { border: 1px solid #344b72; border-radius: 13px; background: #111d38; margin-top: 10px; padding: 0 19px; overflow: hidden; }.faq-polished .sx-faq-row.is-expanded { border-color: #a58c58; background: #1c2947; }.faq-polished .sx-faq-row > button { padding: 17px 0; min-height: 66px; color: #f1f4ff; gap: 16px; }.faq-question-text { display: grid; gap: 6px; min-width: 0; }.faq-question-text small { font-size: 11px; color: #e7c47e; font-weight: 500; }.faq-question-text strong { font-size: 15px; font-weight: 650; line-height: 1.65; overflow-wrap: anywhere; }.faq-expand-symbol { display: grid; place-items: center; flex-shrink: 0; width: 29px; height: 29px; border-radius: 50%; background: #d7b76d17; color: #e9c982; }.faq-polished .sx-faq-row > p { color: #d0dcef; font-size: 14px; line-height: 1.95; margin: 0 0 20px; border-top: 1px solid #4d5c76; padding-top: 15px; white-space: pre-wrap; overflow-wrap: anywhere; }
.faq-empty { padding: 35px 18px; text-align: center; color: #b2c3e0; }.faq-empty > .ui-icon { width: 34px; height: 34px; color: #e0bd72; }.faq-empty h3 { color: #edf2ff; margin-bottom: 8px; }.faq-pagination { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 16px; margin: 25px 0; }.faq-pagination span { font-size: 13px; color: #bbcbea; }.faq-polished .faq-pagination button { min-height: 42px; padding: 10px 18px; background: #233456; border: 1px solid #536c99; border-radius: 10px; color: #ecf2ff; }
.faq-contact { display: flex; align-items: center; justify-content: space-between; gap: 20px; border-top: 1px solid #3a4f74; margin-top: 28px; padding-top: 24px; }.faq-contact strong { font-size: 16px; color: #f0f4ff; }.faq-contact p { font-size: 13px; color: #b6c7e2; margin: 7px 0 0; line-height: 1.7; }.faq-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; }
@media(max-width:640px) { .faq-polished { border-radius: 17px; padding: 20px 15px; }.faq-search { display: grid; grid-template-columns: 1fr 1fr; }.faq-query { grid-column: 1 / -1; }.faq-contact { align-items: stretch; flex-direction: column; }.faq-contact-button { width: 100%; }.faq-search-heading { align-items: flex-start; gap: 12px; }.faq-search-symbol { width: 40px; height: 40px; }.faq-query input { font-size: 14px; }.faq-polished .sx-faq-row { padding: 0 14px; } }
@media(prefers-reduced-motion:reduce) { .faq-polished button { transition: none; } }
</style>

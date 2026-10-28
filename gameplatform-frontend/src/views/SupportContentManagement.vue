<script setup>
// 最高管理員維護共用回覆及 FAQ；以版本欄位防止覆蓋他人的更新。
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
const route = useRoute(),
  kind = computed(() => route.meta.supportContent),
  faq = computed(() => kind.value === 'faqs')
const rows = ref([]),
  categories = ref([]),
  query = ref(''),
  state = ref(''),
  page = ref(0),
  more = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  editing = ref(false),
  selected = ref(null)
const form = ref({}),
  states = computed(() =>
    faq.value
      ? [
          { code: 'DRAFT', name: '草稿' },
          { code: 'PUBLISHED', name: '已發布' },
          { code: 'ARCHIVED', name: '已封存' },
        ]
      : [
          { code: 'ACTIVE', name: '啟用' },
          { code: 'INACTIVE', name: '停用' },
        ],
  )
const stateName = (code) =>
  states.value.find((x) => x.code === code)?.name || code
let run = 0,
  alive = true
function edit(row = null) {
  if (busy.value) return
  selected.value = row
  editing.value = true
  error.value = ''
  form.value = {
    title: row?.title || '',
    content: row?.content || '',
    categoryId: row?.categoryId ?? '',
    status: row?.status || (faq.value ? 'DRAFT' : 'INACTIVE'),
    sortOrder: row?.sortOrder ?? 0,
    version: row?.version ?? null,
  }
}
async function load(reset = false) {
  if (reset) page.value = 0
  const id = ++run
  loading.value = true
  error.value = ''
  try {
    const result = await api(
      '/admin/support/' +
        kind.value +
        '?' +
        new URLSearchParams({
          q: query.value,
          status: state.value,
          page: page.value,
        }),
    )
    if (alive && id === run) {
      rows.value = result.items
      more.value = result.hasMore
    }
  } catch (e) {
    if (alive && id === run) error.value = e.message
  } finally {
    if (alive && id === run) loading.value = false
  }
}
async function save() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  const id = run
  try {
    await api(
      '/admin/support/' +
        kind.value +
        (selected.value ? '/' + selected.value.id : ''),
      {
        method: selected.value ? 'PUT' : 'POST',
        body: {
          ...form.value,
          categoryId:
            form.value.categoryId === '' ? null : Number(form.value.categoryId),
          sortOrder: Number(form.value.sortOrder),
        },
      },
    )
    if (alive && id === run) {
      editing.value = false
      notice.value =
        faq.value && form.value.status === 'PUBLISHED'
          ? 'FAQ 已發布，會員可立即查看。'
          : '內容已儲存。'
      await load()
    }
  } catch (e) {
    if (alive && id === run) error.value = e.message
  } finally {
    busy.value = false
  }
}
watch(
  kind,
  async () => {
    editing.value = false
    page.value = 0
    query.value = ''
    state.value = ''
    notice.value = ''
    load()
    try {
      const value = await api('/categories')
      if (alive) categories.value = value
    } catch (e) {
      if (alive) error.value = e.message
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  alive = false
  run++
})
</script>
<template>
  <section class="page sx-management">
    <span class="sx-eyebrow">SUPPORT CONTENT</span>
    <header class="sx-section-line">
      <div>
        <h1>{{ faq ? 'FAQ 常見問題管理' : '客服常用回覆管理' }}</h1>
        <p>
          {{
            faq
              ? '草稿不公開；發布後會員與訪客可立即查詢。'
              : '範本只帶入客服草稿，確認後才會傳送給會員。'
          }}
        </p>
      </div>
      <button type="button" class="sx-primary" :disabled="busy" @click="edit()">
        ＋ {{ faq ? '新增常見問題' : '新增常用回覆' }}
      </button>
    </header>
    <p v-if="error" class="sx-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="sx-notice" role="status">{{ notice }}</p>
    <form class="sx-search" @submit.prevent="load(true)">
      <input
        v-model="query"
        maxlength="100"
        :disabled="busy"
        :aria-label="faq ? '搜尋常見問題' : '搜尋回覆範本'"
        placeholder="搜尋標題或內容"
      /><select v-model="state" :disabled="busy" aria-label="內容狀態">
        <option value="">全部狀態</option>
        <option v-for="item in states" :key="item.code" :value="item.code">
          {{ item.name }}
        </option></select
      ><button :disabled="loading || busy">搜尋</button
      ><button type="button" :disabled="loading || busy" @click="load()">
        重新整理
      </button>
    </form>
    <p v-if="loading">載入中…</p>
    <div class="sx-content-grid" :class="{ 'sx-editing': editing }">
      <div>
        <article v-for="row in rows" :key="row.id" class="sx-resource-card">
          <div class="sx-section-line">
            <strong>{{ row.title }}</strong
            ><span class="sx-status">{{ stateName(row.status) }}</span>
          </div>
          <small
            >{{ row.categoryName || '通用' }} · 排序 {{ row.sortOrder }}</small
          >
          <p class="sx-content-excerpt">{{ row.content }}</p>
          <button type="button" :disabled="busy" @click="edit(row)">
            編輯
          </button>
        </article>
        <p v-if="!loading && !rows.length" class="sx-empty">
          目前沒有符合的內容。
        </p>
        <div class="sx-pager">
          <button
            :disabled="!page || loading || busy"
            @click="page--; load()"
          >
            上一頁</button
          ><span>{{ page + 1 }}</span
          ><button
            :disabled="!more || loading || busy"
            @click="page++; load()"
          >
            下一頁
          </button>
        </div>
      </div>
      <form v-if="editing" class="sx-content-editor" @submit.prevent="save">
        <h2>{{ selected ? '編輯內容' : '新增內容' }}</h2>
        <label
          >{{ faq ? '問題' : '範本名稱'
          }}<input
            v-model.trim="form.title"
            :maxlength="faq ? 200 : 100"
            required
            :disabled="busy" /></label
        ><label
          >分類<select v-model="form.categoryId" :disabled="busy">
            <option value="">通用</option>
            <option v-for="item in categories" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select></label
        ><label
          >{{ faq ? '答案' : '回覆內容'
          }}<textarea
            v-model="form.content"
            rows="8"
            :maxlength="faq ? 8000 : 4000"
            required
            :disabled="busy"
          ></textarea></label
        ><small
          >{{ form.content.length }} /
          {{ faq ? 8000 : 4000 }} 字；以純文字呈現。</small
        >
        <div class="sx-editor-options">
          <label
            >狀態<select
              v-model="form.status"
              :disabled="busy"
              aria-label="狀態"
            >
              <option
                v-for="item in states"
                :key="item.code"
                :value="item.code"
              >
                {{ item.name }}
              </option>
            </select></label
          ><label
            >排序<input
              v-model.number="form.sortOrder"
              type="number"
              min="0"
              max="100000"
              required
              :disabled="busy"
          /></label>
        </div>
        <p v-if="faq && form.status === 'PUBLISHED'" class="sx-warning">
          儲存後會立即更新公開 FAQ。
        </p>
        <div class="sx-pager">
          <button class="sx-primary" :disabled="busy">儲存</button
          ><button type="button" :disabled="busy" @click="editing = false">
            取消
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

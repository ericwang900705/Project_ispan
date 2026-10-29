<script setup>
import WorkspacePager from '../components/WorkspacePager.vue'
// 最高管理員維護共用回覆及 FAQ；以版本欄位防止覆蓋他人的更新。
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
const route = useRoute(),
  kind = computed(() => route.meta.supportContent),
  faq = computed(() => kind.value === 'faqs')
const rows = ref([]),
  categories = ref([]),
  page = ref(0),
  more = ref(false),
  total = ref(null),
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
        page: page.value,
      }),
    )
    if (alive && id === run) {
      rows.value = result.items
      more.value = result.hasMore
      total.value = result.total ?? null
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
  <section class="page sx-management sx-content-workspace">
    <header class="sx-section-line">
      <div>
        <h1>{{ faq ? 'FAQ 常見問題管理' : '客服常用回覆管理' }}</h1>
        <p>
          {{ faq ? '草稿不公開；發布後會員與訪客可立即查詢。' : '範本只帶入客服草稿，確認後才會傳送給會員。' }}
        </p>
      </div>
      <button type="button" class="sx-primary" :disabled="busy" @click="edit()">
        ＋ {{ faq ? '新增常見問題' : '新增常用回覆' }}
      </button>
    </header>
    <p v-if="error" class="sx-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="sx-notice" role="status">{{ notice }}</p>
    <p v-if="loading">載入中…</p>
    <div class="sx-content-grid sx-content-results" :class="{ 'sx-editing': editing }">
      <div class="sx-card-list">
        <div class="sx-list-heading">
          <span>{{ faq ? "常見問題資訊" : "常用回覆資訊" }}</span>
          <span>狀態及操作</span>
        </div>
        <article v-for="row in rows" :key="row.id" class="sx-resource-card">
          <div class="sx-resource-body">
            <strong>{{ row.title }}</strong>
            <small>{{ row.categoryName || '通用' }} · 排序 {{ row.sortOrder }}</small>
            <p class="sx-content-excerpt">{{ row.content }}</p>
          </div>
          <div class="sx-resource-actions">
            <span class="sx-status">{{ stateName(row.status) }}</span>
            <button type="button" :disabled="busy" @click="edit(row)">編輯</button>
          </div>
        </article>
        <p v-if="!loading && !rows.length" class="sx-empty">
          目前尚無內容。
        </p>
      </div>
      <form v-if="editing" class="sx-content-editor" @submit.prevent="save">
        <h2>{{ selected ? '編輯內容' : '新增內容' }}</h2>
        <label>{{ faq ? '問題' : '範本名稱' }}
          <input v-model.trim="form.title" :maxlength="faq ? 200 : 100" required :disabled="busy" />
        </label>
        <label>分類
          <select v-model="form.categoryId" :disabled="busy">
            <option value="">通用</option>
            <option v-for="item in categories" :key="item.id" :value="item.id">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label>{{ faq ? '答案' : '回覆內容' }}
          <textarea v-model="form.content" rows="8" :maxlength="faq ? 8000 : 4000" required :disabled="busy"></textarea>
        </label>
        <small>{{ form.content.length }} / {{ faq ? 8000 : 4000 }} 字；以純文字呈現。</small>
        <div class="sx-editor-options">
          <label>狀態
            <select v-model="form.status" :disabled="busy" aria-label="狀態">
              <option v-for="item in states" :key="item.code" :value="item.code">
                {{ item.name }}
              </option>
            </select>
          </label>
          <label>排序
            <input v-model.number="form.sortOrder" type="number" min="0" max="100000" required :disabled="busy" />
          </label>
        </div>
        <p v-if="faq && form.status === 'PUBLISHED'" class="sx-warning">
          儲存後會立即更新公開 FAQ。
        </p>
        <div class="sx-pager unified-pager">
          <button class="sx-primary" :disabled="busy">儲存</button>
          <button type="button" :disabled="busy" @click="editing = false">
            取消
          </button>
        </div>
      </form>
    </div>
    <WorkspacePager :page="page" :total="total" :size="20" :more="more" :busy="loading || busy" @change="page = $event; load()" />
  </section>
</template>

<style scoped>
/* 比照後台客服案件管理：標題在背景、篩選與清單各自獨立卡片。 */
.sx-content-workspace.page {
  box-sizing: border-box;
  display: block;
  width: 100% !important;
  max-width: none !important;
  min-width: 0;
  padding: 28px clamp(20px, 2vw, 36px) 52px !important;
  margin: 0 !important;
  background: transparent !important;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  color: #25425b;
}

.sx-content-workspace>.sx-section-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  padding: 6px 0 4px;
  margin: 0 0 26px !important;
  background: transparent !important;
  border: 0 !important;
}

.sx-content-workspace>.sx-section-line>div {
  flex: 1 1 320px;
  min-width: 0;
}

.sx-content-workspace>.sx-section-line h1 {
  margin: 0 0 10px;
  font-size: clamp(24px, 2vw, 30px);
  color: #1b3b53;
}

.sx-content-workspace>.sx-section-line p {
  margin: 0;
  color: #617e93;
}

.sx-content-workspace>.sx-section-line button {
  margin-left: auto;
  min-height: 42px;
}

.sx-content-workspace>.sx-content-results {
  display: grid !important;
  grid-template-columns: minmax(0, 1fr) !important;
  gap: 28px;
  width: 100%;
  margin: 0 !important;
  max-width: none !important;
  align-items: start;
}

.sx-content-workspace>.sx-content-results.sx-editing {
  grid-template-columns: minmax(0, 1fr) minmax(340px, .8fr) !important;
}

.sx-content-workspace .sx-card-list {
  box-sizing: border-box;
  min-width: 0;
  display: block;
  overflow: hidden;
  border: 1px solid #d8e7f0;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 25px rgba(35, 75, 106, .09);
}

.sx-content-workspace .sx-list-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px;
  gap: 24px;
  padding: 19px 24px;
  background: #edf4f9;
  color: #52708a;
  font-size: 13px;
  font-weight: 600;
}

.sx-content-workspace .sx-list-heading span:last-child {
  text-align: right;
}

.sx-content-workspace .sx-card-list>.sx-resource-card {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 132px;
  display: grid !important;
  grid-template-columns: minmax(0, 1fr) 160px !important;
  align-items: center;
  gap: 24px;
  padding: 26px 24px !important;
  margin: 0 !important;
  border: 0 !important;
  border-top: 1px solid #dce9f1 !important;
  border-radius: 0 !important;
  background: #fff !important;
  box-shadow: none !important;
}

.sx-content-workspace .sx-resource-body {
  min-width: 0;
}

.sx-content-workspace .sx-resource-body strong {
  display: block;
  font-size: 16px;
  line-height: 1.6;
  margin-bottom: 8px;
  color: #244561;
}

.sx-content-workspace .sx-resource-body small {
  display: block;
  color: #607d92;
  margin: 0 0 10px;
}

.sx-content-workspace .sx-content-excerpt {
  margin: 0 !important;
  line-height: 1.8;
  color: #465f76;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.sx-content-workspace .sx-resource-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 18px;
}

.sx-content-workspace .sx-resource-actions button {
  min-width: 75px;
  min-height: 38px;
  padding: 7px 14px;
}

.sx-content-workspace .sx-card-list>.sx-pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 18px;
  padding: 20px;
  margin: 0 !important;
  border-top: 1px solid #e5edf4;
}

.sx-content-workspace .sx-empty {
  margin: 0;
  padding: 28px 24px;
}

.sx-content-workspace .sx-content-editor {
  box-sizing: border-box;
  min-width: 0;
  padding: 28px !important;
  border: 1px solid #d8e7f0 !important;
  border-radius: 14px;
  background: #f7fbfe !important;
  box-shadow: 0 10px 25px rgba(35, 75, 106, .09);
}

.sx-content-workspace .sx-content-editor label {
  display: flex;
  flex-direction: column;
  gap: 9px;
  margin-bottom: 20px;
}

.sx-content-workspace .sx-content-editor :is(input, select, textarea) {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}

.sx-content-workspace .sx-editor-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

@media(max-width:1120px) {
  .sx-content-workspace>.sx-content-results.sx-editing {
    grid-template-columns: minmax(0, 1fr) !important;
  }
}

@media(max-width:850px) {
  .sx-content-workspace>.sx-filter-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  }
}

@media(max-width:600px) {
  .sx-content-workspace.page {
    padding: 20px 14px 40px !important;
  }

  .sx-content-workspace>.sx-filter-panel {
    grid-template-columns: minmax(0, 1fr) !important;
    padding: 20px !important;
  }

  .sx-content-workspace .sx-list-heading {
    display: none;
  }

  .sx-content-workspace .sx-card-list>.sx-resource-card {
    grid-template-columns: minmax(0, 1fr) !important;
    gap: 18px;
  }

  .sx-content-workspace .sx-resource-actions {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  .sx-content-workspace .sx-editor-options {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

<style>
/* 後台與發行商分頁統一樣式：僅渲染可翻頁的方向，第一頁不顯示上一頁、末頁不顯示下一頁。 */
:where(.management-pagination, .publisher-pagination, .sx-pager, .pager).unified-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 24px auto 12px;
  padding: 8px 12px;
  min-height: 56px;
}

:where(.management-pagination, .publisher-pagination, .sx-pager, .pager).unified-pager>button {
  appearance: none;
  min-height: 38px;
  min-width: 76px;
  padding: 8px 16px;
  border: 1px solid #c6dcec;
  border-radius: 9px;
  background: rgba(255, 255, 255, .96);
  color: #305674;
  font-weight: 500;
  box-shadow: none;
  line-height: 1.3;
  cursor: pointer;
}

:where(.management-pagination, .publisher-pagination, .sx-pager, .pager).unified-pager>button:hover:not(:disabled) {
  background: #f0f7ff;
  border-color: #8eb8d3;
}

:where(.management-pagination, .publisher-pagination, .sx-pager, .pager).unified-pager>button:disabled {
  opacity: .5;
  cursor: not-allowed;
}

:where(.management-pagination, .publisher-pagination, .sx-pager, .pager).unified-pager>span {
  color: #355673;
  font-size: 14px;
  white-space: nowrap;
}
</style>

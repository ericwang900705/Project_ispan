<script setup>
import WorkspacePager from '../components/WorkspacePager.vue'
import { ref, computed, watch, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { api, timeText } from '../api'
import '../styles/admin-management.css'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import UiIcon from '../components/UiIcon.vue'

const route = useRoute()
const historyMode = computed(() => route.query.mode === 'history')
const items = ref([]), keyword = ref(''), status = ref(''), page = ref(0)
const loading = ref(false), busy = ref(false), error = ref(''), message = ref('')
const actionPanel = ref(null)
const selected = ref(null), approve = ref(null), comment = ref('')
const labels = { APPROVED: '已核准', REJECTED: '已退回' }
let generation = 0, alive = true
const filtered = computed(() => {
  const q = keyword.value.trim().toLocaleLowerCase()
  return items.value.filter(row => (!historyMode.value || !status.value || row.status === status.value)
    && (!q || [row.memberId, row.applicationId, row.username, row.companyName, row.taxId, row.email, row.reviewComment, row.reviewerName]
      .some(value => String(value ?? '').toLocaleLowerCase().includes(q))))
})
const visible = computed(() => filtered.value.slice(page.value * 10, (page.value + 1) * 10))
async function load() {
  const run = ++generation
  loading.value = true; selected.value = null
  try {
    const data = await api('/admin/publishers/' + (historyMode.value ? 'history' : 'pending'))
    if (alive && run === generation) { items.value = data; page.value = 0 }
  } catch (e) {
    if (alive && run === generation) { error.value = e.message; items.value = [] }
  } finally { if (alive && run === generation) loading.value = false }
}
async function choose(row, approved = null) {
  selected.value = row; approve.value = approved; comment.value = ''
  error.value = ''; message.value = ''
  await nextTick()
  actionPanel.value?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  actionPanel.value?.focus({ preventScroll: true })
}
async function submit() {
  if (busy.value || !selected.value || historyMode.value || approve.value === null) return
  if (!approve.value && !comment.value.trim()) { error.value = '退回申請請填寫原因'; return }
  const row = selected.value
  busy.value = true; error.value = ''; message.value = ''
  try {
    const result = await api(`/admin/publishers/${row.memberId}/review?isApproved=${approve.value}`, {
      method: 'POST', body: { applicationId: row.applicationId, comment: comment.value.trim() }
    })
    if (!alive) return
    message.value = result.message
    await load()
  } catch (e) {
    if (alive) { error.value = e.message; if (e.status === 409) await load() }
  } finally { if (alive) busy.value = false }
}
watch(() => route.query.mode, () => {
  keyword.value = ''; status.value = ''; page.value = 0; error.value = ''; message.value = ''; items.value = []
  load()
}, { immediate: true })
watch([keyword, status], () => { page.value = 0; selected.value = null })
onBeforeUnmount(() => { alive = false; generation++ })
function clearFilters() { keyword.value = ''; status.value = ''; page.value = 0; selected.value = null }
function website(value) {
  try { const url = new URL(value); return ['https:', 'http:'].includes(url.protocol) ? url.href : null }
  catch { return null }
}
</script>

<template>
  <section class="page admin-management-page">
    <AdminPageHeader :title="historyMode ? '發行商審核紀錄' : '發行商申請審核'" eyebrow="PUBLISHER REVIEW" icon="building"
      :description="historyMode ? '保留每次審核決策，查看處理人員與審核說明。' : '查看公司申請資料，確認資格後核准或退回。'" :count="filtered.length"
      :count-label="historyMode ? '符合條件的紀錄' : '待審核申請'" />
    <nav class="management-tabs" aria-label="發行商管理">
      <RouterLink to="/admin/publishers" :class="{ active: !historyMode }">待審核申請</RouterLink>
      <RouterLink to="/admin/publishers?mode=history" :class="{ active: historyMode }">審核紀錄</RouterLink>
    </nav>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="management-notice" role="status">{{ message }}</p>
    <form class="management-search" @submit.prevent="page = 0">
      <label>搜尋申請<input v-model="keyword" maxlength="100" placeholder="申請編號、會員、公司或統一編號" :disabled="busy"></label>
      <label v-if="historyMode">審核結果<select v-model="status" :disabled="busy">
          <option value="">全部</option>
          <option value="APPROVED">已核准</option>
          <option value="REJECTED">已退回</option>
        </select></label>
      <div class="management-actions"><button type="submit" class="primary"
          :disabled="loading || busy">搜尋</button><button type="button" :disabled="loading || busy"
          @click="clearFilters">清除篩選</button></div>
    </form>
    <p class="muted">共 {{ filtered.length }} 筆{{ historyMode ? '審核紀錄' : '待審核申請' }}。</p>
    <p v-if="loading" role="status">載入中…</p>
    <div class="management-section-heading">
      <h2>{{ historyMode ? '審核紀錄' : '申請清單' }}</h2><span>每頁 10 筆</span>
    </div>
    <div class="management-table-wrap" :aria-busy="loading">
      <table>
        <thead>
          <tr>
            <th>申請／會員</th>
            <th>公司資料</th>
            <th>申請時間</th><template v-if="historyMode">
              <th>結果</th>
              <th>審核說明</th>
              <th>審核人員／時間</th>
            </template>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="row.applicationId">
            <td>
              <div class="management-person"><span class="management-avatar publisher-avatar">
                  <UiIcon name="building" />
                </span>
                <div><strong>{{ row.username }}</strong><small>申請 #{{ row.applicationId }} · 會員 #{{ row.memberId
                }}</small><small v-if="!historyMode">{{ row.email }}</small></div>
              </div>
            </td>
            <td><strong>{{ row.companyName || '—' }}</strong><small>統編：{{ row.taxId || '—' }}</small><a
                v-if="!historyMode && website(row.websiteUrl)" :href="website(row.websiteUrl)" target="_blank"
                rel="noopener noreferrer">公司網站</a></td>
            <td>{{ timeText(row.appliedAt) }}</td>
            <template v-if="historyMode">
              <td><span class="management-badge" :class="{ warning: row.status === 'REJECTED' }">{{ labels[row.status]
                || row.status }}</span></td>
              <td class="management-comment">{{ row.reviewComment || '—' }}</td>
              <td>{{ row.reviewerName || '—' }}<small>{{ timeText(row.reviewedAt) }}</small></td>
            </template>
            <td>
              <div class="management-actions"><button class="action-info" :disabled="loading || busy"
                  @click="choose(row)">{{
                    historyMode ? '查看紀錄' : '查看申請' }}</button><template v-if="!historyMode"><button class="action-success"
                    :disabled="loading || busy" @click="choose(row, true)">核准</button><button class="action-danger"
                    :disabled="loading || busy" @click="choose(row, false)">退回</button></template>
              </div>
            </td>
          </tr>
          <tr v-if="!loading && !visible.length">
            <td :colspan="historyMode ? 7 : 4" class="management-empty">{{ historyMode ? '沒有符合條件的審核紀錄。' :
              '沒有符合條件的待審核申請。' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <WorkspacePager :page="page" :total="filtered.length" :size="10" :busy="loading || busy" @change="page = $event; selected = null" />
    <section v-if="selected" ref="actionPanel" tabindex="-1" class="management-panel publisher-application-detail"
      aria-labelledby="publisher-detail-title">
      <div class="management-section-heading">
        <h2 id="publisher-detail-title">{{ historyMode ? '發行商審核紀錄詳情' : '發行商申請資料' }}</h2><button type="button"
          :disabled="busy" @click="selected = null">關閉</button>
      </div>
      <dl class="publisher-application-facts">
        <div>
          <dt>申請編號</dt>
          <dd>#{{ selected.applicationId }}</dd>
        </div>
        <div>
          <dt>會員帳號／編號</dt>
          <dd>{{ selected.username }}／#{{ selected.memberId }}</dd>
        </div>
        <div>
          <dt>公司名稱</dt>
          <dd>{{ selected.companyName || '—' }}</dd>
        </div>
        <div>
          <dt>統一編號</dt>
          <dd>{{ selected.taxId || '—' }}</dd>
        </div>
        <div v-if="!historyMode">
          <dt>電子信箱</dt>
          <dd>{{ selected.email || '—' }}</dd>
        </div>
        <div v-if="!historyMode">
          <dt>公司網站</dt>
          <dd><a v-if="website(selected.websiteUrl)" :href="website(selected.websiteUrl)" target="_blank"
              rel="noopener noreferrer">{{ selected.websiteUrl }}</a><span v-else>未提供有效網站</span></dd>
        </div>
        <div>
          <dt>申請時間</dt>
          <dd>{{ timeText(selected.appliedAt) }}</dd>
        </div>
        <div>
          <dt>審核狀態</dt>
          <dd>{{ historyMode ? labels[selected.status] || selected.status : '待審核' }}</dd>
        </div>
        <template v-if="historyMode">
          <div>
            <dt>審核人員</dt>
            <dd>{{ selected.reviewerName || '—' }}</dd>
          </div>
          <div>
            <dt>審核時間</dt>
            <dd>{{ timeText(selected.reviewedAt) }}</dd>
          </div>
          <div class="publisher-application-comment">
            <dt>審核說明</dt>
            <dd>{{ selected.reviewComment || '未填寫補充說明' }}</dd>
          </div>
        </template>
      </dl>
      <template v-if="!historyMode">
        <div class="management-actions"><button type="button" class="action-success" :disabled="busy || loading"
            @click="approve = true; comment = ''">核准申請</button><button type="button" class="action-danger"
            :disabled="busy || loading" @click="approve = false; comment = ''">退回申請</button></div>
        <form v-if="approve !== null" class="publisher-review-form" @submit.prevent="submit">
          <h3>{{ approve ? '確認核准發行商資格' : '確認退回申請' }}</h3>
          <label>{{ approve ? '審核說明（選填）' : '退回原因（必填）' }}<textarea v-model="comment" rows="3" maxlength="500"
              :required="!approve" :disabled="busy"></textarea></label>
          <p class="muted">{{ approve ? '核准後，會員可使用發行商功能。' : '退回後會清除會員目前的公司與銀行申請資料，保留申請歷程，會員可重新申請。' }}</p>
          <div class="management-actions"><button type="submit" class="primary"
              :class="approve ? 'action-success' : 'action-danger'" :disabled="busy || loading">{{ busy ? '處理中…' :
                approve ? '確認核准' : '確認退回' }}</button><button type="button" :disabled="busy"
              @click="approve = null; comment = ''">取消審核</button></div>
        </form>
      </template>
    </section>
  </section>
</template>

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

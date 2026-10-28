<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { api, timeText } from '../api'
import '../styles/admin-management.css'
import AdminPageHeader from '../components/AdminPageHeader.vue'

const items = ref([]), total = ref(0), page = ref(0)
const keyword = ref(''), status = ref(''), type = ref('')
const loading = ref(false), busy = ref(false), error = ref(''), message = ref('')
const actionPanel = ref(null)
const selected = ref(null), action = ref(''), hours = ref(24)
let generation = 0, alive = true
async function load(reset = false) {
  if (reset) page.value = 0
  const run = ++generation
  loading.value = true
  error.value = ''
  selected.value = null
  try {
    const query = new URLSearchParams({
      keyword: keyword.value.trim(), status: status.value,
      type: type.value, page: String(page.value), size: '10'
    })
    const data = await api('/admin/members?' + query)
    if (alive && run === generation) { items.value = data.content; total.value = data.totalElements }
  } catch (e) {
    if (alive && run === generation) { error.value = e.message; items.value = []; total.value = 0 }
  } finally { if (alive && run === generation) loading.value = false }
}
async function choose(row, operation) {
  selected.value = row
  action.value = operation
  hours.value = 24
  error.value = ''; message.value = ''
  await nextTick()
  actionPanel.value?.scrollIntoView?.({ behavior: 'smooth', block: 'center' })
  actionPanel.value?.querySelector('input, textarea, button')?.focus()
}
async function submit() {
  if (busy.value || !selected.value) return
  const row = selected.value
  if (action.value === 'suspend' && (!Number.isInteger(Number(hours.value)) || hours.value < 1 || hours.value > 87600)) {
    error.value = '停權時數須為 1 至 87600 的整數'; return
  }
  busy.value = true; error.value = ''; message.value = ''
  try {
    const suffix = action.value === 'suspend' ? '/suspend?hours=' + Number(hours.value) : '/reactivate'
    const result = await api('/admin/members/' + row.memberId + suffix, { method: 'PATCH' })
    if (!alive) return
    message.value = result.message
    await load()
  } catch (e) { if (alive) error.value = e.message }
  finally { if (alive) busy.value = false }
}
onMounted(() => load())
onBeforeUnmount(() => { alive = false; generation++ })
</script>

<template>
  <section class="page admin-management-page">
    <AdminPageHeader title="會員管理" eyebrow="MEMBER MANAGEMENT" icon="users" description="掌握會員狀態，處理帳號停權與恢復使用。"
      :count="total" count-label="符合條件的會員" />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="management-notice" role="status">{{ message }}</p>
    <form class="management-search" @submit.prevent="load(true)">
      <label>搜尋會員<input v-model="keyword" maxlength="100" placeholder="會員編號、帳號或電子信箱" :disabled="busy"></label>
      <label>帳號狀態<select v-model="status" :disabled="busy">
          <option value="">全部</option>
          <option value="ACTIVE">可使用</option>
          <option value="LOCKED">停權／鎖定中</option>
        </select></label>
      <label>會員類型<select v-model="type" :disabled="busy">
          <option value="">全部</option>
          <option value="MEMBER">一般會員</option>
          <option value="PUBLISHER">發行商</option>
        </select></label>
      <button class="primary" :disabled="loading || busy">搜尋</button>
    </form>
    <p class="muted">共 {{ total }} 位會員。鎖定狀態包含管理員停權及登入失敗造成的暫時鎖定。</p>
    <p v-if="loading" role="status">載入中…</p>
    <div class="management-section-heading">
      <h2>會員列表</h2><span>每頁 10 筆</span>
    </div>
    <div class="management-table-wrap" :aria-busy="loading">
      <table>
        <thead>
          <tr>
            <th>會員</th>
            <th>電子信箱</th>
            <th>類型</th>
            <th>狀態／到期時間</th>
            <th>加入時間</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.memberId">
            <td>
              <div class="management-person"><span class="management-avatar">{{ row.username?.slice(0, 1)?.toUpperCase()
                  }}</span>
                <div><strong>{{ row.username }}</strong><small>會員 #{{ row.memberId }}</small></div>
              </div>
            </td>
            <td>{{ row.email }}</td>
            <td>{{ row.isPublisher ? '發行商' : '一般會員' }}<small v-if="row.isPublisher">{{ row.companyName || '—' }}</small>
            </td>
            <td><span class="management-badge" :class="{ warning: row.status === 'LOCKED' }">{{ row.status === 'LOCKED'
              ? '停權／鎖定中' : '可使用' }}</span><small v-if="row.status === 'LOCKED'">{{ timeText(row.lockedUntil)
                }}</small></td>
            <td>{{ timeText(row.createdAt) }}</td>
            <td>
              <div class="management-actions"><button class="action-danger" :disabled="loading || busy"
                  @click="choose(row, 'suspend')">{{ row.status === 'LOCKED' ? '重設停權時間' : '停權' }}</button><button
                  class="action-success" v-if="row.status === 'LOCKED'" :disabled="loading || busy"
                  @click="choose(row, 'reactivate')">恢復使用</button></div>
            </td>
          </tr>
          <tr v-if="!loading && !items.length">
            <td colspan="6" class="management-empty">沒有符合條件的會員。</td>
          </tr>
        </tbody>
      </table>
    </div>
    <nav class="management-pagination" aria-label="會員列表分頁"><button :disabled="page === 0 || loading || busy"
        @click="page--; load()">上一頁</button><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 10))
        }}</span><button :disabled="(page + 1) * 10 >= total || loading || busy" @click="page++; load()">下一頁</button>
    </nav>
    <form v-if="selected" ref="actionPanel" class="management-panel" @submit.prevent="submit">
      <h2>{{ action === 'suspend' ? '確認停權' : '確認恢復使用' }}</h2>
      <p>{{ selected.username }}（#{{ selected.memberId }}）</p>
      <template v-if="action === 'suspend'"><label>停權時數<input v-model.number="hours" type="number" min="1" max="87600"
            step="1" required :disabled="busy"></label>
        <p class="muted">從本次送出時間重新計算停權期限，原有期限會被替換。</p>
      </template>
      <p v-else>解除此會員的鎖定，並清除登入失敗次數。</p>
      <div class="management-actions"><button class="primary"
          :class="action === 'suspend' ? 'action-danger' : 'action-success'" :disabled="busy || loading">{{ busy ?
            '處理中…' :
          '確認送出' }}</button><button type="button" :disabled="busy" @click="selected = null">取消</button></div>
    </form>
  </section>
</template>

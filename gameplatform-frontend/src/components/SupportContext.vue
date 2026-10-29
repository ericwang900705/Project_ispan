<script setup>
// 對話會員的關聯資料；私人備註與審計只從授權的後台 API 載入。
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { api, session } from '../api'
import { hasPermission } from '../permissions'
const props = defineProps({
  scope: { type: String, required: true },
  id: { type: Number, required: true },
  compact: Boolean,
  contextState: String,
})
const staff = computed(
  () =>
    !['MEMBER', 'PUBLISHER'].includes(session.user?.role) &&
    hasPermission(session.user, 'TICKET_HANDLE'),
)
const tab = ref('orders'),
  data = ref({ orders: [], coupons: [], writable: false }),
  error = ref(''),
  loading = ref(false),
  busy = ref(false),
  open = ref(false)
const picker = ref('order'),
  query = ref(''),
  page = ref(0),
  options = ref([]),
  more = ref(false),
  picked = ref(false)
const note = ref(''),
  notes = ref([]),
  logs = ref([]),
  privateMore = ref(false),
  privateLoading = ref(false),
  message = ref('')
let generation = 0,
  optionRun = 0,
  privateRun = 0,
  alive = true
const root = computed(() => `/${props.scope}/${props.id}`)
const visibleTabs = computed(() =>
  staff.value
    ? [
      { key: 'orders', label: '訂單' },
      { key: 'coupons', label: '優惠券' },
      { key: 'notes', label: '內部備註' },
      { key: 'logs', label: '操作紀錄' },
    ]
    : [
      { key: 'orders', label: '訂單' },
      { key: 'coupons', label: '優惠券' },
      { key: 'logs', label: '處理進度' },
    ],
)
const status = (value) =>
  ({
    PENDING: '待付款',
    PAID: '已付款',
    CANCELLED: '已取消',
    REFUNDED: '已退款',
    FAILED: '付款失敗',
  })[value] ||
  value ||
  '—'
const money = (value) => Number(value || 0).toLocaleString('zh-TW')
const date = (value) =>
  value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
const discount = (c) =>
  c.discountType === 'RATE'
    ? `${Number((Number(c.discountValue) * 10).toFixed(1))} 折`
    : `折 NT$${money(c.discountValue)}`
const scopeText = (c) =>
  c.scopeType === 'PUBLISHER'
    ? `${c.publisherName}旗下遊戲`
    : c.scopeType === 'TAG'
      ? `${c.tagName}類型遊戲`
      : '全平台遊戲'
async function load() {
  const run = ++generation
  loading.value = true
  error.value = ''
  try {
    const value = await api(root.value + '/related-data')
    if (alive && run === generation) data.value = value
  } catch (e) {
    if (alive && run === generation) error.value = e.message
  } finally {
    if (alive && run === generation) loading.value = false
  }
}
async function loadOptions(reset = false) {
  if (reset) page.value = 0
  const run = ++optionRun
  error.value = ''
  picked.value = true
  try {
    const value = await api(
      root.value +
      `/${picker.value}-options?` +
      new URLSearchParams({ q: query.value, page: page.value }),
    )
    if (alive && run === optionRun) {
      options.value = value.items
      more.value = value.hasMore
    }
  } catch (e) {
    if (alive && run === optionRun) error.value = e.message
  } finally {
    if (alive && run === optionRun) picked.value = false
  }
}
function choose(kind) {
  picker.value = kind
  query.value = ''
  options.value = []
  open.value = true
  loadOptions(true)
}
function linked(row) {
  return picker.value === 'order'
    ? data.value.orders.some((x) => x.orderId === row.orderId)
    : data.value.coupons.some((x) => x.couponId === row.couponId)
}
async function change(kind, target, remove = false) {
  if (busy.value) return
  const run = generation
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await api(root.value + `/${kind}-links` + (remove ? '/' + target : ''), {
      method: remove ? 'DELETE' : 'POST',
      body: remove ? undefined : { [kind + 'Id']: target },
    })
    if (alive && run === generation) {
      message.value = remove
        ? '已移除客服關聯'
        : '已關聯，不會變更訂單或優惠券額度'
      await load()
    }
  } catch (e) {
    if (alive && run === generation) error.value = e.message
  } finally {
    busy.value = false
  }
}
async function loadPrivate(append = false) {
  if (!['notes', 'logs'].includes(tab.value)) return
  const selectedTab = tab.value,
    list = selectedTab === 'notes' ? notes : logs,
    id = ++privateRun
  privateLoading.value = true
  error.value = ''
  const last =
    append && list.value.length
      ? list.value.at(-1)[selectedTab === 'notes' ? 'noteId' : 'auditId']
      : '9223372036854775807'
  const endpoint =
    selectedTab === 'notes'
      ? `/admin/support${root.value}/notes`
      : staff.value
        ? `/admin/support${root.value}/audit-logs`
        : root.value + '/timeline'
  try {
    const value = await api(endpoint + '?beforeId=' + last)
    if (alive && id === privateRun) {
      list.value = append ? [...list.value, ...value.items] : value.items
      privateMore.value = value.hasMore
    }
  } catch (e) {
    if (alive && id === privateRun) error.value = e.message
  } finally {
    if (alive && id === privateRun) privateLoading.value = false
  }
}
async function saveNote() {
  if (busy.value || !note.value.trim()) return
  busy.value = true
  error.value = ''
  const run = generation
  try {
    await api(`/admin/support${root.value}/notes`, {
      method: 'POST',
      body: { content: note.value },
    })
    if (alive && run === generation) {
      note.value = ''
      message.value = '內部備註已儲存，不會傳送給會員'
      await loadPrivate()
    }
  } catch (e) {
    if (alive && run === generation) error.value = e.message
  } finally {
    busy.value = false
  }
}
watch(
  () => [props.scope, props.id],
  () => {
    ++optionRun
    ++privateRun
    tab.value = 'orders'
    data.value = { orders: [], coupons: [], writable: false }
    open.value = false
    notes.value = []
    logs.value = []
    note.value = ''
    message.value = ''
    load()
  },
  { immediate: true },
)
function refreshContext() {
  load()
  if (['notes', 'logs'].includes(tab.value)) loadPrivate()
}
watch(() => props.contextState, refreshContext)
onMounted(() => window.addEventListener('support-refresh', refreshContext))
watch(tab, () => {
  privateMore.value = false
  loadPrivate()
})
onBeforeUnmount(() => {
  window.removeEventListener('support-refresh', refreshContext)
  alive = false
  generation++
  optionRun++
  privateRun++
})
</script>
<template>
  <aside class="support-context" :class="{ 'sx-compact': compact }" aria-label="客服關聯與處理資料">
    <header class="sx-context-heading">
      <div>
        <strong>{{ staff ? '處理資料' : '相關資料' }}</strong><small>僅顯示此會員的訂單與已領優惠券</small>
      </div>
      <button v-if="data.writable" type="button" :disabled="busy"
        @click="choose(tab === 'coupons' ? 'coupon' : 'order')">
        ＋ 關聯
      </button>
    </header>
    <p v-if="error" class="sx-error" role="alert">
      {{ error }} <button type="button" @click="load">重試</button>
    </p>
    <p v-if="message" class="sx-notice" role="status">{{ message }}</p>
    <p v-if="loading">載入中…</p>
    <nav class="sx-context-tabs" aria-label="客服資料分頁">
      <button v-for="item in visibleTabs" :key="item.key" type="button" :class="{ active: tab === item.key }"
        @click="tab = item.key">
        {{ item.label
        }}<small v-if="item.key === 'orders' || item.key === 'coupons'">{{
          data[item.key].length
          }}</small>
      </button>
    </nav>
    <template v-if="tab === 'orders'">
      <p v-if="!loading && !data.orders.length" class="sx-empty">
        尚未關聯訂單。沒有訂單也可以詢問客服。
      </p>
      <article v-for="row in data.orders" :key="row.orderId" class="sx-resource-card">
        <div class="sx-section-line">
          <strong>{{ row.orderNo }}</strong><span class="sx-status">{{ status(row.orderStatus) }}</span>
        </div>
        <p>{{row.games.map((g) => g.name).join('、')}}</p>
        <div class="sx-section-line">
          <b>NT$ {{ money(row.totalAmount) }}</b><small>{{ status(row.paymentStatus) }}</small>
        </div>
        <details>
          <summary>查看訂單明細</summary>
          <p>付款方式：{{ row.paymentType || '—' }}</p>
          <p>{{ date(row.createdAt) }}</p>
          <p v-for="game in row.games" :key="game.gameId">
            {{ game.name }} × {{ game.quantity }} · NT$
            {{ money(game.unitPrice) }}
          </p>
          <p v-if="row.virtualAccount">
            銀行 {{ row.bankCode }}／{{ row.virtualAccount }}
          </p>
          <p v-if="row.paymentNo">繳費代碼：{{ row.paymentNo }}</p>
          <p v-if="row.expireDate">期限：{{ row.expireDate }}</p>
        </details>
        <button v-if="data.writable" class="sx-remove" type="button" :disabled="busy"
          @click="change('order', row.orderId, true)">
          移除關聯
        </button>
      </article>
      <button v-if="data.writable" type="button" class="sx-add-resource" @click="choose('order')">
        從會員訂單選取
      </button>
    </template>
    <template v-if="tab === 'coupons'">
      <p v-if="!loading && !data.coupons.length" class="sx-empty">
        尚未關聯優惠券。
      </p>
      <article v-for="row in data.coupons" :key="row.couponId" class="sx-resource-card">
        <div class="sx-section-line">
          <strong>{{ row.name }}</strong><span class="sx-status">已領取</span>
        </div>
        <p>
          {{ discount(row) }} ·
          {{ Number(row.minSpend) ? '滿 NT$' + money(row.minSpend) : '無低消' }}
        </p>
        <p>適用：{{ scopeText(row) }}</p>
        <small>{{ date(row.startAt) }}～{{ date(row.expiresAt) }}</small>
        <p>
          待付款預留 {{ row.pendingUseCount }}／已付款使用
          {{ row.paidUseCount }}
        </p>
        <p v-if="row.unavailableReason" class="sx-warning">
          {{ row.unavailableReason }}
        </p>
        <small>使用次數依適用遊戲訂單明細計算。</small><button v-if="data.writable" class="sx-remove" type="button" :disabled="busy"
          @click="change('coupon', row.couponId, true)">
          移除關聯
        </button>
      </article>
      <button v-if="data.writable" type="button" class="sx-add-resource" @click="choose('coupon')">
        從會員已領券選取
      </button>
    </template>
    <section v-if="tab === 'notes' && staff" class="sx-notes">
      <strong>🔒 內部備註</strong>
      <p class="sx-privacy">
        僅授權客服可見，不會傳給會員。以新增備註更正既有內容。
      </p>
      <article v-for="row in notes" :key="row.noteId">
        <small>{{ row.authorName }} · {{ date(row.createdAt) }}</small><small
          v-if="row.roomId && scope === 'tickets'">來源對話
          #{{ row.roomId }}</small>
        <p>{{ row.content }}</p>
      </article>
      <p v-if="!privateLoading && !notes.length" class="sx-empty">
        目前沒有內部備註。
      </p>
      <form v-if="data.writable" @submit.prevent="saveNote">
        <textarea v-model="note" rows="4" maxlength="2000" required :disabled="busy" aria-label="內部備註內容"
          placeholder="記錄查核結果或交接事項…"></textarea><small>{{ note.length }} / 2000</small><button
          class="sx-primary sx-note-save" :disabled="busy || !note.trim()">
          儲存內部備註
        </button>
      </form>
    </section>
    <section v-if="tab === 'logs'" class="sx-timeline">
      <article v-for="row in logs" :key="row.auditId">
        <small>{{ date(row.createdAt)
        }}<span v-if="staff"> · {{ row.actorName }}</span></small>
        <p>{{ row.summary }}</p>
      </article>
      <p v-if="!privateLoading && !logs.length" class="sx-empty">
        尚無紀錄；功能啟用前的歷史操作未補造。
      </p>
    </section>
    <p v-if="privateLoading" role="status">載入紀錄中…</p>
    <button v-if="privateMore && ['logs', 'notes'].includes(tab)" type="button" :disabled="privateLoading"
      @click="loadPrivate(true)">
      載入更早紀錄
    </button>
    <Teleport to="body">
      <div v-if="open" class="sx-picker-backdrop" @keydown.esc="!busy && (open = false)"
        @click.self="!busy && (open = false)">
        <section role="dialog" aria-modal="true" :aria-label="picker === 'order' ? '選擇訂單關聯' : '選擇優惠券關聯'"
          class="sx-picker">
          <header class="sx-section-line">
            <h2>選擇關聯資料</h2>
            <button type="button" :disabled="busy" aria-label="關閉選擇關聯資料" @click="open = false">
              ✕
            </button>
          </header>
          <p>系統自動列出這位會員的資料，不必手打編號。</p>
          <div class="sx-chips">
            <button type="button" :disabled="busy" :class="{ active: picker === 'order' }" @click="choose('order')">
              訂單</button><button type="button" :disabled="busy" :class="{ active: picker === 'coupon' }"
              @click="choose('coupon')">
              已領優惠券
            </button>
          </div>
          <form class="sx-search" @submit.prevent="loadOptions(true)">
            <input v-model="query" :disabled="busy" maxlength="100" :placeholder="picker === 'order' ? '搜尋訂單編號或遊戲' : '搜尋優惠券名稱'
              " /><button :disabled="picked || busy">搜尋</button>
          </form>
          <p v-if="error" class="sx-error" role="alert">{{ error }}</p>
          <p v-if="picked">載入中…</p>
          <div class="sx-picker-list">
            <article v-for="row in options" :key="row.orderId ?? row.couponId" class="sx-option">
              <div>
                <strong>{{ row.orderNo || row.name }}</strong>
                <p>
                  {{
                    picker === 'order'
                      ? row.games.map((g) => g.name).join('、') +
                      ' · NT$' +
                      money(row.totalAmount)
                      : discount(row) + ' · ' + scopeText(row)
                  }}
                </p>
                <small>{{
                  picker === 'order'
                    ? status(row.orderStatus)
                    : row.unavailableReason || '已領取'
                }}</small>
              </div>
              <button type="button" :disabled="busy || linked(row)"
                @click="change(picker, row.orderId ?? row.couponId)">
                {{ linked(row) ? '已關聯' : '關聯' }}
              </button>
            </article>
            <p v-if="!picked && !options.length" class="sx-empty">
              沒有符合的資料，可以略過關聯直接詢問。
            </p>
          </div>
          <footer class="sx-pager unified-pager">
            <button v-if="page > 0" type="button" :disabled="!page || picked || busy" @click="page--; loadOptions()">
              上一頁</button><span>{{ page + 1 }}</span><button v-if="more" type="button"
              :disabled="!more || picked || busy" @click="page++; loadOptions()">
              下一頁</button><button type="button" :disabled="busy" @click="open = false">
              完成
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </aside>
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

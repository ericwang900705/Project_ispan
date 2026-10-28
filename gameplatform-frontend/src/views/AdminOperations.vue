<script setup>
import DatePicker from '../components/DatePicker.vue'
import SearchableSelect from '../components/SearchableSelect.vue'
import { computed, ref, watch, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api, session } from '../api'
import { hasPermission } from '../permissions'
import { backofficeModules, managementLabels } from '../backoffice-modules'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import '../styles/admin-management.css'
const route = useRoute(), router = useRouter(), commentView = ref('pending')
const commentPage = computed(() => route.meta.workspaceModule === 'comments')
const area = computed(() => commentPage.value ? (commentView.value === 'reviews' ? 'reviews' : 'review-reports') : route.meta.workspaceModule)
const config = computed(() => backofficeModules[area.value])
function changeCommentView(view) { router.replace({ path: '/admin/comments', query: view === 'pending' ? {} : { view } }) }
const rows = ref([]), total = ref(0), page = ref(0), query = ref(''), status = ref(''), loading = ref(false), busy = ref(false), error = ref(''), message = ref('')
const selected = ref(null), detail = ref(null), editing = ref(false), reason = ref(''), decision = ref('HIDDEN')
const moderationInfo = ref(null)
const couponOptions = ref({ publishers: [], tags: [] }), optionsError = ref('')
const appliedFilters = ref({ q: '', status: '' })
const form = ref({}), editable = computed(() => ['tags', 'coupons'].includes(area.value))
let run = 0, optionsRun = 0, alive = true, timer = null
const refreshable = computed(() => ['reviews', 'review-reports', 'operations', 'login-logs'].includes(area.value))
const logPage = computed(() => ['operations', 'login-logs'].includes(area.value))
const readOnlyList = computed(() => logPage.value || area.value === 'promotions')
const emptyMessage = computed(() => query.value.trim() || status.value ? '沒有符合篩選條件的資料。' : ({ reviews: '尚無玩家評論。會員在遊戲商品頁發表評論後，會顯示在這裡。', 'review-reports': '尚無評論檢舉。', operations: '尚無操作紀錄。遊戲、發行商審核及客服等操作完成後會顯示在這裡。', 'login-logs': '尚無會員登入紀錄。會員使用密碼或 Google 登入後會顯示在這裡；不包含後台人員登入。' }[area.value] || '目前沒有資料。'))
function refreshVisible() { if (document.visibilityState === 'visible' && refreshable.value && !loading.value && !busy.value && !editing.value && !selected.value && !query.value.trim() && (!status.value || (commentPage.value && commentView.value === 'pending'))) load() }
onMounted(() => { timer = setInterval(refreshVisible, 30000); window.addEventListener('support-refresh', refreshVisible); document.addEventListener('visibilitychange', refreshVisible) })
const text = (value) => value == null ? '—' : (area.value === 'promotions' ? ({ SCHEDULED: '尚未開始', ACTIVE: '進行中', COOLDOWN: '冷卻中', ENDED: '已結束' }[value]) : null) || managementLabels[value] || String(value)
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
const couponSummary = row => `${Number(row.min_spend) > 0 ? '滿 NT$' + Number(row.min_spend) : '無低消'}・${row.discount_type === 'AMOUNT' ? '折 NT$' + Number(row.discount_value) : Number((Number(row.discount_value) * 10).toFixed(1)) + ' 折'}`
const couponScope = row => row.scope_type === 'PUBLISHER' ? '發行商：' + row.publisher_name : row.scope_type === 'TAG' ? '類型：' + row.tag_name : '全平台遊戲'
const cell = (row, key) => key === 'discount_summary' ? (area.value === 'promotions' ? `${Number(((100 - Number(row.discount_percent)) / 10).toFixed(1))} 折（減價 ${row.discount_percent}%）・NT$ ${Number(row.original_price)} → ${Number(row.discount_price)}` : couponSummary(row)) : key === 'scope_summary' ? couponScope(row) : key.endsWith('_at') ? date(row[key]) : text(row[key])
async function load(reset = false) {
    if (reset) { page.value = 0; appliedFilters.value = { q: query.value.trim(), status: status.value } }; const id = ++run; loading.value = true; error.value = ''; selected.value = null; detail.value = null; moderationInfo.value = null; editing.value = false
    try { const data = await api('/backoffice/' + area.value + '?' + new URLSearchParams({ ...appliedFilters.value, page: String(page.value) })); if (alive && id === run) { rows.value = data.items; total.value = data.total } }
    catch (e) { if (alive && id === run) { error.value = e.message; rows.value = []; total.value = 0 } }
    finally { if (alive && id === run) loading.value = false }
}
async function loadCouponOptions() {
    const id = ++optionsRun; optionsError.value = ''
    try { const options = await api('/backoffice/coupon-options'); if (alive && id === optionsRun) couponOptions.value = options }
    catch (e) { if (alive && id === optionsRun) optionsError.value = '無法載入發行商與類型：' + e.message }
}
watch(() => route.fullPath, () => { rows.value = []; total.value = 0; query.value = ''; status.value = ''; if (commentPage.value) { commentView.value = ['reviews', 'reports'].includes(route.query.view) ? route.query.view : 'pending'; status.value = commentView.value === 'pending' ? 'PENDING' : '' }; appliedFilters.value = { q: '', status: status.value }; page.value = 0; message.value = ''; optionsRun++; couponOptions.value = { publishers: [], tags: [] }; optionsError.value = ''; load(); if (area.value === 'coupons') loadCouponOptions() }, { immediate: true })
onBeforeUnmount(() => { alive = false; run++; optionsRun++; clearInterval(timer); window.removeEventListener('support-refresh', refreshVisible); document.removeEventListener('visibilitychange', refreshVisible) })
function edit(row = null) {
    selected.value = row; editing.value = true; detail.value = null; error.value = ''; message.value = ''
    form.value = area.value === 'tags' ? { name: row?.tag_name || '' } : { name: row?.coupon_name || '', type: row?.discount_type || 'RATE', value: row?.discount_type === 'AMOUNT' ? Number(row.discount_value) : Number((Number(row?.discount_value ?? 0.8) * 10).toFixed(1)), minimum: Number(row?.min_spend ?? 0), hasMinimum: Number(row?.min_spend ?? 0) > 0, scopeType: row?.scope_type || 'ALL', publisherId: row?.publisher_member_id ?? '', tagId: row?.tag_id ?? '', totalLimit: row?.total_limit ?? '', memberLimit: row?.per_member_limit ?? 1, status: row?.status || 'ACTIVE', start: row?.start_at?.slice(0, 16) || '', end: row?.expires_at?.slice(0, 16) || '' }
}
async function open(row) {
    if (editable.value) { edit(row); return } selected.value = row; detail.value = null; moderationInfo.value = null; editing.value = false; reason.value = ''; decision.value = row.status === 'HIDDEN' ? 'NORMAL' : 'HIDDEN'
    if (['reviews', 'review-reports'].includes(area.value)) { busy.value = true; const id = run, key = row[config.value.id]; try { const value = await api('/backoffice/reviews/' + (area.value === 'reviews' ? row.review_id : row.target_id) + '/moderation'); if (alive && id === run && selected.value?.[config.value.id] === key) moderationInfo.value = value } catch (e) { if (alive && id === run) error.value = e.message } finally { busy.value = false } }
    if (['orders', 'refunds'].includes(area.value)) { busy.value = true; const id = run; try { const data = await api('/backoffice/' + area.value + '/' + row.order_id); if (alive && id === run) detail.value = data } catch (e) { if (alive && id === run) error.value = e.message } finally { busy.value = false } }
}
async function mutate(path, method, body) {
    if (busy.value) return; busy.value = true; error.value = ''; message.value = ''; const id = run
    try { await api(path, { method, body }); if (alive && id === run) { message.value = '已儲存'; await load() } }
    catch (e) { if (alive && id === run) error.value = e.message }
    finally { busy.value = false }
}
function save() {
    if (area.value === 'coupons') {
        const items = form.value.scopeType === 'PUBLISHER' ? couponOptions.value.publishers : couponOptions.value.tags
        const id = form.value.scopeType === 'PUBLISHER' ? form.value.publisherId : form.value.tagId
        if (form.value.scopeType !== 'ALL' && !items.some(item => String(item.id) === String(id))) { error.value = '請從搜尋結果選擇有效的發行商或遊戲類型'; return }
    }
    let body = { ...form.value }; if (area.value === 'coupons') body = { ...body, value: body.type === 'RATE' ? Number((Number(body.value) / 10).toFixed(2)) : Number(body.value), minimum: body.hasMinimum ? Number(body.minimum) : 0, scopeType: body.scopeType, publisherId: body.scopeType === 'PUBLISHER' ? Number(body.publisherId) : null, tagId: body.scopeType === 'TAG' ? Number(body.tagId) : null, memberLimit: Number(body.memberLimit), totalLimit: body.totalLimit === '' || body.totalLimit == null ? null : Number(body.totalLimit) }
    delete body.hasMinimum
    mutate('/backoffice/' + area.value + (selected.value ? '/' + selected.value[config.value.id] : ''), selected.value ? 'PUT' : 'POST', body)
}
function remove() { if (confirm('確定刪除這個未使用的標籤？')) mutate('/backoffice/tags/' + selected.value.tag_id, 'DELETE') }
function review() { if (!reason.value.trim()) { error.value = '請填寫處理原因'; return } mutate('/backoffice/reviews/' + selected.value.review_id, 'PATCH', { status: decision.value, reason: reason.value }) }
function report(remove) { if (!reason.value.trim()) { error.value = '請填寫處理原因'; return } mutate('/backoffice/review-reports/' + selected.value.report_id + '/decision', 'POST', { remove, reason: reason.value }) }
function clear() { query.value = ''; status.value = commentPage.value && commentView.value === 'pending' ? 'PENDING' : ''; load(true) }
const orderLabels = { order_no: '訂單編號', username: '會員', order_status: '狀態', subtotal_amount: '小計', discount_amount: '折扣', total_amount: '總金額', points_earned: '獲得點數', created_at: '下單時間', paid_at: '付款時間', payment_type: '付款方式', ecpay_trade_no: '金流交易編號' }
</script>
<template>
    <section class="page admin-management-page operations-page">
        <AdminPageHeader :title="commentPage ? '評論與檢舉' : config.title" eyebrow="ADMIN WORKSPACE" :icon="config.icon"
            :description="commentPage ? '預設處理待辦檢舉，也可查看全部評論；不必等會員檢舉才看得到評論。' : config.note" :count="total"
            count-label="符合條件的紀錄" />
        <nav v-if="logPage" class="management-tabs" aria-label="系統紀錄分類">
            <RouterLink v-if="hasPermission(session.user, 'OPERATION_LOG_VIEW')" to="/admin/operations"
                :class="{ active: area === 'operations' }">操作紀錄</RouterLink>
            <RouterLink v-if="hasPermission(session.user, 'LOGIN_LOG_VIEW')" to="/admin/login-logs"
                :class="{ active: area === 'login-logs' }">會員登入紀錄</RouterLink>
        </nav>
        <nav v-if="commentPage" class="management-tabs comment-tabs" aria-label="評論與檢舉分類">
            <button :class="{ active: commentView === 'pending' }" @click="changeCommentView('pending')">待處理檢舉</button>
            <button :class="{ active: commentView === 'reports' }" @click="changeCommentView('reports')">全部檢舉</button>
            <button :class="{ active: commentView === 'reviews' }" @click="changeCommentView('reviews')">全部評論</button>
        </nav>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="message" class="management-notice" role="status">{{ message }}</p>
        <form class="management-search" @submit.prevent="load(true)"><label>搜尋<input v-model="query" maxlength="100"
                    :disabled="busy" placeholder="輸入名稱、帳號或編號"></label><label v-if="config.states">狀態<select
                    v-model="status" aria-label="狀態" :disabled="busy || (commentPage && commentView === 'pending')">
                    <option value="">全部</option>
                    <option v-for="item in config.states" :value="item" :key="item">{{ text(item) }}</option>
                </select></label>
            <div class="management-actions"><button class="primary" :disabled="loading || busy">搜尋</button><button
                    type="button" :disabled="loading || busy" @click="clear">清除篩選</button><button type="button"
                    :disabled="loading || busy" @click="load()">重新整理</button><button v-if="editable" type="button"
                    :disabled="busy" @click="edit()">新增</button></div>
        </form>
        <p class="list-scope" role="status">{{ appliedFilters.q || appliedFilters.status ? '目前顯示搜尋結果' : '目前顯示全部資料' }}（共
            {{ total }} 筆，分頁顯示）</p>
        <p v-if="loading" role="status">載入中…</p>
        <div class="management-table-wrap" :aria-busy="loading">
            <table>
                <thead>
                    <tr>
                        <th v-for="col in config.columns" :key="col.key">{{ col.label }}</th>
                        <th v-if="!readOnlyList">操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="row in rows" :key="row[config.id]">
                        <td v-for="col in config.columns" :key="col.key" class="operation-cell">{{ cell(row, col.key) }}
                        </td>
                        <td v-if="!readOnlyList"><button class="action-info" :disabled="busy || loading"
                                @click="open(row)">{{ editable ? '編輯' : '查看' }}</button></td>
                    </tr>
                    <tr v-if="!loading && !error && !rows.length">
                        <td :colspan="config.columns.length + (readOnlyList ? 0 : 1)" class="management-empty">{{
                            emptyMessage }}
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <nav class="management-pagination unified-pager" aria-label="列表分頁"><button v-if="page > 0"
                :disabled="!page || busy || loading" @click="page--; load()">上一頁</button><span>{{ page + 1 }} / {{
                    Math.max(1, Math.ceil(total / 20)) }}</span><button v-if="(page + 1) * 20 < total"
                :disabled="(page + 1) * 20 >= total || busy || loading" @click="page++; load()">下一頁</button></nav>
        <form v-if="editing" class="management-panel" @submit.prevent="save">
            <h2>{{ selected ? '編輯' : '新增' }}{{ area === 'tags' ? '標籤' : '優惠券' }}</h2><label>名稱<input
                    v-model.trim="form.name" :maxlength="area === 'tags' ? 50 : 100" required :disabled="busy"></label>
            <template v-if="area === 'coupons'">
                <p v-if="optionsError" role="alert">{{ optionsError }} <button type="button" :disabled="busy"
                        @click="loadCouponOptions">重試載入選單</button></p>
                <div class="coupon-grid">
                    <label>優惠方式<select aria-label="優惠方式" v-model="form.type" :disabled="busy"
                            @change="form.value = form.type === 'RATE' ? 8 : 100">
                            <option value="RATE">打幾折</option>
                            <option value="AMOUNT">折抵金額</option>
                        </select></label>
                    <label>{{ form.type === 'RATE' ? '折數（例：8 代表八折）' : '折抵金額（NT$）' }}<input v-model="form.value"
                            type="number" :min="form.type === 'RATE' ? 0.1 : 0.01"
                            :max="form.type === 'RATE' ? 9.9 : 99999999.99" :step="form.type === 'RATE' ? 0.1 : 0.01"
                            required :disabled="busy"></label>
                    <label>低消條件<select aria-label="低消條件" v-model="form.hasMinimum" :disabled="busy">
                            <option :value="false">無低消</option>
                            <option :value="true">有低消</option>
                        </select></label>
                    <label v-if="form.hasMinimum">最低消費（適用遊戲合計）<input v-model="form.minimum" type="number" min="0.01"
                            max="99999999.99" step="0.01" required :disabled="busy"></label>
                    <label>使用規則<select aria-label="使用規則" v-model="form.scopeType" :disabled="busy">
                            <option value="ALL">全平台遊戲</option>
                            <option value="PUBLISHER">指定發行商旗下遊戲</option>
                            <option value="TAG">指定類型的遊戲</option>
                        </select></label>
                    <SearchableSelect v-if="form.scopeType === 'PUBLISHER'" v-model="form.publisherId"
                        :options="couponOptions.publishers" label="發行商" required :disabled="busy" />
                    <SearchableSelect v-if="form.scopeType === 'TAG'" v-model="form.tagId" :options="couponOptions.tags"
                        label="遊戲類型" required :disabled="busy" />
                    <label>開始時間
                        <DatePicker v-model="form.start" type="datetime-local" label="開始時間" required :disabled="busy" />
                    </label>
                    <label>結束時間
                        <DatePicker v-model="form.end" type="datetime-local" label="結束時間" required :disabled="busy" />
                    </label>
                    <label>總使用額度<input v-model="form.totalLimit" type="number" min="1" step="1" placeholder="留空為不限制"
                            :disabled="busy"><small>沿用現有規則：按適用遊戲的訂單明細計次。</small></label>
                    <label>每位會員額度<input v-model="form.memberLimit" type="number" min="1" step="1" required
                            :disabled="busy"></label>
                    <label>狀態<select v-model="form.status" :disabled="busy">
                            <option v-for="value in config.states" :key="value" :value="value">{{ text(value) }}
                            </option>
                        </select></label>
                </div>
                <p v-if="selected">已使用 {{ selected.used_count }} 次；總額度不得低於此數。</p>
                <p>低消與折扣僅計算符合使用規則的遊戲，其餘遊戲維持原價。</p>
            </template>
            <div class="management-actions"><button class="primary" :disabled="busy">儲存</button><button type="button"
                    :disabled="busy" @click="editing = false; selected = null">取消</button><button class="action-danger"
                    v-if="area === 'tags' && selected" type="button" :disabled="busy || selected.game_count > 0"
                    @click="remove">刪除標籤</button></div>
        </form>
        <section v-if="detail" class="management-panel">
            <h2>訂單明細</h2>
            <div v-if="detail.paymentAttempts?.length" class="payment-attempt-log">
                <h3>付款交易紀錄</h3>
                <p v-for="attempt in detail.paymentAttempts" :key="attempt.merchant_trade_no">{{
                    attempt.merchant_trade_no }} ·
                    {{ attempt.payment_method }} · {{ attempt.status === 'REVIEW' ? '額外付款／需人工核對' : attempt.status
                    }}<span v-if="attempt.ecpay_trade_no"> · {{ attempt.ecpay_trade_no }}</span></p>
            </div>
            <dl class="coupon-grid">
                <div v-for="(label, key) in orderLabels" :key="key">
                    <dt>{{ label }}</dt>
                    <dd>{{ cell(detail.order, key) }}</dd>
                </div>
            </dl>
            <div class="management-table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>遊戲</th>
                            <th>數量</th>
                            <th>原價</th>
                            <th>折扣</th>
                            <th>實付</th>
                            <th>贈送對象</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="item in detail.items" :key="item.order_item_id">
                            <td>{{ item.game_name }}</td>
                            <td>{{ item.quantity }}</td>
                            <td>{{ item.unit_price }}</td>
                            <td>{{ item.discount_amount }}</td>
                            <td>{{ item.final_amount }}</td>
                            <td>{{ item.is_gift ? item.recipient : '自用' }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
        <form v-if="selected && area === 'reviews'" class="management-panel" @submit.prevent="review">
            <h2>評論處理</h2>
            <p class="moderation-policy">軟刪除保留原文並計入違規。30 天內 2 則不同評論確認違規，禁言 6 小時；同一評論不重複計次。恢復評論會撤銷該次違規，未達門檻時會解除禁言。</p>
            <p v-if="moderationInfo">近 30 天違規：{{ moderationInfo.violationsIn30Days || 0 }} 次 <strong
                    v-if="moderationInfo.muted">・禁言至 {{ date(moderationInfo.mutedUntil) }}</strong></p>
            <p>{{ selected.game_name }} · {{ selected.username }}</p>
            <p class="review-text">{{ selected.content }}</p><template
                v-if="selected.status !== 'DELETED'"><label>處理方式<select v-model="decision" :disabled="busy">
                        <option value="HIDDEN">軟刪除（保留資料）</option>
                        <option value="NORMAL">恢復正常</option>
                    </select></label><label>處理原因<textarea v-model="reason" rows="3" maxlength="500" required
                        :disabled="busy"></textarea></label><button class="primary"
                    :class="decision === 'HIDDEN' ? 'action-danger' : 'action-success'"
                    :disabled="busy">確認儲存</button></template>
            <p v-else>此評論已由會員刪除，不能恢復。</p>
        </form>
        <section v-if="selected && area === 'review-reports'" class="management-panel">
            <h2>檢舉 #{{ selected.report_id }}</h2>
            <p class="moderation-policy">軟刪除保留原文並計入違規。30 天內 2 則不同評論確認違規，禁言 6 小時；同一評論不重複計次。恢復評論會撤銷該次違規，未達門檻時會解除禁言。</p>
            <p v-if="moderationInfo">近 30 天違規：{{ moderationInfo.violationsIn30Days || 0 }} 次 <strong
                    v-if="moderationInfo.muted">・禁言至 {{ date(moderationInfo.mutedUntil) }}</strong></p>
            <h3>檢舉快照</h3>
            <p class="review-text">{{ selected.snapshot_text }}</p>
            <h3>目前內容</h3>
            <p class="review-text">{{ selected.current_content || '評論不存在或無內容' }}</p>
            <p>檢舉原因：{{ selected.reason }}</p><template v-if="selected.status === 'PENDING'"><label>處理原因<textarea
                        v-model="reason" rows="3" maxlength="500" :disabled="busy"></textarea></label>
                <div class="management-actions"><button class="action-danger" :disabled="busy"
                        @click="report(true)">確認違規、軟刪除並結案</button><button class="action-success" :disabled="busy"
                        @click="report(false)">檢舉不成立</button></div>
            </template>
            <p v-else>處理說明：{{ selected.review_comment }}</p>
        </section>
    </section>
</template>
<style scoped>
.operation-cell {
    max-width: 300px;
    white-space: pre-wrap;
    overflow-wrap: anywhere
}

.coupon-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px
}

.coupon-grid dt {
    color: #6e8495;
    font-size: 13px
}

.coupon-grid dd {
    margin: 6px 0;
    overflow-wrap: anywhere
}

.review-text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    line-height: 1.9
}

.management-panel>label {
    display: grid;
    gap: 8px;
    margin: 15px 0
}

.management-panel button {
    margin-top: 12px
}

.management-panel textarea {
    width: 100%;
    box-sizing: border-box
}

@media(max-width:700px) {
    .coupon-grid {
        grid-template-columns: 1fr
    }
}
</style>

<style scoped>
.comment-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 6px;
    margin-bottom: 24px;
}

.comment-tabs button {
    border: 0;
    border-radius: 8px;
    padding: 10px 18px;
    font-weight: 600;
    color: #36566b;
    background: transparent;
}

.comment-tabs button.active {
    background: #fff;
    color: #075f76;
    box-shadow: 0 2px 7px #214b6315;
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

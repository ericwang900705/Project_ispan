<script setup>
import PublisherBuildLink from '../components/PublisherBuildLink.vue'
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import UiIcon from '../components/UiIcon.vue'
import '../styles/admin-management.css'

const route = useRoute()
const historyMode = computed(() => route.query.mode === 'history')
const pendingMode = computed(() => route.query.mode === 'pending')
const title = computed(() => historyMode.value ? '遊戲審核紀錄' : pendingMode.value ? '遊戲上架審核' : '遊戲搜尋')
const items = ref([]), history = ref([]), total = ref(0), page = ref(0)
const keyword = ref(''), status = ref(''), selected = ref(null)
const error = ref(''), busy = ref(false), loading = ref(false), detailLoading = ref(false)
const comment = ref(''), decision = ref('')
const labels = { PENDING_REVIEW: '待審核', COMING_SOON: '即將發售', PUBLISHED: '已上架', PENDING_OFF_SHELF: '預定下架', OFF_SHELF: '已下架' }
const decisionLabel = computed(() => ({ approve: '核准上架', reject: '退回申請', 'force-off-shelf': '強制下架' }[decision.value] || ''))
const money = value => 'NT$ ' + new Intl.NumberFormat('zh-TW').format(Number(value) || 0)
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
let run = 0, detailRun = 0, alive = true
async function load(reset = false) {
  if (reset) page.value = 0
  const current = ++run
  loading.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ page: String(page.value), size: '10', keyword: keyword.value, status: pendingMode.value ? 'PENDING_REVIEW' : status.value })
    const result = await api(historyMode.value ? '/admin/game-reviews/history?page=' + page.value + '&size=10' : '/admin/games/search?' + query)
    if (!alive || current !== run) return
    if (historyMode.value) history.value = result.content
    else items.value = result.content
    total.value = result.totalElements
  } catch (e) {
    if (alive && current === run) { error.value = e.message; items.value = []; history.value = []; total.value = 0 }
  } finally { if (alive && current === run) loading.value = false }
}
function clearDetail() { detailRun++; selected.value = null; decision.value = ''; detailLoading.value = false }
async function choose(id) {
  const current = ++detailRun
  selected.value = null; decision.value = ''; error.value = ''; detailLoading.value = true
  try { const data = await api('/admin/games/' + id); if (alive && current === detailRun) selected.value = data }
  catch (e) { if (alive && current === detailRun) error.value = e.message }
  finally { if (alive && current === detailRun) detailLoading.value = false }
}
async function submit() {
  if (busy.value || !selected.value || !decision.value) return
  if (decision.value !== 'approve' && !comment.value.trim()) { error.value = '請填寫處理原因'; return }
  busy.value = true; error.value = ''
  try {
    const id = selected.value.game.gameId
    const action = decision.value
    await api(`/admin/game-reviews/${id}/${action}`, { method: 'PUT', body: { comment: comment.value.trim() } })
    if (!alive) return
    decision.value = ''; comment.value = ''
    // Rejection returns a game to its publisher's private draft; never reload its admin detail.
    if (action === 'reject') { selected.value = null; detailRun++; await load() }
    else await Promise.all([load(), choose(id)])
  } catch (e) { if (alive) error.value = e.message }
  finally { if (alive) busy.value = false }
}
watch(() => route.query.mode, () => {
  detailRun++; detailLoading.value = false; selected.value = null; decision.value = ''; items.value = []; history.value = []
  status.value = ''; keyword.value = ''; load(true)
}, { immediate: true })
watch(() => [route.query.mode, route.query.game], ([, id]) => { if (/^\d+$/.test(String(id || ''))) choose(id) }, { immediate: true })
onBeforeUnmount(() => { alive = false; run++; detailRun++ })
</script>

<template>
  <section class="page admin-management-page admin-game-page">
    <AdminPageHeader :title="title" eyebrow="GAME REVIEW" icon="game" :description="historyMode ? '追蹤每次審核結果，保留上架與下架的處理紀錄。' : '檢視已送審遊戲、版本與素材，處理上架申請。'" :count="total" :count-label="historyMode ? '筆審核紀錄' : '款符合條件的遊戲'" />
    <nav class="management-tabs" aria-label="遊戲管理"><RouterLink to="/admin/games" :class="{ active: !historyMode && !pendingMode }">遊戲搜尋</RouterLink><RouterLink to="/admin/games?mode=pending" :class="{ active: pendingMode }">上架審核</RouterLink><RouterLink to="/admin/games?mode=history" :class="{ active: historyMode }">審核紀錄</RouterLink></nav>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <form v-if="!historyMode" class="management-search" @submit.prevent="clearDetail(); load(true)">
      <label>遊戲名稱<input v-model="keyword" placeholder="搜尋已送審的遊戲名稱" maxlength="100" :disabled="busy"></label>
      <label v-if="!pendingMode">遊戲狀態<select v-model="status" :disabled="busy"><option value="">全部已送審狀態</option><option v-for="(name, key) in labels" :key="key" :value="key">{{ name }}</option></select></label>
      <span v-else class="management-filter-note"><span class="management-badge warning">待審核</span>僅顯示已送出的上架申請</span>
      <button class="primary" :disabled="loading || busy">搜尋</button>
    </form>
    <div class="management-section-heading"><h2>{{ historyMode ? '審核紀錄' : pendingMode ? '待審核清單' : '遊戲清單' }}</h2><span>每頁 10 筆</span></div>
    <p v-if="loading" class="management-loading" role="status">正在載入資料…</p>
    <div v-if="historyMode" class="management-table-wrap game-history-table" :aria-busy="loading">
      <table><thead><tr><th>紀錄／遊戲</th><th>審核類型</th><th>處理結果</th><th>審核說明</th><th>審核人員</th><th>處理時間</th></tr></thead><tbody>
        <tr v-for="row in history" :key="row.gameAuditId"><td><strong>遊戲 #{{ row.gameId }}</strong><small>紀錄 #{{ row.gameAuditId }}</small></td><td>{{ row.reviewType === 'PUBLISH' ? '上架審核' : '下架處理' }}</td><td><span class="management-badge" :class="{ warning: row.decision !== 'APPROVED' }">{{ row.decision === 'APPROVED' ? '已通過' : '已退回' }}</span></td><td class="management-comment">{{ row.comment || '未填寫補充說明' }}</td><td><span class="reviewer-chip"><UiIcon name="users" />人員 #{{ row.adminId }}</span></td><td>{{ date(row.reviewedAt) }}</td></tr>
        <tr v-if="!loading && !history.length"><td colspan="6" class="management-empty"><UiIcon name="file" /><h3>尚無審核紀錄</h3><p>完成審核後，處理結果會顯示在這裡。</p></td></tr>
      </tbody></table>
    </div>
    <div v-else class="review-workspace">
      <section class="review-list" aria-label="遊戲清單">
        <button v-for="game in items" :key="game.gameId" class="review-game-card" :class="{ active: selected?.game.gameId === game.gameId }" :disabled="busy || loading" @click="choose(game.gameId)">
          <img v-if="game.coverUrl" :src="game.coverUrl" alt=""><span v-else class="review-cover-empty"><UiIcon name="game" /></span>
          <span class="review-card-copy"><small>遊戲 #{{ game.gameId }}</small><strong>{{ game.gameName }}</strong><span class="management-badge" :class="{ warning: game.status === 'PENDING_REVIEW' }">{{ labels[game.status] || game.status }}</span><span class="review-price">{{ money(game.price) }}</span></span><UiIcon class="review-card-arrow" name="chevron" />
        </button>
        <div v-if="!loading && !items.length" class="review-empty"><UiIcon name="game" /><h3>目前沒有符合的遊戲</h3><p>{{ pendingMode ? '發行商送出上架申請後，會出現在這裡。' : '試試其他搜尋條件。未送審的草稿不會顯示。' }}</p></div>
      </section>
      <article v-if="selected" class="review-detail">
        <div class="review-detail-heading"><div><span class="management-eyebrow">GAME #{{ selected.game.gameId }}</span><h2>{{ selected.game.gameName }}</h2></div><span class="management-badge" :class="{ warning: selected.game.status === 'PENDING_REVIEW' }">{{ labels[selected.game.status] }}</span></div>
        <img v-if="selected.game.coverUrl" class="review-detail-cover" :src="selected.game.coverUrl" alt="遊戲封面">
        <dl class="review-facts"><div><dt>發行商編號</dt><dd>#{{ selected.game.memberId }}</dd></div><div><dt>售價</dt><dd>{{ money(selected.game.price) }}</dd></div><div><dt>發售時間</dt><dd>{{ selected.game.releaseDate ? date(selected.game.releaseDate) : '未設定' }}</dd></div><div><dt>遊戲標籤</dt><dd>{{ selected.tags.map(t => t.tagName).join('、') || '未設定' }}</dd></div></dl>
        <section class="review-detail-section"><h3>遊戲介紹</h3><p class="review-description">{{ selected.game.description || '未提供遊戲介紹' }}</p></section>
        <section class="review-detail-section"><h3>版本資訊 <small>{{ selected.builds.length }} 個版本</small></h3><div v-for="build in selected.builds" :key="build.build_id" class="review-build"><div><strong>{{ build.version }}</strong><small>{{ build.status }}</small></div><PublisherBuildLink :url="build.file_url" /></div><p v-if="!selected.builds.length" class="muted">尚未提供版本檔案。</p></section>
        <section class="review-detail-section"><h3>宣傳素材</h3><div class="review-media"><template v-for="media in selected.media" :key="media.media_id"><img v-if="media.media_type === 'IMAGE'" :src="media.media_url" alt="遊戲宣傳圖"><a v-else :href="media.media_url" target="_blank" rel="noopener noreferrer">查看影片 ↗</a></template><p v-if="!selected.media.length" class="muted">尚未提供宣傳素材。</p></div></section>
        <div class="management-actions review-decision-actions"><template v-if="selected.game.status === 'PENDING_REVIEW'"><button class="primary action-success" :disabled="busy" @click="decision = 'approve'; comment = ''">核准上架</button><button class="review-reject-button action-danger" :disabled="busy" @click="decision = 'reject'; comment = ''">退回申請</button></template><button class="action-danger" v-if="['PUBLISHED', 'COMING_SOON', 'PENDING_OFF_SHELF'].includes(selected.game.status)" :disabled="busy" @click="decision = 'force-off-shelf'; comment = ''">強制下架</button></div>
        <form v-if="decision" class="management-panel" @submit.prevent="submit"><h3>確認{{ decisionLabel }}</h3><label>{{ decision === 'approve' ? '審核說明（選填）' : '處理原因（必填）' }}<textarea v-model="comment" :required="decision !== 'approve'" :disabled="busy" maxlength="500" rows="3"></textarea></label><p>即將對「{{ selected.game.gameName }}」執行{{ decisionLabel }}。</p><div class="management-actions"><button class="primary" :class="decision === 'approve' ? 'action-success' : 'action-danger'" :disabled="busy">{{ busy ? '處理中…' : '確認送出' }}</button><button type="button" :disabled="busy" @click="decision = ''">取消</button></div></form>
      </article>
      <article v-else class="review-detail review-detail-placeholder"><UiIcon name="game" /><h2>{{ detailLoading ? '正在載入遊戲資料' : '選擇遊戲以開始審核' }}</h2><p>從左側清單選擇遊戲，查看介紹、版本與宣傳素材。</p></article>
    </div>
    <nav class="management-pagination" aria-label="遊戲列表分頁"><button :disabled="!page || loading || busy" @click="page--; clearDetail(); load()">上一頁</button><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 10)) }}</span><button :disabled="(page + 1) * 10 >= total || loading || busy" @click="page++; clearDetail(); load()">下一頁</button></nav>
  </section>
</template>

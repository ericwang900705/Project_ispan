<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter, RouterLink, onBeforeRouteUpdate, onBeforeRouteLeave } from 'vue-router'
import { api, session } from '../api'
import { publisherFunctions, publisherLink, publisherGroups, publisherIcons } from '../publisher-functions'
import { notifyPublisher } from '../publisher-notifications'
import UiIcon from '../components/UiIcon.vue'
import PublisherDialog from '../components/PublisherDialog.vue'
import PublisherSaveActions from '../components/PublisherSaveActions.vue'
import PublisherPreview from '../components/PublisherPreview.vue'
import PublisherOperations from '../components/PublisherOperations.vue'
import '../styles/publisher.css'

const route = useRoute(), router = useRouter()
const mode = computed(() => publisherFunctions.some(x => x.mode === route.query.mode) ? route.query.mode : 'list')
const title = computed(() => publisherFunctions.find(x => x.mode === mode.value)?.label)
const items = ref([]), total = ref(0), page = ref(0), q = ref('')
const loading = ref(false), busy = ref(false), error = ref('')
const selected = ref(null), allTags = ref([]), tagIds = ref([]), builds = ref([]), media = ref([]), reason = ref('')
const emptyForm = () => ({ name: '', price: 0, description: '', coverUrl: '', releaseDate: '' })
const form = ref(emptyForm()), editorForm = ref(null), baseline = ref('')
const labels = { DRAFT: '草稿', PUBLISHED: '已上架', COMING_SOON: '即將上架', OFF_SHELF: '已下架' }
const tick = ref(Date.now()), clockOffset = ref(0)
const dialog = ref(null), dialogError = ref(''), discardIndex = ref(-1)
let clockTimer, listRun = 0, detailRun = 0, allowRoute = false, pendingLeave = null
const canRestore = computed(() => selected.value?.can_restore && selected.value.restore_until > tick.value + clockOffset.value)
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((selected.value?.restore_until || 0) - tick.value - clockOffset.value) / 1000))
  return Math.floor(seconds / 3600) + ' 小時 ' + Math.floor(seconds % 3600 / 60) + ' 分 ' + seconds % 60 + ' 秒'
})
const editable = computed(() => selected.value && !selected.value.pending_review_type && selected.value.off_shelf_source !== 'ADMIN' && !canRestore.value && ['DRAFT', 'OFF_SHELF'].includes(selected.value.status))
const checks = computed(() => selected.value?.publish_checks || [])
const publishable = computed(() => editable.value && checks.value.length === 4 && checks.value.every(x => x.complete))
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
const downloadSpace = (() => {
  try { const url = new URL(import.meta.env.VITE_PUBLISHER_DOWNLOAD_SPACE_URL || ''); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : '' } catch { return '' }
})()
function payload() { return { ...form.value, price: Number(form.value.price), releaseDate: form.value.releaseDate || null } }
function buildPayload() { return builds.value.map(x => ({ ...x, fileSize: x.fileSize === '' || x.fileSize == null ? null : Number(x.fileSize) })) }
function snapshot() {
  if (['new', 'edit'].includes(mode.value)) return JSON.stringify(payload())
  if (mode.value === 'tags') return JSON.stringify([...tagIds.value].sort((a,b) => a-b))
  if (mode.value === 'builds') return JSON.stringify(buildPayload())
  if (mode.value === 'media') return JSON.stringify(media.value)
  return ''
}
const dirty = computed(() => (mode.value === 'new' ? !selected.value || editable.value : editable.value) && baseline.value !== snapshot())
function assign(game) {
  selected.value = game
  clockOffset.value = game.server_time - Date.now()
  tick.value = Date.now()
  form.value = { name: game.game_name, price: game.price, description: game.description || '', coverUrl: game.cover_url || '', releaseDate: game.release_date ? String(game.release_date).replace(' ', 'T').slice(0, 16) : '' }
  tagIds.value = game.tags.map(x => x.tag_id)
  builds.value = game.builds.map(x => ({ id: x.build_id, version: x.version, fileUrl: x.file_url, fileSize: x.file_size, status: x.status }))
  media.value = game.media.map(x => ({ type: x.media_type, url: x.media_url, order: x.display_order }))
  reason.value = game.review_request_reason || ''
  baseline.value = snapshot()
}
async function load() {
  const run = ++listRun
  loading.value = true
  try {
    const result = await api('/aki/publisher/games?page=' + page.value + '&q=' + encodeURIComponent(q.value.trim()))
    if (run !== listRun) return
    items.value = result.items; total.value = result.total
    if (!items.value.length && page.value > 0) { page.value--; return load() }
  } catch (e) { if (run === listRun) error.value = e.message }
  finally { if (run === listRun) loading.value = false }
}
async function choose(id) {
  const run = ++detailRun
  selected.value = null; error.value = ''
  try { const game = await api('/aki/publisher/games/' + id); if (run === detailRun) assign(game) }
  catch (e) { if (run === detailRun) error.value = e.message }
}
async function goTo(next, id = selected.value?.game_id) {
  allowRoute = true
  try { await router.replace({ path: '/aki/publisher/games', query: { mode: next, ...(id ? { game: id } : {}) } }) }
  finally { allowRoute = false }
}
function select(game) { if (!busy.value) router.replace({ path: '/aki/publisher/games', query: { mode: mode.value, game: game.game_id } }) }
function search() { page.value = 0; load() }
function endpoint(suffix) { return '/aki/publisher/games/' + selected.value.game_id + '/' + suffix }
async function saveEditor(next = '', syncRoute = true) {
  if (busy.value) return null
  if (!editorForm.value?.reportValidity()) { dialogError.value = '請先完成必填欄位並修正格式，再儲存。'; return null }
  const current = mode.value
  let path, body, method = 'PUT'
  if (['new', 'edit'].includes(current)) {
    path = '/aki/publisher/games' + (selected.value ? '/' + selected.value.game_id : '')
    method = selected.value ? 'PUT' : 'POST'; body = payload()
  } else {
    path = endpoint(current)
    body = current === 'tags' ? { tagIds: tagIds.value } : current === 'builds' ? { builds: buildPayload() } : { media: media.value }
  }
  busy.value = true; error.value = ''; dialogError.value = ''
  try {
    const game = await api(path, { method, body })
    assign(game); await load()
    notifyPublisher(['new','builds'].includes(current) ? '草稿已儲存' : current === 'edit' ? '修改已儲存' : '設定已儲存')
    if (syncRoute && (next || current === 'new')) await goTo(next || current, game.game_id)
    return game
  } catch (e) { error.value = e.message; dialogError.value = e.message; return null }
  finally { busy.value = false }
}
function submitEditor(event) { saveEditor(event.submitter?.dataset.next || '') }
function openDialog(type, index = -1) { dialogError.value = ''; discardIndex.value = index; dialog.value = type }
function closeDialog() {
  if (busy.value) return
  if (pendingLeave) { pendingLeave.resolve(false); pendingLeave = null }
  dialog.value = null
}
function guard(to) {
  if (allowRoute || !session.user) return true
  if (busy.value) return false
  if (!dirty.value) return true
  if (pendingLeave) return false
  openDialog('leave')
  return new Promise(resolve => { pendingLeave = { to, resolve } })
}
onBeforeRouteUpdate(guard)
onBeforeRouteLeave(guard)
async function finishLeave(save) {
  const pending = pendingLeave
  if (!pending) return
  const game = save ? await saveEditor('', false) : null
  if (save && !game) return
  baseline.value = snapshot(); pendingLeave = null; dialog.value = null
  const to = pending.to
  if (game && to.path === '/aki/publisher/games' && to.query.mode !== 'new' && !to.query.game) pending.resolve({ path: to.path, query: { ...to.query, game: game.game_id }, hash: to.hash })
  else pending.resolve(true)
}
async function action(suffix, message, next = '') {
  if (busy.value) return
  busy.value = true; error.value = ''; dialogError.value = ''
  try {
    const game = await api(endpoint(suffix), { method: 'POST', ...(suffix !== 'restore' ? { body: { reason: reason.value } } : {}) })
    assign(game); await load(); dialog.value = null; notifyPublisher(message)
    if (next) await goTo(next, game.game_id)
  } catch (e) { error.value = e.message; dialogError.value = e.message }
  finally { busy.value = false }
}
function discardBuild() {
  const index = discardIndex.value, build = builds.value[index]
  if (!build) return closeDialog()
  const original = selected.value.builds.find(x => x.build_id === build.id)
  if (original) builds.value[index] = { id: original.build_id, version: original.version, fileUrl: original.file_url, fileSize: original.file_size, status: original.status }
  else builds.value.splice(index, 1)
  closeDialog()
}
function beforeUnload(event) { if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { clockTimer = setInterval(() => { tick.value = Date.now() }, 1000); window.addEventListener('beforeunload', beforeUnload) })
onUnmounted(() => { clearInterval(clockTimer); window.removeEventListener('beforeunload', beforeUnload); pendingLeave?.resolve(false) })
watch(() => [route.query.mode, route.query.game], async () => {
  detailRun++; selected.value = null; error.value = ''
  form.value = emptyForm(); tagIds.value = []; builds.value = []; media.value = []; reason.value = ''; baseline.value = snapshot()
  if (route.query.game && /^\d+$/.test(String(route.query.game))) await choose(route.query.game)
}, { immediate: true })
load()
api('/aki/publisher/tags').then(data => { allTags.value = data }).catch(e => { error.value = e.message })
</script>

<template>
  <section class="platform-dashboard publisher-games">
    <header class="publisher-page-heading">
      <div><span class="eyebrow">PUBLISHER CENTER / GAME WORKSPACE</span><h1>{{ title }}</h1><p class="muted">{{ publisherFunctions.find(x => x.mode === mode)?.description }}</p></div>
      <RouterLink class="secondary publisher-home-link" to="/aki/publisher/overview"><UiIcon name="dashboard" />發行商主頁</RouterLink>
    </header>
    <div class="publisher-management-layout">
      <nav class="publisher-tools" aria-label="發行商功能">
        <div class="publisher-navigation-heading"><UiIcon name="game" /><strong>遊戲工作區</strong></div>
        <section v-for="group in publisherGroups" :key="group.title" class="publisher-nav-group"><h2>{{ group.title }}</h2>
          <RouterLink v-for="key in group.modes" :key="key" :to="{ ...publisherLink(key), query: { mode: key, ...(key !== 'new' && selected ? { game: selected.game_id } : {}) } }" :class="{ active: mode === key }" :aria-current="mode === key ? 'page' : undefined"><UiIcon :name="publisherIcons[key]" /><span>{{ publisherFunctions.find(x => x.mode === key).label }}</span></RouterLink>
        </section>
      </nav>
      <div class="publisher-management-main">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="publisher-work-columns" :class="{ 'publisher-create': mode === 'new' }">
      <section v-if="mode !== 'new'" class="publisher-panel publisher-list-panel"><header class="publisher-panel-heading"><h2>遊戲清單</h2><span>{{ total }} 款</span></header>
        <form class="publisher-game-search" @submit.prevent="search"><input v-model="q" maxlength="100" aria-label="搜尋我的遊戲" placeholder="搜尋遊戲名稱"><button class="primary" :disabled="loading || busy">搜尋</button></form>
        <p v-if="loading" role="status">載入中…</p>
        <p v-else-if="!items.length" class="muted">目前沒有符合條件的遊戲。</p>
        <button v-for="game in items" :key="game.game_id" class="publisher-game-row" :class="{ active: selected?.game_id === game.game_id }" :disabled="busy" @click="select(game)">
          <span class="publisher-game-info"><span class="publisher-game-thumb"><UiIcon name="game" /></span><span><strong>{{ game.game_name }}</strong><small>{{ labels[game.status] || game.status }}<template v-if="game.pending_review_type"> · {{ game.pending_review_type === 'PUBLISH' ? '待上架審核' : '待下架審核' }}</template></small></span></span>
          <span class="publisher-buyers"><b>{{ game.buyer_count }}</b> 人購買</span>
        </button>
        <div class="publisher-pagination"><button :disabled="page === 0 || loading || busy" @click="page--; load()">上一頁</button><span>{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><button :disabled="(page + 1) * 20 >= total || loading || busy" @click="page++; load()">下一頁</button></div>
      </section>
      <section class="publisher-panel publisher-editor">
        <template v-if="mode === 'new' || selected">
          <p v-if="selected?.off_shelf_source === 'ADMIN'" class="publisher-pending">管理員強制下架原因：{{ selected.off_shelf_reason }}</p><header v-if="selected" class="publisher-selected-heading"><div class="publisher-game-title"><span class="publisher-selected-icon"><UiIcon name="game" /></span><div><h2>{{ selected.game_name }}</h2><span class="publisher-status-pill" :class="selected.status.toLowerCase()">{{ selected.pending_review_type ? '審核中' : labels[selected.status] }}</span></div></div><span class="publisher-selected-buyers"><UiIcon name="users" />{{ selected.buyer_count }} 人購買</span><p v-if="selected.pending_review_type" class="publisher-pending">{{ selected.pending_review_type === 'PUBLISH' ? '上架' : '下架' }}申請待審核 · {{ date(selected.review_requested_at) }}</p></header>
          <form v-if="['new', 'edit'].includes(mode)" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
            <fieldset :disabled="busy || (selected && !editable)">
              <label>遊戲名稱<input v-model="form.name" maxlength="150" required></label>
              <div class="publisher-form-pair"><label>售價（NT$）<input v-model.number="form.price" type="number" min="0" max="99999999.99" step="0.01" required></label><label>預定發行時間<input v-model="form.releaseDate" type="datetime-local"></label></div>
              <label>遊戲介紹<textarea v-model="form.description" maxlength="20000" rows="6"></textarea></label>
              <label>封面網址<input v-model="form.coverUrl" type="url" maxlength="500" placeholder="https://…"></label>
              <PublisherSaveActions :secondary="mode === 'new' ? '儲存草稿' : '僅儲存修改'" :primary="mode === 'new' ? '儲存並前往：設定遊戲版本' : '儲存並前往：設定標籤與版本'" :next="mode === 'new' ? 'builds' : 'tags'" :busy="busy" />
            </fieldset>
          </form>
          <template v-else-if="mode === 'publish'">
            <div class="publisher-notice-toolbar"><h3>上架條件檢核</h3><button class="secondary" @click="goTo('preview')">預覽商品頁</button></div>
            <p class="muted">完成必要設定後即可送審，審核通過後才會上架。</p>
            <ul class="publisher-preflight"><li v-for="check in checks" :key="check.key" :class="{ complete: check.complete }"><UiIcon :name="check.complete ? 'check' : 'alert'" /><div><strong>{{ check.label }}</strong><small>{{ check.complete ? '已完成' : '未完成' }}</small></div><button v-if="!check.complete && editable" class="secondary" @click="goTo(check.key === 'media' && !selected.cover_url ? 'edit' : check.mode)">前往設定</button></li></ul>
            <form class="publisher-form" @submit.prevent="openDialog('publish')"><label>補充說明<textarea v-model="reason" maxlength="500" rows="3" :disabled="busy || !editable"></textarea></label><button class="primary" :disabled="busy || !publishable">送出上架審核</button><p v-if="editable && !publishable" class="muted">尚有未完成的必要設定，無法送出審核。</p><p v-else-if="!editable" class="publisher-pending">目前狀態無法送審。待審核、24小時恢復期間及管理員強制下架的遊戲不可重複申請。</p></form>
          </template>
          <form v-else-if="mode === 'builds'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
            <fieldset :disabled="busy || !editable">
              <div v-for="(build, index) in builds" :key="index" class="publisher-resource-card"><h3>版本 {{ index + 1 }}</h3><label>版本號<input v-model="build.version" maxlength="30" required></label><label>版本檔案網址<input v-model="build.fileUrl" type="url" maxlength="500" required></label><div class="publisher-form-pair"><label>檔案大小（Bytes，可留空）<input v-model="build.fileSize" type="number" min="0" step="1" @change="build.fileSize = build.fileSize === '' ? null : Number(build.fileSize)"></label><label>狀態<select v-model="build.status"><option value="ACTIVE">啟用</option><option value="INACTIVE">停用</option></select></label></div><button type="button" class="text-button" @click="openDialog('discardBuild', index)">放棄此版本變更</button></div>
              <div class="publisher-button-row"><button type="button" :disabled="builds.length >= 50" @click="builds.push({ id: null, version: '', fileUrl: '', fileSize: null, status: 'ACTIVE' })">新增下一個版本</button></div><div class="publisher-download-space"><a v-if="downloadSpace" :href="downloadSpace" target="_blank" rel="noopener noreferrer">前往下載空間複製 ↗</a><button v-else type="button" disabled>前往下載空間複製</button><small v-if="!downloadSpace">尚未設定團隊下載空間網址。</small></div><PublisherSaveActions secondary="儲存草稿" primary="儲存並前往：上架檢核" next="publish" :busy="busy" />
            </fieldset><p class="muted">此處管理版本資訊與檔案網址；檔案請先放到團隊的下載空間。既有版本可停用，不直接刪除。</p>
          </form>
          <form v-else-if="mode === 'tags'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
            <fieldset :disabled="busy || !editable"><p class="muted">只能勾選平台提供的8種標籤，可複選。</p><div class="publisher-tag-grid"><label v-for="tag in allTags" :key="tag.tag_id"><input v-model="tagIds" type="checkbox" :value="tag.tag_id">{{ tag.tag_name }}</label></div><p v-if="!allTags.length" class="muted">尚未建立可選用的標籤。</p><PublisherSaveActions primary="儲存並前往：設定圖片與影片" next="media" :busy="busy" /></fieldset>
          </form>
          <form v-else-if="mode === 'media'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
            <fieldset :disabled="busy || !editable"><div v-for="(item, index) in media" :key="index" class="publisher-resource-card"><div class="publisher-form-pair"><label>類型<select v-model="item.type"><option value="IMAGE">圖片</option><option value="VIDEO">影片</option></select></label><label>顯示順序<input v-model.number="item.order" type="number" min="1" max="1000" required></label></div><label>媒體網址<input v-model="item.url" type="url" maxlength="500" required></label><button type="button" @click="media.splice(index, 1)">移除</button></div><div class="publisher-button-row"><button type="button" :disabled="media.length >= 50" @click="media.push({ type: 'IMAGE', url: '', order: media.length + 1 })">新增圖片或影片</button></div><PublisherSaveActions primary="儲存並前往：上架檢核" next="publish" :busy="busy" /></fieldset><p class="muted">封面請至「修改遊戲」設定；圖片與影片使用已上傳的網址。</p>
          </form>
          <PublisherPreview v-else-if="mode === 'preview'" :game="selected" @navigate="goTo" />
          <PublisherOperations v-else :game="selected" :mode="mode" :can-restore="!!canRestore" :remaining="remaining" :editable="!!editable" :busy="busy" @off-shelf="reason = ''; openDialog('offShelf')" @restore="action('restore', '已恢復原上架狀態')" @navigate="goTo" />
          <p v-if="!editable && ['edit', 'builds', 'tags', 'media'].includes(mode)" class="publisher-pending">已上架、待審核及24小時恢復期間保留原資料，不可修改。管理員強制下架的遊戲不能自行修改或送審。</p>
        </template>
        <div v-else class="publisher-empty"><span><UiIcon :name="publisherIcons[mode]" /></span><h2>選擇一款遊戲開始</h2><p>從遊戲清單選取作品，即可{{ title }}。</p></div>
      </section>
    </div>
      </div>
    </div>
  </section>
  <PublisherDialog :open="!!dialog" :busy="busy" :title="dialog === 'leave' ? '尚有未儲存的變更' : dialog === 'publish' ? '確認送出上架審核' : dialog === 'offShelf' ? '確認立即下架' : '放棄此版本變更'" :description="dialog === 'leave' ? '您有尚未儲存的資料，要先儲存再前往嗎？' : dialog === 'publish' ? '送出後在審核完成前將無法修改遊戲資料，確定要送出嗎？' : dialog === 'offShelf' ? '下架立即停止販售，24小時內可恢復。遊戲資料與購買紀錄會保留。' : '確定要捨棄此版本尚未儲存的變更嗎？既有版本會還原，新增版本會移除。'" @close="closeDialog">
    <label v-if="dialog === 'offShelf'">下架說明（選填）<textarea v-model="reason" maxlength="500" rows="3" :disabled="busy"></textarea></label>
    <p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p>
    <template #actions>
      <button class="secondary" :disabled="busy" @click="closeDialog">繼續編輯 / 取消</button>
      <template v-if="dialog === 'leave'"><button class="secondary" :disabled="busy" @click="finishLeave(false)">放棄變更</button><button class="primary" :disabled="busy" @click="finishLeave(true)">儲存並前往</button></template>
      <button v-else-if="dialog === 'publish'" class="primary" :disabled="busy || !publishable" @click="action('publish', '已送出上架審核', 'history')">確認送出審核</button>
      <button v-else-if="dialog === 'offShelf'" class="publisher-danger" :disabled="busy" @click="action('off-shelf', '已下架，24小時內可恢復')">確認立即下架</button>
      <button v-else class="publisher-danger" :disabled="busy" @click="discardBuild">放棄此版本變更</button>
    </template>
  </PublisherDialog>
</template>

<script setup>
import PublisherBuildLink from '../components/PublisherBuildLink.vue'
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter, RouterLink, onBeforeRouteUpdate, onBeforeRouteLeave } from 'vue-router'
import { api, session } from '../api.js'
import { publisherFunctions, publisherLink, publisherGroups, publisherIcons } from '../publisher-functions.js'
import { notifyPublisher } from '../publisher-notifications.js'
import UiIcon from '../components/UiIcon.vue'
import PublisherDialog from '../components/PublisherDialog.vue'
import PublisherSaveActions from '../components/PublisherSaveActions.vue'
import PublisherPreview from '../components/PublisherPreview.vue'
import PublisherStoreEditor from '../components/PublisherStoreEditor.vue'
import PublisherOperations from '../components/PublisherOperations.vue'

const route = useRoute()
const router = useRouter()
const mode = computed(() => publisherFunctions.some(x => x.mode === route.query.mode) ? route.query.mode : 'list')
const title = computed(() => publisherFunctions.find(x => x.mode === mode.value)?.label)
const items = ref([])
const total = ref(0)
const page = ref(0)
const q = ref('')
const uploading = ref(false)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const selected = ref(null)
const allTags = ref([])
const tagIds = ref([])
const builds = ref([])
const media = ref([])
const reason = ref('')
const emptyForm = () => ({
  name: '',
  price: 0,
  description: '',
  coverUrl: '',
  releaseDate: ''
})
const form = ref(emptyForm()), editorForm = ref(null), baseline = ref('')
const labels = {
  PENDING_REVIEW: '待審核',
  PENDING_OFF_SHELF: '預定下架',
  DRAFT: '草稿',
  PUBLISHED: '已上架',
  COMING_SOON: '即將上架',
  OFF_SHELF: '已下架'
}
const tick = ref(Date.now())
const clockOffset = ref(0)
const dialog = ref(null)
const dialogError = ref('')
const tagValidationError = ref('')
let clockTimer
let listRun = 0
let detailRun = 0
let allowRoute = false
let pendingLeave = null
const canRestore = computed(() => selected.value?.can_restore && selected.value.restore_until > tick.value + clockOffset.value)
const remaining = computed(() => {
  const seconds = Math.max(0, Math.ceil(((selected.value?.restore_until || 0) - tick.value - clockOffset.value) / 1000))
  return Math.floor(seconds / 3600) + ' 小時 ' + Math.floor(seconds % 3600 / 60) + ' 分 ' + seconds % 60 + ' 秒'
})
const editable = computed(() => selected.value && !selected.value.pending_review_type && selected.value.off_shelf_source !== 'ADMIN' && !canRestore.value && ['DRAFT', 'OFF_SHELF'].includes(selected.value.status))
// 遊戲標籤頁僅開放已上架且沒有進行中審核的遊戲修改。
const canEditPublishedContent = computed(() => selected.value?.status === 'PUBLISHED' && !selected.value?.pending_review_type && selected.value?.off_shelf_source !== 'ADMIN' && !canRestore.value)
const canEditTags = computed(() => selected.value?.status === 'PUBLISHED' && !selected.value?.pending_review_type && selected.value?.off_shelf_source !== 'ADMIN' && !canRestore.value)
const canManageBuilds = computed(() => editable.value || (selected.value?.status === 'PUBLISHED' && !selected.value.pending_review_type))
// 只顯示唯一啟用的目前版本及最近一筆歷史版本；其餘紀錄保留在資料庫。
const visibleBuilds = computed(() => {
  const saved = builds.value.map((build, index) => ({ build, index }))
    .filter(item => item.build.id != null)
    .sort((a, b) => Number(a.build.id) - Number(b.build.id))
  const current = [...saved].reverse().find(item => item.build.status === 'ACTIVE') ?? saved.at(-1)
  const history = [...saved].reverse().find(item => item.build.id !== current?.build.id)
  const pending = builds.value.map((build, index) => ({ build, index }))
    .filter(item => item.build.id == null)
  return [...(history ? [history] : []), ...(current ? [current] : []), ...pending]
})
const currentBuildId = computed(() =>
  [...builds.value].reverse().find(build => build.id != null && build.status === 'ACTIVE')?.id
  ?? [...builds.value].reverse().find(build => build.id != null)?.id
)
const hasPendingBuild = computed(() => builds.value.some(build => build.id == null))
const hasStatusChanges = computed(() => selected.value?.status === 'PUBLISHED' && builds.value.some(build => {
  const original = selected.value?.builds?.find(item => item.build_id === build.id)
  return original && original.status !== build.status
}))
function changeBuildStatus(build) {
  if (selected.value?.status === 'DRAFT') { build.status = 'ACTIVE'; return }
  if (selected.value?.status === 'PUBLISHED' && build.status === 'ACTIVE')
    builds.value.forEach(item => { if (item !== build) item.status = 'INACTIVE' })
}
const checks = computed(() => selected.value?.publish_checks || [])
const publishable = computed(() => editable.value && checks.value.length === 4 && checks.value.every(x => x.complete))
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
function payload() {
  return {
    ...form.value,
    price: Number(form.value.price),
    releaseDate: form.value.releaseDate || null
  }
}
// 新增版本依上一版提供三種語意化版本號；既有版本號不允許在此改名。
const versionPattern = /(?:^|[^\d])(\d+)\.(\d+)\.(\d+)$/
function versionChoices(index) {
  const current = builds.value[index]
  if (!current || current.id != null) return []
  // 依清單順序找前一個版本，避免同時新增多筆時產生重複版本號。
  const previous = builds.value.slice(0, index).at(-1)
  if (!previous) return [{ value: '1.0.0', label: '1.0.0（第一版）' }]
  const match = String(previous.version || '').trim().match(versionPattern)
  if (!match) return []
  const [, majorText, minorText, patchText] = match
  const major = Number(majorText), minor = Number(minorText), patch = Number(patchText)
  if (![major, minor, patch].every(Number.isSafeInteger)) return []
  return [
    { value: `${major}.${minor}.${patch + 1}`, label: `${major}.${minor}.${patch + 1}（小幅修正）` },
    { value: `${major}.${minor + 1}.0`, label: `${major}.${minor + 1}.0（功能更新）` },
    { value: `${major + 1}.0.0`, label: `${major + 1}.0.0（重大改版）` }
  ]
}
watch(() => builds.value.map(b => b.version).join('|'), () => {
  builds.value.forEach((build, index) => {
    if (build.id != null) return
    const choices = versionChoices(index)
    if (!choices.some(choice => choice.value === build.version)) build.version = choices[0]?.value || ''
  })
})
function addNextBuild() {
  // 草稿尚無版本時允許建立第一版；已有版本的草稿不允許再新增。
  if (selected.value?.status === 'DRAFT' && builds.value.length > 0) return
  if (!canManageBuilds.value || builds.value.length >= 50 || hasPendingBuild.value) return
  const choices = versionChoices(builds.value.length)
  builds.value.push({ id: null, version: choices[0]?.value || '', fileUrl: '', fileSize: null, status: 'ACTIVE', source: 'upload' })
}
function buildPayload() {
  return builds.value.map(x => ({
    id: x.id, version: x.version, fileUrl: x.fileUrl, status: x.status,
    fileSize: x.fileSize === '' || x.fileSize == null ? null : Number(x.fileSize)
  }))
}
function snapshot() {
  if (['new', 'edit'].includes(mode.value))
    return JSON.stringify({
      game: payload(),
      tags: tagIds.value,
      media: media.value
    })
  if (mode.value === 'tags')
    return JSON.stringify([...tagIds.value].sort((a, b) => a - b))
  if (mode.value === 'builds')
    return JSON.stringify(buildPayload())
  if (mode.value === 'media')
    return JSON.stringify(media.value)
  return ''
}
const dirty = computed(() => (mode.value === 'new' ? !selected.value || editable.value : mode.value === 'builds' ? canManageBuilds.value : mode.value === 'edit' ? (editable.value || canEditPublishedContent.value) : mode.value === 'media' ? (editable.value || canEditPublishedContent.value) : mode.value === 'tags' ? canEditTags.value : editable.value) && baseline.value !== snapshot())
function assign(game) {
  selected.value = game
  clockOffset.value = game.server_time - Date.now()
  tick.value = Date.now()
  form.value = {
    name: game.game_name,
    price: game.price,
    description: game.description || '',
    coverUrl: game.cover_url || '',
    releaseDate: game.release_date ? String(game.release_date).replace(' ', 'T').slice(0, 16) : ''
  }
  tagIds.value = game.tags.map(x => x.tag_id)
  builds.value = game.builds.map(x => ({
    id: x.build_id,
    version: x.version,
    fileUrl: x.file_url,
    fileSize: x.file_size, status: x.status,
    source: x.file_url?.startsWith('/api/publisher-assets/builds/') ? 'upload' : 'url' 
  }))
  media.value = game.media.map(x => ({
    type: x.media_type,
    url: x.media_url,
    order: x.display_order
  }))
  reason.value = game.review_request_reason || ''
  baseline.value = snapshot()
}
async function load() {
  const run = ++listRun
  loading.value = true
  try {
    const result = await api('/workspace/publisher/games?page=' + page.value + '&q=' + encodeURIComponent(q.value.trim()))
    if (run !== listRun) return
    items.value = result.items; total.value = result.total
    if (!items.value.length && page.value > 0) { page.value--; return load() }
  } catch (e) {
    if (run === listRun) error.value = e.message
  } finally {
    if (run === listRun) loading.value = false
  }
}
async function choose(id) {
  const run = ++detailRun
  selected.value = null; error.value = ''
  try {
    const game = await api('/workspace/publisher/games/' + id); if (run === detailRun) assign(game)
  } catch (e) {
    if (run === detailRun) error.value = e.message
  }
}
async function goTo(next, id = selected.value?.game_id) {
  allowRoute = true
  try {
    await router.replace({
      path: '/publisher/games',
      query: { mode: next, ...(id ? { game: id } : {}) }
    })
  } finally { allowRoute = false }
}
function select(game) {
  if (!busy.value) router.replace({
    path: '/publisher/games',
    query: {
      mode: mode.value,
      game: game.game_id
    }
  })
}
function search() { page.value = 0; load() }
function endpoint(suffix) {
  return '/workspace/publisher/games/' + selected.value.game_id + '/' + suffix
}
async function saveEditor(next = '', syncRoute = true) {
  if (busy.value || uploading.value)
    return null
  if (!editorForm.value?.reportValidity()) {
    dialogError.value = '請先完成必填欄位並修正格式，再儲存。';
    return null
  }
  const current = mode.value
  tagValidationError.value = ''
  if (['new', 'edit', 'tags'].includes(current) && !tagIds.value.length) {
    tagValidationError.value = '請至少選擇一個遊戲標籤，再進行儲存。'
    return null
  }
  if (current === 'builds' && selected.value?.status === 'PUBLISHED' && !hasPendingBuild.value) {
    dialogError.value = '請先新增下一個版本，再儲存更新。'
    error.value = dialogError.value
    return null
  }
  let path, body, method = 'PUT'
  if (current === 'edit' && canEditPublishedContent.value) {
    path = endpoint('published-content')
    body = { description: form.value.description, tags: { tagIds: tagIds.value }, media: { media: media.value } }
  } else if (['new', 'edit'].includes(current)) {
    path = '/workspace/publisher/games' + (selected.value ? '/' + selected.value.game_id : '') + '/document'
    method = selected.value ? 'PUT' : 'POST'; body = { game: payload(), tags: { tagIds: tagIds.value }, media: { media: media.value } }
  } else {
    path = endpoint(current)
    body = current === 'tags' ? { tagIds: tagIds.value } : current === 'builds' ? { builds: buildPayload() } : { media: media.value }
  }
  busy.value = true; error.value = ''; dialogError.value = ''
  try {
    const game = await api(path, { method, body })
    assign(game); await load()
    notifyPublisher(current === 'builds' && game.status === 'PUBLISHED' ? '新版本已儲存' : ['new', 'builds'].includes(current) ? '草稿已儲存' : current === 'edit' ? '修改已儲存' : '設定已儲存')
    if (syncRoute && (next || current === 'new')) await goTo(next || current, game.game_id)
    return game
  } catch (e) { error.value = e.message; dialogError.value = e.message; return null }
  finally { busy.value = false }
}
function submitEditor(event) {
  const next = event.submitter?.dataset.next || ''
  // 草稿版本按「儲存草稿」時，先讓發行商確認。
  if (mode.value === 'builds' && selected.value?.status === 'DRAFT' && !next) {
    if (!editorForm.value?.reportValidity()) return
    openDialog('saveDraftBuild')
    return
  }
  saveEditor(next)
}
async function confirmSaveDraftBuild() {
  const game = await saveEditor()
  if (game) dialog.value = null
}
function openDialog(type) { dialogError.value = ''; dialog.value = type }
function closeDialog() {
  if (busy.value) return
  if (pendingLeave) { pendingLeave.resolve(false); pendingLeave = null }
  dialog.value = null
}
function guard(to) {
  if (allowRoute || !session.user) return true
  if (busy.value || uploading.value) return false
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
  if (game && to.path === '/publisher/games' && to.query.mode !== 'new' && !to.query.game) pending.resolve({ path: to.path, query: { ...to.query, game: game.game_id }, hash: to.hash })
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
async function confirmDeleteDraft() {
  if (busy.value || selected.value?.status !== 'DRAFT' || selected.value.pending_review_type) return
  busy.value = true; error.value = ''; dialogError.value = ''
  try {
    await api('/workspace/publisher/games/' + selected.value.game_id, { method: 'DELETE' })
    dialog.value = null
    selected.value = null
    builds.value = []; media.value = []; tagIds.value = []
    baseline.value = snapshot()
    await load()
    notifyPublisher('草稿已永久刪除')
    await goTo('list', null)
  } catch (e) {
    error.value = e.message; dialogError.value = e.message
  } finally { busy.value = false }
}
function changeBuildSource(build) { build.fileUrl = ''; build.fileSize = null }
async function uploadBuild(event, build) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || busy.value || uploading.value || !canManageBuilds.value) return
  if (!/\.zip$/i.test(file.name) || !file.size || file.size > 100 * 1024 * 1024) {
    error.value = '請選擇 ZIP 檔案，每個最多 100 MB。'; return
  }
  uploading.value = true; error.value = ''
  try {
    const body = new FormData(); body.append('file', file); body.append('kind', 'BUILD')
    const result = await api('/publisher/files', { method: 'POST', body })
    build.fileUrl = result.url; build.fileSize = result.fileSize
    notifyPublisher('ZIP 已上傳，請儲存版本設定')
  } catch (e) { error.value = e.message }
  finally { uploading.value = false }
}
function beforeUnload(event) { if (dirty.value || busy.value || uploading.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { clockTimer = setInterval(() => { tick.value = Date.now() }, 1000); window.addEventListener('beforeunload', beforeUnload) })
onUnmounted(() => { clearInterval(clockTimer); window.removeEventListener('beforeunload', beforeUnload); pendingLeave?.resolve(false) })
watch(() => [route.query.mode, route.query.game], async () => {
  if (route.query.mode === 'media') { await goTo('edit', route.query.game); return }
  detailRun++; selected.value = null; error.value = ''
  form.value = emptyForm(); tagIds.value = []; builds.value = []; media.value = []; reason.value = ''; baseline.value = snapshot()
  if (route.query.game && /^\d+$/.test(String(route.query.game))) await choose(route.query.game)
}, { immediate: true })
load()
api('/workspace/publisher/tags').then(data => { allTags.value = data }).catch(e => { error.value = e.message })
</script>

<template>
  <section class="platform-dashboard publisher-games">
    <header class="publisher-page-heading">
      <div><span class="eyebrow">PUBLISHER CENTER / GAME WORKSPACE</span>
        <h1>{{ title }}</h1>
        <p class="muted">{{publisherFunctions.find(x => x.mode === mode)?.description}}</p>
      </div>
      <RouterLink class="secondary publisher-home-link" to="/publisher/overview">
        <UiIcon name="dashboard" />發行商主頁
      </RouterLink>
    </header>
    <div class="publisher-management-layout">
      <div class="publisher-management-main">
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <div class="publisher-work-columns"
          :class="{ 'publisher-create': mode === 'new', 'publisher-editing': ['new', 'edit'].includes(mode) }">
          <section v-if="mode !== 'new'" class="publisher-panel publisher-list-panel">
            <header class="publisher-panel-heading">
              <h2>遊戲清單</h2>
              <span>{{ total }} 款</span>
            </header>
            <form class="publisher-game-search" @submit.prevent="search">
              <input v-model="q" maxlength="100" aria-label="搜尋我的遊戲" placeholder="搜尋遊戲名稱">
              <button class="primary" :disabled="loading || busy">搜尋</button>
            </form>
            <p v-if="loading" role="status">載入中…</p>
            <p v-else-if="!items.length" class="muted">目前沒有符合條件的遊戲。</p>
            <button v-for="game in items" :key="game.game_id" class="publisher-game-row"
              :class="{ active: selected?.game_id === game.game_id }" :disabled="busy" @click="select(game)">
              <span class="publisher-game-info">
                <span class="publisher-game-thumb">
                  <UiIcon name="game" />
                </span>
                <span>
                  <strong>{{ game.game_name }}</strong>
                  <small>{{ labels[game.status] || game.status }}
                    <template v-if="game.pending_review_type">
                      · {{ game.pending_review_type === 'PUBLISH' ? '待上架審核' : '待下架審核' }}
                    </template>
                  </small>
                </span>
              </span>
              <span class="publisher-buyers">
                <b>{{ game.buyer_count }}</b>
                人購買
              </span>
            </button>
            <div class="publisher-pagination">
              <button :disabled="page === 0 || loading || busy" @click="page--; load()">上一頁</button>
              <span>
                {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 20)) }}
              </span>
              <button :disabled="(page + 1) * 20 >= total || loading || busy" @click="page++; load()">下一頁</button>
            </div>
          </section>
          <section class="publisher-panel publisher-editor">
            <template v-if="mode === 'new' || selected">
              <p v-if="selected?.off_shelf_source === 'ADMIN'" class="publisher-pending">
                管理員強制下架原因：{{ selected.off_shelf_reason }}
              </p>
              <header v-if="selected" class="publisher-selected-heading">
                <div class="publisher-game-title">
                  <span class="publisher-selected-icon">
                    <UiIcon name="game" />
                  </span>
                  <div>
                    <h2>{{ selected.game_name }}</h2>
                    <span class="publisher-status-pill" :class="selected.status.toLowerCase()">
                      {{ selected.pending_review_type ? '審核中' : labels[selected.status] }}
                    </span>
                  </div>
                </div>
                <span class="publisher-selected-buyers">
                  <UiIcon name="users" />{{ selected.buyer_count }} 人購買
                </span>
                <p v-if="selected.pending_review_type" class="publisher-pending">
                  {{ selected.pending_review_type === 'PUBLISH' ? '上架' : '下架' }}申請待審核
                </p>
              </header>
              <form v-if="['new', 'edit'].includes(mode)" class="publisher-form" ref="editorForm"
                @submit.prevent="submitEditor">
                <PublisherStoreEditor v-model:form="form" v-model:tag-ids="tagIds" v-model:media="media" :tags="allTags"
                  :disabled="!!selected && !editable && !canEditPublishedContent" :published-only="!!canEditPublishedContent" :busy="busy" :tag-error="tagValidationError && !tagIds.length ? tagValidationError : ''" @uploading="uploading = $event" />
              </form>
              <template v-else-if="mode === 'publish'">
                <div class="publisher-notice-toolbar">
                  <h3>上架條件檢核</h3>
                  <button class="secondary" @click="goTo('preview')">預覽商品頁</button>
                </div>
                <p class="muted">完成必要設定後即可送審，審核通過後才會上架。</p>
                <ul class="publisher-preflight">
                  <li v-for="check in checks" :key="check.key" :class="{ complete: check.complete }">
                    <UiIcon :name="check.complete ? 'check' : 'alert'" />
                    <div>
                      <button v-if="check.key === 'media' && editable" type="button" class="text-button" @click="goTo('edit')">{{ check.label }}</button>
                      <strong v-else>{{ check.label }}</strong>
                      <small>{{ check.complete ? '已完成' : '未完成' }}</small>
                    </div>
                    <button v-if="editable" class="secondary"
                      @click="goTo(check.key === 'media' ? 'edit' : check.mode)">
                      前往設定
                    </button>
                  </li>
                </ul>
                <form class="publisher-form" @submit.prevent="openDialog('publish')">
                  <label>補充說明
                    <textarea v-model="reason" maxlength="500" rows="3" :disabled="busy || uploading || !editable"></textarea>
                  </label>
                  <button class="primary" :disabled="busy || !publishable">送出上架審核</button>
                  <p v-if="editable && !publishable" class="muted">尚有未完成的必要設定，無法送出審核。</p>
                  <p v-else-if="!editable" class="publisher-pending">目前狀態無法送審。待審核及預定下架期間不可重複申請。</p>
                </form>
              </template>
              <form v-else-if="mode === 'builds'" class="publisher-form" ref="editorForm"
                @submit.prevent="submitEditor">
                <fieldset :disabled="busy || uploading || !canManageBuilds">
                  <fieldset v-for="{ build, index } in visibleBuilds" :key="build.id ?? `new-${index}`" class="publisher-resource-card" :disabled="build.id != null && selected?.status !== 'PUBLISHED'">
                    <h3>{{ build.id == null ? '新增遊戲更新（尚未儲存）' : (currentBuildId === build.id ? '目前版本' : '歷史版本') }} · {{ build.version || '待選擇版本號' }}</h3>
                    <label>版本號
                      <input v-if="build.id != null" :value="build.version" type="text" readonly aria-label="已建立的版本號">
                      <select v-else v-model="build.version" required>
                        <option disabled value="">請先完成上一個版本</option>
                        <option v-for="choice in versionChoices(index)" :key="choice.value" :value="choice.value">{{ choice.label }}</option>
                      </select>
                    </label>
                    <p v-if="build.id == null && !versionChoices(index).length" class="muted">上一個版本號格式不符，請先確認版本資料。</p>
                    <div class="publisher-build-source" role="group" aria-label="版本檔案來源">
                      <label><input v-model="build.source" type="radio" :disabled="build.id != null" :name="'build-source-' + index" value="upload" @change="changeBuildSource(build)">上傳 ZIP</label>
                      <label><input v-model="build.source" type="radio" :disabled="build.id != null" :name="'build-source-' + index" value="url" @change="changeBuildSource(build)">使用網址</label>
                    </div>
                    <template v-if="build.source === 'upload'">
                      <label>選擇 ZIP 檔案（最多 100 MB）<input type="file" accept=".zip,application/zip" :disabled="uploading || build.id != null" @change="uploadBuild($event, build)"></label>
                      <label>已上傳版本<input v-model="build.fileUrl" type="text" readonly required placeholder="請先上傳 ZIP"></label>
                      <div v-if="build.fileUrl" class="publisher-build-download"><PublisherBuildLink :url="build.fileUrl" /></div><p v-if="uploading" role="status">ZIP 上傳中，請稍候…</p>
                    </template>
                    <label v-else>版本檔案網址<input v-model="build.fileUrl" type="url" maxlength="500" :readonly="build.id != null" required placeholder="https\://…"></label>
                    <div class="publisher-form-pair">
                      <label>檔案大小（Bytes，可留空）
                        <input v-model="build.fileSize" type="number" min="0" step="1" :readonly="build.id != null"
                          @change="build.fileSize = build.fileSize === '' ? null : Number(build.fileSize)">
                      </label>
                      <label>狀態
                        <select v-model="build.status" :disabled="selected?.status === 'DRAFT'" @change="changeBuildStatus(build)">
                          <option value="ACTIVE">啟用</option>
                          <option value="INACTIVE">停用</option>
                        </select>
                      </label>
                    </div>
                  </fieldset>
                  <div class="publisher-button-row"><button type="button" :disabled="(selected?.status === 'DRAFT' && builds.length > 0) || builds.length >= 50 || hasPendingBuild || !canManageBuilds"
                      class="publisher-next-version-button" @click="addNextBuild">
                      {{ selected?.status === 'DRAFT' ? '新增遊戲' : '新增下一個版本' }}
                    </button>
                  </div>
                  <button v-if="selected?.status === 'PUBLISHED' && (hasPendingBuild || hasStatusChanges)" type="submit" class="primary" :disabled="busy || uploading">{{ hasPendingBuild ? '儲存新版本' : '儲存版本狀態' }}</button>
                  <PublisherSaveActions v-else-if="selected?.status === 'DRAFT'" secondary="儲存草稿" primary="儲存並前往：上架檢核" next="publish" :busy="busy" />
                </fieldset>
                <p class="muted">新增版本可選擇上傳 ZIP 或填寫 HTTP／HTTPS 網址，擇一即可。畫面僅顯示目前版本與上一個歷史版本；更早版本仍保留在資料庫。</p>
              </form>
              <form v-else-if="mode === 'tags'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
                <fieldset :disabled="busy || uploading || !canEditTags">
                  <p class="muted">只能勾選平台提供的標籤，至少選擇一個，可複選。</p>
                  <div class="publisher-tag-grid">
                    <label v-for="tag in allTags" :key="tag.tag_id">
                      <input v-model="tagIds" type="checkbox" :value="tag.tag_id">
                      {{ tag.tag_name }}
                    </label>
                  </div>
                  <p v-if="tagValidationError && !tagIds.length" class="error" role="alert">{{ tagValidationError }}</p>
                  <p v-if="!allTags.length" class="muted">尚未建立可選用的標籤。</p>
                  <PublisherSaveActions primary="儲存並前往：設定圖片與影片" next="media" :busy="busy" />
                </fieldset>
              </form>
              <form v-else-if="mode === 'media'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
                <fieldset :disabled="busy || uploading || !(editable || canEditPublishedContent)">
                  <div v-for="(item, index) in media" :key="index" class="publisher-resource-card">
                    <div class="publisher-form-pair">
                      <label>類型
                        <select v-model="item.type">
                          <option value="IMAGE">圖片</option>
                          <option value="VIDEO">影片</option>
                        </select>
                      </label>
                      <label>顯示順序
                        <input v-model.number="item.order" type="number" min="1" max="1000" required></label>
                    </div>
                    <label>媒體網址
                      <input v-model="item.url" type="text" maxlength="500" required>
                    </label>
                    <button type="button" @click="media.splice(index, 1)">移除</button>
                  </div>
                  <div class="publisher-button-row">
                    <button type="button" :disabled="media.length >= 50"
                      @click="media.push({ type: 'IMAGE', url: '', order: media.length + 1 })">
                      新增圖片或影片
                    </button>
                  </div>
                  <PublisherSaveActions primary="儲存並前往：上架檢核" next="publish" :busy="busy" />
                </fieldset>
                <p class="muted">封面請至「修改遊戲」設定；圖片與影片使用已上傳的網址。</p>
              </form>
              <PublisherPreview v-else-if="mode === 'preview'" :game="selected" @navigate="goTo" />
              <PublisherOperations v-else :game="selected" :mode="mode" :can-restore="!!canRestore"
                :remaining="remaining" :editable="!!editable" :busy="busy"
                @off-shelf="reason = ''; openDialog('offShelf')" @restore="action('restore', '已取消下架申請')"
                @navigate="goTo" @delete-draft="openDialog('deleteDraft')" />
              <p v-if="!editable && ((['edit', 'media'].includes(mode) && !canEditPublishedContent) || (mode === 'tags' && !canEditTags) || (mode === 'builds' && !canManageBuilds))" class="publisher-pending">
                {{ mode === 'tags' ? '只有已上架且非審核中的遊戲可以修改標籤。' : '目前狀態無法修改此資料；待審核及預定下架期間不可修改。' }}
              </p>
            </template>
            <div v-else class="publisher-empty">
              <span>
                <UiIcon :name="publisherIcons[mode]" />
              </span>
              <h2>選擇一款遊戲開始</h2>
              <p>從遊戲清單選取作品，即可{{ title }}。</p>
            </div>
          </section>
        </div>
      </div>
    </div>
  </section>
  <PublisherDialog :open="!!dialog" :busy="busy"
    :title="dialog === 'leave' ? '尚有未儲存的變更' : dialog === 'publish' ? '確認送出上架審核' : dialog === 'offShelf' ? '確認申請下架' : dialog === 'saveDraftBuild' ? '確認儲存草稿版本' : dialog === 'deleteDraft' ? '確認永久刪除草稿？' : ''"
    :description="dialog === 'leave' ? '您有尚未儲存的資料，要先儲存再前往嗎？' : dialog === 'publish' ? '送出後在審核完成前將無法修改遊戲資料，確定要送出嗎？' : dialog === 'offShelf' ? '提出申請後 24 小時會自動下架，期限內可以取消下架。遊戲資料與購買紀錄會保留。' : dialog === 'saveDraftBuild' ? '確定要儲存此版本嗎？儲存後，這筆已建立的版本將暫時鎖定，不能在版本管理畫面直接修改；請先確認版本號與 ZIP／網址。' : dialog === 'deleteDraft' ? `確定要永久刪除「${selected?.game_name || '此遊戲'}」嗎？遊戲草稿、版本及相關設定將永久刪除，無法復原。` : ''"
    @close="closeDialog">
    <label v-if="dialog === 'offShelf'">下架說明（選填）
      <textarea v-model="reason" maxlength="500" rows="3" :disabled="busy"></textarea>
    </label>
    <p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p>
    <template #actions>
      <button class="secondary" :disabled="busy" @click="closeDialog">繼續編輯 / 取消</button>
      <template v-if="dialog === 'leave'">
        <button class="secondary" :disabled="busy" @click="finishLeave(false)">
          放棄變更
        </button>
        <button class="primary" :disabled="busy" @click="finishLeave(true)">
          儲存並前往
        </button>
      </template>
      <button v-else-if="dialog === 'deleteDraft'" class="publisher-danger" :disabled="busy" @click="confirmDeleteDraft">確定刪除</button>
      <button v-else-if="dialog === 'saveDraftBuild'" class="primary" :disabled="busy || uploading" @click="confirmSaveDraftBuild">確定儲存</button>
      <button v-else-if="dialog === 'publish'" class="primary" :disabled="busy || !publishable"
        @click="action('publish', '已送出上架審核', 'history')">
        確認送出審核
      </button>
      <button v-else-if="dialog === 'offShelf'" class="publisher-danger" :disabled="busy"
        @click="action('off-shelf', '已提出下架申請，24 小時內可取消')">
        確認申請下架
      </button>
    </template>
  </PublisherDialog>
</template>

<style scoped>
/* 新增下一個版本按鈕使用黃色文字。 */
.publisher-next-version-button { color: #f5cf61; }
/* 下載 ZIP 按鈕與下方檔案大小、狀態欄位留一排間距。 */
.publisher-build-download { margin-bottom: 22px; }

/* 來源選項保留原生單選尺寸，不套用一般文字欄位的滿寬規則。 */
.publisher-build-source { display: flex; flex-wrap: wrap; gap: 18px; margin: 12px 0; }
.publisher-build-source label { display: flex; align-items: center; gap: 8px; }
.publisher-build-source input[type="radio"] { width: auto; margin: 0; accent-color: #d4a64a; }

/* 修改遊戲：清單和編輯區上下排列 */
.publisher-games .publisher-editing {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 20px;
}

/* 遊戲清單：取消內部捲軸 */
.publisher-games .publisher-editing .publisher-list-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  max-height: none;
  overflow: visible;
  padding: 22px;
}

/* 標題、搜尋列和分頁佔滿整排 */
.publisher-games .publisher-editing .publisher-list-panel> :is(.publisher-panel-heading,
  .publisher-game-search,
  .publisher-pagination,
  .muted,
  [role="status"]) {
  grid-column: 1 / -1;
}

/* 遊戲卡片 */
.publisher-games .publisher-editing .publisher-game-row {
  margin: 0;
  min-width: 0;
  min-height: 84px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #35406f;
  background: #192050;
  transition: background .2s, border-color .2s;
}

.publisher-games .publisher-editing .publisher-game-row:hover,
.publisher-games .publisher-editing .publisher-game-row.active {
  background: #263365;
  border-color: #d4a64a;
}

/* 右側購買人數 */
.publisher-games .publisher-buyers {
  margin-left: auto;
  text-align: right;
}

/* 下方尚未選擇遊戲的提示 */
.publisher-games .publisher-editing .publisher-editor {
  min-height: 220px;
}

.publisher-games .publisher-editing .publisher-empty {
  min-height: 190px;
  padding: 30px 16px;
}

/* 響應式 */
@media (max-width: 1150px) {
  .publisher-games .publisher-editing .publisher-list-panel {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .publisher-games .publisher-editing .publisher-list-panel {
    grid-template-columns: 1fr;
  }
}
</style>

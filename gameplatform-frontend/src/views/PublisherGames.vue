<script setup>
import WorkspacePager from '../components/WorkspacePager.vue'
import WorkspaceModal from '../components/WorkspaceModal.vue'
import PublisherBuildLink from '../components/PublisherBuildLink.vue'
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter, RouterLink, onBeforeRouteUpdate, onBeforeRouteLeave } from 'vue-router'
import { api, session } from '../api'
import { publisherFunctions, publisherLink, publisherGroups, publisherIcons } from '../publisher-functions'
import { notifyPublisher } from '../publisher-notifications'
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
const pageSize = computed(() => ['tags', 'builds', 'buyers', 'history', 'publish', 'offShelf', 'preview', 'list'].includes(mode.value) ? 5 : 20)
const filterable = computed(() => ['history','publish','offShelf','list','preview'].includes(mode.value))
const statusFilter = ref(''), appliedFilters = ref({ q: '', status: '' })
const gameEditorOpen = ref(false), detailLoading = ref(false)
const q = ref('')
const brokenCovers = ref({})
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
// 不同工作頁的遊戲狀態白名單，同時交由後端套用至清單及總筆數。
const modeStates = {
  edit: ['DRAFT'],
  offShelf: ['PUBLISHED', 'PENDING_OFF_SHELF'],
  preview: ['DRAFT', 'PENDING_REVIEW', 'COMING_SOON', 'PUBLISHED'],
  publish: ['DRAFT', 'PENDING_REVIEW', 'COMING_SOON', 'PUBLISHED'],
  history: ['DRAFT', 'PENDING_REVIEW', 'COMING_SOON', 'PUBLISHED'],
  buyers: ['PUBLISHED']
}
const allowedStates = computed(() => modeStates[mode.value] || Object.keys(labels))
const statusOptions = computed(() => allowedStates.value.map(code => ({ code, label: labels[code] })))
const tick = ref(Date.now())
const clockOffset = ref(0)
const dialog = ref(null)
const dialogError = ref('')
const discardIndex = ref(-1)
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
const liveBuilds = computed(() => mode.value === 'builds' && selected.value?.status === 'PUBLISHED')
const buildsEditable = computed(() => editable.value || liveBuilds.value)
const checks = computed(() => selected.value?.publish_checks || [])
const publishable = computed(() => editable.value && checks.value.length === 4 && checks.value.every(x => x.complete))
const date = value => value ? new Date(value).toLocaleString('zh-TW', { hour12: false }) : '—'
const downloadSpace = (() => {
  try {
    const url = new URL(import.meta.env.VITE_PUBLISHER_DOWNLOAD_SPACE_URL || '');
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''
  } catch {
    return ''
  }
})()
function payload() {
  return {
    ...form.value,
    price: Number(form.value.price),
    releaseDate: form.value.releaseDate || null
  }
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
const dirty = computed(() => (mode.value === 'new' ? !selected.value || editable.value : mode.value === 'builds' ? buildsEditable.value : editable.value) && baseline.value !== snapshot())
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
    const result = await api('/workspace/publisher/games?' + new URLSearchParams({ page: String(page.value), size: String(pageSize.value), ...appliedFilters.value, status: mode.value === 'edit' ? 'DRAFT' : appliedFilters.value.status, mode: mode.value }))
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
  detailLoading.value = true
  selected.value = null; error.value = ''
  try {
    const game = await api('/workspace/publisher/games/' + id); if (run === detailRun) {
      if (!allowedStates.value.includes(game.status)) throw Error('此頁不提供這個狀態的遊戲，請從清單選擇。')
      assign(game)
    }
  } catch (e) {
    if (run === detailRun) error.value = e.message
  } finally { if (run === detailRun) detailLoading.value = false }
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
function search() { error.value = ''; if (filterable.value) { detailRun++; selected.value = null; detailLoading.value = false }; page.value = 0; appliedFilters.value = { q: q.value.trim(), status: filterable.value ? statusFilter.value : '' }; load() }
function clearSearch() { q.value = ''; if (statusFilter.value) statusFilter.value = ''; else search() }
watch(statusFilter, () => { if (filterable.value) search() })
function openGameEditor(game) {
  if (busy.value || game.status !== 'DRAFT') return
  gameEditorOpen.value = true
  if (String(route.query.game) === String(game.game_id)) choose(game.game_id)
  else select(game)
}
function closeGameEditor() {
  if (busy.value || uploading.value) return
  if (dirty.value) { openDialog('closeEditor'); return }
  gameEditorOpen.value = false; detailRun++; detailLoading.value = false
}
async function finishEditor(save) {
  if (save && !(await saveEditor('',false))) return
  if (!save && selected.value) assign(selected.value)
  dialog.value = null; await nextTick(); gameEditorOpen.value = false
}
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
  let path, body, method = 'PUT'
  if (['new', 'edit'].includes(current)) {
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
    if (current === 'edit' && !dialog.value) gameEditorOpen.value = false
    notifyPublisher(current === 'builds' && liveBuilds.value ? '新版本已儲存，確認後可發布' : ['new', 'builds'].includes(current) ? '草稿已儲存' : current === 'edit' ? '修改已儲存' : '設定已儲存')
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
function discardBuild() {
  const index = discardIndex.value, build = builds.value[index]
  if (!build) return closeDialog()
  const original = selected.value.builds.find(x => x.build_id === build.id)
  if (original) builds.value[index] = { id: original.build_id, version: original.version, fileUrl: original.file_url, fileSize: original.file_size, status: original.status, source: original.file_url?.startsWith('/api/publisher-assets/builds/') ? 'upload' : 'url' }
  else builds.value.splice(index, 1)
  closeDialog()
}
function changeBuildSource(build) { build.fileUrl = ''; build.fileSize = null }
async function uploadBuild(event, build) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || busy.value || uploading.value || !buildsEditable.value || (liveBuilds.value && build.id)) return
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
const releaseTarget = ref(null)
function confirmRelease(build) {
  if (dirty.value) { error.value = '請先儲存或放棄版本變更，再發布已儲存的版本。'; return }
  releaseTarget.value = build; openDialog('releaseBuild')
}
async function releaseBuild() {
  if (!releaseTarget.value || busy.value || dirty.value) return
  busy.value = true; dialogError.value = ''; error.value = ''
  try {
    const game = await api(endpoint(`builds/${releaseTarget.value.id}/release`), { method: 'POST' })
    assign(game); dialog.value = null; notifyPublisher('版本已發布，舊版本已保留為停用')
  } catch (e) { dialogError.value = e.message }
  finally { busy.value = false }
}
watch(mode, () => { page.value = 0; statusFilter.value = ''; appliedFilters.value = { q: q.value.trim(), status: '' }; gameEditorOpen.value = false; load() })
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
        <form v-if="mode !== 'new'" class="publisher-panel publisher-game-search publisher-search-refined" @submit.prevent="search">
              <label class="publisher-query-label">搜尋遊戲<input v-model="q" maxlength="100" aria-label="搜尋我的遊戲" placeholder="輸入遊戲名稱" :disabled="loading || busy"></label>
              <label v-if="filterable" class="publisher-status-filter">遊戲狀態<select v-model="statusFilter" aria-label="遊戲狀態篩選" :disabled="loading || busy" ><option value="">全部狀態</option><option v-for="item in statusOptions" :key="item.code" :value="item.code">{{ item.label }}</option></select></label>
              <div class="publisher-search-actions"><button class="primary" :disabled="loading || busy"><UiIcon name="search" />搜尋</button><button type="button" class="secondary" :disabled="loading || busy" @click="clearSearch">清除篩選</button></div>
            </form>
        <div class="publisher-work-columns"
          :class="{ 'publisher-create': mode === 'new', 'publisher-editing': mode === 'new', 'publisher-edit-list': mode === 'edit' }">
          <section v-if="mode !== 'new'" class="publisher-panel publisher-list-panel">
            <header class="publisher-panel-heading">
              <h2>{{ mode === 'edit' ? '可修改的遊戲草稿' : '遊戲清單' }}</h2>
              <span>{{ total }} 款</span>
            </header>

            <p v-if="mode === 'edit'" class="publisher-draft-hint"><UiIcon name="edit" />僅顯示草稿，點「修改」編輯遊戲內容並查看即時預覽。</p>
            <p v-if="loading" role="status">載入中…</p>
            <p v-else-if="!items.length" class="muted">目前沒有符合條件的遊戲。</p>
            <div v-for="game in items" :key="game.game_id" class="publisher-game-row"
              :class="{ active: selected?.game_id === game.game_id }" >
              <button type="button" class="publisher-game-select" :disabled="busy || detailLoading" @click="select(game)">
              <span class="publisher-game-info">
                <span class="publisher-game-thumb">
                  <img v-if="game.cover_url && !brokenCovers[game.game_id]" :src="game.cover_url" :alt="game.game_name + ' 封面'" loading="lazy" @error="brokenCovers[game.game_id] = true"><UiIcon v-else name="game" />
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
              <button v-if="mode === 'edit'" type="button" class="publisher-edit-launch primary" :disabled="busy || game.status !== 'DRAFT'" @click="openGameEditor(game)"><UiIcon name="edit" />修改</button>
            </div>
            <WorkspacePager :page="page" :total="total" :size="pageSize" :busy="loading || busy" @change="page = $event; load()" />
          </section>
          <section v-if="mode !== 'edit'" class="publisher-panel publisher-editor">
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
              <form v-if="mode === 'new'" class="publisher-form" ref="editorForm"
                @submit.prevent="submitEditor">
                <PublisherStoreEditor v-model:form="form" v-model:tag-ids="tagIds" v-model:media="media" :tags="allTags"
                  :disabled="!!selected && !editable" :busy="busy" @uploading="uploading = $event" />
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
                <fieldset :disabled="busy || uploading || !buildsEditable">
                  <div v-for="(build, index) in builds" :key="index" class="publisher-resource-card">
                    <h3>{{ build.id ? build.version : '新增版本' }} <small v-if="liveBuilds">{{ build.status === 'ACTIVE' ? '目前啟用' : '待發布／已停用' }}</small></h3>
                    <fieldset :disabled="liveBuilds && !!build.id">
                    <label>版本號
                      <input v-model="build.version" maxlength="30" required>
                    </label>
                    <div class="publisher-build-source" role="group" aria-label="版本檔案來源">
                      <label><input v-model="build.source" type="radio" :name="'build-source-' + index" value="upload" @change="changeBuildSource(build)">上傳 ZIP</label>
                      <label><input v-model="build.source" type="radio" :name="'build-source-' + index" value="url" @change="changeBuildSource(build)">使用網址</label>
                    </div>
                    <template v-if="build.source === 'upload'">
                      <label>選擇 ZIP 檔案（最多 100 MB）<input type="file" accept=".zip,application/zip" :disabled="uploading" @change="uploadBuild($event, build)"></label>
                      <label>已上傳版本<input v-model="build.fileUrl" type="text" readonly required placeholder="請先上傳 ZIP"></label>
<p v-if="uploading" role="status">ZIP 上傳中，請稍候…</p>
                    </template>
                    <label v-else>版本檔案網址<input v-model="build.fileUrl" type="url" maxlength="500" required placeholder="https://…"></label>
                    <div class="publisher-form-pair">
                      <label>檔案大小（Bytes，可留空）
                        <input v-model="build.fileSize" type="number" min="0" step="1"
                          @change="build.fileSize = build.fileSize === '' ? null : Number(build.fileSize)">
                      </label>
                      <label>狀態
                        <select v-model="build.status" :disabled="liveBuilds">
                          <option value="ACTIVE">啟用</option>
                          <option value="INACTIVE">停用</option>
                        </select>
                      </label>
                    </div>
                    </fieldset>
                    <PublisherBuildLink v-if="build.fileUrl" :url="build.fileUrl" />
                    <button v-if="liveBuilds && build.id && build.status !== 'ACTIVE'" type="button" class="primary" :disabled="dirty || busy" @click="confirmRelease(build)">發布此版本</button>
                    <button v-if="!liveBuilds || !build.id" type="button" class="text-button" @click="openDialog('discardBuild', index)">
                      放棄此版本變更
                    </button>
                  </div>
                  <div class="publisher-button-row"><button type="button" :disabled="builds.length >= 50"
                      @click="builds.push({ id: null, version: '', fileUrl: '', fileSize: null, status: liveBuilds ? 'INACTIVE' : 'ACTIVE', source: 'upload' })">
                      新增下一個版本
                    </button>
                  </div>
                  <div class="publisher-download-space">
                    <a v-if="downloadSpace" :href="downloadSpace" target="_blank" rel="noopener noreferrer">
                      前往下載空間複製 ↗
                    </a>
                    <button v-else type="button" disabled>前往下載空間複製</button>
                    <small v-if="!downloadSpace">尚未設定團隊下載空間網址。</small>
                  </div>
                  <button v-if="liveBuilds" class="primary" type="submit" :disabled="busy || !dirty">儲存新版本</button>
                  <PublisherSaveActions v-else secondary="儲存草稿" primary="儲存並前往：上架檢核" next="publish" :busy="busy" />
                </fieldset>
                <p v-if="liveBuilds" class="publisher-version-note">已上架更新：先新增版本並儲存，再按「發布此版本」。發布前沿用目前版本，發布後只保留一個啟用版本；歷史版本不刪除，可再次發布回復。網頁內建小遊戲的程式更新仍需部署新版網站。</p>
                <p class="muted">每個版本可選擇上傳 ZIP 或填寫 HTTP／HTTPS 網址，擇一即可。上傳後請儲存；既有版本可停用，不直接刪除。</p>
              </form>
              <form v-else-if="mode === 'tags'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
                <fieldset :disabled="busy || uploading || !editable">
                  <p class="muted">只能勾選平台提供的標籤，可複選。</p>
                  <div class="publisher-tag-grid">
                    <label v-for="tag in allTags" :key="tag.tag_id">
                      <input v-model="tagIds" type="checkbox" :value="tag.tag_id">
                      {{ tag.tag_name }}
                    </label>
                  </div>
                  <p v-if="!allTags.length" class="muted">尚未建立可選用的標籤。</p>
                  <PublisherSaveActions primary="儲存並前往：設定圖片與影片" next="media" :busy="busy" />
                </fieldset>
              </form>
              <form v-else-if="mode === 'media'" class="publisher-form" ref="editorForm" @submit.prevent="submitEditor">
                <fieldset :disabled="busy || uploading || !editable">
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
                @navigate="goTo" />
              <p v-if="!editable && !(mode === 'builds' && liveBuilds) && ['edit', 'builds', 'tags', 'media'].includes(mode)" class="publisher-pending">
                已上架、待審核及預定下架期間保留原資料，不可修改。
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
  <WorkspaceModal :open="gameEditorOpen && mode === 'edit'" :title="selected ? '修改遊戲：' + selected.game_name : '修改遊戲'" wide publisher :busy="busy || uploading" :suspended="!!dialog" @close="closeGameEditor">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="detailLoading" role="status">正在載入遊戲資料…</p>
    <template v-else-if="selected">
      <p v-if="selected.status !== 'DRAFT'" class="publisher-pending">修改遊戲頁僅提供草稿編輯。</p>
      <form class="publisher-form" ref="editorForm" @submit.prevent="submitEditor"><PublisherStoreEditor v-model:form="form" v-model:tag-ids="tagIds" v-model:media="media" :tags="allTags" :disabled="selected.status !== 'DRAFT'" :busy="busy" @uploading="uploading = $event" /></form>
    </template>
  </WorkspaceModal>
  <PublisherDialog :open="!!dialog" :busy="busy" :elevated="gameEditorOpen"
    :title="dialog === 'closeEditor' ? '尚有未儲存的變更' : dialog === 'releaseBuild' ? '確認發布版本 ' + releaseTarget?.version : dialog === 'leave' ? '尚有未儲存的變更' : dialog === 'publish' ? '確認送出上架審核' : dialog === 'offShelf' ? '確認申請下架' : '放棄此版本變更'"
    :description="dialog === 'closeEditor' ? '要儲存修改再關閉，或放棄這次變更？' : dialog === 'releaseBuild' ? '發布後會員可取得此版本；目前版本會停用並保留，遊戲維持上架。確定發布嗎？' : dialog === 'leave' ? '您有尚未儲存的資料，要先儲存再前往嗎？' : dialog === 'publish' ? '送出後在審核完成前將無法修改遊戲資料，確定要送出嗎？' : dialog === 'offShelf' ? '提出申請後 24 小時會自動下架，期限內可以取消下架。遊戲資料與購買紀錄會保留。' : '確定要捨棄此版本尚未儲存的變更嗎？既有版本會還原，新增版本會移除。'"
    @close="closeDialog">
    <label v-if="dialog === 'offShelf'">下架說明（選填）
      <textarea v-model="reason" maxlength="500" rows="3" :disabled="busy"></textarea>
    </label>
    <p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p>
    <template #actions>
      <button class="secondary" :disabled="busy" @click="closeDialog">繼續編輯 / 取消</button>
      <template v-if="dialog === 'closeEditor'"><button class="secondary" :disabled="busy" @click="finishEditor(false)">放棄變更並關閉</button><button class="primary" :disabled="busy" @click="finishEditor(true)">儲存並關閉</button></template>
      <template v-else-if="dialog === 'leave'">
        <button class="secondary" :disabled="busy" @click="finishLeave(false)">
          放棄變更
        </button>
        <button class="primary" :disabled="busy" @click="finishLeave(true)">
          儲存並前往
        </button>
      </template>
      <button v-else-if="dialog === 'releaseBuild'" class="primary" :disabled="busy || dirty" @click="releaseBuild">確認發布版本</button>
      <button v-else-if="dialog === 'publish'" class="primary" :disabled="busy || !publishable"
        @click="action('publish', '已送出上架審核', 'history')">
        確認送出審核
      </button>
      <button v-else-if="dialog === 'offShelf'" class="publisher-danger" :disabled="busy"
        @click="action('off-shelf', '已提出下架申請，24 小時內可取消')">
        確認申請下架
      </button>
      <button v-else class="publisher-danger" :disabled="busy" @click="discardBuild">放棄此版本變更</button>
    </template>
  </PublisherDialog>
</template>

<style scoped>
.publisher-game-thumb img { width: 100%; height: 100%; object-fit: cover; border-radius: inherit; }
.publisher-draft-hint { display: flex; align-items: center; gap: 10px; padding: 14px 16px; margin: 8px 0 20px; border: 1px solid #425883; border-radius: 10px; background: #1c2b51; color: #ccd9ee; line-height: 1.7; }
.publisher-draft-hint .ui-icon { width: 18px; height: 18px; flex: none; color: #e4c07e; }
body.cart-portal .publisher-games .publisher-edit-list .publisher-game-row { margin-bottom: 12px; border-radius: 12px; border: 1px solid #3c5280; background: linear-gradient(110deg,#1a2851,#14203f); }
body.cart-portal .publisher-games .publisher-edit-list .publisher-game-row:hover { border-color: #bd9b63; box-shadow: 0 4px 18px #0002; }
body.cart-portal .publisher-games .publisher-edit-list .publisher-game-select { padding: 20px; }
body.cart-portal .publisher-games .publisher-edit-list .publisher-edit-launch { margin-right: 20px; min-width: 92px; background: #d8b677; border: 1px solid #d8b677; color: #17213c; border-radius: 9px; }

/* 搜尋工具列沿用新增遊戲的深藍與金色，桌面欄位保持單行。 */
.publisher-games .publisher-search-refined {
  display: grid; grid-template-columns: minmax(150px,1fr) minmax(130px,.65fr) auto;
  align-items: end; gap: 16px; margin: 18px 0 22px; padding: 20px;
  border: 1px solid #3e5186; border-radius: 12px; background: #182255;
}
.publisher-games .publisher-search-refined:not(:has(.publisher-status-filter)) { grid-template-columns: minmax(150px,1fr) auto; }
.publisher-games .publisher-search-refined label { display: grid; gap: 9px; min-width: 0; margin: 0; font-size: 13px; color: #d6dff2; font-weight: 600; }
body.cart-portal .publisher-games .publisher-search-refined :is(input,select) {
  width: 100%; min-width: 0; height: 44px; box-sizing: border-box; margin: 0; padding: 10px 13px;
  border: 1px solid #506599; border-radius: 8px; background: #101936; color: #f0f3ff; font-size: 14px; color-scheme: dark;
}
.publisher-games .publisher-search-refined input::placeholder { color: #a6b6d5; }
.publisher-games .publisher-search-refined :is(input,select):focus-visible { outline: 2px solid #d4ae68; outline-offset: 2px; }
.publisher-games .publisher-search-actions { display: flex; flex-wrap: nowrap; gap: 10px; }
body.cart-portal .publisher-games .publisher-search-actions button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 44px; min-height: 44px; padding: 10px 15px; margin: 0; white-space: nowrap; border-radius: 8px; }
body.cart-portal .publisher-games .publisher-search-actions .primary { background: #d7b36c; color: #17213d; border: 1px solid #d7b36c; }
body.cart-portal .publisher-games .publisher-search-actions .secondary { background: #24335e; color: #e7ecfa; border: 1px solid #526591; }
.publisher-search-actions .ui-icon { width: 16px; height: 16px; }
@media(max-width:700px) { .publisher-games .publisher-search-refined { grid-template-columns: 1fr; padding: 16px; }.publisher-games .publisher-search-refined:not(:has(.publisher-status-filter)) { grid-template-columns: 1fr; }.publisher-games .publisher-search-actions { justify-content: flex-end; } }

.publisher-games .publisher-game-row { display: flex; align-items: center; gap: 14px; padding: 0; }.publisher-game-select { display: flex; flex: 1; align-items: center; justify-content: space-between; gap: 14px; min-width: 0; width: 100%; padding: 16px; border: 0; border-radius: inherit; background: transparent; text-align: left; color: inherit; cursor: pointer; }.publisher-game-select .publisher-game-info { min-width: 0; }.publisher-game-select strong { overflow-wrap: anywhere; }.publisher-edit-launch { display: inline-flex; align-items: center; gap: 7px; min-height: 42px; padding: 10px 17px; margin-right: 16px; white-space: nowrap; }.publisher-edit-launch .ui-icon { width: 17px; height: 17px; }
.publisher-games .publisher-edit-list { grid-template-columns: minmax(0,1fr); }.publisher-games .publisher-edit-list .publisher-list-panel { max-height: none; overflow: visible; }
@media(max-width:650px) { .publisher-games .publisher-search-refined { padding: 13px; gap: 12px; }.publisher-query-label,.publisher-status-filter { flex-basis: 100%; }.publisher-search-actions { width: 100%; }.publisher-search-actions button { flex: 1; }.publisher-game-select { padding: 13px 10px; }.publisher-edit-launch { margin-right: 9px; padding: 9px 12px; } }

.publisher-version-note { padding: 14px; border: 1px solid #b5ccd9; border-radius: 10px; background: #eef6fa; line-height: 1.8; color: #244157; }
.publisher-resource-card fieldset { border: 0; margin: 0; padding: 0; min-width: 0; }
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

<style>
/* 後台與發行商分頁統一樣式：僅渲染可翻頁的方向，第一頁不顯示上一頁、末頁不顯示下一頁。 */
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 24px auto 12px;
  padding: 8px 12px;
  min-height: 56px;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button {
  appearance: none;
  min-height: 38px;
  min-width: 76px;
  padding: 8px 16px;
  border: 1px solid #c6dcec;
  border-radius: 9px;
  background: rgba(255,255,255,.96);
  color: #305674;
  font-weight: 500;
  box-shadow: none;
  line-height: 1.3;
  cursor: pointer;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:hover:not(:disabled) {
  background: #f0f7ff;
  border-color: #8eb8d3;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:disabled {
  opacity: .5;
  cursor: not-allowed;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > span {
  color: #355673;
  font-size: 14px;
  white-space: nowrap;
}

</style>

<script setup>
// 沿用 GitHub cart 分支的遊玩時間算法：可見分頁才計時，每 15 秒檢查，滿分鐘先入帳，離開時四捨五入剩餘秒數。
// API 保持目前整合專題的 JWT 身分驗證，不使用 CART 舊版固定的 MEMBER_ID。
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, accessToken, session } from '../../api'
import { useCartStore } from '@/cart/stores/cart'
import { readGameSave, writeGameSave, sandboxGameHtml, gameSaveMessage } from '@/cart/utils/gameSave'
import { miniGameOf } from '@/cart/minigames'

const route = useRoute()
const cart = useCartStore()
const gameId = Number(route.params.id)
const member = session.user?.id
const token = accessToken()
const entry = computed(() => cart.library.find(e => e.game.gameId === gameId && e.isActive !== false) ?? null)
const mini = computed(() => miniGameOf(entry.value?.game))
const aiNames = new Set(["暗夜倖存者", "無盡深淵", "失落王國", "星露森林", "荒野生存", "戰場前線", "火柴人射擊", "火柴人紅藍競技場", "三國戰棋", "牛排大師", "火柴人羽毛球", "火柴人賽車"])
const isAi = computed(() => aiNames.has(entry.value?.game?.gameName))
const html = ref('')
const gameFrame = ref(null)
const saveChannel = crypto.randomUUID()
const saveKey = `hero-game-save:${member}:${gameId}`
function onGameSave(event) {
  if (!validIdentity() || !gameFrame.value) return
  const data = gameSaveMessage(event, gameFrame.value.contentWindow, saveChannel)
  if (data && !writeGameSave(saveKey, data)) error.value = '目前無法保存遊戲進度，請確認瀏覽器儲存空間。'
}
const loading = ref(true)
const error = ref('')
const screen = ref(null)
const isFullscreen = ref(false)
const canFullscreen = document.fullscreenEnabled
const previousTitle = document.title
watch(entry, value => { if (value) document.title = value.game.gameName + '・線上遊玩' }, { immediate: true })
let playedMs = 0
let resumedAt = null
let timer = null
let alive = true

function resume() {
  if (entry.value && (mini.value || (isAi.value && html.value)) && resumedAt === null && document.visibilityState === 'visible') resumedAt = Date.now()
}
function pause() {
  if (resumedAt !== null) {
    playedMs += Math.max(0, Date.now() - resumedAt)
    resumedAt = null
  }
}
function validIdentity() {
  return member != null && token && accessToken() === token && session.user?.id === member
}

// CART 的 POST /api/member-games/{memberId}/{gameId}/play，body 為 { minutes }。
function send(minutes, keepalive = false) {
  if (!(mini.value || (isAi.value && html.value)) || !entry.value || !validIdentity() || minutes < 1) return
  const path = `/member-games/${member}/${gameId}/play`
  if (keepalive) {
    // 原 CART 使用 sendBeacon，但目前專題採 Bearer JWT：使用可帶 Authorization 的 keepalive fetch。
    fetch('/api' + path, {
      method: 'POST',
      keepalive: true,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ minutes }),
    }).catch(() => { })
    return
  }
  api(path, { method: 'POST', body: { minutes } })
    .then(() => cart.fetchLibrary())
    .then(() => { if (alive) error.value = '' })
    .catch(e => { if (alive) error.value = '遊玩時間尚未同步：' + e.message })
}

// CART 原邏輯：每 15 秒檢查，累積滿 1 分鐘先寫入，零頭保留。
function tick() {
  pause()
  const minutes = Math.floor(playedMs / 60000)
  playedMs -= minutes * 60000
  resume()
  send(minutes)
}
function finish(keepalive = false) {
  pause()
  const minutes = Math.round(playedMs / 60000)
  playedMs = 0
  send(minutes, keepalive)
}
function onVisibility() {
  if (document.visibilityState === 'visible') resume()
  else pause()
}
function onPageHide() { finish(true) }
function onFullscreenChange() { isFullscreen.value = document.fullscreenElement === screen.value }
async function toggleFullscreen() {
  try {
    if (document.fullscreenElement === screen.value) await document.exitFullscreen()
    else await screen.value?.requestFullscreen()
  } catch { error.value = '目前無法切換全螢幕，請使用支援的瀏覽器再試一次。' }
}

onMounted(async () => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('message', onGameSave)
  try {
    await cart.fetchLibrary()
    if (!alive || !entry.value) return
    if (isAi.value) {
      const data = await api(`/member-games/${member}/${gameId}/ai-html`)
      if (!alive) return
      html.value = data.html ? sandboxGameHtml(data.html, readGameSave(saveKey), saveChannel) : ''
    }
    if (!mini.value && !html.value) return
    resume()
    timer = setInterval(tick, 15000)
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('pagehide', onPageHide)
  } catch (e) { if (alive) error.value = e.message }
  finally { if (alive) loading.value = false }
})
onBeforeUnmount(() => {
  alive = false
  document.title = previousTitle
  clearInterval(timer)
  finish()
  document.removeEventListener('visibilitychange', onVisibility)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('message', onGameSave)
  window.removeEventListener('pagehide', onPageHide)
  if (document.fullscreenElement === screen.value) document.exitFullscreen().catch(() => { })
})
</script>
<template>
  <main class="play game-window">
    <header class="game-toolbar">
      <div class="game-toolbar-left"><span class="game-brand">HERO</span><RouterLink to="/member-games" class="back">← 返回遊戲庫</RouterLink></div>
      <div class="game-toolbar-title"><h1>{{ entry?.game.gameName || '線上遊玩' }}</h1><small v-if="mini">{{ mini.howTo }}</small></div>
      <div class="game-toolbar-right"><button v-if="(mini || html) && canFullscreen" class="fs-btn" @click="toggleFullscreen">⛶ {{ isFullscreen ? '離開全螢幕' : '全螢幕' }}</button></div>
    </header>
    <p v-if="error" class="game-error" role="alert">{{ error }}</p>
    <div v-if="loading" class="notice">正在確認遊戲庫…</div>
    <div v-else-if="!entry" class="notice"><h2>目前無法遊玩這款遊戲</h2><p>請確認已登入購買帳號，且遊戲仍在你的有效遊戲庫。</p><RouterLink to="/member-games" class="btn">返回遊戲庫</RouterLink></div>
    <div v-else-if="!mini && !html" class="notice"><h2>暫時沒有可用的線上版本</h2><p>請稍後再試，或回遊戲庫查看下載版本。</p><RouterLink to="/member-games" class="btn">返回遊戲庫</RouterLink></div>
    <section v-else ref="screen" class="screen" :class="{ 'mini-screen': !!mini }" aria-label="遊戲畫面">
      <button v-if="isFullscreen" class="fs-exit" @click="toggleFullscreen">✕ 離開全螢幕</button>
      <component v-if="mini" :is="mini.component" />
      <iframe ref="gameFrame" v-else-if="html" :srcdoc="html" sandbox="allow-scripts allow-pointer-lock" referrerpolicy="no-referrer" :title="entry.game.gameName + ' 遊戲畫面'" class="ai-frame" />
    </section>
  </main>
</template>
<style scoped>
.game-window { display: flex; flex-direction: column; width: 100%; height: 100dvh; min-height: 340px; margin: 0; padding: 0; color: #eaf0ff; background: #0b1020; box-sizing: border-box; }
.game-toolbar { display: grid; grid-template-columns: minmax(0,1fr) auto minmax(0,1fr); align-items: center; gap: 18px; flex-shrink: 0; min-height: 64px; padding: 10px 24px; border-bottom: 1px solid #aa874c; background: #0d1223; box-sizing: border-box; }
.game-toolbar-left { display: flex; flex-wrap: wrap; align-items: center; gap: 18px; }.game-brand { font-family: Georgia,serif; font-size: 23px; font-weight: 750; color: #edc67d; letter-spacing: 1px; }.back { color: #d6c295; text-decoration: none; font-size: 13px; }.back:hover { color: #ffe6ae; }.back:focus-visible,.fs-btn:focus-visible { outline: 2px solid #f2c779; outline-offset: 3px; }
.game-toolbar-title { text-align: center; min-width: 0; }.game-toolbar-title h1 { margin: 0; font-size: clamp(16px,2vw,21px); color: #efca83; font-weight: 650; letter-spacing: 1px; }.game-toolbar-title small { display: block; max-width: 480px; margin-top: 5px; color: #b5c4df; font-size: 11px; line-height: 1.5; }.game-toolbar-right { display: flex; justify-content: flex-end; }
.fs-btn,.fs-exit { display: inline-flex; align-items: center; justify-content: center; min-height: 38px; padding: 8px 15px; border: 1px solid #d1aa66; border-radius: 7px; font-size: 13px; cursor: pointer; color: #f1d294; background: #10172c; white-space: nowrap; }.fs-btn:hover,.fs-exit:hover { background: #d6b16c; color: #172033; }
.screen { position: relative; flex: 1; min-height: 0; width: 100%; overflow: hidden; border: 0; box-sizing: border-box; background: #0b1020; }.ai-frame { display: block; width: 100%; height: 100%; border: 0; background: #10182c; }
.mini-screen { display: flex; align-items: flex-start; justify-content: center; overflow: auto; padding: 22px 16px; }.mini-screen > :deep(.game) { width: min(1100px,100%); margin: auto; }
.screen:fullscreen { width: 100%; height: 100%; min-height: 100%; background: #0b1020; }.mini-screen:fullscreen { align-items: center; padding: 56px 20px 20px; }.mini-screen:fullscreen > :deep(.game) { width: min(1100px,100%,calc((100dvh - 170px)*16/9)); }
.mini-screen:fullscreen :deep(.stage),.mini-screen:fullscreen :deep(.board) { max-width: min(calc(100dvh - 220px),92vw) !important; }.mini-screen:fullscreen :deep(.board.memory) { max-width: min(calc((100dvh - 220px)*.75),92vw) !important; }
.fs-exit { position: absolute; top: 12px; right: 15px; z-index: 10; box-shadow: 0 5px 20px #0008; }.game-error { flex-shrink: 0; margin: 0; padding: 10px 20px; color: #ffe1cd; background: #4a2933; font-size: 13px; line-height: 1.5; }
.notice { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 15px; padding: 35px 20px; text-align: center; color: #c5d2eb; }.notice h2 { margin: 0; font-size: 21px; color: #f0d49d; }.notice p { margin: 0; line-height: 1.8; }.btn { display: inline-flex; align-items: center; min-height: 42px; padding: 10px 20px; border-radius: 8px; text-decoration: none; background: #dbb774; color: #172038; font-weight: 650; }
@media(max-width:700px) { .game-toolbar { grid-template-columns: 1fr auto; gap: 8px 12px; padding: 10px 14px; }.game-toolbar-title { grid-column: 1 / -1; grid-row: 2; text-align: left; }.game-toolbar-left { gap: 12px; }.game-brand { font-size: 19px; }.game-toolbar-title small { max-width: 100%; }.mini-screen { padding: 16px 10px; } }
</style>

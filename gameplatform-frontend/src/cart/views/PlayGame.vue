<script setup>
// 沿用 GitHub cart 分支的遊玩時間算法：可見分頁才計時，每 15 秒檢查，滿分鐘先入帳，離開時四捨五入剩餘秒數。
// API 保持目前整合專題的 JWT 身分驗證，不使用 CART 舊版固定的 MEMBER_ID。
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
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
  <main class="play">
    <div class="head">
      <RouterLink to="/member-games" class="back">← 回遊戲庫</RouterLink>
      <h1 v-if="entry">{{ entry.game.gameName }}</h1>
      <span v-if="mini" class="how">{{ mini.howTo }}</span>
      <button v-if="(mini || html) && canFullscreen" class="fs-btn" @click="toggleFullscreen">⛶ 全螢幕</button>
    </div>

    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="loading" class="notice">正在確認遊戲庫…</div>
    <div v-else-if="!entry" class="notice">
      <p>你的遊戲庫裡沒有這款遊戲</p>
      <RouterLink :to="`/games/${gameId}`" class="btn">去看看這款遊戲</RouterLink>
    </div>
    <div v-else-if="!mini && !html" class="notice">
      <p>這款遊戲還沒有可以在網頁上玩的版本</p>
      <RouterLink to="/member-games" class="btn">回遊戲庫</RouterLink>
    </div>
    <section v-else ref="screen" class="screen">
      <!-- 全螢幕時標題列看不到，所以在右上角另外放一顆離開的按鈕 -->
      <button v-if="isFullscreen" class="fs-exit" @click="toggleFullscreen">✕ 結束全螢幕</button>
      <component v-if="mini" :is="mini.component" />
      <iframe ref="gameFrame" v-else-if="html" :srcdoc="html" sandbox="allow-scripts allow-pointer-lock"
        referrerpolicy="no-referrer" title="網頁遊戲" class="ai-frame" />
    </section>
  </main>
</template>

<style scoped>
.ai-frame { display:block; width:100%; height:min(76vh,780px); border:0; background:#111b3e; border-radius:10px; }
.screen:fullscreen .ai-frame { height:calc(100vh - 85px); }
.play {
  max-width: 760px;
  margin: 0 auto;
  padding: 16px;
  min-height: 70vh;
  color: #e4e8f8;
}

.head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid #2e4a9a;
}

.head h1 {
  margin: 0;
  font-size: 26px;
  color: #d4a64a;
}

.back {
  color: #98a2c8;
  font-size: 14px;
}

.back:hover {
  color: #d4a64a;
  background: transparent;
}

.how {
  font-size: 13px;
  color: #98a2c8;
}

.screen {
  position: relative;
  padding: 20px 16px;
  border-radius: 12px;
  background: rgba(18, 26, 74, .85);
  border: 1px solid #2e4a9a;
}

.fs-btn {
  margin-left: auto;
  padding: 5px 14px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #d4a64a;
  background: transparent;
  color: #d4a64a;
}

.fs-btn:hover {
  background: #d4a64a;
  color: #1b0f0d;
}

/* ===== 全螢幕：遊戲置中，放大到螢幕裝得下的最大尺寸 ===== */
.screen:fullscreen {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
  padding: 56px 24px 24px;
  border: none;
  border-radius: 0;
  background: #0b0c32;
}

.fs-exit {
  position: absolute;
  top: 12px;
  right: 16px;
  z-index: 1;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #2e4a9a;
  background: #121a4a;
  color: #e4e8f8;
}

.fs-exit:hover {
  border-color: #d4a64a;
  color: #d4a64a;
}

/* 橫的畫面（格鬥 16:9、打磚塊 4:3）：寬度以「高度放得下」為上限 */
.screen:fullscreen> :deep(.game) {
  width: min(100%, calc((100vh - 200px) * 16 / 9));
}

/* 方的畫面（貪食蛇、2048、踩地雷、記憶翻牌）：原本限制 400px 左右，全螢幕時放大到螢幕高度 */
.screen:fullscreen :deep(.stage),
.screen:fullscreen :deep(.board) {
  max-width: min(calc(100vh - 220px), 92vw) !important;
}

/* 記憶翻牌的牌是直的（3:4），整個牌桌比寬度高，所以寬度再縮成 3/4 */
.screen:fullscreen :deep(.board.memory) {
  max-width: min(calc((100vh - 220px) * 0.75), 92vw) !important;
}

.notice {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 16px;
  border: 1px dashed #2e4a9a;
  border-radius: 10px;
  color: #98a2c8;
}

.notice p {
  margin: 0;
  font-size: 16px;
}

.btn {
  padding: 8px 20px;
  border-radius: 4px;
  font-weight: bold;
  color: #1b0f0d;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}
</style>

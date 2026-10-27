<script setup>
// 遊玩頁：遊戲庫裡有、而且有小遊戲版本的遊戲才能玩
// 離開頁面時把這次玩了幾分鐘記到遊戲庫（member_games.total_play_minutes、last_played_at）
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import axios from 'axios'
import { useCartStore, MEMBER_ID } from '@/stores/cart'
import { miniGameOf } from '@/minigames'

const route = useRoute()
const cart = useCartStore()
const gameId = Number(route.params.id)

// 遊戲庫裡這款遊戲的紀錄（退款停用的不算）
const entry = computed(() =>
  cart.library.find((e) => e.game.gameId === gameId && e.isActive !== false) ?? null,
)
const mini = computed(() => miniGameOf(entry.value?.game))

// 遊玩時間：只算這個分頁在畫面上的時間，切到別的分頁就暫停
let playedMs = 0
let resumedAt = null
function resume() {
  if (resumedAt === null && document.visibilityState === 'visible') resumedAt = Date.now()
}
function pause() {
  if (resumedAt !== null) {
    playedMs += Date.now() - resumedAt
    resumedAt = null
  }
}
function onVisibility() {
  if (document.visibilityState === 'visible') resume()
  else pause()
}

// 送出遊玩時間
function send(minutes, useBeacon = false) {
  if (!mini.value || minutes < 1) return
  const url = `/api/member-games/${MEMBER_ID}/${gameId}/play`
  const body = { minutes }
  if (useBeacon) {
    // 直接關掉分頁時，一般的請求可能來不及送出，改用 sendBeacon
    navigator.sendBeacon(url, new Blob([JSON.stringify(body)], { type: 'application/json' }))
  } else {
    axios.post(url, body).then(() => cart.fetchLibrary()).catch(() => {})
  }
}

// 邊玩邊記：每 15 秒檢查一次，滿 1 分鐘就先記下來（不滿 1 分鐘的部分留著繼續累積）
function tick() {
  pause()
  const minutes = Math.floor(playedMs / 60000)
  playedMs -= minutes * 60000
  resume()
  send(minutes)
}

// 離開時：剩下的時間超過 30 秒就算 1 分鐘；記完歸零，避免重複記
function finish(useBeacon = false) {
  pause()
  const minutes = Math.round(playedMs / 60000)
  playedMs = 0
  send(minutes, useBeacon)
}
function onPageHide() {
  finish(true)
}
let tickTimer = null

// 全螢幕：只把遊戲區塊放大到整個螢幕；按 Esc 或「結束全螢幕」離開
const screen = ref(null)
const isFullscreen = ref(false)
const canFullscreen = document.fullscreenEnabled
function toggleFullscreen() {
  if (document.fullscreenElement) document.exitFullscreen()
  else screen.value?.requestFullscreen().catch(() => {})
}
function onFullscreenChange() {
  isFullscreen.value = document.fullscreenElement === screen.value
}

onMounted(async () => {
  if (!cart.library.length) await cart.fetchLibrary()
  resume()
  tickTimer = setInterval(tick, 15000)
  document.addEventListener('visibilitychange', onVisibility)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('pagehide', onPageHide)
})
onBeforeUnmount(() => {
  clearInterval(tickTimer)
  if (document.fullscreenElement) document.exitFullscreen()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('pagehide', onPageHide)
  finish()
})
</script>

<template>
  <main class="play">
    <div class="head">
      <RouterLink to="/member-games" class="back">← 回遊戲庫</RouterLink>
      <h1 v-if="entry">{{ entry.game.gameName }}</h1>
      <span v-if="mini" class="how">{{ mini.howTo }}</span>
      <button v-if="mini && canFullscreen" class="fs-btn" @click="toggleFullscreen">⛶ 全螢幕</button>
    </div>

    <div v-if="!entry" class="notice">
      <p>你的遊戲庫裡沒有這款遊戲</p>
      <RouterLink :to="`/games/${gameId}`" class="btn">去看看這款遊戲</RouterLink>
    </div>
    <div v-else-if="!mini" class="notice">
      <p>這款遊戲還沒有可以在網頁上玩的版本</p>
      <RouterLink to="/member-games" class="btn">回遊戲庫</RouterLink>
    </div>
    <section v-else ref="screen" class="screen">
      <!-- 全螢幕時標題列看不到，所以在右上角另外放一顆離開的按鈕 -->
      <button v-if="isFullscreen" class="fs-exit" @click="toggleFullscreen">✕ 結束全螢幕</button>
      <component :is="mini.component" />
    </section>
  </main>
</template>

<style scoped>
.play { max-width: 760px; margin: 0 auto; padding: 16px; min-height: 70vh; color: #e4e8f8; }
.head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 14px; margin-bottom: 18px; padding-bottom: 12px; border-bottom: 1px solid #2e4a9a; }
.head h1 { margin: 0; font-size: 26px; color: #d4a64a; }
.back { color: #98a2c8; font-size: 14px; }
.back:hover { color: #d4a64a; background: transparent; }
.how { font-size: 13px; color: #98a2c8; }
.screen { position: relative; padding: 20px 16px; border-radius: 12px; background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; }
.fs-btn {
  margin-left: auto; padding: 5px 14px; border-radius: 6px; font-size: 14px; cursor: pointer;
  border: 1px solid #d4a64a; background: transparent; color: #d4a64a;
}
.fs-btn:hover { background: #d4a64a; color: #1b0f0d; }

/* ===== 全螢幕：遊戲置中，放大到螢幕裝得下的最大尺寸 ===== */
.screen:fullscreen {
  display: flex; align-items: center; justify-content: center; overflow: auto;
  padding: 56px 24px 24px; border: none; border-radius: 0; background: #0b0c32;
}
.fs-exit {
  position: absolute; top: 12px; right: 16px; z-index: 1; padding: 6px 14px; border-radius: 6px; font-size: 14px;
  cursor: pointer; border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8;
}
.fs-exit:hover { border-color: #d4a64a; color: #d4a64a; }
/* 橫的畫面（格鬥 16:9、打磚塊 4:3）：寬度以「高度放得下」為上限 */
.screen:fullscreen > :deep(.game) { width: min(100%, calc((100vh - 200px) * 16 / 9)); }
/* 方的畫面（貪食蛇、2048、踩地雷、記憶翻牌）：原本限制 400px 左右，全螢幕時放大到螢幕高度 */
.screen:fullscreen :deep(.stage),
.screen:fullscreen :deep(.board) { max-width: min(calc(100vh - 220px), 92vw) !important; }
/* 記憶翻牌的牌是直的（3:4），整個牌桌比寬度高，所以寬度再縮成 3/4 */
.screen:fullscreen :deep(.board.memory) { max-width: min(calc((100vh - 220px) * 0.75), 92vw) !important; }
.notice { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 16px; border: 1px dashed #2e4a9a; border-radius: 10px; color: #98a2c8; }
.notice p { margin: 0; font-size: 16px; }
.btn { padding: 8px 20px; border-radius: 4px; font-weight: bold; color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f); }
</style>

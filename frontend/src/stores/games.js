import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
// axios：發送 HTTP 請求到後端 API
import axios from 'axios'

// 定義遊戲清單的 Pinia store（id 為 'games'），導覽列下拉選單、遊戲首頁、所有遊戲頁共用同一份資料
export const useGamesStore = defineStore('games', () => {
  // 全部遊戲，格式與後端 GET /api/games 回傳的相同
  // [{ gameId, gameName, price, originalPrice, description, coverUrl, status, genre }]
  const games = ref([])

  // 玩家看得到的遊戲：已下架（OFF_SALE）的不顯示；開發商後台用 games（全部）
  const onSaleGames = computed(() => games.value.filter((g) => g.status !== 'OFF_SALE'))

  // 所有出現過的分類（去掉重複和沒填分類的），用來產生分類按鈕 / 選單
  const genres = computed(() => [...new Set(onSaleGames.value.map((g) => g.genre).filter(Boolean))])


  // 有打折的遊戲（原價比售價高）
  const discountedGames = computed(() => onSaleGames.value.filter(hasDiscount))


  // 取得全部遊戲
  async function fetchGames() {
    // GET /api/games：向後端取得全部遊戲
    const { data } = await axios.get('/api/games')
    games.value = data
  }

  // 每款遊戲的統計，用 gameId 查：{ 1: { avgRating, reviewCount, soldCount }, ... }
  const stats = ref({})

  // 取得統計（首頁「評價排行」「暢銷商品」用）
  async function fetchStats() {
    // GET /api/games/stats：[{ gameId, avgRating, reviewCount, soldCount }]
    const { data } = await axios.get('/api/games/stats')
    stats.value = Object.fromEntries(data.map((s) => [s.gameId, s]))
  }

  // 開發商：改遊戲狀態（上架 / 下架），成功後把本地那一筆換成後端回傳的新資料，畫面馬上跟著變
  async function updateStatus(gameId, status) {
    // PATCH /api/developer/games/{gameId}/status
    const { data } = await axios.patch(`/api/developer/games/${gameId}/status`, { status })
    const i = games.value.findIndex((g) => g.gameId === gameId)
    if (i !== -1) games.value[i] = data
  }

  // 開發商：儲存遊戲。有 gameId 就是修改（PUT），沒有就是新增（POST）
  // 成功後更新本地的 games，回到列表時不用重新抓
  async function saveGame(gameId, form) {
    if (gameId) {
      // PUT /api/developer/games/{gameId}
      const { data } = await axios.put(`/api/developer/games/${gameId}`, form)
      const i = games.value.findIndex((g) => g.gameId === data.gameId)
      if (i !== -1) games.value[i] = data
      return data
    }
    // POST /api/developer/games
    const { data } = await axios.post('/api/developer/games', form)
    games.value.push(data)
    return data
  }

  return { games, onSaleGames, genres, discountedGames, stats, fetchGames, fetchStats, updateStatus, saveGame }
})

// 有原價且原價比售價高，才算有打折
export function hasDiscount(game) {
  return game.originalPrice > game.price
}

// 折扣百分比，例如原價 1590、售價 318 → 80
export function discountPercent(game) {
  return Math.round((1 - game.price / game.originalPrice) * 100)
}

// 每個分類的圖示：要換圖示改這裡就好；沒列到的分類用 🎮
const GENRE_ICONS = {
  '動作冒險': '⚔️',
  '角色扮演': '🧙',
  '競速遊戲': '🏎️',
  '射擊': '🎯',
  '策略': '♟️',
}
export function genreIcon(genre) {
  return GENRE_ICONS[genre] ?? '🎮'
}

// ===== 最近瀏覽 =====
// 存在瀏覽器 localStorage，只記 gameId，最新的在前面，最多 10 款
const RECENT_KEY = 'recentGames'
export function getRecentIds() {
  try {
    return JSON.parse(localStorage.getItem(RECENT_KEY)) ?? []
  } catch {
    return []
  }
}
// 看過某款遊戲時呼叫（遊戲詳情頁）
export function addRecent(gameId) {
  const ids = [Number(gameId), ...getRecentIds().filter((id) => id !== Number(gameId))].slice(0, 10)
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(ids))
  } catch {
    // 無痕模式等情況存不進去就算了
  }
}

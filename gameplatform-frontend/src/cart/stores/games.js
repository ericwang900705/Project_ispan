
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from '../../http.js'

// ========================================
// 遊戲分類與上架狀態
// ========================================

// CART 五種分類 + AI 遊戲新增種類（只有有上架遊戲時才顯示）
const CART_GENRES = [
  '動作冒險',
  '角色扮演',
  '競速遊戲',
  '射擊',
  '策略',
  'Roguelike',
  '冒險',
  '格鬥',
  '經營養成',
  '運動',
]

// 前台允許顯示的遊戲狀態
const VISIBLE_STATUSES = [
  'PUBLISHED',
  'PENDING_OFF_SHELF',
]

function isVisibleGame(game) {
  return VISIBLE_STATUSES.includes(game?.status)
}

// ========================================
// 遊戲 Store
// ========================================

export const useGamesStore = defineStore('games', () => {
  // 從 API 取得的全部遊戲
  const games = ref([])

  // 前台可顯示的遊戲
  const onSaleGames = computed(() =>
    games.value.filter(isVisibleGame)
  )

  // 前台分類：
  // 只顯示 CART 五種中有上架遊戲的分類
  const genres = computed(() =>
    CART_GENRES.filter((genre) =>
      onSaleGames.value.some((game) =>
        game.genre === genre
      )
    )
  )

  // 有折扣且可在前台顯示的遊戲
  const discountedGames = computed(() =>
    onSaleGames.value.filter(hasDiscount)
  )

  // ========================================
  // 取得遊戲資料
  // ========================================

  async function fetchGames() {
    const { data } = await axios.get('/api/games')
    games.value = Array.isArray(data) ? data : []
  }

  // ========================================
  // 遊戲統計
  // ========================================

  const stats = ref({})

  async function fetchStats() {
    const { data } = await axios.get('/api/games/stats')

    stats.value = Object.fromEntries(
      (Array.isArray(data) ? data : [])
        .filter((item) => item?.gameId != null)
        .map((item) => [item.gameId, item])
    )
  }

  // ========================================
  // 定期更新活動價格
  // ========================================

  let refreshTimer = null

  function refreshOnVisible() {
    if (document.visibilityState === 'visible') {
      fetchGames().catch(console.error)
    }
  }

  function startPriceRefresh() {
    if (refreshTimer || typeof window === 'undefined') {
      return
    }

    refreshTimer = window.setInterval(
      refreshOnVisible,
      30000
    )

    document.addEventListener(
      'visibilitychange',
      refreshOnVisible
    )
  }

  startPriceRefresh()

  return {
    games,
    onSaleGames,
    genres,
    discountedGames,
    stats,
    fetchGames,
    fetchStats,
  }
})

// ========================================
// 折扣相關
// ========================================

// 判斷遊戲是否有折扣
export function hasDiscount(game) {
  return (
    Number(game?.originalPrice) > Number(game?.price) &&
    Number(
      game?.discountPercent ?? discountPercent(game)
    ) > 0
  )
}

// 計算折扣百分比
export function discountPercent(game) {
  if (
    game?.discountPercent != null &&
    Number.isInteger(Number(game.discountPercent))
  ) {
    return Number(game.discountPercent)
  }

  if (!(Number(game?.originalPrice) > 0)) {
    return 0
  }

  return Math.round(
    (1 - Number(game.price) / Number(game.originalPrice)) * 100
  )
}

// ========================================
// CART 五種分類圖示
// ========================================

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

// ========================================
// 最近瀏覽紀錄
// ========================================

const RECENT_KEY = 'recentGames'

// 讀取最近瀏覽的遊戲 ID
export function getRecentIds() {
  try {
    const ids = JSON.parse(
      localStorage.getItem(RECENT_KEY)
    )

    return Array.isArray(ids) ? ids : []
  } catch {
    return []
  }
}

// 記錄最近瀏覽的遊戲
export function addRecent(gameId) {
  const id = Number(gameId)

  if (!Number.isFinite(id)) {
    return
  }

  const ids = [
    id,
    ...getRecentIds().filter(
      (item) => Number(item) !== id
    ),
  ].slice(0, 10)

  try {
    localStorage.setItem(
      RECENT_KEY,
      JSON.stringify(ids)
    )
  } catch {
    // 無痕模式或儲存空間限制時忽略
  }
}

import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

const API = '/api/public/games'

export const useGamesStore = defineStore('games', () => {
  const games = ref([])
  const details = ref({})
  const loading = ref(false)
  const error = ref('')
  const loaded = ref(false)
  let loadingPromise = null

  const genres = computed(() => [...new Set(games.value.map(g => g.genre).filter(Boolean))])
  const discountedGames = computed(() => games.value.filter(hasDiscount))

  // The GitHub API returns Spring Data Page<Game>, not an unbounded array.
  // Read every page so that store navigation doesn't silently omit game #101+.
  async function fetchGames({ force = false } = {}) {
    if (loadingPromise) return loadingPromise
    if (loaded.value && !force) return games.value
    loading.value = true
    error.value = ''
    loadingPromise = (async () => {
      try {
        const rows = []
        const size = 100
        let page = 0
        while (true) {
          const { data } = await axios.get(API, { params: { page, size } })
          const chunk = Array.isArray(data) ? data : (data?.content ?? [])
          if (!Array.isArray(chunk)) throw new Error('遊戲列表回傳格式不正確')
          rows.push(...chunk)
          // Supports both GitHub's Page<Game> and a plain array for compatibility.
          if (Array.isArray(data) || data.last === true || chunk.length === 0) break
          if (Number.isInteger(data.totalPages) && page + 1 >= data.totalPages) break
          if (++page >= 1000) throw new Error('遊戲列表頁數超過限制')
        }
        games.value = rows
        loaded.value = true
        return rows
      } catch (cause) {
        error.value = `遊戲清單取得失敗：${cause.response?.data?.message ?? cause.message}`
        throw cause
      } finally {
        loading.value = false
        loadingPromise = null
      }
    })()
    return loadingPromise
  }

  // Search on GitHub's endpoint; don't search only the first loaded page.
  async function searchGames(keyword) {
    const search = String(keyword ?? '').trim()
    if (!search) return fetchGames()
    const { data } = await axios.get(`${API}/search`, { params: { keyword: search } })
    if (!Array.isArray(data)) throw new Error('搜尋結果回傳格式不正確')
    return data
  }

  // Details have a dedicated endpoint; deep links must work without opening the home page.
  async function fetchGame(gameId) {
    const id = Number(gameId)
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('遊戲編號不正確')
    const { data } = await axios.get(`${API}/${id}`)
    if (!data || Number(data.gameId) !== id) throw new Error('遊戲詳細資料回傳格式不正確')
    details.value = { ...details.value, [id]: data }
    return data
  }

  return { games, details, genres, discountedGames, loading, error, loaded, fetchGames, fetchGame, searchGames }
})

export function hasDiscount(game) {
  return typeof game?.originalPrice === 'number' && game.originalPrice > Number(game.price)
}
export function discountPercent(game) {
  return hasDiscount(game) ? Math.round((1 - game.price / game.originalPrice) * 100) : 0
}
const GENRE_ICONS = { '動作冒險': '⚔️', '角色扮演': '🧙', '競速遊戲': '🏎️', '射擊': '🎯', '策略': '♟️' }
export function genreIcon(genre) { return GENRE_ICONS[genre] ?? '🎮' }

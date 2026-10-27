import { ref } from 'vue'

// 每款小遊戲的最佳紀錄：存在瀏覽器（localStorage），只有自己這台電腦看得到
// lowerIsBetter：踩地雷（秒數）、記憶翻牌（步數）是越少越好
export function useBest(key, lowerIsBetter = false) {
  const storageKey = `minigame-best-${key}`
  const best = ref(read())

  function read() {
    try {
      const value = localStorage.getItem(storageKey)
      return value === null ? null : Number(value)
    } catch {
      return null // 無痕模式等情況讀不到，就當作沒有紀錄
    }
  }

  // 這次的成績比較好就更新，回傳是不是新紀錄
  function submit(value) {
    const better = best.value === null || (lowerIsBetter ? value < best.value : value > best.value)
    if (!better) return false
    best.value = value
    try {
      localStorage.setItem(storageKey, String(value))
    } catch {
      // 存不進去也沒關係，這次開著的頁面還是看得到
    }
    return true
  }

  return { best, submit }
}

import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

// 平台名稱：瀏覽器分頁標題、後台標題都用這個，要改名改這裡就好
export const SITE_NAME = 'JavaPlay'

// 工作室設定（名稱、Logo、簡介）
// ⚠️ 目前還沒有開發商的資料表和登入功能，先存在瀏覽器的 localStorage，只有這台電腦看得到；
//    之後有 developers 表，改成從後端讀寫就好，畫面不用改
const KEY = 'studio'
const DEFAULT = { name: '示範工作室', logoUrl: '', bio: '' }

function load() {
  try {
    return { ...DEFAULT, ...JSON.parse(localStorage.getItem(KEY)) }
  } catch {
    return { ...DEFAULT }
  }
}

export const useStudioStore = defineStore('studio', () => {
  const studio = ref(load())

  // 存檔：寫回 localStorage（無痕模式等情況存不進去就算了，畫面上還是會更新）
  function save(value) {
    studio.value = { ...DEFAULT, ...value }
  }
  watch(studio, (value) => {
    try {
      localStorage.setItem(KEY, JSON.stringify(value))
    } catch {
      // 存不進去就算了
    }
  }, { deep: true })

  return { studio, save }
})

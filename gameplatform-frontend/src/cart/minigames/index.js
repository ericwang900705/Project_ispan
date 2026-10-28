import { defineAsyncComponent } from 'vue'

// 可以直接在網頁上玩的小遊戲：用遊戲名稱（game.game_name）對應到遊戲本體
// 遊戲庫中的遊戲名稱必須與下方對照相同；不會自動新增遊戲或授予所有權。
// defineAsyncComponent：真的要玩時才下載那款遊戲的程式
export const MINI_GAMES = {
  '貪食蛇': { component: defineAsyncComponent(() => import('./SnakeGame.vue')), howTo: '方向鍵 / WASD 移動，空白鍵暫停' },
  '數字合成 2048': { component: defineAsyncComponent(() => import('./Game2048.vue')), howTo: '方向鍵 / WASD 滑動方塊，手機直接滑' },
  '踩地雷': { component: defineAsyncComponent(() => import('./MinesweeperGame.vue')), howTo: '左鍵翻開、右鍵插旗' },
  '打磚塊': { component: defineAsyncComponent(() => import('./BreakoutGame.vue')), howTo: '滑鼠或方向鍵移動板子，點一下發球' },
  '記憶翻牌': { component: defineAsyncComponent(() => import('./MemoryGame.vue')), howTo: '一次翻兩張，一樣就配對成功' },
  '火柴人格鬥：霓虹對決': { component: defineAsyncComponent(() => import('./StickmanGame.vue')), howTo: 'A / D 移動、W 跳、J 拳、K 踢、L 防禦、U 氣功波' },
}

// 這款遊戲有沒有可以玩的版本
export function miniGameOf(game) {
  return game ? MINI_GAMES[game.gameName] ?? null : null
}


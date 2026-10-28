// 僅保留 GitHub cart 原有五種分類圖示。
export const GENRE_ICONS = {'動作冒險': '⚔️', '角色扮演': '🧙', '競速遊戲': '🏎️', '射擊': '🎯', '策略': '♟️'}
export function genreIcon(name) {
  return GENRE_ICONS[String(name ?? "").trim()] ?? "🎮"
}

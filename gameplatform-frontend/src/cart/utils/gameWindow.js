// 使用者點擊時同步開啟分頁，先傳入原登入期限的會員憑證，再切斷 opener。
import { prepareMemberGameWindow } from '../../api'
export function openGameWindow(gameId) {
  const id = Number(gameId)
  if (!Number.isSafeInteger(id) || id < 1) throw Error('遊戲編號無效')
  const url = new URL(window.location.href)
  url.search = ''; url.hash = '/play/' + id
  const popup = window.open('about:blank', '_blank')
  if (!popup) throw Error('瀏覽器阻擋了遊戲分頁，請允許本站開啟新分頁後再試一次。')
  try {
    prepareMemberGameWindow(popup)
    popup.location.replace(url.href)
  } catch (e) { popup.close(); throw e }
}

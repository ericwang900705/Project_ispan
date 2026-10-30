import { browserUuid } from '../../browser-uuid'
// 每批分鐘以固定 UUID 暫存；收到後端確認才移除，重送由後端去重。
// 不儲存 JWT、帳號密碼或遊戲存檔；僅重試目前登入會員的這款遊戲。
export function createPlaytimeSync({ memberId, gameId, validIdentity, sendBatch, onError = () => {}, onSynced = () => {}, onBusy = () => {}, storage: suppliedStorage }) {
  const prefix = `hero-play-pending-v1:${memberId}:${gameId}:`
  let storage
  try { storage = suppliedStorage === undefined ? globalThis.localStorage : suppliedStorage } catch { storage = null }
  const pending = new Map()
  let working = false
  function readStored() {
    if (!storage) return
    try {
      for (let i = 0; i < storage.length; i++) {
        const key = storage.key(i)
        if (!key?.startsWith(prefix)) continue
        try {
          const batch = JSON.parse(storage.getItem(key))
          if (batch && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(batch.batchId) && Number.isInteger(batch.minutes) && batch.minutes >= 1 && batch.minutes <= 1440 && key === prefix + batch.batchId)
            pending.set(batch.batchId, batch)
        } catch { /* 單筆損壞資料不阻擋其他待同步批次。 */ }
      }
    } catch { onError('無法讀取待同步時數，請確認瀏覽器儲存空間。') }
  }
  function enqueue(minutes) {
    if (!Number.isInteger(minutes) || minutes < 1 || !validIdentity()) return
    while (minutes > 0) {
      const amount = Math.min(minutes, 1440)
      const batch = { batchId: browserUuid(), minutes: amount, createdAt: Date.now() }
      pending.set(batch.batchId, batch)
      try {
        if (!storage) throw Error('storage unavailable')
        storage.setItem(prefix + batch.batchId, JSON.stringify(batch))
      } catch { onError('待同步時數暫存失敗，請保持此頁開啟並重新同步。') }
      minutes -= amount
    }
  }
  async function flush({ keepalive = false } = {}) {
    if (working || !validIdentity()) return false
    working = true; onBusy(true)
    let updated = false
    try {
      readStored()
      const batches = [...pending.values()].sort((a, b) => a.createdAt - b.createdAt)
      for (const batch of batches) {
        if (!validIdentity()) return false
        await sendBatch(batch, { keepalive })
        // 即使回應曾經遺失，這次去重確認也代表這批已完成。
        pending.delete(batch.batchId)
        try { storage?.removeItem(prefix + batch.batchId) } catch { /* 下次重送仍由後端去重。 */ }
        updated = true
      }
      if (updated) await onSynced()
      return true
    } catch (e) {
      onError('遊玩時數尚未同步：' + (e.message || '網路暫時中斷') + '。將自動重試，也可按重新同步。')
      return false
    } finally { working = false; onBusy(false) }
  }
  return { enqueue, flush }
}

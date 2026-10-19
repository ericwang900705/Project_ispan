<script setup>
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useGamesStore, hasDiscount, discountPercent } from '@/stores/games'

const gamesStore = useGamesStore()
const { games } = storeToRefs(gamesStore)
onMounted(() => gamesStore.fetchGames())

// 勾選的遊戲（gameId 的陣列，v-model 綁 checkbox）
const selected = ref([])
// 要打幾 % off（例如 30 → 打 7 折）
const percent = ref(30)
const busy = ref(false)
const message = ref('')

// 全選 / 全不選
const allChecked = computed({
  get: () => games.value.length > 0 && selected.value.length === games.value.length,
  set: (on) => (selected.value = on ? games.value.map((g) => g.gameId) : []),
})

// 原價：已經在特價的用 originalPrice，沒有的話現在的售價就是原價
const basePrice = (g) => (hasDiscount(g) ? g.originalPrice : g.price)
// 打折後的價格（四捨五入到整數元）
const salePrice = (g) => Math.round((basePrice(g) * (100 - percent.value)) / 100)

// 修改遊戲的 API 會整筆覆蓋，所以其他欄位要照原本的一起送
function payload(g, price, originalPrice) {
  return {
    gameName: g.gameName, price, originalPrice,
    description: g.description, coverUrl: g.coverUrl, genre: g.genre,
  }
}

// 套用特價 / 取消特價（一款一款送出）
async function run(action) {
  const targets = games.value.filter((g) => selected.value.includes(g.gameId))
  if (!targets.length) return
  if (action === 'apply' && !(percent.value >= 1 && percent.value <= 99)) {
    message.value = '折扣請填 1～99'
    return
  }
  const text = action === 'apply'
    ? `確定把 ${targets.length} 款遊戲設成 -${percent.value}% 特價？`
    : `確定取消 ${targets.length} 款遊戲的特價，恢復原價？`
  if (!confirm(text)) return
  busy.value = true
  message.value = ''
  try {
    for (const g of targets) {
      const body = action === 'apply'
        ? payload(g, salePrice(g), basePrice(g))
        : payload(g, basePrice(g), null)
      await gamesStore.saveGame(g.gameId, body)
    }
    message.value = action === 'apply' ? `已設定 ${targets.length} 款特價` : `已取消 ${targets.length} 款特價`
    selected.value = []
  } catch (e) {
    message.value = e.response?.data?.message ?? '更新失敗'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="discounts">
    <h1>折扣 / 特賣</h1>
    <p class="hint">勾選遊戲後設定折扣。原價會記在「原價」欄，商店頁會顯示刪除線原價和折扣 %。</p>

    <!-- 操作列 -->
    <div class="bar">
      <label for="d-percent">折扣</label>
      <span class="minus">-</span>
      <input id="d-percent" v-model.number="percent" type="number" min="1" max="99" />
      <span>%</span>
      <button class="apply" :disabled="busy || !selected.length" @click="run('apply')">套用特價（{{ selected.length }}）</button>
      <button class="clear" :disabled="busy || !selected.length" @click="run('clear')">取消特價</button>
      <span v-if="message" class="message">{{ message }}</span>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th><input v-model="allChecked" type="checkbox" aria-label="全選" /></th>
            <th>遊戲名稱</th>
            <th class="num">原價</th>
            <th class="num">目前售價</th>
            <th class="num">套用後</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="g in games" :key="g.gameId" :class="{ on: selected.includes(g.gameId) }">
            <td><input v-model="selected" type="checkbox" :value="g.gameId" :aria-label="g.gameName" /></td>
            <td class="name">
              {{ g.gameName }}
              <span v-if="hasDiscount(g)" class="sale">-{{ discountPercent(g) }}%</span>
            </td>
            <td class="num">NT$ {{ basePrice(g) }}</td>
            <td class="num">NT$ {{ g.price }}</td>
            <td class="num preview">
              <template v-if="selected.includes(g.gameId)">NT$ {{ salePrice(g) }}</template>
              <template v-else>—</template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.discounts h1 { margin: 0 0 8px; font-size: 24px; }
.hint { margin: 0 0 16px; font-size: 13px; color: #98a2c8; }
.bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.bar label { font-size: 14px; color: #98a2c8; margin-right: 4px; }
.bar input[type="number"] {
  width: 64px; padding: 6px 8px; font-size: 14px; border-radius: 6px; text-align: right;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8; outline: none;
}
.bar input:focus { border-color: #d4a64a; }
.bar button { padding: 6px 16px; border-radius: 6px; font-size: 14px; cursor: pointer; }
.bar button:disabled { opacity: .5; cursor: default; }
.apply { border: none; font-weight: bold; color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f); }
.clear { border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8; }
.message { font-size: 14px; color: #d4a64a; }

.table-wrap { overflow-x: auto; background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px; font-size: 13px; font-weight: normal; color: #98a2c8; border-bottom: 1px solid #2e4a9a; white-space: nowrap; }
td { padding: 10px 12px; border-bottom: 1px solid #1c2a66; white-space: nowrap; }
tbody tr:hover, tbody tr.on { background: #1c2a66; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.name { font-weight: bold; }
.sale { margin-left: 6px; padding: 1px 6px; font-size: 12px; border-radius: 3px; background: #b23a2a; color: #fff1e0; font-weight: normal; }
.preview { color: #d4a64a; }
input[type="checkbox"] { width: 16px; height: 16px; accent-color: #d4a64a; cursor: pointer; }
</style>

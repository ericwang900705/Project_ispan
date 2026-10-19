<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useGamesStore, hasDiscount, discountPercent } from '@/stores/games'

const route = useRoute()
const gamesStore = useGamesStore()
const { games, stats } = storeToRefs(gamesStore)

// 進頁面時抓遊戲和統計（銷量、評價）
onMounted(() => {
  gamesStore.fetchGames()
  gamesStore.fetchStats()
})

// 搜尋框的文字（v-model 綁定）
const keyword = ref('')
// 狀態篩選放在網址 ?status=OFF_SALE（數據總覽的狀態統計點過來會帶上），沒有代表全部
const statusFilter = computed(() => route.query.status ?? '')

// 依狀態和搜尋文字過濾後要顯示的遊戲；toLowerCase 讓英文不分大小寫
const shown = computed(() =>
  games.value
    .filter((g) => !statusFilter.value || statusOf(g) === statusFilter.value)
    .filter((g) => g.gameName.toLowerCase().includes(keyword.value.trim().toLowerCase()))
)

// 某款遊戲的統計，沒有資料就回傳空物件（畫面上用 ?? 0 顯示 0）
function statOf(game) {
  return stats.value[game.gameId] ?? {}
}

// 狀態的中文名稱；資料庫裡沒填（NULL）的舊資料當作上架中
const STATUS_LABELS = { ON_SALE: '上架中', OFF_SALE: '已下架', COMING_SOON: '即將推出' }
const statusOf = (g) => g.status ?? 'ON_SALE'

// 按鈕：已下架 → 上架，其他 → 下架
async function toggleStatus(g) {
  const next = statusOf(g) === 'OFF_SALE' ? 'ON_SALE' : 'OFF_SALE'
  try {
    await gamesStore.updateStatus(g.gameId, next)
  } catch (e) {
    alert(e.response?.data?.message ?? '更新失敗')
  }
}
</script>

<template>
  <section class="my-games">
    <!-- 標題列：左邊標題 + 數量，右邊搜尋框和新增按鈕 -->
    <div class="head">
      <h1>
        我的遊戲 <small>（{{ games.length }} 款）</small>
        <!-- 有狀態篩選時顯示標籤，按 ✕ 回到全部 -->
        <RouterLink v-if="statusFilter" to="/developer/games" class="filter-chip">
          只看{{ STATUS_LABELS[statusFilter] ?? statusFilter }} ✕
        </RouterLink>
      </h1>
      <div class="head-right">
        <input v-model="keyword" class="search" placeholder="🔍 搜尋遊戲名稱" />
        <RouterLink to="/developer/games/new" class="add">＋ 新增遊戲</RouterLink>
      </div>
    </div>

    <!-- 表格太寬時（手機）可以左右捲動，不會把整頁撐破 -->
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>封面</th>
            <th>遊戲名稱</th>
            <th>分類</th>
            <th>售價</th>
            <th class="num">銷量</th>
            <th class="num">評價</th>
            <th>狀態</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="g in shown" :key="g.gameId">
            <td><img :src="g.coverUrl" :alt="g.gameName" class="cover" /></td>
            <td class="name">{{ g.gameName }}</td>
            <td>{{ g.genre || '未分類' }}</td>
            <td>
              {{ g.price === 0 ? '免費' : `NT$ ${g.price}` }}
              <!-- 有打折：顯示折扣百分比 -->
              <span v-if="hasDiscount(g)" class="sale">-{{ discountPercent(g) }}%</span>
            </td>
            <td class="num">{{ statOf(g).soldCount ?? 0 }}</td>
            <td class="num">
              <template v-if="statOf(g).reviewCount">★ {{ statOf(g).avgRating.toFixed(1) }}</template>
              <template v-else>—</template>
            </td>
            <td>
              <!-- :class 用狀態當 class 名稱，不同狀態不同顏色 -->
              <span class="status" :class="statusOf(g)">{{ STATUS_LABELS[statusOf(g)] }}</span>
            </td>
            <!-- 操作：編輯、上架 / 下架按鈕，和到玩家看到的商店頁 -->
            <td class="actions">
              <RouterLink :to="`/developer/games/${g.gameId}/edit`" class="toggle">編輯</RouterLink>
              <button class="toggle" @click="toggleStatus(g)">
                {{ statusOf(g) === 'OFF_SALE' ? '上架' : '下架' }}
              </button>
              <RouterLink :to="`/games/${g.gameId}`" class="link">查看商店頁</RouterLink>
            </td>
          </tr>
          <!-- 搜尋不到時顯示一列提示 -->
          <tr v-if="!shown.length">
            <td colspan="8" class="empty">找不到符合「{{ keyword }}」的遊戲</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
/* 標題列：左右兩端對齊，太窄時搜尋框換到下一行 */
.head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.head h1 { margin: 0; font-size: 24px; }
.head small { font-size: 14px; color: #98a2c8; font-weight: normal; }
.search {
  width: 240px; padding: 8px 12px; font-size: 14px; border-radius: 6px; outline: none;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8;
}
.search:focus { border-color: #d4a64a; }

/* 表格外框：overflow-x: auto 讓表格太寬時在框裡捲動 */
.table-wrap { overflow-x: auto; background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px; font-size: 13px; font-weight: normal; color: #98a2c8; border-bottom: 1px solid #2e4a9a; white-space: nowrap; }
td { padding: 10px 12px; border-bottom: 1px solid #1c2a66; vertical-align: middle; }
/* 滑鼠移到某一列時整列變亮 */
tbody tr:hover { background: #1c2a66; }
/* 數字欄靠右對齊，tabular-nums 讓每個數字一樣寬，上下才會對齊 */
.num { text-align: right; font-variant-numeric: tabular-nums; }
.cover { width: 92px; height: 43px; object-fit: cover; border-radius: 4px; display: block; }
.name { font-weight: bold; }
.sale { margin-left: 6px; padding: 1px 6px; font-size: 12px; border-radius: 3px; background: #b23a2a; color: #fff1e0; }
.link { color: #d4a64a; white-space: nowrap; }
.link:hover { text-decoration: underline; background: transparent; }
.empty { text-align: center; color: #98a2c8; padding: 32px; }
/* 狀態標籤：上架中綠、已下架灰、即將推出藍 */
.status { padding: 2px 8px; font-size: 12px; border-radius: 10px; white-space: nowrap; border: 1px solid; }
.status.ON_SALE { color: #8fd07a; border-color: #4a7a3a; }
.status.OFF_SALE { color: #98a2c8; border-color: #5e6894; }
.status.COMING_SOON { color: #66c0f4; border-color: #2e6a9a; }
/* 操作欄：按鈕和連結橫排 */
.actions { display: flex; align-items: center; gap: 12px; }
.toggle {
  padding: 4px 12px; font-size: 13px; border-radius: 4px; cursor: pointer; white-space: nowrap;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8;
}
.toggle:hover { border-color: #d4a64a; background: #121a4a; }
/* 狀態篩選標籤 */
.filter-chip { margin-left: 8px; padding: 2px 10px; font-size: 13px; font-weight: normal; vertical-align: middle; border-radius: 12px; border: 1px solid #d4a64a; color: #d4a64a; }
.filter-chip:hover { background: #1c2a66; }
/* 標題列右邊：搜尋框 + 新增按鈕 */
.head-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.add {
  padding: 8px 16px; border-radius: 6px; font-size: 14px; font-weight: bold; white-space: nowrap;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.add:hover { filter: brightness(1.1); background: linear-gradient(to bottom, #e8be63, #b8892f); }
</style>

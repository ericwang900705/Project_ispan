<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'
import axios from 'axios'
import { useGamesStore } from '@/stores/games'

const gamesStore = useGamesStore()
// games：所有遊戲（有價格、狀態）；stats：{ gameId: { avgRating, reviewCount, soldCount } }
const { games, stats } = storeToRefs(gamesStore)

// 平台抽成：Steam 是三成，開發商實拿七成
const PLATFORM_FEE = 0.3

// 營收趨勢（後端 /api/developer/stats）：daily 每天一筆，thisMonth / lastMonth 本月和上個月
const trend = ref({ daily: [], thisMonth: { revenue: 0, sold: 0 }, lastMonth: { revenue: 0, sold: 0 } })
const days = ref(30) // 折線圖顯示最近幾天：7 或 30
const recentOrders = ref([])
const recentReviews = ref([])

async function fetchTrend() {
  // GET /api/developer/stats?days=30
  const { data } = await axios.get('/api/developer/stats', { params: { days: days.value } })
  trend.value = data
}
watch(days, fetchTrend)

onMounted(async () => {
  gamesStore.fetchGames()
  gamesStore.fetchStats()
  fetchTrend()
  // 最近 5 筆已付款訂單、最近 5 則評論
  const [orders, reviews] = await Promise.all([
    axios.get('/api/developer/orders/paid'),
    axios.get('/api/developer/reviews'),
  ])
  recentOrders.value = orders.data.slice(0, 5)
  recentReviews.value = reviews.data.slice(0, 5)
})

// 每款遊戲配上它的統計；沒有統計資料的遊戲（沒人買、沒人評）就當 0
const rows = computed(() =>
  games.value.map((g) => {
    const s = stats.value[g.gameId] ?? {}
    return {
      gameId: g.gameId,
      gameName: g.gameName,
      sold: s.soldCount ?? 0,
      rating: s.avgRating ?? 0,
      reviews: s.reviewCount ?? 0,
      // 營收先用「銷量 × 現在的售價」估算（實際成交價可能有用優惠券）
      revenue: (s.soldCount ?? 0) * g.price,
    }
  })
)

// 上面卡片的數字：reduce 把每一列加總起來
const totalSold = computed(() => rows.value.reduce((sum, r) => sum + r.sold, 0))
const totalRevenue = computed(() => rows.value.reduce((sum, r) => sum + r.revenue, 0))
const netRevenue = computed(() => Math.round(totalRevenue.value * (1 - PLATFORM_FEE)))
const totalReviews = computed(() => rows.value.reduce((sum, r) => sum + r.reviews, 0))
// 平均評價：用評論數加權（評論多的遊戲影響比較大），沒有評論就顯示 —
const avgRating = computed(() => {
  if (!totalReviews.value) return '—'
  const sum = rows.value.reduce((s, r) => s + r.rating * r.reviews, 0)
  return (sum / totalReviews.value).toFixed(1)
})

// 跟上個月比：回傳 { text: '↑12%', up: true }；上個月是 0 就不好比，顯示「本月新增」
function compare(current, previous) {
  current = Number(current)
  previous = Number(previous)
  if (!previous) return current ? { text: '本月新增', up: true } : null
  const pct = Math.round(((current - previous) / previous) * 100)
  return { text: `${pct >= 0 ? '↑' : '↓'}${Math.abs(pct)}% 比上個月`, up: pct >= 0 }
}
const revenueChange = computed(() => compare(trend.value.thisMonth.revenue, trend.value.lastMonth.revenue))
const soldChange = computed(() => compare(trend.value.thisMonth.sold, trend.value.lastMonth.sold))

// 遊戲狀態統計：點了跳到「我的遊戲」並篩選那個狀態；資料庫裡沒填狀態的當作上架中
const statusCounts = computed(() => {
  const count = (s) => games.value.filter((g) => (g.status ?? 'ON_SALE') === s).length
  return [
    { status: 'ON_SALE', label: '上架中', count: count('ON_SALE') },
    { status: 'OFF_SALE', label: '已下架', count: count('OFF_SALE') },
    { status: 'COMING_SOON', label: '即將推出', count: count('COMING_SOON') },
  ]
})

// 熱銷排行：依銷量由多到少，只取前 5 名；最長那條 = 100%
const chart = computed(() => [...rows.value].sort((a, b) => b.sold - a.sold).slice(0, 5))
const maxSold = computed(() => Math.max(1, ...chart.value.map((r) => r.sold)))

// ===== 營收趨勢折線圖（SVG，不用圖表套件） =====
// 畫布座標：寬 600、高 220；左邊留 56 放金額、下面留 24 放日期
const W = 600, H = 220, LEFT = 56, RIGHT = 12, TOP = 12, BOTTOM = 24
// y 軸最大值：取最高營收往上湊整（至少 100），刻度才是好讀的數字
const yMax = computed(() => {
  const max = Math.max(0, ...trend.value.daily.map((d) => Number(d.revenue)))
  if (max === 0) return 100
  const step = 10 ** Math.floor(Math.log10(max))
  return Math.ceil(max / step) * step
})
// 每一天的點：x 平均分配、y 依營收比例
const points = computed(() => {
  const list = trend.value.daily
  const n = Math.max(1, list.length - 1)
  return list.map((d, i) => ({
    x: LEFT + ((W - LEFT - RIGHT) * i) / n,
    y: TOP + (H - TOP - BOTTOM) * (1 - Number(d.revenue) / yMax.value),
    date: d.date,
    revenue: Number(d.revenue),
    orders: d.orders,
  }))
})
const linePath = computed(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
// 折線下面的淡色面積：多接兩個點回到底線
const areaPath = computed(() => {
  if (!points.value.length) return ''
  const first = points.value[0], last = points.value[points.value.length - 1]
  return `${first.x},${H - BOTTOM} ${linePath.value} ${last.x},${H - BOTTOM}`
})
// y 軸刻度：0、一半、最大
const yTicks = computed(() => [0, yMax.value / 2, yMax.value].map((v) => ({
  value: v, y: TOP + (H - TOP - BOTTOM) * (1 - v / yMax.value),
})))
// x 軸只標第一天、中間、最後一天，避免擠在一起
const xTicks = computed(() => {
  const p = points.value
  if (!p.length) return []
  return [p[0], p[Math.floor(p.length / 2)], p[p.length - 1]]
})
const periodRevenue = computed(() => trend.value.daily.reduce((s, d) => s + Number(d.revenue), 0))
const short = (date) => date.slice(5) // 2026-10-19 → 10-19
const money = (v) => Number(v).toLocaleString()
const day = (v) => v?.slice(0, 10)
</script>

<template>
  <section class="dash">
    <h1>數據總覽</h1>

    <!-- 摘要卡片 -->
    <div class="tiles">
      <div class="tile">
        <span class="label">總營收（估算）</span>
        <b>NT$ {{ money(totalRevenue) }}</b>
        <span class="sub">實拿 NT$ {{ money(netRevenue) }}（扣平台 {{ PLATFORM_FEE * 100 }}%）</span>
      </div>
      <div class="tile">
        <span class="label">本月營收</span>
        <b>NT$ {{ money(trend.thisMonth.revenue) }}</b>
        <span v-if="revenueChange" class="sub" :class="revenueChange.up ? 'up' : 'down'">{{ revenueChange.text }}</span>
      </div>
      <div class="tile">
        <span class="label">總銷量</span>
        <b>{{ totalSold }} <small>套</small></b>
        <span v-if="soldChange" class="sub" :class="soldChange.up ? 'up' : 'down'">本月 {{ trend.thisMonth.sold }} 套 · {{ soldChange.text }}</span>
      </div>
      <div class="tile">
        <span class="label">平均評價</span>
        <b>★ {{ avgRating }}</b>
        <RouterLink to="/developer/reviews" class="sub link">{{ totalReviews }} 則評論 →</RouterLink>
      </div>
    </div>

    <!-- 遊戲狀態統計：點了到「我的遊戲」看那個狀態的遊戲 -->
    <div class="status-row">
      <RouterLink v-for="s in statusCounts" :key="s.status" :to="{ path: '/developer/games', query: { status: s.status } }"
        class="status-chip" :class="s.status">
        {{ s.label }} <b>{{ s.count }}</b>
      </RouterLink>
    </div>

    <!-- 營收趨勢 -->
    <div class="panel">
      <div class="panel-head">
        <h2>營收趨勢 <small>近 {{ days }} 天 NT$ {{ money(periodRevenue) }}</small></h2>
        <div class="switch">
          <button :class="{ on: days === 7 }" @click="days = 7">7 天</button>
          <button :class="{ on: days === 30 }" @click="days = 30">30 天</button>
        </div>
      </div>
      <svg :viewBox="`0 0 ${W} ${H}`" class="line-chart" role="img" :aria-label="`近 ${days} 天營收趨勢`">
        <!-- 橫向格線和金額 -->
        <g v-for="t in yTicks" :key="t.value">
          <line :x1="LEFT" :x2="W - RIGHT" :y1="t.y" :y2="t.y" class="grid" />
          <text :x="LEFT - 8" :y="t.y + 4" text-anchor="end" class="axis">{{ money(t.value) }}</text>
        </g>
        <!-- 日期 -->
        <text v-for="t in xTicks" :key="t.date" :x="t.x" :y="H - 6" text-anchor="middle" class="axis">{{ short(t.date) }}</text>
        <polygon :points="areaPath" class="area" />
        <polyline :points="linePath" class="line" />
        <!-- 有營收的日子畫一個點；滑鼠移上去顯示那天的金額 -->
        <circle v-for="p in points.filter((p) => p.revenue > 0)" :key="p.date" :cx="p.x" :cy="p.y" r="4" class="dot">
          <title>{{ p.date }}：NT$ {{ money(p.revenue) }}（{{ p.orders }} 筆）</title>
        </circle>
      </svg>
    </div>

    <!-- 熱銷排行（前 5 名） -->
    <div class="panel">
      <h2>熱銷排行 <small>前 5 名</small></h2>
      <ul class="chart">
        <li v-for="r in chart" :key="r.gameId">
          <!-- title：名字太長被截斷時，滑鼠移上去看完整名稱 -->
          <span class="name" :title="r.gameName">{{ r.gameName }}</span>
          <span class="track">
            <span class="bar" :style="{ width: (r.sold / maxSold) * 100 + '%' }"></span>
          </span>
          <span class="num">{{ r.sold }}</span>
        </li>
      </ul>
    </div>

    <!-- 最近訂單、最近評論 -->
    <div class="two-col">
      <div class="panel">
        <div class="panel-head">
          <h2>最近訂單</h2>
          <RouterLink to="/developer/orders" class="more">全部 →</RouterLink>
        </div>
        <p v-if="!recentOrders.length" class="empty">還沒有訂單</p>
        <ul class="list">
          <li v-for="o in recentOrders" :key="o.orderId">
            <span class="main" :title="o.games.join('、')">#{{ o.orderId }} {{ o.games.join('、') }}</span>
            <span class="side">{{ o.username }} · NT$ {{ money(o.totalAmount) }} · {{ day(o.paidAt ?? o.createdAt) }}</span>
          </li>
        </ul>
      </div>
      <div class="panel">
        <div class="panel-head">
          <h2>最近評論</h2>
          <RouterLink to="/developer/reviews" class="more">全部 →</RouterLink>
        </div>
        <p v-if="!recentReviews.length" class="empty">還沒有評論</p>
        <ul class="list">
          <li v-for="r in recentReviews" :key="r.id">
            <span class="main" :title="r.content">{{ r.recommended ? '👍' : '👎' }} {{ r.gameName }}：{{ r.content || '（沒有內容）' }}</span>
            <span class="side">{{ r.username }} · ★{{ r.rating }} · {{ day(r.createdAt) }}<template v-if="!r.developerReply"> · <em>未回覆</em></template></span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.dash { display: flex; flex-direction: column; gap: 16px; }
.dash h1 { margin: 0; font-size: 24px; }
.dash h2 { margin: 0; font-size: 18px; }
.dash h2 small { font-size: 13px; color: #98a2c8; font-weight: normal; margin-left: 8px; }

/* 摘要卡片：寬度夠就一排四格，不夠自動換行 */
.tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.tile {
  display: flex; flex-direction: column; gap: 6px; padding: 16px 18px;
  background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px;
}
.tile .label { font-size: 13px; color: #98a2c8; }
.tile b { font-size: 26px; color: #d4a64a; font-variant-numeric: tabular-nums; }
.tile small { font-size: 13px; color: #98a2c8; font-weight: normal; }
.sub { font-size: 12px; color: #98a2c8; }
.sub.up { color: #8fd07a; }
.sub.down { color: #e06060; }
.sub.link:hover { color: #d4a64a; background: transparent; }

/* 遊戲狀態統計 */
.status-row { display: flex; gap: 8px; flex-wrap: wrap; }
.status-chip { padding: 6px 14px; font-size: 13px; border-radius: 16px; border: 1px solid; background: rgba(18, 26, 74, .85); }
.status-chip b { margin-left: 4px; font-size: 15px; }
.status-chip.ON_SALE { color: #8fd07a; border-color: #4a7a3a; }
.status-chip.OFF_SALE { color: #98a2c8; border-color: #5e6894; }
.status-chip.COMING_SOON { color: #66c0f4; border-color: #2e6a9a; }
.status-chip:hover { filter: brightness(1.25); background: rgba(18, 26, 74, .85); }

/* 共用的區塊外框 */
.panel { padding: 16px 18px; background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px; min-width: 0; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.panel > h2 { margin-bottom: 12px; }
.switch { display: flex; border: 1px solid #2e4a9a; border-radius: 14px; overflow: hidden; }
.switch button { border: none; background: transparent; color: #98a2c8; padding: 3px 12px; font-size: 13px; cursor: pointer; }
.switch button.on { background: #d4a64a; color: #1b0f0d; font-weight: bold; }
.more { font-size: 13px; color: #98a2c8; }
.more:hover { color: #d4a64a; background: transparent; }

/* 折線圖：寬度跟著外框縮放（viewBox 會等比例縮放） */
.line-chart { width: 100%; height: auto; display: block; }
.grid { stroke: #1c2a66; stroke-width: 1; }
.axis { fill: #98a2c8; font-size: 11px; font-variant-numeric: tabular-nums; }
.area { fill: rgba(212, 166, 74, .15); }
.line { fill: none; stroke: #d4a64a; stroke-width: 2.5; stroke-linejoin: round; }
.dot { fill: #e8be63; stroke: #121a4a; stroke-width: 2; cursor: default; }

/* 熱銷排行：每列三欄：遊戲名稱 | 長條 | 數字 */
.chart { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.chart li { display: grid; grid-template-columns: 200px 1fr 40px; align-items: center; gap: 12px; font-size: 14px; }
.name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.track { height: 12px; background: #1c2a66; border-radius: 6px; overflow: hidden; }
.bar { display: block; height: 100%; background: linear-gradient(to right, #b8892f, #e8be63); border-radius: 6px; transition: width .6s ease; }
.num { text-align: right; color: #98a2c8; font-variant-numeric: tabular-nums; }

/* 最近訂單、最近評論：寬度夠就左右兩欄 */
.two-col { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.list li { display: flex; flex-direction: column; gap: 2px; padding: 8px 0; border-bottom: 1px solid #1c2a66; min-width: 0; }
.list li:last-child { border-bottom: none; }
.list .main { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.list .side { font-size: 12px; color: #98a2c8; }
.list em { font-style: normal; color: #f0c060; }
.empty { margin: 8px 0; color: #98a2c8; font-size: 13px; }

@media (max-width: 600px) {
  .chart li { grid-template-columns: 110px 1fr 32px; }
}
</style>

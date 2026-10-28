<script setup>
import DatePicker from '../components/DatePicker.vue'
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api'

const data = ref(null), from = ref(''), to = ref(''), gameId = ref(''), loading = ref(false), error = ref('')
let run = 0
const money = v => new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD', maximumFractionDigits: 2 }).format(Number(v || 0))
const maximum = computed(() => Math.max(1, ...(data.value?.daily || []).map(x => Number(x.paid_amount))))
async function load(initial = false) {
  const id = ++run; loading.value = true; error.value = ''
  const query = new URLSearchParams()
  if (from.value) query.set('from', from.value)
  if (to.value) query.set('to', to.value)
  if (gameId.value) query.set('gameId', gameId.value)
  try { const result = await api('/workspace/publisher/sales?' + query); if (id !== run) return; data.value = result; if (initial) { from.value = result.from; to.value = result.to } }
  catch (e) { if (id === run) error.value = e.message }
  finally { if (id === run) loading.value = false }
}
function exportCsv() {
  if (!data.value || loading.value) return
  const report = data.value
  const rows = [
    ['HERO Game Platform 發行商銷售報表'],
    ['統計開始日期', report.from], ['統計結束日期', report.to],
    ['遊戲篩選', gameId.value ? (report.options || []).find(x => String(x.game_id) === String(gameId.value))?.game_name || gameId.value : '全部遊戲'],
    [], ['摘要指標', '數值'],
    ['已付款銷售金額', report.summary?.paid_amount ?? 0],
    ['已付款銷售份數', report.summary?.paid_units ?? 0],
    ['不重複購買人數', report.summary?.buyers ?? 0],
    ['已退款份數', report.summary?.refunded_units ?? 0],
    ['退款明細金額', report.summary?.refunded_amount ?? 0],
    [], ['遊戲編號', '遊戲名稱', '銷售份數', '購買人數', '已付款金額', '退款份數', '退款明細金額'],
    ...(report.games || []).map(g => [g.game_id, g.game_name, g.paid_units, g.buyers, g.paid_amount, g.refunded_units, g.refunded_amount]),
    [], ['每日日期', '已付款金額', '銷售份數', '退款份數'],
    ...(report.daily || []).map(d => [d.sale_date, d.paid_amount, d.paid_units, d.refunded_units]),
    [], ['備註', '資料依目前查詢 API 回傳結果匯出；金額非平台結算收益。']
  ]
  const csv = '\ufeff' + rows.map(cols => cols.map(v => '"' + String(v ?? '').replace(/"/g, '""') + '"').join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }))
  const link = document.createElement('a'); link.href = url; link.download = `HERO_銷售報表_${report.from}_${report.to}.csv`; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(() => load(true))
onUnmounted(() => { run++ })
</script>
<template>
  <section class="publisher-insights platform-dashboard">
    <header class="publisher-page-heading">
      <div>
        <span class="eyebrow">PUBLISHER CENTER / SALES</span>
        <h1>銷售統計</h1>
        <p class="muted">依付款日期與遊戲查看成交表現，預設最近30天。</p>
      </div>
      <div class="publisher-report-actions">
        <button type="button" class="secondary" :disabled="loading || !data" @click="exportCsv">
          匯出 CSV 報表
        </button>
        <RouterLink class="secondary" to="/publisher/overview">發行商主頁</RouterLink>
      </div>
    </header>
    <form class="publisher-panel publisher-sales-filter" @submit.prevent="load()">
      <label>開始日期
        <DatePicker v-model="from" label="開始日期" required :max="to || undefined" />
      </label>
      <label>結束日期
        <DatePicker v-model="to" label="結束日期" required :min="from || undefined" />
      </label>
      <label>遊戲
        <select v-model="gameId">
          <option value="">全部遊戲</option>
          <option v-for="game in data?.options" :key="game.game_id" :value="game.game_id">
            {{ game.game_name }}
          </option>
        </select>
      </label>
      <button class="primary" :disabled="loading">{{ loading ? '查詢中…' : '查詢統計' }}</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <template v-if="data">
      <p class="publisher-report-range">
        統計期間：{{ data.from }} ～ {{ data.to }}（依訂單付款時間）
      </p>
      <div class="publisher-sales-metrics">
        <article>
          <span>已付款銷售金額</span>
          <strong>{{ money(data.summary.paid_amount) }}</strong>
          <small>已扣明細折扣，排除已退款訂單</small>
        </article>
        <article>
          <span>銷售份數</span>
          <strong>{{ data.summary.paid_units }}</strong>
          <small>{{ data.summary.buyers }} 位不重複購買會員</small>
        </article>
        <article>
          <span>已退款份數</span>
          <strong>{{ data.summary.refunded_units }}</strong>
          <small>退款訂單明細金額 {{ money(data.summary.refunded_amount) }}</small>
        </article>
      </div>
      <p class="publisher-pending">
        金額採下單時的明細成交快照，非平台結算或實收收益。退款以所選期間付款、目前已退款的訂單統計，並非退款發生日期；不含未付款與取消訂單。
      </p>
      <section class="publisher-panel">
        <h2>各遊戲銷售表現</h2>
        <p v-if="!data.games.length" class="publisher-empty">此期間尚無已付款或已退款訂單。</p>
        <div v-else class="publisher-table-scroll">
          <table class="publisher-sales-table">
            <thead>
              <tr>
                <th>遊戲</th>
                <th>銷售份數</th>
                <th>購買人數</th>
                <th>已付款金額</th>
                <th>退款份數</th>
                <th>退款明細金額</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="game in data.games" :key="game.game_id">
                <td>
                  <RouterLink :to="{ path: '/publisher/games', query: { mode: 'buyers', game: game.game_id } }">
                    {{ game.game_name }}
                  </RouterLink>
                </td>
                <td>{{ game.paid_units }}</td>
                <td>{{ game.buyers }}</td>
                <td>{{ money(game.paid_amount) }}</td>
                <td>{{ game.refunded_units }}</td>
                <td>{{ money(game.refunded_amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="publisher-panel publisher-sales-daily">
        <h2>每日已付款金額</h2>
        <p class="muted">僅列有付款紀錄的日期；退款另列，未列日期為零。</p>
        <p v-if="!data.daily.length" class="muted">此期間沒有銷售紀錄。</p>
        <div v-for="day in data.daily" :key="day.sale_date" class="publisher-sales-day">
          <time>{{ day.sale_date }}</time>
          <div class="publisher-sales-bar" aria-hidden="true">
            <span :style="{ width: (Number(day.paid_amount) / maximum * 100) + '%' }"></span>
          </div>
          <strong>{{ money(day.paid_amount) }}</strong>
          <small>售出 {{ day.paid_units }} 份・退款 {{ day.refunded_units }} 份</small>
        </div>
      </section>
    </template>
    <p v-else-if="loading" role="status">正在載入統計…</p>
  </section>
</template>

<style scoped>
/* 銷售表維持前台深色底，明確指定文字與列背景，避免共用表格樣式互相覆蓋。 */
.publisher-insights .publisher-sales-table { width: 100%; border: 1px solid #435a81; background: #13223f; border-radius: 12px; color: #e8f0ff; font-size: 14px; }
.publisher-insights .publisher-sales-table :is(th,td) { padding: 16px 15px; background: #142540; color: #e7efff; border-bottom: 1px solid #344e74; font-variant-numeric: tabular-nums; }
.publisher-insights .publisher-sales-table th { background: #2a3e60; color: #f0d29a; font-size: 12px; font-weight: 700; }
.publisher-insights .publisher-sales-table tr:nth-child(even) td { background: #192c4c; }.publisher-insights .publisher-sales-table tbody tr:hover td { background: #253d61; }
.publisher-insights .publisher-sales-table a { color: #f5d38b; font-size: 14px; font-weight: 650; text-decoration: none; }.publisher-insights .publisher-sales-table a:hover { color: #ffe6ae; text-decoration: underline; }.publisher-insights .publisher-table-scroll { border: 1px solid #40587e; border-radius: 12px; overflow: auto; margin-top: 18px; }
.publisher-insights .publisher-sales-filter { display: grid; grid-template-columns: repeat(3,minmax(150px,1fr)) auto; gap: 18px; align-items: end; }.publisher-insights .publisher-sales-filter label { display: grid; gap: 9px; }.publisher-insights .publisher-sales-filter > button { min-height: 44px; margin: 0; padding: 12px 20px; white-space: nowrap; }
@media(max-width:850px) { .publisher-insights .publisher-sales-filter { grid-template-columns: repeat(2,minmax(0,1fr)); } }@media(max-width:500px) { .publisher-insights .publisher-sales-filter { grid-template-columns: 1fr; } }
</style>

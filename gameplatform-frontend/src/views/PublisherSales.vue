<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api'
import '../styles/publisher.css'
const data = ref(null), from = ref(''), to = ref(''), gameId = ref(''), loading = ref(false), error = ref('')
let run = 0
const money = v => new Intl.NumberFormat('zh-TW',{style:'currency',currency:'TWD',maximumFractionDigits:2}).format(Number(v || 0))
const maximum = computed(() => Math.max(1,...(data.value?.daily || []).map(x => Number(x.paid_amount))))
async function load(initial = false) {
  const id = ++run; loading.value = true; error.value = ''
  const query = new URLSearchParams()
  if (from.value) query.set('from',from.value)
  if (to.value) query.set('to',to.value)
  if (gameId.value) query.set('gameId',gameId.value)
  try { const result = await api('/aki/publisher/sales?' + query); if (id !== run) return; data.value = result; if (initial) { from.value = result.from; to.value = result.to } }
  catch (e) { if (id === run) error.value = e.message }
  finally { if (id === run) loading.value = false }
}
onMounted(() => load(true))
onUnmounted(() => { run++ })
</script>
<template>
  <section class="publisher-insights platform-dashboard">
    <header class="publisher-page-heading"><div><span class="eyebrow">PUBLISHER CENTER / SALES</span><h1>銷售統計</h1><p class="muted">依付款日期與遊戲查看成交表現，預設最近30天。</p></div><RouterLink class="secondary" to="/aki/publisher/overview">發行商主頁</RouterLink></header>
    <form class="publisher-panel publisher-sales-filter" @submit.prevent="load()"><label>開始日期<input v-model="from" type="date" required :max="to || undefined"></label><label>結束日期<input v-model="to" type="date" required :min="from || undefined"></label><label>遊戲<select v-model="gameId"><option value="">全部遊戲</option><option v-for="game in data?.options" :key="game.game_id" :value="game.game_id">{{ game.game_name }}</option></select></label><button class="primary" :disabled="loading">{{ loading ? '查詢中…' : '查詢統計' }}</button></form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <template v-if="data">
      <p class="publisher-report-range">統計期間：{{ data.from }} ～ {{ data.to }}（依訂單付款時間）</p>
      <div class="publisher-sales-metrics"><article><span>已付款銷售金額</span><strong>{{ money(data.summary.paid_amount) }}</strong><small>已扣明細折扣，排除已退款訂單</small></article><article><span>銷售份數</span><strong>{{ data.summary.paid_units }}</strong><small>{{ data.summary.buyers }} 位不重複購買會員</small></article><article><span>已退款份數</span><strong>{{ data.summary.refunded_units }}</strong><small>退款訂單明細金額 {{ money(data.summary.refunded_amount) }}</small></article></div>
      <p class="publisher-pending">金額採下單時的明細成交快照，非平台結算或實收收益。退款以所選期間付款、目前已退款的訂單統計，並非退款發生日期；不含未付款與取消訂單。</p>
      <section class="publisher-panel"><h2>各遊戲銷售表現</h2><p v-if="!data.games.length" class="publisher-empty">此期間尚無已付款或已退款訂單。</p><div v-else class="publisher-table-scroll"><table class="publisher-sales-table"><thead><tr><th>遊戲</th><th>銷售份數</th><th>購買人數</th><th>已付款金額</th><th>退款份數</th><th>退款明細金額</th></tr></thead><tbody><tr v-for="game in data.games" :key="game.game_id"><td><RouterLink :to="{path:'/aki/publisher/games',query:{mode:'buyers',game:game.game_id}}">{{ game.game_name }}</RouterLink></td><td>{{ game.paid_units }}</td><td>{{ game.buyers }}</td><td>{{ money(game.paid_amount) }}</td><td>{{ game.refunded_units }}</td><td>{{ money(game.refunded_amount) }}</td></tr></tbody></table></div></section>
      <section class="publisher-panel publisher-sales-daily"><h2>每日已付款金額</h2><p class="muted">僅列有付款紀錄的日期；退款另列，未列日期為零。</p><p v-if="!data.daily.length" class="muted">此期間沒有銷售紀錄。</p><div v-for="day in data.daily" :key="day.sale_date" class="publisher-sales-day"><time>{{ day.sale_date }}</time><div class="publisher-sales-bar" aria-hidden="true"><span :style="{width: (Number(day.paid_amount)/maximum*100) + '%'}"></span></div><strong>{{ money(day.paid_amount) }}</strong><small>售出 {{ day.paid_units }} 份・退款 {{ day.refunded_units }} 份</small></div></section>
    </template><p v-else-if="loading" role="status">正在載入統計…</p>
  </section>
</template>

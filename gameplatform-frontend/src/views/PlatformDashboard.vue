<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, session } from '../api'
import { hasPermission } from '../permissions'
import UiIcon from '../components/UiIcon.vue'
const data = ref(null), error = ref(''), loading = ref(false)
const maximum = computed(() => Math.max(1, ...(data.value?.revenueTrend || []).map(row => Number(row.amount))))
const number = v => v == null ? '—' : new Intl.NumberFormat('zh-TW').format(v)
const metrics = computed(() => [
  {
    key: 'members',
    label: '會員總數',
    icon: 'users',
    value: number(data.value?.memberCount),
    note: '既有會員資料表'
  },
  {
    key: 'games',
    label: '已送審遊戲總數',
    icon: 'game',
    value: number(data.value?.gameCount),
    note: '包含待審、已上架及下架遊戲；不含草稿'
  },
  {
    key: 'orders',
    label: '本月訂單',
    icon: 'cart',
    value: number(data.value?.monthlyOrderCount),
    note: '本月建立的訂單'
  },
  {
    key: 'revenue',
    label: '本月已付款金額',
    icon: 'wallet',
    value: data.value?.monthlyRevenue == null ? '—' : 'NT$ ' + number(data.value.monthlyRevenue),
    note: '依付款日，排除退款與取消訂單'
  }
])
async function load() {
  if (loading.value || !hasPermission(session.user, 'DASHBOARD_VIEW')) return
  loading.value = true; error.value = ''
  try {
    data.value = await api('/management/dashboard')
  } catch (e) {
    error.value = e.message; data.value = null
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <section class="platform-dashboard">
    <div class="platform-heading">
      <div><span class="eyebrow">ADMIN HOME</span>
        <h1>管理員主頁</h1>
        <p class="muted">掌握平台資料概況，從左側選單進入管理功能。</p>
      </div><button class="secondary" :disabled="loading" @click="load">{{ loading ? '更新中…' : '重新整理' }}</button>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="platform-stat-grid">
      <article v-for="metric in metrics" :key="metric.key" class="platform-stat">
        <div><span>{{ metric.label }}</span>
          <UiIcon :name="metric.icon" />
        </div><strong>{{ loading ? '…' : metric.value }}</strong><small>{{ metric.note }}</small>
      </article>
    </div>
    <article class="platform-trend">
      <header>
        <div>
          <h2>近六個月已付款金額</h2>
          <p>依訂單付款日期統計</p>
        </div>
      </header>
      <div v-if="data" class="revenue-trend">
        <div v-for="row in data.revenueTrend" :key="row.month" class="revenue-row"><time>{{ row.month }}</time>
          <div class="revenue-track"><span :style="{ width: Number(row.amount) / maximum * 100 + '%' }"></span></div>
          <strong>NT$
            {{ number(row.amount) }}</strong>
        </div>
      </div>
      <p v-else class="platform-chart-empty">{{ loading ? '載入統計中…' : '尚無可用的統計資料' }}</p>
      <footer>僅統計目前為已付款狀態的訂單金額，非平台結算收益。</footer>
    </article>
    <footer class="platform-footer dashboard-update-footer"><div class="dashboard-update-card" role="status"><span class="dashboard-update-dot" :class="{ loading }"></span><div><strong>{{ loading ? '正在更新平台資料' : '資料更新時間' }}</strong><time v-if="data?.updatedAt" :datetime="data.updatedAt">{{ new Date(data.updatedAt).toLocaleString('zh-TW', { hour12: false }) }}</time><small v-else>尚未取得更新時間</small></div><UiIcon name="refresh" /></div></footer>
  </section>
</template>

<style scoped>
.dashboard-update-footer { display: flex; justify-content: flex-end; margin-top: 24px; }.dashboard-update-card { display: flex; align-items: center; gap: 13px; padding: 15px 20px; border: 1px solid #b7cbdc; border-radius: 13px; background: #f2f7fb; color: #294b65; box-shadow: 0 4px 16px #2446600a; }.dashboard-update-dot { width: 9px; height: 9px; flex-shrink: 0; border-radius: 50%; background: #3d9b76; box-shadow: 0 0 0 4px #3d9b7614; }.dashboard-update-dot.loading { background: #c49a4c; }.dashboard-update-card strong { display: block; font-size: 11px; color: #587287; margin-bottom: 4px; }.dashboard-update-card time,.dashboard-update-card small { font-size: 13px; color: #24465f; font-variant-numeric: tabular-nums; }.dashboard-update-card > .ui-icon { width: 18px; height: 18px; margin-left: 15px; color: #6b8ba3; }
@media(max-width:600px) { .dashboard-update-footer { justify-content: stretch; }.dashboard-update-card { width: 100%; box-sizing: border-box; } }

.revenue-trend {
  padding-top: 24px;
  display: grid;
  gap: 22px
}

.revenue-row {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) 130px;
  gap: 14px;
  align-items: center;
  font-size: 13px
}

.revenue-track {
  height: 16px;
  background: #edf3f8;
  border-radius: 8px;
  overflow: hidden
}

.revenue-track span {
  display: block;
  height: 100%;
  background: #178fa5;
  border-radius: 8px
}

.revenue-row strong {
  text-align: right
}

@media(max-width:600px) {
  .revenue-row {
    grid-template-columns: 65px minmax(0, 1fr) 95px;
    gap: 8px;
    font-size: 11px
  }
}

.platform-dashboard {
  max-width: 1200px;
  padding: 32px;
  margin: auto;
  color: #21384e
}

.platform-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px
}

.platform-heading h1 {
  font-size: 27px;
  margin: 7px 0 10px
}

.platform-heading p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7
}

.platform-heading button {
  white-space: nowrap
}

.platform-info {
  font-size: 12px;
  line-height: 1.9;
  color: #637e92;
  background: #edf7fc;
  border: 1px solid #d4e7f3;
  border-radius: 8px;
  padding: 12px 16px;
  margin: 0 0 22px
}

.platform-info a {
  color: #06899f;
  text-decoration: underline;
  margin-left: 12px
}

.platform-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 22px
}

.platform-stat {
  background: #fff;
  border: 1px solid #e0e8ee;
  border-radius: 12px;
  padding: 22px;
  box-shadow: 0 5px 16px #16456305
}

.platform-stat>div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #76899a;
  font-size: 13px;
  gap: 8px
}

.platform-stat .ui-icon {
  width: 20px;
  height: 20px;
  color: #7996a9
}

.platform-stat strong {
  display: block;
  font-size: 29px;
  font-weight: 650;
  color: #20364b;
  margin: 12px 0 8px
}

.platform-stat small {
  font-size: 10px;
  color: #9aa9b5
}

.platform-trend {
  background: white;
  border: 1px solid #e0e8ee;
  border-radius: 12px;
  padding: 24px
}

.platform-trend header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px
}

.platform-trend h2 {
  font-size: 16px;
  margin: 0
}

.platform-trend header p {
  font-size: 11px;
  color: #93a2af;
  margin: 8px 0 0
}

.platform-pending {
  color: #8596a4;
  background: #f2f5f7;
  border-radius: 20px;
  padding: 6px 11px;
  font-size: 10px;
  white-space: nowrap
}

.platform-chart-empty {
  min-height: 270px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
  gap: 13px;
  margin-top: 18px;
  border-radius: 8px;
  background: repeating-linear-gradient(to bottom, #fff 0, #fff 65px, #edf2f5 66px, #fff 67px)
}

.platform-chart-empty>.ui-icon {
  height: 42px;
  width: 42px;
  color: #acc4d2;
  background: white
}

.platform-chart-empty h3 {
  font-size: 15px;
  font-weight: 500;
  margin: 0;
  color: #7590a2;
  background: white;
  padding: 5px
}

.platform-chart-empty p {
  font-size: 12px;
  margin: 0;
  color: #9aaebb;
  line-height: 1.8;
  background: white;
  padding: 0 8px
}

.platform-trend footer {
  font-size: 10px;
  color: #9aa9b5;
  margin-top: 20px
}

.platform-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
  flex-wrap: wrap
}

.platform-footer small {
  font-size: 11px;
  color: #859db0
}

@media(max-width:960px) {
  .platform-dashboard {
    padding: 24px 16px
  }

  .platform-heading h1 {
    font-size: 23px
  }

  .platform-stat {
    padding: 18px
  }

  .platform-trend {
    padding: 20px
  }

  .platform-heading {
    align-items: flex-start
  }
}

@media(max-width:560px) {
  .platform-stat-grid {
    grid-template-columns: 1fr
  }

  .platform-stat {
    padding: 18px
  }

  .platform-trend {
    padding: 18px
  }
}
</style>

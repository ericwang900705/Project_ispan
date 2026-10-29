<script setup>
import DatePicker from '../components/DatePicker.vue'
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { api } from '../api'
const games = ref([]), gameId = ref(''), percent = ref(20), startAt = ref(''), busy = ref(false), error = ref(''), notice = ref('')
const now = ref(Date.now())
let clockTimer
// 日期時間欄位精度為分鐘，因此最早只能選下一分鐘
const toLocalMinute = timestamp => {
  const d = new Date(timestamp)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const minStartAt = computed(() => toLocalMinute(Math.floor(now.value / 60000) * 60000 + 60000))
const startTimeExpired = computed(() => !!startAt.value && (new Date(startAt.value).getTime() < now.value || startAt.value < minStartAt.value))

// 下拉選單僅顯示已上架遊戲
const publishedGames = computed(() => games.value.filter(g => g.status === 'PUBLISHED'))
const selected = computed(() => publishedGames.value.find(g => String(g.gameId) === String(gameId.value)))
const price = computed(() => selected.value ? Math.round(Number(selected.value.originalPrice) * (100 - Number(percent.value)) / 100) : 0)
const money = n => `NT$${Number(n || 0).toLocaleString('zh-TW')}`
const date = s => s ? String(s).replace('T', ' ').slice(0, 16) : '—'
const endDate = computed(() => { if (!startAt.value) return '選擇開始時間後自動計算'; const d = new Date(startAt.value); d.setDate(d.getDate() + 7); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` })
const locked = computed(() => !selected.value || selected.value.status !== 'PUBLISHED' || (selected.value.nextAllowedAt && new Date(selected.value.nextAllowedAt).getTime() > now.value))
async function load() { try { games.value = await api('/workspace/publisher/discounts') } catch (e) { error.value = e.message } }
async function save() {
  error.value = ''; notice.value = ''
  now.value = Date.now()
  if (startTimeExpired.value) { error.value = '開始時間不可早於現在，請重新選擇未來時間。'; return }
  if (locked.value || !Number.isInteger(Number(percent.value)) || percent.value < 1 || percent.value > 99 || !startAt.value) { error.value = '請選擇可設定折扣的遊戲、開始時間與 1～99 的整數折扣'; return }
  if (!window.confirm('確定建立固定七天的折扣活動？建立後無法修改或取消。')) return
  busy.value = true
  try { const r = await api('/workspace/publisher/discounts', { method: 'POST', body: { gameId: Number(gameId.value), percent: Number(percent.value), startAt: startAt.value } }); notice.value = `折扣設定成功！結束時間：${date(r.endAt)}`; await load() }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(() => {
  load()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => window.clearInterval(clockTimer))
</script>
<template>
  <section class="publisher-insights platform-dashboard">
    <header class="publisher-page-heading">
      <div><span class="eyebrow">PUBLISHER CENTER / DISCOUNTS</span>
        <h1>遊戲折扣管理</h1>
        <p class="muted">僅已上架遊戲可設定，固定 7 天，活動結束後 30 天內不可再次折扣。</p>
      </div>
    </header>
    <div class="publisher-panel" style="padding:24px;max-width:760px">
      <p v-if="!selected" class="published-only-notice" role="note"><span class="notice-icon"
          aria-hidden="true">!</span><strong>只有已上架遊戲可以設定折扣。</strong></p>
      <div class="discount-game-field"><label for="discount-game-select">選擇遊戲</label><select id="discount-game-select"
          v-model="gameId">
          <option value="">請選擇已上架遊戲</option>
          <option v-for="g in publishedGames" :key="g.gameId" :value="String(g.gameId)">{{ g.gameName }}</option>
        </select></div>
      <p v-if="!publishedGames.length" class="muted">目前沒有已上架的遊戲可設定折扣。</p>
      <template v-if="selected">
        <p>原價：<strong>{{ money(selected.originalPrice) }}</strong></p>
        <p v-if="selected.nextAllowedAt">下次可開始折扣：{{ date(selected.nextAllowedAt) }}</p>
        <label class="discount-setting-field">折扣百分比（1～99 整數）<input v-model.number="percent" type="number" min="1"
            max="99" step="1" :disabled="locked || busy" /></label>
        <label class="discount-setting-field">開始時間<DatePicker v-model="startAt" type="datetime-local" label="開始時間" :min="minStartAt" :disabled="locked || busy" required /></label>
        <p v-if="startTimeExpired" class="discount-time-error" role="alert">開始時間已經過去，請選擇目前時間之後的時段。</p>
        <p>結束時間：{{ endDate }}（固定 7 天）</p>
        <p>預估折扣價：<strong>{{ money(price) }}</strong> <span v-if="percent >= 1 && percent <= 99"
            style="background:#a4d542;color:#101827;padding:6px;font-weight:bold">-{{ percent }}%</span></p>
        <button class="primary" :disabled="locked || busy || !startAt || startTimeExpired" @click="save">{{ busy ?
          '儲存中…' : '建立折扣活動' }}</button>
      </template>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <p v-if="notice">{{ notice }}</p>
    </div>
  </section>
</template>

<style scoped>
/* 已上架限制：醒目提示，避免被背景說明文字淹沒 */
.published-only-notice {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  margin: 0 0 22px;
  border: 1px solid #e4b54c;
  border-left: 5px solid #e4b54c;
  border-radius: 8px;
  background: rgba(228, 181, 76, .14);
  color: #ffe5a1;
  font-size: 16px;
  line-height: 1.5;
}

.notice-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 24px;
  height: 24px;
  border-radius: 50%;
  background: #e4b54c;
  color: #101827;
  font-weight: 800;
}

/* 讓選擇遊戲標籤與下拉選單保留明顯間距 */
.discount-game-field {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 14px;
}

.discount-game-field>label {
  flex: 0 0 auto;
}

.discount-game-field>select {
  width: min(100%, 340px);
  min-width: 220px;
  padding: 10px 14px;
  box-sizing: border-box;
}

/* 折扣百分比與開始時間：標籤、輸入框文字統一大小 */
.discount-setting-field {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 14px 14px 0;
  font-size: 16px;
  line-height: 1.5;
}

.discount-setting-field>input {
  font-size: 16px;
  line-height: 1.5;
  padding: 10px 12px;
  box-sizing: border-box;
}

.discount-setting-field>input[type="number"] {
  width: 76px;
}

.discount-setting-field>input[type="datetime-local"] {
  min-width: 230px;
}

.discount-time-error {
  color: #ffd071;
  font-weight: 700;
  margin: 0 0 12px;
}
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useCouponsStore, discountLabel } from '@/stores/coupons'

const store = useCouponsStore()

// 上面兩大區：領券中心 / 我的優惠券
const section = ref('center')

// 我的優惠券的分頁：key 對應 member_coupons.status
const tabs = [
  { key: 'UNUSED', label: '可使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已過期' },
]
const tab = ref('UNUSED')
const shown = computed(() => store.mine.filter((m) => m.status === tab.value))
// 每個分頁的張數（顯示在分頁旁邊）
const counts = computed(() =>
  Object.fromEntries(tabs.map((t) => [t.key, store.mine.filter((m) => m.status === t.key).length])),
)

// 時間顯示成「2026/10/21 14:30」
function fmt(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-TW', { dateStyle: 'short', timeStyle: 'short', hour12: false })
}

// 有效期間：開始日（DATE，例如 2026-10-01）～ 到期時間；沒有到期時間就是「長期有效」
function period(c) {
  const start = c.startAt ? c.startAt.replaceAll('-', '/') : ''
  if (!c.expiresAt) return start ? `${start} 起長期有效` : '長期有效'
  return `${start} ～ ${fmt(c.expiresAt)}`
}

// 總張數：沒有上限就不顯示剩幾張
function remainText(item) {
  const limit = item.coupon.totalLimit
  return limit == null ? '不限量' : `剩 ${Math.max(0, limit - item.totalClaimed)} / ${limit} 張`
}

async function claim(item) {
  try {
    await store.claim(item.coupon.couponId)
  } catch (e) {
    alert(e.message) // 例如「每人限領 1 張，你已經領過了」
  }
}

onMounted(() => store.fetchAll())
</script>

<template>
  <main class="coupons">
    <div class="wrap">
      <div class="head">
        <h1>優惠券</h1>
        <span>先領券，結帳時在購物車選用</span>
      </div>

      <div class="sections">
        <button :class="{ on: section === 'center' }" @click="section = 'center'">🎁 領券中心</button>
        <button :class="{ on: section === 'mine' }" @click="section = 'mine'">
          🏷️ 我的優惠券 <span class="count">{{ store.mine.length }}</span>
        </button>
      </div>

      <!-- 領券中心 -->
      <template v-if="section === 'center'">
        <div v-if="!store.center.length" class="empty">目前沒有可以領的優惠券</div>
        <div v-else class="grid">
          <div v-for="item in store.center" :key="item.coupon.couponId" class="ticket"
            :class="{ off: !item.canClaim }">
            <div class="value">{{ discountLabel(item.coupon) }}</div>
            <div class="body">
              <div class="name">{{ item.coupon.couponName }}</div>
              <div class="meta">{{ period(item.coupon) }}</div>
              <div class="meta">
                每人限領 {{ item.coupon.perMemberLimit }} 張（已領 {{ item.claimedByMe }}）· {{ remainText(item) }}
              </div>
            </div>
            <button class="claim" :disabled="!item.canClaim || store.loading" :title="item.reason ?? ''"
              @click="claim(item)">
              {{ item.canClaim ? '領取' : item.reason?.includes('領完') ? '已領完' : '已領取' }}
            </button>
          </div>
        </div>
      </template>

      <!-- 我的優惠券 -->
      <template v-else>
        <div class="tabs">
          <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">
            {{ t.label }} <span class="count">{{ counts[t.key] }}</span>
          </button>
        </div>
        <div v-if="!shown.length" class="empty">
          這裡沒有優惠券
          <a v-if="tab === 'UNUSED'" href="#" @click.prevent="section = 'center'">去領券中心 →</a>
        </div>
        <div v-else class="grid">
          <div v-for="m in shown" :key="m.memberCouponId" class="ticket" :class="{ off: m.status !== 'UNUSED' }">
            <div class="value">{{ discountLabel(m.coupon) }}</div>
            <div class="body">
              <div class="name">{{ m.coupon.couponName }}</div>
              <div class="meta">{{ period(m.coupon) }}</div>
              <div class="meta">領取：{{ fmt(m.receivedAt) }}</div>
              <div v-if="m.status === 'USED'" class="meta">
                使用：{{ fmt(m.usedAt) }}<template v-if="m.orderId">（訂單 #{{ m.orderId }}）</template>
              </div>
            </div>
            <RouterLink v-if="m.status === 'UNUSED'" to="/shopping-cart" class="claim">去使用</RouterLink>
            <span v-else class="stamp">{{ m.status === 'USED' ? '已使用' : '已過期' }}</span>
          </div>
        </div>
      </template>
    </div>
  </main>
</template>

<!-- 優惠券頁樣式：和我的訂單同色系（深藍 + 金色） -->
<style scoped>
.coupons {
  --bg: #0b0c32;
  --surface: #121a4a;
  --surface-2: #1c2a66;
  --gold: #d4a64a;
  --gold-dark: #2e4a9a;
  --text: #e4e8f8;
  --text-muted: #98a2c8;

  min-height: 100vh; padding: 32px 16px; color: var(--text);
}
.wrap { max-width: 1000px; margin: 0 auto; }

.head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.head h1 { margin: 0; font-size: 26px; padding-left: 12px; border-left: 4px solid var(--gold); }
.head span { color: var(--text-muted); font-size: 14px; }

.sections, .tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.sections button, .tabs button {
  padding: 6px 16px; border-radius: 16px; cursor: pointer; font-size: 14px;
  border: 1px solid var(--surface-2); background: var(--surface); color: var(--text-muted);
}
.sections button { font-size: 16px; padding: 8px 20px; }
.sections button.on, .tabs button.on { border-color: var(--gold); color: var(--text); }
.count { font-size: 12px; color: var(--gold); margin-left: 2px; }

.empty { text-align: center; padding: 48px 0; color: var(--text-muted); background: var(--surface); border-radius: 8px; border: 1px dashed var(--gold-dark); }
.empty a { margin-left: 8px; color: var(--gold); }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }

/* 一張券：左邊金額、中間說明、右邊按鈕 */
.ticket {
  display: flex; align-items: center; gap: 12px; padding: 14px;
  background: var(--surface); border: 1px solid var(--gold-dark); border-left: 6px solid var(--gold); border-radius: 8px;
}
.ticket.off { border-left-color: var(--surface-2); opacity: .65; }
.value { width: 84px; flex-shrink: 0; text-align: center; color: var(--gold); font-weight: bold; font-size: 15px; line-height: 1.3; }
.body { flex: 1; min-width: 0; }
.name { font-weight: bold; margin-bottom: 4px; }
.meta { font-size: 12px; color: var(--text-muted); }

.claim {
  flex-shrink: 0; padding: 6px 14px; border-radius: 6px; border: none; cursor: pointer; font-size: 14px;
  background: var(--gold); color: #1b0f0d; font-weight: bold; white-space: nowrap;
}
.claim:hover { background: #e6bb62; }
.claim:disabled { background: var(--surface-2); color: var(--text-muted); cursor: not-allowed; }
.stamp { flex-shrink: 0; font-size: 13px; color: var(--text-muted); border: 1px solid var(--surface-2); padding: 4px 10px; border-radius: 6px; }
</style>

<script setup>
import UiIcon from '../components/UiIcon.vue'
const metrics = [
    { key: 'members', label: '會員總數', icon: 'users', note: '尚未串接 · 會員組串接' },
    { key: 'games', label: '遊戲總數', icon: 'game', note: '尚未串接 · 遊戲組串接' },
    { key: 'orders', label: '本月訂單', icon: 'cart', note: '尚未串接 · 購物車／訂單組串接' },
    { key: 'revenue', label: '本月營收', icon: 'wallet', note: '尚未串接 · 訂單／金流組串接' }
]
</script>
<template>
 <section class="platform-dashboard">
  <div class="platform-heading"><div><span class="eyebrow">ADMIN HOME</span><h1>管理員主頁</h1><p class="muted">掌握平台資料概況，從左側選單進入管理功能。</p></div></div>
   <div class="platform-stat-grid"><article v-for="metric in metrics" :key="metric.key" class="platform-stat"><div><span>{{metric.label}}</span><UiIcon :name="metric.icon" /></div><strong>—</strong><small>{{metric.note}}</small></article></div>
   <article class="platform-trend"><header><div><h2>每月營收趨勢</h2><p>待訂單／金流組串接後提供統計</p></div><span class="platform-pending">尚未串接</span></header><div class="platform-chart-empty"><UiIcon name="trend" /><h3>尚無可用的營收資料</h3><p>由訂單／金流組串接後，這裡會顯示每月營收趨勢。</p></div><footer>未接入的數據顯示「—」，不使用示範數字代替。</footer></article>
   
 </section>
</template>

<style scoped>
.platform-dashboard{max-width:1200px;padding:32px;margin:auto;color:#21384e}.platform-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:24px}.platform-heading h1{font-size:27px;margin:7px 0 10px}.platform-heading p{margin:0;font-size:13px;line-height:1.7}.platform-heading button{white-space:nowrap}.platform-info{font-size:12px;line-height:1.9;color:#637e92;background:#edf7fc;border:1px solid #d4e7f3;border-radius:8px;padding:12px 16px;margin:0 0 22px}.platform-info a{color:#06899f;text-decoration:underline;margin-left:12px}.platform-stat-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:16px;margin-bottom:22px}.platform-stat{background:#fff;border:1px solid #e0e8ee;border-radius:12px;padding:22px;box-shadow:0 5px 16px #16456305}.platform-stat>div{display:flex;align-items:center;justify-content:space-between;color:#76899a;font-size:13px;gap:8px}.platform-stat .ui-icon{width:20px;height:20px;color:#7996a9}.platform-stat strong{display:block;font-size:29px;font-weight:650;color:#20364b;margin:12px 0 8px}.platform-stat small{font-size:10px;color:#9aa9b5}.platform-trend{background:white;border:1px solid #e0e8ee;border-radius:12px;padding:24px}.platform-trend header{display:flex;justify-content:space-between;align-items:center;gap:14px}.platform-trend h2{font-size:16px;margin:0}.platform-trend header p{font-size:11px;color:#93a2af;margin:8px 0 0}.platform-pending{color:#8596a4;background:#f2f5f7;border-radius:20px;padding:6px 11px;font-size:10px;white-space:nowrap}.platform-chart-empty{min-height:270px;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center;gap:13px;margin-top:18px;border-radius:8px;background:repeating-linear-gradient(to bottom,#fff 0,#fff 65px,#edf2f5 66px,#fff 67px)}.platform-chart-empty>.ui-icon{height:42px;width:42px;color:#acc4d2;background:white}.platform-chart-empty h3{font-size:15px;font-weight:500;margin:0;color:#7590a2;background:white;padding:5px}.platform-chart-empty p{font-size:12px;margin:0;color:#9aaebb;line-height:1.8;background:white;padding:0 8px}.platform-trend footer{font-size:10px;color:#9aa9b5;margin-top:20px}.platform-footer{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:20px;flex-wrap:wrap}.platform-footer small{font-size:11px;color:#859db0}
@media(max-width:960px){.platform-dashboard{padding:24px 16px}.platform-heading h1{font-size:23px}.platform-stat{padding:18px}.platform-trend{padding:20px}.platform-heading{align-items:flex-start}}
@media(max-width:560px){.platform-stat-grid{grid-template-columns:1fr}.platform-stat{padding:18px}.platform-trend{padding:18px}}
</style>

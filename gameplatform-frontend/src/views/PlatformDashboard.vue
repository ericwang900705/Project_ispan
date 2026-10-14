<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, session } from '../api'
import UiIcon from '../components/UiIcon.vue'
const data=ref(null), error=ref(''), loading=ref(false)
const number=v=>v==null?'—':new Intl.NumberFormat('zh-TW').format(v)
const metrics=computed(()=>[
 {key:'members',label:'會員總數',icon:'users',value:number(data.value?.memberCount),note:'既有會員資料表'},
 {key:'games',label:'遊戲總數',icon:'game',value:number(data.value?.gameCount),note:'既有遊戲資料表'},
 {key:'orders',label:'本月訂單',icon:'cart',value:number(data.value?.monthlyOrderCount),note:'尚未接入訂單資料'},
 {key:'revenue',label:'本月營收',icon:'wallet',value:data.value?.monthlyRevenue==null?'—':'NT$ '+number(data.value.monthlyRevenue),note:'尚未接入付款資料'}
])
async function load(){
 if(session.user?.role!=='ADMIN')return
 loading.value=true;error.value=''
 try { data.value=await api('/management/dashboard') }
 catch(e){error.value=e.message;data.value=null}
 finally{loading.value=false}
}
onMounted(load)
</script>
<template>
 <section class="platform-dashboard">
  <div class="platform-heading"><div><span class="eyebrow">ADMIN HOME</span><h1>管理員主頁</h1><p class="muted">掌握平台資料概況，從左側選單進入管理功能。</p></div><button class="secondary" :disabled="loading" @click="load">{{loading?'更新中…':'重新整理'}}</button></div>
   <p v-if="error" class="error" role="alert">{{error}}</p>
   <div class="platform-stat-grid"><article v-for="metric in metrics" :key="metric.key" class="platform-stat"><div><span>{{metric.label}}</span><UiIcon :name="metric.icon" /></div><strong>{{loading ? '…' : metric.value}}</strong><small>{{metric.note}}</small></article></div>
   <article class="platform-trend"><header><div><h2>每月營收趨勢</h2><p>依實際訂單與付款資料統計</p></div><span class="platform-pending">尚未接入</span></header><div class="platform-chart-empty"><UiIcon name="trend" /><h3>尚無可用的營收資料</h3><p>接入訂單與付款資料後，這裡會顯示每月營收趨勢。</p></div><footer>未接入的數據顯示「—」，不使用示範數字代替。</footer></article>
   <div class="platform-footer"><small v-if="data?.updatedAt">更新時間：{{new Date(data.updatedAt).toLocaleString('zh-TW',{hour12:false})}}</small></div>
 </section>
</template>

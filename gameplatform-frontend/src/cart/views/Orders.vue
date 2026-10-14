<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../api'
import { currentMemberId } from '../stores/cart'
const orders=ref([]), error=ref(''), loading=ref(false)
const label={PENDING:'待付款',PAID:'已付款',CANCELLED:'已取消',REFUNDED:'已退款'}
async function load(){loading.value=true;error.value='';try{orders.value=await api(`/orders/${currentMemberId()}`)}catch(e){error.value=e.message}finally{loading.value=false}}
async function cancel(id){if(!confirm('取消這筆待付款訂單？已取得的 ATM／超商代碼請勿再繳費。'))return;try{await api(`/orders/${currentMemberId()}/${id}/cancel`,{method:'POST'});await load()}catch(e){error.value=e.message}}
onMounted(load)
</script>
<template>
 <main class="orders-page"><header class="hero-page-heading hero-case-heading"><span class="hero-case-heading__eyebrow">MY ORDERS</span><h1 class="hero-page-heading__title">我的訂單</h1></header>
 <p v-if="error" role="alert">{{error}}</p><p v-if="loading">讀取中…</p><p v-else-if="!orders.length">目前沒有訂單。</p>
 <article v-for="order in orders" :key="order.orderId" class="order-card">
  <h2>{{order.orderNo}} <small>{{label[order.orderStatus] || order.orderStatus}}</small></h2><p>NT$ {{order.totalAmount}} · {{String(order.createdAt).replace('T',' ').slice(0,16)}}</p>
  <ul><li v-for="(item,i) in order.orderItems" :key="i">{{item.game.gameName}} · NT$ {{Number(item.unitPrice)-Number(item.discountAmount)}}</li></ul>
  <p v-if="order.virtualAccount">銀行：{{order.bankCode}}／虛擬帳號：{{order.virtualAccount}}</p><p v-if="order.paymentNo">超商繳費代碼：{{order.paymentNo}}</p><p v-if="order.expireDate">繳費期限：{{order.expireDate}}</p>
  <p v-if="order.orderStatus==='PENDING' && !order.virtualAccount && !order.paymentNo">若未完成付款，可取消訂單後回商城重新加入購物車。</p>
  <button v-if="order.orderStatus==='PENDING'" @click="cancel(order.orderId)">取消訂單</button>
 </article><RouterLink to="/shopping-cart">返回購物車</RouterLink> · <RouterLink to="/member-games">我的遊戲庫</RouterLink>
 </main>
</template>
<style scoped>
.orders-page{max-width:1132px;margin:auto;padding:32px 16px;color:var(--hero-page-ink)}.order-card{margin:18px 0;padding:22px;border:1px solid #bdd3df;border-radius:16px;background:rgba(248,252,255,.95)}.order-card h2{font-size:18px;overflow-wrap:anywhere}.order-card small{font-size:14px;margin-left:12px}.order-card button{padding:8px 16px;border:1px solid #b6cbd9;border-radius:8px;cursor:pointer}
</style>

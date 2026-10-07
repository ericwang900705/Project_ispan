<script setup>
// onMounted：元件顯示到畫面上之後要執行的動作
import { onMounted } from 'vue'
// 共用的購物車 store（遊戲庫資料也放在這裡）
import { useCartStore } from '@/stores/cart'

const cart = useCartStore()
// 進入頁面時向後端抓最新的遊戲庫
onMounted(() => cart.fetchLibrary())

// 取得途徑的中文顯示
const sourceLabel = { PURCHASE: '購買', GIFT: '禮物' }
</script>

<template>
  <!-- 遊戲庫頁面主要內容 -->
  <main class="member-games">
    <h1>遊戲庫</h1>
    <p v-if="cart.library.length === 0">還沒有任何遊戲</p>
    <div class="list">
      <!-- v-for：每筆遊戲庫紀錄顯示一張卡片 -->
      <div v-for="entry in cart.library" :key="entry.libraryId" class="card">
        <img :src="entry.game.coverUrl" :alt="entry.game.gameName" />
        <h3>{{ entry.game.gameName }}</h3>
        <!-- 取得途徑與時間（只顯示日期部分） -->
        <p>{{ sourceLabel[entry.obtainedSource] ?? entry.obtainedSource }}｜{{ entry.obtainedAt?.slice(0, 10) }}</p>
        <!-- 遊玩時長 -->
        <p>已遊玩 {{ entry.totalPlayMinutes }} 分鐘</p>
      </div>
    </div>
  </main>
</template>

<!-- 遊戲庫頁面樣式（scoped：只作用於此元件），與遊戲頁面的卡片一致 -->
<style scoped>
.list { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.card { border: 1px solid #ddd; border-radius: 8px; padding: 12px; }
.card img { width: 100%; height: 160px; object-fit: cover; }
</style>

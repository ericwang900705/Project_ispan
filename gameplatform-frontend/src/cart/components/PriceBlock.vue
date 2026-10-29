<!-- src/components/PriceBlock.vue -->
<!-- 價格區塊：有打折時顯示 綠色 -XX% + 刪除線原價 + 售價，沒打折只顯示售價 -->
<!-- 用法：<PriceBlock :game="game" /> -->
<script setup>
import { hasDiscount, discountPercent } from '@/cart/stores/games'

// 元件接收的參數：整筆遊戲資料（要用 price、originalPrice）
const props = defineProps({
  game: { type: Object, required: true },
  wholeAmounts: { type: Boolean, default: false },
})
const money = value => Number(value).toLocaleString('zh-TW', props.wholeAmounts ? { maximumFractionDigits: 0 } : undefined)
</script>

<template>
  <div class="price-block">
    <!-- 折扣%：有打折才顯示 -->
    <span v-if="hasDiscount(game)" class="pct">-{{ (game.discountPercent ?? discountPercent(game)) }}%</span>
    <span class="prices">
      <!-- 刪除線原價：有打折才顯示 -->
      <s v-if="hasDiscount(game)">NT$ {{ money(game.originalPrice) }}</s>
      <!-- 售價：0 元顯示「免費遊玩」 -->
      <b :class="{ sale: hasDiscount(game) }">{{ game.price === 0 ? '免費遊玩' : `NT$
        ${money(game.price)}` }}</b>
    </span>
  </div>
</template>

<style scoped>
/* 折扣% 和價格並排，靠右 */
.price-block {
  display: inline-flex;
  align-items: stretch;
  line-height: 1.2;
}

/* 折扣%：綠底深色字 */
.pct {
  background: #a4d007;
  color: #1b2b08;
  padding: 4px 6px;
  font-size: 15px;
  font-weight: bold;
  display: flex;
  align-items: center;
}

/* 價格：深色底，原價在上、售價在下 */
.prices {
  background: #16283b;
  padding: 2px 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
}

/* 刪除線原價：較小、灰褐色 */
.prices s {
  color: #8295a7;
  font-size: 11px;
}

/* 售價：米白色；打折時改成金色 */
.prices b {
  color: #e8dcc8;
  font-size: 13px;
  font-weight: normal;
}

.prices b.sale {
  color: #ffffff;
  font-weight: 700;
}
</style>

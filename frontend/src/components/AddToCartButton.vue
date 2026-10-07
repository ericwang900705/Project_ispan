<!-- src/components/AddToCartButton.vue -->
<!-- 用法：<AddToCartButton :game-id="game.gameId" /> -->
<script setup>
import { computed } from 'vue'
import { useCartStore } from '@/stores/cart'

// 元件接收的參數
const props = defineProps({
  // 遊戲 ID（必填）
  gameId: { type: [String, Number], required: true },
})

// 取得共用的購物車 store
const cart = useCartStore()
// 已擁有的遊戲不能再買（遊戲庫變動時自動重新計算）
const owned = computed(() => cart.isOwned(props.gameId))
// 此遊戲是否已在購物車中（購物車內容變動時自動重新計算）
const inCart = computed(() => cart.isInCart(props.gameId))

// 按鈕上顯示的文字，依狀態決定
const label = computed(() => {
  if (owned.value) return '已擁有'
  if (inCart.value) return '已在購物車'
  return '加入購物車'
})

// 點擊按鈕：呼叫 store 把遊戲加入購物車
async function add() {
  try {
    await cart.addItem(props.gameId)
  } catch (e) {
    alert(e.message) // 後端拒絕時顯示原因
  }
}
</script>

<template>
  <!-- 已擁有、已在購物車、或正在處理請求時停用按鈕，避免重複加入 -->
  <button :disabled="owned || inCart || cart.loading" @click="add">
    {{ label }}
  </button>
</template>

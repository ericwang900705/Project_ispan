<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { session } from '../../api'
import { useCartStore } from '@cart/stores/cart'
const props = defineProps({ gameId: { type: [String, Number], required: true } })
const cart = useCartStore(), router = useRouter(), error = ref('')
const owned = computed(() => cart.isOwned(props.gameId))
const inCart = computed(() => cart.isInCart(props.gameId))
const label = computed(() => owned.value ? '已擁有' : inCart.value ? '已在購物車' : '加入購物車')
async function add() {
  error.value = ''
  if (!session.user || !['MEMBER','PUBLISHER'].includes(session.user.role)) { await router.push('/member/login'); return }
  try { await cart.addItem(props.gameId) } catch (e) { error.value = e.message }
}
</script>
<template>
  <button :disabled="owned || inCart || cart.loading" @click="add">{{ label }}</button>
  <p v-if="error" role="alert">{{ error }}</p>
</template>

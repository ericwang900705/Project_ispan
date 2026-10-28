<script setup>
import { ref, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login, session } from '../api'
import { homeFor } from '../permissions'
const props = defineProps({ area: { type: String, required: true } })
const router = useRouter(), route = useRoute()
const name = ref(''), password = ref(''), error = ref(''), busy = ref(false)
async function submit(event) {
  if (busy.value) return

  const fields = new FormData(event.currentTarget)
  const account = String(
    fields.get(`${props.area}-username`) ?? ''
  ).trim()
  const loginPassword = String(
    fields.get(`${props.area}-password`) ?? ''
  )

  if (!account || !loginPassword) {
    error.value = '請輸入帳號和密碼'
    return
  }
  try {
    const openSupport = props.area === 'member' && route.query.support === '1'
    await login(account, loginPassword, props.area)
    const destination = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') && !route.query.redirect.includes('/login') ? route.query.redirect : homeFor(session.user?.role)
    await router.replace(destination)
    if (openSupport && session.user?.role === 'MEMBER') { await nextTick(); window.dispatchEvent(new Event('open-chat')) }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <form @submit.prevent="submit">
    <label>帳號
      <input v-model="name" :name="area + '-username'" autocomplete="username" required maxlength="100"
        :placeholder="area === 'staff' ? '輸入後台帳號' : '輸入會員帳號'" :disabled="busy">
    </label>
    <label>密碼
      <input v-model="password" :name="area + '-password'" type="password" autocomplete="current-password" required
        maxlength="200" placeholder="輸入密碼" :disabled="busy">
    </label>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button class="primary full" :disabled="busy">
      {{ busy ? '登入中…' : area === 'staff' ? '登入工作後台' : '會員登入' }}
    </button>
  </form>
</template>

<script setup>
// 公開帳號求助入口；登入前即可使用密碼重設、重寄驗證信及客服信箱。
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { api, session } from '../api'

const route = useRoute()
const email = ref(''),
  loading = ref(true),
  error = ref('')
const member = computed(() =>
  ['MEMBER', 'PUBLISHER'].includes(session.user?.role),
)
const mailto = computed(
  () =>
    'mailto:' +
    email.value +
    '?subject=' +
    encodeURIComponent('HERO 帳號與登入協助'),
)
let alive = true,
  request = 0
async function loadContact() {
  const id = ++request
  loading.value = true
  error.value = ''
  try {
    const value = await api('/public/support-contact')
    if (alive && id === request) {
      email.value =
        value.available && typeof value.email === 'string' ? value.email : ''
    }
  } catch (e) {
    if (alive && id === request) error.value = e.message
  } finally {
    if (alive && id === request) loading.value = false
  }
}
onMounted(async () => {
  loadContact()
  if (route.query.topic === 'account') {
    await nextTick()
    if (alive)
      document.getElementById('account-help')?.focus({ preventScroll: true })
  }
})
onBeforeUnmount(() => {
  alive = false
  request++
})
</script>

<template>
  <section id="account-help" class="support-account-help" tabindex="-1" aria-labelledby="account-help-title">
    <header>
      <span class="sx-eyebrow">ACCOUNT HELP</span>
      <h2 id="account-help-title">登入遇到問題？</h2>
      <p>不用登入也能使用以下協助，先選擇符合你情況的方式。</p>
    </header>
    <div class="sx-account-actions">
      <RouterLink class="sx-account-action" to="/member/forgot-password">
        <span class="sx-account-symbol" aria-hidden="true">01</span>
        <strong>忘記密碼</strong>
        <span>使用註冊信箱申請密碼重設連結。</span>
        <b>重設密碼 →</b>
      </RouterLink>
      <RouterLink class="sx-account-action" to="/member/verification-sent">
        <span class="sx-account-symbol" aria-hidden="true">02</span>
        <strong>沒收到驗證信</strong>
        <span>輸入註冊信箱重新寄送，也請查看垃圾郵件。</span>
        <b>重寄驗證信 →</b>
      </RouterLink>
      <article class="sx-account-action">
        <span class="sx-account-symbol" aria-hidden="true">03</span>
        <strong>客服信箱</strong>
        <span>上述方式仍無法解決時，來信說明帳號、發生時間與問題。</span>
        <p v-if="loading" role="status">正在載入客服信箱…</p>
        <p v-else-if="error" role="alert">
          信箱資訊暫時無法載入。<button type="button" @click="loadContact">
            重新載入
          </button>
        </p>
        <a v-else-if="email" :href="mailto">{{ email }} ↗</a>
        <p v-else>客服信箱尚未開放，請先使用密碼重設或重寄驗證信。</p>
      </article>
    </div>
    <p class="sx-account-privacy">
      來信時請勿提供密碼或驗證碼。客服信箱會開啟你的郵件軟體，不會自動寄出。
    </p>
    <div v-if="member" class="sx-account-member">
      <div>
        <strong>已經登入，想處理帳號設定？</strong>
        <p>Google 綁定與會員資料可到個人資料設定；其他問題可由線上客服協助。</p>
      </div>
      <div>
        <RouterLink to="/member/profile">個人資料與 Google 綁定</RouterLink>
        <RouterLink to="/member/support?chat=1">詢問線上客服 →</RouterLink>
      </div>
    </div>
    <p v-else class="sx-account-member-note">
      線上聊天室需登入後使用；無法登入時，可以先透過上方功能或客服信箱求助。
    </p>
  </section>
</template>

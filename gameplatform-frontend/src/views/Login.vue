<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api, login, session } from '../api'
const name = ref(''), password = ref(''), error = ref(''), busy = ref(false), demo = ref(false), router = useRouter()
onMounted(async() => {
  try {
    demo.value = (await api('/public/config')).demo
  } catch {}
})
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    await login(name.value, password.value);
    router.push(session.user.role === 'ADMIN' ? '/admin' : '/tickets')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-layout">
    <section class="login-story">
      <span class="eyebrow">
        森遊 SUPPORT CENTER
      </span>
      <h1>
        遊戲之外，
        <br>
        我們也在。
      </h1>
      <p>
        從一個小疑問，到需要追蹤的問題。
        <br>
        先聊聊，讓客服陪你一起解決。
      </p>
      <div class="orbit-art">
        <div>
          ?
        </div>
        <span class="small-orbit">
          ✦
        </span>
      </div>
      <small>
        即時詢問 ／ 案件追蹤 ／ 持續協助
      </small>
    </section>
    <section class="login-card">
      <span class="eyebrow">
        WELCOME BACK
      </span>
      <h2>
        登入客服中心
      </h2>
      <p class="muted">
        使用你的會員或客服帳號繼續。登入有效時間為 15 分鐘，重新整理頁面需再次登入。
      </p>
      <form @submit.prevent="submit">
        <label>
          帳號
          <input v-model="name" autocomplete="username" required placeholder="輸入帳號">
        </label>
        <label>
          密碼
          <input v-model="password" type="password" autocomplete="current-password" required placeholder="輸入密碼">
        </label>
        <p v-if="error" class="error" role="alert">
          {{error}}
        </p>
        <button class="primary full" :disabled="busy">
          {{busy?'登入中…':'登入'}}
        </button>
      </form>
      <div v-if="demo" class="demo-note">
        <strong>
          本機示範帳號
        </strong>
        <p>
          會員：member1／member2
          <br>
          密碼：Member123!
        </p>
        <p>
          客服：admin1／admin2
          <br>
          密碼：Admin123!
        </p>
        <small>
          用一般視窗與無痕視窗，分別登入會員和客服。
        </small>
      </div>
    </section>
  </div>
</template>

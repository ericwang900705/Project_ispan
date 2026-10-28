<script setup>
import LoginForm from '../components/LoginForm.vue'
import GoogleSignInButton from '../components/GoogleSignInButton.vue'
import { ref, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginWithGoogle, session } from '../api'
import { homeFor } from '../permissions'
const route = useRoute(), router = useRouter(), busy = ref(false), googleError = ref('')
let alive = true
onBeforeUnmount(() => { alive = false })
async function googleLogin({ idToken, nonce }) {
  if (busy.value) return; busy.value = true; googleError.value = ''
  const openSupport = route.query.support === '1', value = route.query.redirect
  try {
    await loginWithGoogle(idToken, nonce); if (!alive) return
    const destination = typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.includes('/login') ? value : homeFor(session.user?.role)
    await router.replace(destination)
    if (openSupport && session.user?.role === 'MEMBER') { await nextTick(); window.dispatchEvent(new Event('open-chat')) }
  } catch (e) { if (alive) googleError.value = e.message } finally { if (alive) busy.value = false }
}

</script>

<template>
  <div class="login-layout member-login">

    <!-- 左側品牌介紹 -->
    <section class="login-story">
      <span class="eyebrow">HERO GAME PLATFORM</span>

      <h1>
        探索你的<br>
        <span>遊戲世界。</span>
      </h1>

      <p>
        發現更多精彩遊戲，管理你的遊戲庫，<br>
        開啟專屬於你的遊戲旅程。
      </p>

      <div class="orbit-art" aria-hidden="true">
        <div>H</div>
        <span class="small-orbit">✦</span>
      </div>

      <small>DISCOVER · PLAY · CREATE</small>
    </section>

    <!-- 右側登入卡片 -->
    <section class="login-card" aria-labelledby="member-login-title">
      <span class="eyebrow">WELCOME BACK</span>

      <h2 id="member-login-title">
        會員登入
      </h2>

      <p class="muted">
        歡迎回來！請登入你的會員帳號。
      </p>

      <!-- 保留原本登入功能 -->
      <fieldset :disabled="busy" class="google-password-form">
        <LoginForm area="member" />
      </fieldset>
      <p class="google-divider"><span>或使用 Google 帳號</span></p>
      <GoogleSignInButton :busy="busy" @credential="googleLogin" />
      <p v-if="googleError" class="google-error" role="alert">{{ googleError }}</p>

      <!-- 帳號相關連結靠右 -->
      <nav class="member-login-links">
        <RouterLink to="/member/register">
          建立會員帳號
        </RouterLink>

        <RouterLink to="/member/forgot-password">
          忘記密碼
        </RouterLink>
      </nav>
    </section>

  </div>
</template>
<style scoped>
/* 整體登入頁 */
.member-login {
  box-sizing: border-box;
  width: 100%;
  max-width: 1320px;
  min-height: calc(100dvh - 70px);
  margin: 0 auto;
  padding: 50px 48px;

  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  align-items: center;
  gap: 55px;

  color: #dfe6ff;
}

/* 左側介紹 */
.member-login .login-story {
  position: relative;
  min-width: 0;
  overflow: hidden;
  min-height: 570px;
  padding: 55px 48px;

  display: flex;
  flex-direction: column;
  justify-content: center;

  background: linear-gradient(145deg,
      #192255 0%,
      #111648 65%,
      #0c1034 100%);

  border: 1px solid #35406f;
  border-radius: 26px;
  box-shadow: 0 18px 45px #0003;
}

.member-login .eyebrow {
  display: block;
  margin-bottom: 18px;

  color: #d4a64a;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
}

.member-login .login-story h1 {
  margin: 0 0 24px;
  color: #ffffff;
  font-size: clamp(34px, 3.3vw, 48px);
  line-height: 1.4;
}

.member-login .login-story h1 span {
  color: #f0c060;
}

.member-login .login-story p {
  color: #b9c8eb;
  font-size: 15px;
  line-height: 2;
}

.member-login .login-story>small {
  margin-top: 25px;
  color: #8295c1;
  letter-spacing: 2px;
  font-size: 11px;
}

/* 左側 HERO 裝飾 */
.member-login .orbit-art {
  position: relative;
  display: grid;
  place-items: center;

  width: 180px;
  height: 180px;
  margin: 35px auto 10px;

  border: 2px solid #4c5a91;
  border-radius: 50%;

  background: radial-gradient(circle,
      #24326b 0%,
      #151d4e 70%);
}

.member-login .orbit-art>div {
  display: grid;
  place-items: center;

  width: 118px;
  height: 118px;
  border-radius: 50%;

  background: #202b5d;
  border: 1px solid #d4a64a;

  color: #f0c060;
  font-size: 60px;
  font-weight: 800;
}

.member-login .small-orbit {
  position: absolute;
  top: 10px;
  right: -10px;
  color: #f0c060;
  font-size: 28px;
}

/* 右側登入卡片 */
.member-login .login-card {
  box-sizing: border-box;
  min-width: 0;
  width: 100%;
  padding: 46px 42px;

  background: #151b43;
  border: 1px solid #384474;
  border-radius: 24px;

  box-shadow: 0 18px 50px #0005;
}

.member-login .login-card h2 {
  margin: 0 0 12px;
  color: #ffffff;
  font-size: 30px;
  font-weight: 700;
}

.member-login .login-card .muted {
  margin: 0 0 32px;
  color: #aebcde;
  font-size: 14px;
  line-height: 1.7;
}

/* 登入表單 */
.login-card :deep(form) {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.login-card :deep(form label) {
  display: flex;
  flex-direction: column;
  gap: 9px;

  color: #dfe6ff;
  font-size: 14px;
  font-weight: 600;
}

.login-card :deep(form input) {
  box-sizing: border-box;
  width: 100%;
  height: 48px;
  padding: 0 15px;

  background: #0b1035;
  color: #ffffff;

  border: 1px solid #485383;
  border-radius: 10px;
  outline: none;

  transition: 0.2s;
}

.login-card :deep(form input::placeholder) {
  color: #8593bb;
}

.login-card :deep(form input:focus) {
  border-color: #d4a64a;
  box-shadow: 0 0 0 3px #d4a64a22;
}

.login-card :deep(form input:disabled) {
  opacity: 0.6;
}

/* 登入按鈕 */
.login-card :deep(form .primary) {
  width: 100%;
  min-height: 50px;
  margin-top: 5px;

  background: #d4a64a;
  color: #151735;

  border: 0;
  border-radius: 10px;

  font-size: 15px;
  font-weight: 700;

  cursor: pointer;
  transition: 0.2s;
}

.login-card :deep(form .primary:hover:not(:disabled)) {
  background: #f0c060;
  transform: translateY(-2px);
}

.login-card :deep(form .primary:disabled) {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 登入錯誤提示 */
.login-card :deep(form .error) {
  padding: 12px;
  margin: 0;

  color: #ffcccc;
  background: #4b2535;
  border-radius: 8px;
  font-size: 13px;
}

.member-login .member-login-help {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 16px 18px;
  margin-top: 24px;
  color: #f0c060;
  background: #202b5d;
  border: 1px solid #485383;
  border-radius: 12px;
  text-decoration: none;
}

.member-login .member-login-help span {
  color: #b9c8eb;
  font-size: 12px;
  line-height: 1.7;
}

.member-login .member-login-help:hover {
  border-color: #d4a64a;
  background: #29366a;
}

.member-login .member-login-help:focus-visible {
  outline: 3px solid #d4a64a;
  outline-offset: 3px;
}

/* 底部建立會員帳號等連結，靠右 */
.member-login .member-login-links {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;

  gap: 12px 20px;
  margin-top: 28px;
  padding-top: 22px;

  border-top: 1px solid #35406f;
  text-align: right;
}

.member-login .member-login-links a {
  color: #d4a64a;
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
}

.member-login .member-login-links a:hover {
  color: #ffe1a2;
  text-decoration: underline;
}

/* 平板與手機 */
@media (max-width: 1000px) {
  .member-login {
    grid-template-columns: 1fr;
    max-width: 650px;
    padding: 35px 20px;
    gap: 22px;
  }

  .member-login .login-story {
    min-height: auto;
    padding: 30px;
  }

  .member-login .login-story h1 {
    font-size: 32px;
  }

  .member-login .orbit-art {
    display: none;
  }

  .member-login .login-card {
    padding: 35px 28px;
  }
}

@media (max-width: 600px) {
  .member-login {
    padding: 20px 12px;
  }

  .member-login .login-card {
    padding: 28px 20px;
  }

  .member-login .member-login-links {
    gap: 10px 15px;
  }
}

.google-password-form {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}

.google-divider {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #aebcde;
  margin: 26px 0 16px;
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: .04em;
}

.google-divider::before,
.google-divider::after {
  content: '';
  height: 1px;
  flex: 1;
  background: linear-gradient(90deg, transparent, #62749a80);
}

.google-divider::after {
  transform: rotate(180deg);
}

.google-divider span {
  flex-shrink: 0;
}

.member-login :deep(.google-signin) {
  --google-frame-bg: linear-gradient(135deg, #202b50, #141d3d);
  --google-frame-border: #cba34b52;
  --google-frame-shadow: 0 8px 22px #05091d30;
  --google-status-color: #bccbea;
  --google-focus-color: #d4a64a;
}

.google-error {
  padding: 12px;
  color: #ffcccc;
  background: #4b2535;
  border-radius: 8px;
  overflow-wrap: anywhere;
}
</style>

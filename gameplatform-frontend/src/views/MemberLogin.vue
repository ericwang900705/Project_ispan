<script setup>
import LoginForm from '../components/LoginForm.vue'

</script>
<template>
  <div class="login-layout member-login">
    <section class="login-story">
      <span class="eyebrow">HERO GAME PLATFORM</span>
      <h1>遊戲之外，<br>我們也在。</h1>
      <p>會員與發行商共用此登入入口，<br>登入後依身分進入專屬介面。</p>
      <div class="orbit-art" aria-hidden="true"><div>?</div><span class="small-orbit">✦</span></div>
      <small>即時詢問 ／ 案件追蹤 ／ 持續協助</small>
    </section>
    <section class="login-card" aria-labelledby="member-login-title">
      <span class="eyebrow">WELCOME BACK</span>
      <h2 id="member-login-title">會員登入</h2>
      <p class="muted">會員可使用客服服務；發行商可管理自己的遊戲與優惠活動。</p>
      <LoginForm area="member" />
      <div class="member-login-switch"><RouterLink to="/member/register">建立會員帳號</RouterLink><span aria-hidden="true">　·　</span><RouterLink to="/member/forgot-password">忘記密碼</RouterLink><span aria-hidden="true">　·　</span><RouterLink to="/staff/login">後台人員登入 →</RouterLink></div>
    </section>
  </div>
</template>

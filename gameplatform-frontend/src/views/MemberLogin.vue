<script setup>
import LoginForm from '../components/LoginForm.vue'

</script>
<template>
  <div class="login-layout member-login">
    <section class="login-story">
      <span class="eyebrow">HERO GAME PLATFORM</span>
      <h1>遊戲之外，<br>我們也在。</h1>
      <p>使用既有會員帳號登入，<br>即可聯絡客服與追蹤案件。</p>
      <div class="orbit-art" aria-hidden="true"><div>?</div><span class="small-orbit">✦</span></div>
      <small>即時詢問 ／ 案件追蹤 ／ 持續協助</small>
    </section>
    <section class="login-card" aria-labelledby="member-login-title">
      <span class="eyebrow">WELCOME BACK</span>
      <h2 id="member-login-title">會員登入</h2>
      <p class="muted">沿用小組既有會員帳號；此版只提供客服功能。</p>
      <LoginForm area="member" />

    </section>
  </div>
</template>

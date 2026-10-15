<script setup>
import LoginForm from '../components/LoginForm.vue'

</script>
<template>
  <section class="staff-login">
    <div class="staff-login-brand">
      <strong>赫洛</strong>
      <span>STAFF WORKSPACE</span>
    </div>
    <div class="login-card staff-login-card" aria-labelledby="staff-login-title">
      <span class="eyebrow">後台人員專用</span>
      <h1 id="staff-login-title">工作後台登入</h1>
      <p class="muted">管理者及客服人員<br>請使用你的後台帳號登入。</p>
      <LoginForm area="staff" />
      <p class="staff-login-note">管理者進入管理主頁，客服人員進入客服工作區。</p>
    </div>
  </section>
</template>

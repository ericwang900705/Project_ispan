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
      <p class="muted">最高管理員、管理員、審核及客服人員<br>請使用你的後台帳號登入。</p>
      <LoginForm area="staff" />
      <RouterLink class="staff-register-link" to="/staff/register">建立後台帳號，等待最高管理員授權</RouterLink>
      <p class="staff-login-note">最高管理員進入管理主頁，其他人員依個別授權進入工作後台。</p>
    </div>
  </section>
</template>

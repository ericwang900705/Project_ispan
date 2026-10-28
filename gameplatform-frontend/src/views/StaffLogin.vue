<script setup>
// 後台人員登入頁；使用共用品牌背景，保留原表單流程。
import StaffAuthShell from '../components/StaffAuthShell.vue'
import LoginForm from '../components/LoginForm.vue'
import UiIcon from '../components/UiIcon.vue'

</script>
<template>
  <StaffAuthShell>
    <section class="staff-login">

      <div class="login-card staff-login-card" aria-labelledby="staff-login-title">
        <span class="eyebrow">後台人員專用</span>
        <h1 id="staff-login-title">工作後台登入</h1>
        <p class="muted">最高管理員、管理員、審核及客服人員<br>請使用你的後台帳號登入。</p>
        <LoginForm area="staff" />
        <div class="staff-account-entry">
          <span class="account-divider">尚未建立後台帳號？</span>
          <RouterLink class="staff-register-link" to="/staff/register">
            <UiIcon name="plus" />
            <span>建立後台帳號</span>
            <UiIcon name="chevron" />
          </RouterLink>
          <small>建立後需等待最高管理員授權</small>
        </div>
        <p class="staff-login-note">最高管理員進入管理主頁，其他人員依個別授權進入工作後台。</p>
      </div>
    </section>
  </StaffAuthShell>
</template>

<style scoped>
.staff-login .staff-account-entry {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 24px;
}

.account-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8a9fab;
  font-size: 11px;
  white-space: nowrap;
}

.account-divider::before,
.account-divider::after {
  content: '';
  height: 1px;
  background: #e0eaf0;
  flex: 1;
}

.staff-login .staff-register-link {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 46px;
  width: 100%;
  margin: 0;
  padding: 12px 16px;
  border: 1px solid #cfe2e9;
  border-radius: 10px;
  background: #f4fafb;
  color: #377b8e;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  text-decoration: none;
  transition: border-color .15s, background .15s;
}

.staff-register-link svg {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.staff-login .staff-register-link:hover {
  background: #e8f5f8;
  border-color: #9fcbd7;
}

.staff-login .staff-register-link:focus-visible {
  outline: 3px solid #75c1d3;
  outline-offset: 3px;
}

.staff-account-entry small {
  text-align: center;
  font-size: 11px;
  color: #8aa0ad;
  line-height: 1.7;
}

.staff-login .staff-login-note {
  padding-top: 18px;
  border-top: 1px solid #e3edf2;
  margin-top: 23px;
  font-size: 11px;
  color: #8aa0ad;
}
</style>

<script setup>
// 後台人員註冊頁；使用共用品牌背景，保留原表單流程。
import StaffAuthShell from '../components/StaffAuthShell.vue'
import { ref } from 'vue'
import { api } from '../api'
import UiIcon from '../components/UiIcon.vue'
const form = ref({ username: '', name: '', email: '', password: '' })
const confirmPassword = ref(''), busy = ref(false), error = ref(''), notice = ref('')
async function submit() {
  if (busy.value) return
  error.value = ''; notice.value = ''
  if (form.value.password !== confirmPassword.value) { error.value = '兩次密碼不同'; return }
  busy.value = true
  try {
    const result = await api('/auth/staff/register', { method: 'POST', body: form.value })
    notice.value = result.message
    form.value = { username: '', name: '', email: '', password: '' }; confirmPassword.value = ''
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <StaffAuthShell register>
    <section class="staff-register-page">

      <div class="staff-register" aria-labelledby="staff-register-title">
        <header class="register-heading">
          <span class="register-icon">
            <UiIcon name="users" />
          </span>
          <span class="register-eyebrow">加入工作後台</span>
          <h1 id="staff-register-title">建立後台人員帳號</h1>
          <p>填寫基本資料，開始建立你的後台帳號。</p>
        </header>
        <div class="register-tip">
          <UiIcon name="shield" />
          <p>新帳號將設為待授權，經最高管理員分配角色與權限後，即可登入工作後台。</p>
        </div>
        <p v-if="error" class="register-feedback error-feedback" role="alert">{{ error }}</p>
        <p v-if="notice" class="register-feedback success-feedback" role="status">{{ notice }}</p>
        <form class="register-form" @submit.prevent="submit">
          <label>帳號
            <input v-model.trim="form.username" required pattern="[A-Za-z0-9_]{4,50}" maxlength="50"
              autocomplete="username" placeholder="4–50 位英文字母、數字或底線" :disabled="busy">
          </label>
          <label>姓名
            <input v-model.trim="form.name" required maxlength="50" autocomplete="name" placeholder="輸入你的姓名"
              :disabled="busy">
          </label>
          <label>信箱
            <input v-model.trim="form.email" type="email" required maxlength="100" autocomplete="email"
              placeholder="name@example.com" :disabled="busy">
          </label>
          <label>密碼
            <input v-model="form.password" type="password" required minlength="12" maxlength="72"
              autocomplete="new-password" placeholder="至少 12 個字元" :disabled="busy">
          </label>
          <label>確認密碼
            <input v-model="confirmPassword" type="password" required autocomplete="new-password" placeholder="再次輸入密碼"
              :disabled="busy">
          </label>
          <button class="primary register-submit" :disabled="busy">
            <UiIcon name="plus" />{{ busy ? '建立中…' : '建立待授權帳號' }}
          </button>
        </form>
        <footer class="register-footer">
          <span>已經有後台帳號？</span>
          <RouterLink class="register-return" to="/staff/login">
            <span aria-hidden="true">←</span>
            返回後台登入
          </RouterLink>
        </footer>
      </div>
    </section>
  </StaffAuthShell>
</template>
<style scoped>
.staff-register-page {
  width: min(100% - 40px, 500px);
  margin: 0 auto;
  padding: 38px 0 42px;
}

.staff-register-page,
.staff-register-page * {
  box-sizing: border-box;
}

.register-brand {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 14px;
  margin-bottom: 24px;
  color: #153c64;
}

.register-brand strong {
  font-size: 28px;
  font-weight: 650;
}

.register-brand span {
  font-size: 11px;
  letter-spacing: 2px;
}

.staff-register {
  padding: 32px 36px;
  border: 1px solid #d6e6ed;
  border-top: 4px solid #17647d;
  border-radius: 20px;
  background: rgba(255, 255, 255, .97);
  box-shadow: 0 18px 48px #173f5d14;
  color: #28485d;
}

.register-heading {
  position: relative;
  padding-right: 54px;
  margin-bottom: 22px;
}

.register-icon {
  position: absolute;
  right: 0;
  top: 1px;
  display: grid;
  place-items: center;
  width: 43px;
  height: 43px;
  border-radius: 13px;
  background: #e8f4f6;
  color: #29869a;
}

.register-icon svg {
  width: 22px;
  height: 22px;
}

.register-eyebrow {
  display: block;
  font-size: 11px;
  letter-spacing: 1px;
  color: #668d9c;
  margin-bottom: 10px;
}

.staff-register h1 {
  font-size: 26px;
  line-height: 1.4;
  font-weight: 650;
  margin: 0 0 10px;
  color: #203f56;
}

.register-heading p {
  font-size: 12px;
  line-height: 1.8;
  margin: 0;
  color: #8196a3;
}

.register-tip {
  display: flex;
  align-items: start;
  gap: 10px;
  padding: 13px 15px;
  border: 1px solid #ddecef;
  border-radius: 11px;
  background: #f1f8fa;
  margin-bottom: 23px;
}

.register-tip svg {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  color: #5797a7;
  margin-top: 2px;
}

.register-tip p {
  margin: 0;
  font-size: 11px;
  line-height: 1.8;
  color: #6b8797;
}

.staff-register .register-form {
  display: grid;
  gap: 17px;
  margin: 0;
}

.staff-register .register-form label {
  display: grid;
  gap: 8px;
  margin: 0;
  color: #4f6f82;
  font-size: 12px;
  font-weight: 600;
}

.staff-register .register-form input {
  width: 100%;
  height: 44px;
  margin: 0;
  padding: 0 13px;
  border: 1px solid #d4e2ea;
  border-radius: 9px;
  background: #fbfdfe;
  font-size: 13px;
  font-weight: 400;
  color: #294e65;
  transition: border-color .15s, box-shadow .15s;
}

.register-form input::placeholder {
  color: #97aab7;
  font-size: 12px;
}

.staff-register .register-form input:focus {
  border-color: #62acbf;
  box-shadow: 0 0 0 3px #148ca412;
  outline: none;
}

.staff-register .register-submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  width: 100%;
  min-height: 46px;
  padding: 12px 16px;
  margin: 6px 0 0;
  border: 0;
  border-radius: 10px;
  background: linear-gradient(110deg, #13a1b1, #097e9b);
  color: white;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 5px 12px #087e9b16;
}

.register-submit svg {
  height: 17px;
  width: 17px;
}

.register-submit:disabled {
  opacity: .6;
  cursor: wait;
}

.staff-register .register-footer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 24px;
  padding: 20px 0 0;
  border-top: 1px solid #e4edf2;
  text-align: center;
  color: #8b9eab;
  font-size: 11px;
}

.staff-register .register-return {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 11px 16px;
  border: 1px solid #d7e5ec;
  border-radius: 10px;
  background: #fff;
  color: #4c7d90;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background .15s, border-color .15s;
}

.register-return:hover {
  background: #f0f8fa;
  border-color: #a9ceda;
}

.register-return:focus-visible,
.register-submit:focus-visible {
  outline: 3px solid #75c1d3;
  outline-offset: 3px;
}

.register-feedback {
  padding: 12px 14px;
  border-radius: 9px;
  font-size: 12px;
  line-height: 1.8;
}

.error-feedback {
  background: #fff1f1;
  color: #a55454;
}

.success-feedback {
  background: #edf8f2;
  color: #38765f;
}

@media(max-width:520px) {
  .staff-register-page {
    width: calc(100% - 28px);
    padding: 26px 0;
  }

  .staff-register {
    padding: 27px 22px;
  }

  .staff-register h1 {
    font-size: 22px;
  }

  .register-heading {
    padding-right: 42px;
  }

  .register-icon {
    width: 35px;
    height: 35px;
    border-radius: 10px;
  }

  .register-icon svg {
    width: 18px;
    height: 18px;
  }
}
</style>

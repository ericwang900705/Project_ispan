<script setup>
import { computed, ref, watch, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, session } from '../api'

const route = useRoute()
const router = useRouter()
const action = computed(() => route.path.split('/').at(-1))
const loading = ref(false)
const error = ref('')
const notice = ref('')
const profile = ref(null)
const profileSection = ref('profile')
const avatarFailed = ref(false)
const birthdayLocked = computed(() => !!profile.value?.birthDate)
const birthdayText = computed(() => String(profile.value?.birthDate || '').replaceAll('-', ' / '))
const avatarPreview = computed(() => /^https?:\/\//.test(form.value.avatarUrl) ? form.value.avatarUrl : '')
const watchTicket = ref(sessionStorage.getItem('hero-verification-watch') || '')
const verificationComplete = ref(false)
const verificationStatusNotice = ref('')
let monitorTimer = null, monitorGeneration = 0
function stopMonitor() { monitorGeneration++; clearTimeout(monitorTimer); monitorTimer = null }
function clearWaiting() {
  for (const key of ['hero-verification-email','hero-verification-sent','hero-verification-watch']) sessionStorage.removeItem(key)
}
function rememberWatch(result) {
  watchTicket.value = result.watchTicket || ''
  if (watchTicket.value) sessionStorage.setItem('hero-verification-watch', watchTicket.value)
}
async function monitorVerification(generation) {
  if (generation !== monitorGeneration || action.value !== 'verification-sent' || !watchTicket.value) return
  const ticket = watchTicket.value
  try {
    const status = await api('/auth/verification-status?ticket=' + encodeURIComponent(ticket))
    if (generation !== monitorGeneration || ticket !== watchTicket.value) return
    if (status.verified) { stopMonitor(); clearWaiting(); await router.replace('/member/login'); return }
    verificationStatusNotice.value = '等待信箱驗證，完成後本頁會自動前往登入。'
  } catch (e) {
    if (generation !== monitorGeneration) return
    verificationStatusNotice.value = e.message
    if ([400,410].includes(e.status)) return
  }
  if (generation === monitorGeneration && action.value === 'verification-sent') monitorTimer = setTimeout(() => monitorVerification(generation), 3000)
}
function startMonitor() { stopMonitor(); if (watchTicket.value) monitorVerification(monitorGeneration) }
onUnmounted(stopMonitor)
const verificationEmail = ref(sessionStorage.getItem('hero-verification-email') || '')
const verificationSent = ref(sessionStorage.getItem('hero-verification-sent') === 'true')
const form = ref({ username: '', email: '', nationalId: '', rawPassword: '', confirmPassword: '',
  oldPassword: '', newPassword: '', birthDate: '', address: '', avatarUrl: '' })
watch(() => form.value.avatarUrl, () => { avatarFailed.value = false })
const title = computed(() => ({register: '建立會員帳號', verify:verificationComplete.value ? '信箱驗證完成' : '驗證會員信箱', 'verification-sent': verificationSent.value ? '已發送驗證信' : '信箱驗證',
  'forgot-password':'忘記密碼', 'reset-password':'重設密碼', profile:profileSection.value === 'password' ? '更改密碼' : '個人資料'})[action.value] || '會員中心')
const isProfile = computed(() => action.value === 'profile')
watch(() => route.fullPath, async () => {
  stopMonitor(); error.value = ''; notice.value = ''
  if (isProfile.value) await loadProfile()
  if (action.value === 'verification-sent') { form.value.email = verificationEmail.value; startMonitor() }
  if (action.value === 'verify') await verify()
}, {immediate:true})
async function loadProfile() {
  if (!session.user) return
  try {
    profile.value = await api('/members/me')
    form.value.birthDate = profile.value.birthDate || ''
    form.value.address = profile.value.address || ''
    form.value.avatarUrl = profile.value.avatarUrl || ''
  } catch(e) { error.value = e.message }
}
async function send(path, body, method = 'POST') {
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const result = await api(path, {method, body})
    notice.value = result?.message || '操作成功'
    return result
  } catch (e) { error.value = e.message; return null }
  finally { loading.value = false }
}
async function register() {
  if (form.value.rawPassword !== form.value.confirmPassword) { error.value = '兩次輸入的密碼不同'; return }
  const result = await send('/auth/register', {
    username:form.value.username, email:form.value.email,
    nationalId:form.value.nationalId, rawPassword:form.value.rawPassword
  })
  if (result) {
    rememberWatch(result)
    verificationEmail.value = form.value.email
    verificationSent.value = true
    sessionStorage.setItem('hero-verification-email', verificationEmail.value)
    sessionStorage.setItem('hero-verification-sent', 'true')
    form.value.rawPassword = ''; form.value.confirmPassword = ''; form.value.nationalId = ''
    await router.push('/member/verification-sent')
  }
}
async function forgot() { await send('/auth/forgot-password',{email:form.value.email}) }
async function resendVerification() {
  if (loading.value) return
  const result = await send('/auth/resend-verification', {email:form.value.email})
  if (result) {
    rememberWatch(result)
    verificationEmail.value = form.value.email
    verificationSent.value = true
    sessionStorage.setItem('hero-verification-email', verificationEmail.value)
    sessionStorage.setItem('hero-verification-sent', 'true')
    startMonitor()
  }
}
async function resetPassword() {
  if (form.value.newPassword !== form.value.confirmPassword) { error.value = '兩次輸入的密碼不同'; return }
  const result = await send('/auth/reset-password', {token: route.query.token, newPassword:form.value.newPassword})
  if (result) form.value.newPassword = form.value.confirmPassword = ''
}
async function verify() {
  if (!route.query.token) { error.value = '缺少驗證碼，請使用電子郵件中的驗證連結'; return }
  const token = route.query.token
  const result = await send('/auth/verify?token='+encodeURIComponent(token), null, 'GET')
  if (result && action.value === 'verify' && route.query.token === token) {
    verificationComplete.value = true
    if (watchTicket.value) { clearWaiting(); await router.replace('/member/login') }
    else notice.value = '驗證完成！原本的註冊等待頁會自動前往登入，請返回原頁。'
  }
}
async function updateProfile() {
  loading.value = true;error.value = '';notice.value = ''
  try {
    profile.value = await api('/members/profile', {method:'PATCH',body:{
      birthDate:birthdayLocked.value ? null : form.value.birthDate || null,address:form.value.address,avatarUrl:form.value.avatarUrl
    }})
    form.value.birthDate = profile.value.birthDate || ''
    notice.value = '個人資料已更新'
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function changePassword() {
  if (form.value.newPassword !== form.value.confirmPassword) { error.value = '兩次輸入的新密碼不同'; return }
  const result = await send('/members/change-password', {
    oldPassword:form.value.oldPassword, newPassword:form.value.newPassword
  })
  if (result) form.value.oldPassword = form.value.newPassword = ''
}
</script>
<template>
  <main class="member-account-wrap" :class="{'member-profile-layout':isProfile}">
    <aside v-if="isProfile" class="member-profile-nav" aria-label="會員設定">
      <strong>會員設定</strong>
      <button type="button" :class="{active:profileSection==='profile'}" :aria-pressed="profileSection==='profile'" @click="profileSection='profile';error='';notice=''">個人資料</button>
      <button type="button" :class="{active:profileSection==='password'}" :aria-pressed="profileSection==='password'" @click="profileSection='password';error='';notice=''">更改密碼</button>
    </aside>
    <section class="member-account-card">
      <span class="eyebrow">HERO MEMBERSHIP</span>
      <h1>{{ title }}</h1>
      <p class="member-account-lead">{{ action === 'register' ? '建立帳號，開始探索你的遊戲世界。' : action === 'verification-sent' ? '請開啟電子郵件中的連結完成驗證。' : '會員資料與帳號安全管理' }}</p>
      <p v-if="error" class="member-account-error" role="alert">{{ error }}</p>
      <p v-if="notice" class="member-account-success" role="status">{{ notice }}</p>
      <form v-if="action === 'register'" @submit.prevent="register">
        <label>帳號 <input v-model.trim="form.username" autocomplete="username" required minlength="4" maxlength="30" placeholder="4～30 位英數字或底線" /></label>
        <label>電子郵件 <input v-model.trim="form.email" type="email" autocomplete="email" required maxlength="100" /></label>
        <label>身分證字號 <input v-model.trim="form.nationalId" autocomplete="off" required maxlength="10" placeholder="台灣身分證字號" /></label>
        <label>密碼 <input v-model="form.rawPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="72" /></label>
        <label>確認密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="72" /></label>
        <button class="primary" :disabled="loading">{{loading?'建立中…':'建立帳號'}}</button>
      </form>
      <form v-else-if="action === 'verification-sent'" @submit.prevent="resendVerification">
        <p v-if="verificationSent">驗證信已寄送至 <strong>{{ verificationEmail }}</strong>，請查看收件匣或垃圾郵件。完成信箱驗證後會自動前往會員登入。</p>
        <p v-if="verificationStatusNotice" role="status">{{ verificationStatusNotice }}</p>
        <p>沒有收到或連結過期？確認註冊信箱後重新寄送。</p>
        <label>電子郵件 <input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <button class="primary" :disabled="loading">{{ loading ? '寄送中…' : '重發驗證信' }}</button>
      </form>
      <form v-else-if="action === 'forgot-password'" @submit.prevent="forgot">
        <p>輸入註冊信箱，我們將寄送密碼重設連結。</p>
        <label>電子郵件 <input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <button class="primary" :disabled="loading">寄送重設密碼信</button>
      </form>
      <form v-else-if="action === 'reset-password'" @submit.prevent="resetPassword">
        <label>新密碼 <input v-model="form.newPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="72" /></label>
        <label>確認新密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" required minlength="8" maxlength="72" /></label>
        <button class="primary" :disabled="loading || !route.query.token">確定重設密碼</button>
      </form>
      <div v-else-if="action === 'verify'">
        <p>{{ loading ? '正在確認信箱驗證，請稍候…' : '請使用驗證郵件內的連結完成啟用。' }}</p>
        <button v-if="!verificationComplete" class="primary" type="button" :disabled="loading || !route.query.token" @click="verify">{{loading?'驗證中…':'完成信箱驗證'}}</button>
      </div>
      <template v-else-if="isProfile">
        <template v-if="profileSection === 'profile'">
          <div v-if="profile" class="member-profile-identity">
            <div class="member-avatar">
              <img v-if="avatarPreview && !avatarFailed" :src="avatarPreview" alt="會員頭像" referrerpolicy="no-referrer" @error="avatarFailed=true" />
              <span v-else>{{ (profile.username || '會員').slice(0,2) }}</span>
            </div>
            <div><strong>{{profile.username}}</strong><p>{{profile.email}}</p><small>{{profile.isPublisher?'發行商':'會員'}}</small></div>
          </div>
          <form @submit.prevent="updateProfile">
            <div class="profile-birthday"><span>生日</span>
              <p v-if="birthdayLocked" class="profile-static-date">{{ birthdayText }}</p>
              <template v-else><input v-model="form.birthDate" type="date" aria-label="生日" /><small>生日只能設定一次，儲存後無法修改。</small></template>
            </div>
            <label>地址 <input v-model="form.address" maxlength="500" /></label>
            <label>頭像網址 <input v-model="form.avatarUrl" type="url" maxlength="500" placeholder="https://" /></label>
            <button class="primary" :disabled="loading">儲存個人資料</button>
          </form>
        </template>
        <form v-else @submit.prevent="changePassword">
          <label>目前密碼 <input v-model="form.oldPassword" type="password" autocomplete="current-password" required /></label>
          <label>新密碼 <input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required /></label>
          <label>確認新密碼 <input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required /></label>
          <button class="primary" :disabled="loading">修改密碼</button>
        </form>
      </template>
      <div class="member-account-links">
        <RouterLink to="/member/login">會員登入</RouterLink>
        <RouterLink to="/">返回商城</RouterLink>
      </div>
    </section>
  </main>
</template>
<style scoped>
.member-account-wrap{width:min(100% - 32px,620px);margin:clamp(36px,5vw,76px) auto 70px;color:#17364e}
.member-account-card{padding:clamp(24px,4vw,44px);background:rgba(247,252,255,.94);border:1px solid rgba(125,170,202,.55);border-radius:22px;box-shadow:0 16px 48px rgba(18,59,95,.15)}
.member-account-card h1{margin:8px 0 6px;font-size:clamp(27px,3vw,35px);color:#17364e}
.member-account-lead,.member-account-hint{color:#45637b}
.member-account-card form{display:grid;gap:14px;margin-top:24px}
.member-account-card h2{font-size:19px;color:#17364e;margin:6px 0 0}
.member-account-card label{display:grid;gap:7px;color:#17364e;font-weight:600}
.member-account-card input{width:100%;box-sizing:border-box;border:1px solid #91b2cd;border-radius:10px;background:#fff;color:#17364e;padding:12px 13px;font-size:16px}
.member-account-card input:focus{outline:2px solid #1784a4;outline-offset:1px}
.member-account-card button.primary{width:100%;min-height:45px;border:0;border-radius:10px;background:#096a86;color:white;font-size:16px;font-weight:700;cursor:pointer}
.member-account-card button:disabled{opacity:.55;cursor:not-allowed}
.member-account-links{display:flex;justify-content:flex-end;flex-wrap:wrap;gap:12px 20px;margin-top:24px;padding-top:20px;border-top:1px solid rgba(125,170,202,.35)}
.member-account-links a{color:#0b678b;font-weight:650;text-decoration:underline}
.member-account-hint{display:block;margin-top:24px;line-height:1.6}
.member-account-error{padding:12px;background:#fff0ed;color:#942a1a;border-radius:9px}
.member-account-success{padding:12px;background:#edf9f8;color:#115c55;border-radius:9px}
.member-profile-summary{margin:22px 0;background:rgba(209,230,244,.66);padding:12px 18px;border-radius:12px}
.member-profile-summary p{margin:8px 0}
@media(max-width:480px){.member-account-wrap{width:calc(100% - 24px);margin:25px auto 40px}.member-account-card{padding:20px}}
.member-profile-layout{width:min(100% - 32px,820px);display:grid;grid-template-columns:160px minmax(0,1fr);align-items:start;gap:20px;margin:36px auto 56px}
.member-profile-layout .member-account-card{padding:26px;border-radius:18px}
.member-profile-nav{display:grid;gap:8px;padding:18px 12px;background:rgba(247,252,255,.94);border:1px solid var(--hero-page-border);border-radius:15px;position:sticky;top:94px}
.member-profile-nav strong{padding:5px 10px 12px;font-size:14px}
.member-profile-nav button{text-align:left;padding:12px;border-radius:9px;color:#45637b;background:transparent}
.member-profile-nav button.active{background:#d8edf6;color:#095c77;font-weight:700}
.member-profile-identity{display:flex;align-items:center;gap:16px;margin:22px 0;padding-bottom:20px;border-bottom:1px solid #d1e2ec}
.member-profile-identity strong{font-size:18px}.member-profile-identity p{margin:5px 0;font-size:14px;overflow-wrap:anywhere}.member-profile-identity small{color:#45637b}
.member-avatar{width:76px;height:76px;flex:none;border-radius:50%;overflow:hidden;background:#d8edf6;display:grid;place-items:center;color:#0b678b;font-size:23px;font-weight:700;border:2px solid #fff}
.member-avatar img{width:100%;height:100%;object-fit:cover}
.profile-birthday{display:grid;gap:7px;font-weight:600}.profile-birthday small{font-size:12px;font-weight:400;color:#45637b}.profile-static-date{margin:0;padding:10px 0;font-variant-numeric:tabular-nums;font-size:16px}
@media(max-width:650px){.member-profile-layout{grid-template-columns:1fr;gap:12px}.member-profile-nav{position:static;grid-template-columns:1fr 1fr;padding:10px}.member-profile-nav strong{display:none}.member-profile-nav button{text-align:center}.member-profile-layout .member-account-card{padding:22px}}
</style>
